package com.ghatana.appplatform.pricing.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Built-in option pricing: Black-Scholes formula for European call/put options.
 *              Greeks: delta, gamma, theta, vega, rho. CRR binomial tree for American options
 *              with configurable steps (default 100). Implied volatility solver via
 *              Newton-Raphson (max 200 iterations, tolerance 1e-8). Satisfies STORY-D05-008.
 * @doc.layer   Domain
 * @doc.pattern Black-Scholes closed form; CRR binomial tree; Newton-Raphson IV solver;
 *              Timer for pricing latency.
 */
public class OptionPricingService {

    private static final MathContext MC             = MathContext.DECIMAL128;
    private static final int         BINOMIAL_STEPS = 100;
    private static final int         IV_MAX_ITER    = 200;
    private static final double      IV_TOLERANCE   = 1e-8;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Timer            pricingTimer;

    public OptionPricingService(HikariDataSource dataSource, Executor executor,
                                 MeterRegistry registry) {
        this.dataSource  = dataSource;
        this.executor    = executor;
        this.pricingTimer = Timer.builder("pricing.option.duration_ms").register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record OptionInput(double S, double K, double T, double r, double sigma,
                              boolean isCall, boolean isAmerican) {}

    public record Greeks(double delta, double gamma, double theta, double vega, double rho) {}

    public record OptionResult(double price, Greeks greeks) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<OptionResult> price(OptionInput input) {
        return Promise.ofBlocking(executor, () ->
                pricingTimer.recordCallable(() -> {
                    if (input.isAmerican()) {
                        double p = binomialPrice(input);
                        return new OptionResult(p, computeGreeksNumerically(input));
                    } else {
                        return blackScholesWithGreeks(input);
                    }
                }));
    }

    public Promise<Double> impliedVolatility(double marketPrice, OptionInput seedInput) {
        return Promise.ofBlocking(executor, () -> solveImpliedVol(marketPrice, seedInput));
    }

    // ─── Black-Scholes ────────────────────────────────────────────────────────

    private OptionResult blackScholesWithGreeks(OptionInput in) {
        double d1    = (Math.log(in.S() / in.K()) + (in.r() + 0.5 * in.sigma() * in.sigma()) * in.T())
                       / (in.sigma() * Math.sqrt(in.T()));
        double d2    = d1 - in.sigma() * Math.sqrt(in.T());
        double Nd1   = normCdf(d1), Nd2 = normCdf(d2);
        double Nd1n  = normCdf(-d1), Nd2n = normCdf(-d2);
        double nd1   = normPdf(d1);
        double disc  = Math.exp(-in.r() * in.T());

        double price, delta, rho;
        if (in.isCall()) {
            price = in.S() * Nd1 - in.K() * disc * Nd2;
            delta = Nd1;
            rho   = in.K() * in.T() * disc * Nd2 / 100.0;
        } else {
            price = in.K() * disc * Nd2n - in.S() * Nd1n;
            delta = Nd1 - 1.0;
            rho   = -in.K() * in.T() * disc * Nd2n / 100.0;
        }
        double gamma = nd1 / (in.S() * in.sigma() * Math.sqrt(in.T()));
        double vega  = in.S() * nd1 * Math.sqrt(in.T()) / 100.0;
        double theta = in.isCall()
                ? (-in.S() * nd1 * in.sigma() / (2.0 * Math.sqrt(in.T()))
                   - in.r() * in.K() * disc * Nd2) / 365.0
                : (-in.S() * nd1 * in.sigma() / (2.0 * Math.sqrt(in.T()))
                   + in.r() * in.K() * disc * Nd2n) / 365.0;

        return new OptionResult(price, new Greeks(delta, gamma, theta, vega, rho));
    }

    // ─── CRR Binomial tree ───────────────────────────────────────────────────

    private double binomialPrice(OptionInput in) {
        int    n   = BINOMIAL_STEPS;
        double dt  = in.T() / n;
        double u   = Math.exp(in.sigma() * Math.sqrt(dt));
        double d   = 1.0 / u;
        double p   = (Math.exp(in.r() * dt) - d) / (u - d);
        double disc = Math.exp(-in.r() * dt);

        double[] prices = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double sT = in.S() * Math.pow(u, n - i) * Math.pow(d, i);
            prices[i] = in.isCall() ? Math.max(0, sT - in.K()) : Math.max(0, in.K() - sT);
        }
        // Backward induction
        for (int step = n - 1; step >= 0; step--) {
            for (int i = 0; i <= step; i++) {
                double hold = disc * (p * prices[i] + (1 - p) * prices[i + 1]);
                double sNode = in.S() * Math.pow(u, step - i) * Math.pow(d, i);
                double exercise = in.isCall()
                        ? Math.max(0, sNode - in.K())
                        : Math.max(0, in.K() - sNode);
                prices[i] = Math.max(hold, exercise); // American early exercise
            }
        }
        return prices[0];
    }

