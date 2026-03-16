package com.ghatana.appplatform.risk.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Calculates Value-at-Risk for client portfolios using three methods:
 *              parametric (variance-covariance), historical simulation, and Monte Carlo (D06-004/005/006).
 * @doc.layer   Domain — risk analytics
 * @doc.pattern Strategy — method selected at call time; all three backed by same historical return data
 */
public class VarCalculationService {

    public enum VarMethod { PARAMETRIC, HISTORICAL, MONTE_CARLO }
    public enum ConfidenceLevel {
        PCT_95(1.645), PCT_99(2.326);
        final double zScore;
        ConfidenceLevel(double z) { this.zScore = z; }
    }

    public record VarResult(
        String portfolioId,
        VarMethod method,
        ConfidenceLevel confidenceLevel,
        int holdingPeriodDays,
        BigDecimal varAmount,     // in portfolio currency
        BigDecimal varPct,        // as % of portfolio value
        int observationsUsed,
        long computedAtMs
    ) {}

    private static final int DEFAULT_LOOKBACK_DAYS = 252;
    private static final int MC_SIMULATIONS = 10_000;

    private final DataSource dataSource;
    private final Executor executor;
    private final Timer varTimer;

    public VarCalculationService(DataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.varTimer = Timer.builder("risk.var.calculation_duration")
            .description("Time to compute VaR")
            .register(registry);
    }

    public Promise<VarResult> calculateVar(String portfolioId, VarMethod method,
                                            ConfidenceLevel confidence, int holdingPeriodDays) {
        return Promise.ofBlocking(executor, () -> {
            Timer.Sample sample = Timer.start();
            try {
                double[] returns = loadDailyReturns(portfolioId, DEFAULT_LOOKBACK_DAYS);
                double portfolioValue = loadPortfolioValue(portfolioId);
                double varPct = switch (method) {
                    case PARAMETRIC   -> parametricVar(returns, confidence.zScore, holdingPeriodDays);
                    case HISTORICAL   -> historicalVar(returns, confidence, holdingPeriodDays);
                    case MONTE_CARLO  -> monteCarloVar(returns, confidence, holdingPeriodDays);
                };
                BigDecimal varAmt = BigDecimal.valueOf(portfolioValue * varPct).setScale(2, RoundingMode.HALF_UP);
                BigDecimal varPctBd = BigDecimal.valueOf(varPct * 100).setScale(4, RoundingMode.HALF_UP);
                return new VarResult(portfolioId, method, confidence, holdingPeriodDays,
                    varAmt, varPctBd, returns.length, System.currentTimeMillis());
            } finally {
                sample.stop(varTimer);
            }
        });
    }

    // Parametric (variance-covariance): VaR = Z * σ * √t
    private double parametricVar(double[] returns, double zScore, int holdingPeriodDays) {
        double mean = Arrays.stream(returns).average().orElse(0.0);
        double variance = Arrays.stream(returns).map(r -> (r - mean) * (r - mean)).average().orElse(0.0);
        double dailyVol = Math.sqrt(variance);
        return zScore * dailyVol * Math.sqrt(holdingPeriodDays);
    }

    // Historical simulation: nth percentile of sorted losses
    private double historicalVar(double[] returns, ConfidenceLevel confidence, int holdingPeriodDays) {
        double[] scaled = new double[returns.length];
        for (int i = 0; i < returns.length; i++) {
            scaled[i] = returns[i] * Math.sqrt(holdingPeriodDays);
        }
        Arrays.sort(scaled);
        int idx = (int) Math.floor((1.0 - (confidence == ConfidenceLevel.PCT_99 ? 0.99 : 0.95)) * scaled.length);
        return -scaled[Math.max(0, idx)];  // losses are negative returns
    }

    // Monte Carlo: GBM with MC_SIMULATIONS paths
    private double monteCarloVar(double[] returns, ConfidenceLevel confidence, int holdingPeriodDays) {
        double mean = Arrays.stream(returns).average().orElse(0.0);
        double variance = Arrays.stream(returns).map(r -> (r - mean) * (r - mean)).average().orElse(0.0);
        double vol = Math.sqrt(variance);
        Random rng = new Random(42);  // deterministic seed for reproducibility
        double[] simReturns = new double[MC_SIMULATIONS];
        for (int i = 0; i < MC_SIMULATIONS; i++) {
            double totalReturn = 0.0;
            for (int d = 0; d < holdingPeriodDays; d++) {
                totalReturn += mean + vol * rng.nextGaussian();
            }
            simReturns[i] = totalReturn;
        }
        Arrays.sort(simReturns);
        int idx = (int) Math.floor((1.0 - (confidence == ConfidenceLevel.PCT_99 ? 0.99 : 0.95)) * MC_SIMULATIONS);
        return -simReturns[Math.max(0, idx)];
    }

    private double[] loadDailyReturns(String portfolioId, int lookbackDays) throws Exception {
        // Loads daily P&L returns from the portfolio's position history
        String sql = "SELECT daily_return FROM portfolio_daily_returns WHERE portfolio_id = ? " +
                     "ORDER BY return_date DESC LIMIT ?";
        List<Double> rets = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(portfolioId));
            ps.setInt(2, lookbackDays);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rets.add(rs.getDouble("daily_return"));
            }
        }
        // Fallback: return zero returns if no data (warn in calling layer)
        return rets.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private double loadPortfolioValue(String portfolioId) throws Exception {
        String sql = "SELECT COALESCE(SUM(market_value), 0) FROM portfolio_holdings WHERE portfolio_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(portfolioId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }
}
