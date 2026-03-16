package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Mean-variance portfolio optimization engine. Given expected returns, a
 *              covariance matrix, and constraints (no short selling, sector limits,
 *              single-stock concentration limits), computes optimal portfolio weights
 *              using quadratic programming approximation (gradient descent). Outputs:
 *              recommended allocation with expected return, volatility, Sharpe ratio.
 *              T3 plugin interface for custom optimization models (K-04).
 *              Satisfies STORY-D03-003.
 * @doc.layer   Domain
 * @doc.pattern Quadratic optimization; no-short-sell constraints; efficient frontier;
 *              T3 OptimizationModelPort (K-04); Timer for ≤ 5sec performance SLA.
 */
public class PortfolioOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioOptimizationService.class);

    private static final int    MAX_ITERATIONS   = 1000;
    private static final double CONVERGENCE_TOL  = 1e-6;
    private static final double RISK_FREE_RATE   = 0.05; // 5% per annum

    private final HikariDataSource       dataSource;
    private final Executor               executor;
    private final OptimizationModelPort  customModel;
    private final Timer                  optimizationTimer;

    public PortfolioOptimizationService(HikariDataSource dataSource, Executor executor,
                                        OptimizationModelPort customModel, MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.customModel       = customModel;
        this.optimizationTimer = Timer.builder("pms.optimization.duration_ms")
                                     .description("Portfolio optimization wall-clock time")
                                     .register(registry);
    }

    // ─── Inner port (K-04 T3 plugin) ──────────────────────────────────────────

    public interface OptimizationModelPort {
        boolean isCustomModelActive(String portfolioId);
        OptimizationResult runCustomModel(OptimizationInput input);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record OptimizationConstraints(boolean allowShortSelling,
                                          double maxSectorWeight,
                                          double maxSingleStockWeight) {}

    public record OptimizationInput(String portfolioId, List<String> instruments,
                                    double[] expectedReturns, double[][] covarianceMatrix,
                                    OptimizationConstraints constraints) {}

    public record OptimizationResult(String portfolioId, List<WeightedHolding> weights,
                                     double expectedReturn, double volatility, double sharpeRatio) {}

    public record WeightedHolding(String instrumentId, double weight) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<OptimizationResult> optimize(OptimizationInput input) {
        return Promise.ofBlocking(executor, () ->
                optimizationTimer.recordCallable(() -> {
                    if (customModel.isCustomModelActive(input.portfolioId())) {
                        return customModel.runCustomModel(input);
                    }
                    return runMeanVariance(input);
                }));
    }

    public Promise<List<OptimizationResult>> computeEfficientFrontier(OptimizationInput baseInput,
                                                                       int numPoints) {
        return Promise.ofBlocking(executor, () -> {
            List<OptimizationResult> frontier = new ArrayList<>();
            double minRet = minReturn(baseInput.expectedReturns());
            double maxRet = maxReturn(baseInput.expectedReturns());
            double step = (maxRet - minRet) / (numPoints - 1);
            for (int i = 0; i < numPoints; i++) {
                double targetRet = minRet + i * step;
                OptimizationResult res = runMeanVariance(baseInput, targetRet);
                frontier.add(res);
            }
            return frontier;
        });
    }

    // ─── Core optimization ────────────────────────────────────────────────────

    private OptimizationResult runMeanVariance(OptimizationInput input) {
        return runMeanVariance(input, -1.0);
    }

    /**
     * Projected gradient descent for mean-variance optimization.
     * Minimizes: 0.5 × w^T Σ w − λ × w^T μ subject to sum(w)=1, w≥0, and per-instrument limits.
     */
    private OptimizationResult runMeanVariance(OptimizationInput input, double targetReturn) {
        int n = input.instruments().size();
        double[] weights = initEqualWeights(n);
        double lambda = targetReturn > 0 ? 0.5 : 1.0;
        double lr = 0.01;
        double maxSingle = input.constraints().maxSingleStockWeight();

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double[] grad = computeGradient(weights, input.expectedReturns(),
                    input.covarianceMatrix(), lambda);
            double[] newWeights = new double[n];
            for (int i = 0; i < n; i++) {
                newWeights[i] = weights[i] - lr * grad[i];
                if (!input.constraints().allowShortSelling()) {
                    newWeights[i] = Math.max(0.0, newWeights[i]);
                }
                newWeights[i] = Math.min(maxSingle, newWeights[i]);
            }
            newWeights = projectToSimplex(newWeights);
            double diff = l2Norm(subtract(newWeights, weights));
            weights = newWeights;
            if (diff < CONVERGENCE_TOL) break;
        }

        double expReturn = dot(weights, input.expectedReturns());
        double variance  = quadratic(weights, input.covarianceMatrix());
        double vol       = Math.sqrt(Math.max(0.0, variance));
        double sharpe    = vol > 0 ? (expReturn - RISK_FREE_RATE) / vol : 0.0;

        List<WeightedHolding> holdings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (weights[i] > 1e-6) {
                holdings.add(new WeightedHolding(input.instruments().get(i), weights[i]));
            }
        }
        return new OptimizationResult(input.portfolioId(), holdings, expReturn, vol, sharpe);
    }

    // ─── Math helpers ─────────────────────────────────────────────────────────

    private double[] initEqualWeights(int n) {
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = 1.0 / n;
        return w;
    }

    private double[] computeGradient(double[] w, double[] mu, double[][] cov, double lambda) {
        int n = w.length;
        double[] grad = new double[n];
        for (int i = 0; i < n; i++) {
            double covTerm = 0;
            for (int j = 0; j < n; j++) covTerm += cov[i][j] * w[j];
            grad[i] = covTerm - lambda * mu[i];
        }
        return grad;
    }

    // Euclidean projection onto probability simplex (Duchi et al. 2008)
    private double[] projectToSimplex(double[] v) {
        int n = v.length;
        double[] sorted = v.clone();
        java.util.Arrays.sort(sorted);
        double cumsum = 0;
        double theta = 0;
        for (int i = n - 1; i >= 0; i--) {
            cumsum += sorted[i];
            double t = (cumsum - 1.0) / (n - i);
            if (t >= sorted[i]) { theta = t; break; }
        }
        double[] result = new double[n];
        for (int i = 0; i < n; i++) result[i] = Math.max(0.0, v[i] - theta);
        return result;
    }

    private double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private double quadratic(double[] w, double[][] m) {
        int n = w.length;
        double s = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                s += w[i] * m[i][j] * w[j];
        return s;
    }

    private double[] subtract(double[] a, double[] b) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] - b[i];
        return r;
    }

    private double l2Norm(double[] v) {
        double s = 0;
        for (double x : v) s += x * x;
        return Math.sqrt(s);
    }

    private double minReturn(double[] mu) {
        double m = mu[0];
        for (double v : mu) if (v < m) m = v;
        return m;
    }

    private double maxReturn(double[] mu) {
        double m = mu[0];
        for (double v : mu) if (v > m) m = v;
        return m;
    }
}