    // ─── Numerical Greeks (for American options) ─────────────────────────────

    private Greeks computeGreeksNumerically(OptionInput in) {
        double dS    = in.S() * 0.01;
        double dSig  = 0.001;
        double dT    = 1.0 / 365.0;
        double dR    = 0.0001;

        double base   = binomialPrice(in);
        double upS    = binomialPrice(new OptionInput(in.S() + dS, in.K(), in.T(), in.r(), in.sigma(), in.isCall(), true));
        double downS  = binomialPrice(new OptionInput(in.S() - dS, in.K(), in.T(), in.r(), in.sigma(), in.isCall(), true));
        double upSig  = binomialPrice(new OptionInput(in.S(), in.K(), in.T(), in.r(), in.sigma() + dSig, in.isCall(), true));
        double downT  = binomialPrice(new OptionInput(in.S(), in.K(), in.T() - dT, in.r(), in.sigma(), in.isCall(), true));
        double upR    = binomialPrice(new OptionInput(in.S(), in.K(), in.T(), in.r() + dR, in.sigma(), in.isCall(), true));

        double delta = (upS - downS) / (2 * dS);
        double gamma = (upS - 2 * base + downS) / (dS * dS);
        double vega  = (upSig - base) / dSig / 100.0;
        double theta = -(downT - base) / dT / 365.0;
        double rho   = (upR - base) / dR / 100.0;

        return new Greeks(delta, gamma, theta, vega, rho);
    }

    // ─── Implied Volatility Solver ────────────────────────────────────────────

    private double solveImpliedVol(double marketPrice, OptionInput seed) throws Exception {
        double sigma = seed.sigma() > 0 ? seed.sigma() : 0.2; // initial guess
        for (int i = 0; i < IV_MAX_ITER; i++) {
            OptionInput curr = new OptionInput(seed.S(), seed.K(), seed.T(), seed.r(), sigma,
                    seed.isCall(), seed.isAmerican());
            OptionResult res = seed.isAmerican() ? new OptionResult(binomialPrice(curr), null)
                    : blackScholesWithGreeks(curr);
            double diff = res.price() - marketPrice;
            if (Math.abs(diff) < IV_TOLERANCE) return sigma;

            double vega = seed.isAmerican() ? computeGreeksNumerically(curr).vega()
                    : res.greeks().vega();
            if (Math.abs(vega) < 1e-12) break;
            sigma -= diff / (vega * 100.0); // vega quoted per 1% move
            if (sigma <= 0) sigma = 0.001;
        }
        return sigma;
    }

    // ─── Stats utils ─────────────────────────────────────────────────────────

    private static double normCdf(double x) {
        return 0.5 * (1 + erf(x / Math.sqrt(2)));
    }

    private static double normPdf(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
    }

    /** Approximation of error function (Abramowitz & Stegun 7.1.26). */
    private static double erf(double x) {
        double sign = x < 0 ? -1 : 1;
        x = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return sign * y;
    }
}
