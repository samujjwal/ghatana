package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Portfolio risk summary: Value-at-Risk (VaR 95%/99% historical simulation),
 *              portfolio beta vs benchmark, Sharpe ratio, maximum drawdown, annualized
 *              volatility, and concentration risk (top-5 weight). Risk data sourced from
 *              D-06 RiskPort integration for VaR + beta. Satisfies STORY-D03-013.
 * @doc.layer   Domain
 * @doc.pattern Historical-simulation VaR; Sharpe ratio; max drawdown; D-06 RiskPort;
 *              Gauge for VaR95 and concentration risk.
 */
public class PortfolioRiskSummaryService {

    private static final double RISK_FREE_RATE = 0.05;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final RiskPort         riskPort;
    private final AtomicLong       latestVar95Bps   = new AtomicLong(0);
    private final AtomicLong       latestConcentBps  = new AtomicLong(0);

    public PortfolioRiskSummaryService(HikariDataSource dataSource, Executor executor,
                                        RiskPort riskPort, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor   = executor;
        this.riskPort   = riskPort;
        Gauge.builder("pms.risk.var95_bps", latestVar95Bps, AtomicLong::get).register(registry);
        Gauge.builder("pms.risk.concentration_bps", latestConcentBps, AtomicLong::get).register(registry);
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /** D-06 risk engine integration. */
    public interface RiskPort {
        double getVar(String portfolioId, double confidenceLevel, int horizonDays);
        double getBeta(String portfolioId, String benchmarkId, int lookbackDays);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ConcentrationRisk(String instrumentId, BigDecimal weight) {}

    public record RiskSummary(String portfolioId, LocalDate asOf,
                              double var95, double var99,
                              double beta, double sharpeRatio,
                              double maxDrawdownPct, double annualizedVolatility,
                              List<ConcentrationRisk> top5Concentration) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<RiskSummary> computeRiskSummary(String portfolioId, String benchmarkId,
                                                    LocalDate asOf) {
        return Promise.ofBlocking(executor, () -> {
            double var95  = riskPort.getVar(portfolioId, 0.95, 1);
            double var99  = riskPort.getVar(portfolioId, 0.99, 1);
            double beta   = riskPort.getBeta(portfolioId, benchmarkId, 252);
            double vol    = computeAnnualizedVolatility(portfolioId, 252);
            double sharpe = computeSharpeRatio(portfolioId, 252, vol);
            double maxDD  = computeMaxDrawdown(portfolioId, 252);
            List<ConcentrationRisk> top5 = loadTop5Concentration(portfolioId);

            latestVar95Bps.set(Math.round(var95 * 10_000));
            latestConcentBps.set(top5.isEmpty() ? 0
                    : Math.round(top5.get(0).weight().doubleValue() * 10_000));

            return new RiskSummary(portfolioId, asOf, var95, var99, beta, sharpe, maxDD, vol, top5);
        });
    }

    // ─── Risk metrics ─────────────────────────────────────────────────────────

    private double computeAnnualizedVolatility(String portfolioId, int lookbackDays)
            throws SQLException {
        String sql = """
                SELECT STDDEV(dr) * SQRT(252) FROM (
                    SELECT (nav / LAG(nav) OVER (ORDER BY calc_date_ad) - 1) AS dr
                    FROM nav_history WHERE portfolio_id = ?
                    ORDER BY calc_date_ad DESC LIMIT ?
                ) t WHERE dr IS NOT NULL
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setInt(2, lookbackDays + 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double v = rs.getDouble(1);
                    return rs.wasNull() ? 0.0 : v;
                }
            }
        }
        return 0.0;
    }

    private double computeSharpeRatio(String portfolioId, int lookbackDays, double vol)
            throws SQLException {
        if (vol == 0.0) return 0.0;
        String sql = """
                SELECT AVG(dr) * 252 AS annualized_return FROM (
                    SELECT (nav / LAG(nav) OVER (ORDER BY calc_date_ad) - 1) AS dr
                    FROM nav_history WHERE portfolio_id = ?
                    ORDER BY calc_date_ad DESC LIMIT ?
                ) t WHERE dr IS NOT NULL
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setInt(2, lookbackDays + 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double annRet = rs.getDouble(1);
                    return rs.wasNull() ? 0.0 : (annRet - RISK_FREE_RATE) / vol;
                }
            }
        }
        return 0.0;
    }

    private double computeMaxDrawdown(String portfolioId, int lookbackDays) throws SQLException {
        String sql = """
                SELECT MAX(1.0 - nav / MAX(nav) OVER (ORDER BY calc_date_ad ROWS UNBOUNDED PRECEDING)) AS mdd
                FROM nav_history WHERE portfolio_id = ?
                ORDER BY calc_date_ad DESC LIMIT ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setInt(2, lookbackDays);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double v = rs.getDouble("mdd");
                    return rs.wasNull() ? 0.0 : v;
                }
            }
        }
        return 0.0;
    }

    private List<ConcentrationRisk> loadTop5Concentration(String portfolioId) throws SQLException {
        List<ConcentrationRisk> list = new ArrayList<>();
        String sql = """
                SELECT h.instrument_id,
                       h.market_value / NULLIF(SUM(h.market_value) OVER (), 0) AS weight
                FROM portfolio_holdings_latest h
                WHERE h.portfolio_id = ?
                ORDER BY h.market_value DESC LIMIT 5
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ConcentrationRisk(rs.getString("instrument_id"),
                            rs.getBigDecimal("weight").setScale(6, RoundingMode.HALF_UP)));
                }
            }
        }
        return list;
    }
}
