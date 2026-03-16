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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Derive discount factors and forward rates from yield curve data.
 *              Discount factor: DF(t) = exp(-r(t) × t) with day count conventions
 *              ACT/365, ACT/360, 30/360. Forward rate f(t1,t2) = (r2×t2 - r1×t1)/(t2-t1).
 *              Present value: PV = CashFlow × DF(t). Batch PV across a portfolio.
 *              Satisfies STORY-D05-005.
 * @doc.layer   Domain
 * @doc.pattern Zero-coupon bootstrapping; day count convention; batch PV; Timer SLA.
 */
public class DiscountFactorService {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Timer            pvTimer;

    public DiscountFactorService(HikariDataSource dataSource, Executor executor,
                                  MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor   = executor;
        this.pvTimer    = Timer.builder("pricing.discount_factor.duration_ms").register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum DayCountConvention { ACT_365, ACT_360, THIRTY_360 }

    public record DiscountFactor(double tenorYears, double rate, double df) {}

    public record ForwardRate(double t1, double t2, double forwardRate) {}

    public record PresentValue(String instrumentId, LocalDate cashFlowDate, BigDecimal cashFlow,
                               double tenorYears, double df, BigDecimal pv) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Compute discount factor for a given tenor in years from a named curve. */
    public Promise<DiscountFactor> getDiscountFactor(String curveId, LocalDate curveDate,
                                                      double tenorYears) {
        return Promise.ofBlocking(executor, () -> {
            double rate = interpolateRate(curveId, curveDate, tenorYears);
            double df   = Math.exp(-rate * tenorYears);
            return new DiscountFactor(tenorYears, rate, df);
        });
    }

    /** Forward rate between two tenor points. */
    public Promise<ForwardRate> getForwardRate(String curveId, LocalDate curveDate,
                                                double t1, double t2) {
        return Promise.ofBlocking(executor, () -> {
            if (t2 <= t1) throw new IllegalArgumentException("t2 must be > t1");
            double r1  = interpolateRate(curveId, curveDate, t1);
            double r2  = interpolateRate(curveId, curveDate, t2);
            double fwd = (r2 * t2 - r1 * t1) / (t2 - t1);
            return new ForwardRate(t1, t2, fwd);
        });
    }

    /** Batch PV calculation for a list of cash flows of a portfolio. */
    public Promise<List<PresentValue>> batchPv(String curveId, LocalDate valueDate,
                                                String portfolioId, DayCountConvention dcc) {
        return Promise.ofBlocking(executor, () ->
                pvTimer.recordCallable(() -> computeBatchPv(curveId, valueDate, portfolioId, dcc)));
    }

    // ─── Computation ─────────────────────────────────────────────────────────

    private List<PresentValue> computeBatchPv(String curveId, LocalDate valueDate,
                                               String portfolioId, DayCountConvention dcc)
            throws SQLException {
        List<CashFlowRow> cashFlows = loadCashFlows(portfolioId, valueDate);
        List<PresentValue> results  = new ArrayList<>();

        for (CashFlowRow cf : cashFlows) {
            double tenorYears = yearFraction(valueDate, cf.cashFlowDate(), dcc);
            if (tenorYears <= 0) tenorYears = 0.0027; // same-day: ~1 day
            double rate = interpolateRate(curveId, valueDate, tenorYears);
            double df   = Math.exp(-rate * tenorYears);
            BigDecimal pv = cf.cashFlow().multiply(BigDecimal.valueOf(df), MC)
                    .setScale(6, RoundingMode.HALF_UP);
            results.add(new PresentValue(cf.instrumentId(), cf.cashFlowDate(),
                    cf.cashFlow(), tenorYears, df, pv));
        }
        return results;
    }

    /** Linear interpolation between curve nodes. */
    private double interpolateRate(String curveId, LocalDate curveDate, double tenorYears)
            throws SQLException {
        String sql = """
                SELECT tenor_years, rate
                FROM yield_curve_rates
                WHERE curve_id = ? AND curve_date = ?
                ORDER BY tenor_years
                """;
        List<double[]> nodes = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, curveId);
            ps.setObject(2, curveDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nodes.add(new double[]{rs.getDouble("tenor_years"), rs.getDouble("rate")});
                }
            }
        }
        if (nodes.isEmpty()) throw new IllegalStateException("No curve data for " + curveId);
        if (tenorYears <= nodes.get(0)[0]) return nodes.get(0)[1];
        if (tenorYears >= nodes.get(nodes.size() - 1)[0]) return nodes.get(nodes.size() - 1)[1];

        for (int i = 0; i < nodes.size() - 1; i++) {
            double t1 = nodes.get(i)[0], t2 = nodes.get(i + 1)[0];
            if (tenorYears >= t1 && tenorYears <= t2) {
                double r1 = nodes.get(i)[1], r2 = nodes.get(i + 1)[1];
                return r1 + (r2 - r1) * (tenorYears - t1) / (t2 - t1);
            }
        }
        return nodes.get(nodes.size() - 1)[1];
    }

    private double yearFraction(LocalDate start, LocalDate end, DayCountConvention dcc) {
        return switch (dcc) {
            case ACT_365 -> (double) (end.toEpochDay() - start.toEpochDay()) / 365.0;
            case ACT_360 -> (double) (end.toEpochDay() - start.toEpochDay()) / 360.0;
            case THIRTY_360 -> {
                int y1 = start.getYear(), m1 = start.getMonthValue(), d1 = Math.min(start.getDayOfMonth(), 30);
                int y2 = end.getYear(),   m2 = end.getMonthValue(),   d2 = Math.min(end.getDayOfMonth(), 30);
                yield (360.0 * (y2 - y1) + 30.0 * (m2 - m1) + (d2 - d1)) / 360.0;
            }
        };
    }

    private record CashFlowRow(String instrumentId, LocalDate cashFlowDate, BigDecimal cashFlow) {}

    private List<CashFlowRow> loadCashFlows(String portfolioId, LocalDate fromDate)
            throws SQLException {
        List<CashFlowRow> rows = new ArrayList<>();
        String sql = """
                SELECT instrument_id, cash_flow_date, cash_flow_amount
                FROM instrument_cash_flows
                WHERE portfolio_id = ? AND cash_flow_date >= ?
                ORDER BY cash_flow_date
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setObject(2, fromDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CashFlowRow(rs.getString("instrument_id"),
                            rs.getObject("cash_flow_date", LocalDate.class),
                            rs.getBigDecimal("cash_flow_amount")));
                }
            }
        }
        return rows;
    }
}
