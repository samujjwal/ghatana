package com.ghatana.appplatform.risk.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type    DomainService
 * @doc.purpose Aggregates data from VarApiService, MarginCallService, and StressTestingService
 *              to produce the unified risk dashboard response used by
 *              {@code GET /risk/dashboard}. Also supports per-client and per-instrument
 *              drill-down for risk managers. Data is read-only from existing risk tables.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; pure read aggregation, no writes; Gauge for open margin calls.
 */
public class RiskDashboardService {

    private static final Logger log = LoggerFactory.getLogger(RiskDashboardService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final AtomicInteger    openMarginCallsGauge;
    private final Counter          dashboardQueryCounter;

    public RiskDashboardService(HikariDataSource dataSource, Executor executor,
                                MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.openMarginCallsGauge  = new AtomicInteger(0);
        this.dashboardQueryCounter = registry.counter("risk.dashboard.query");
        Gauge.builder("risk.margin_calls.open", openMarginCallsGauge, AtomicInteger::get)
             .register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record RiskPosition(
        String instrumentId,
        double quantity,
        double marketValue,
        double individualVarBps,
        double concentrationPct
    ) {}

    public record RiskAlert(
        String alertType,    // VAR_BREACH | MARGIN_CALL | STRESS_THRESHOLD | BACKTEST_RED
        String portfolioId,
        String description,
        String severity      // HIGH | MEDIUM | LOW
    ) {}

    public record DashboardResponse(
        double         varTotal,
        double         varChange1d,
        double         marginUtilizationPct,
        int            marginCallsOpen,
        double         stressWorstCase,
        LocalDate      asOf,
        List<RiskPosition> topRiskPositions,
        List<RiskAlert>    riskAlerts
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Build the unified dashboard response.
     * Exposed as {@code GET /risk/dashboard}.
     */
    public Promise<DashboardResponse> getDashboard() {
        return Promise.ofBlocking(executor, () -> {
            dashboardQueryCounter.increment();
            return buildDashboard();
        });
    }

    /**
     * Per-client risk drill-down: returns positions, VaR, margin for a single client.
     */
    public Promise<List<RiskPosition>> getClientDrillDown(String clientId) {
        return Promise.ofBlocking(executor, () -> loadClientPositions(clientId));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private DashboardResponse buildDashboard() {
        double varTotal          = loadAggregateVar();
        double varChange1d       = loadVarChange1d(varTotal);
        double marginUtil        = loadAverageMarginUtilization();
        int    openCalls         = loadOpenMarginCallCount();
        double stressWorstCase   = loadStressWorstCase();

        openMarginCallsGauge.set(openCalls);

        List<RiskPosition> topPositions = loadTopRiskPositions(10);
        List<RiskAlert>    alerts       = buildAlerts(varTotal, marginUtil, openCalls, stressWorstCase);

        log.debug("Risk dashboard: varTotal={} marginUtil={} openCalls={}", varTotal, marginUtil, openCalls);
        return new DashboardResponse(
            varTotal, varChange1d, marginUtil, openCalls, stressWorstCase,
            LocalDate.now(NST), topPositions, alerts
        );
    }

    private double loadAggregateVar() {
        String sql = """
            SELECT COALESCE(SUM(var_amount), 0)
            FROM var_history
            WHERE trade_date = CURRENT_DATE
              AND method = 'parametric'
            """;
        return querySingleDouble(sql);
    }

    private double loadVarChange1d(double today) {
        String sql = """
            SELECT COALESCE(SUM(var_amount), 0)
            FROM var_history
            WHERE trade_date = CURRENT_DATE - 1
              AND method = 'parametric'
            """;
        double yesterday = querySingleDouble(sql);
        return today - yesterday;
    }

    private double loadAverageMarginUtilization() {
        String sql = """
            SELECT COALESCE(AVG(utilization), 0)
            FROM margin_calls
            WHERE status = 'ISSUED'
            """;
        return querySingleDouble(sql);
    }

    private int loadOpenMarginCallCount() {
        String sql = "SELECT COUNT(*) FROM margin_calls WHERE status = 'ISSUED'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ex) {
            log.error("Failed to count open margin calls", ex);
        }
        return 0;
    }

    private double loadStressWorstCase() {
        String sql = """
            SELECT COALESCE(MIN(absolute_impact), 0)
            FROM stress_test_results
            WHERE created_at >= date_trunc('day', now() AT TIME ZONE 'Asia/Kathmandu')
            """;
        return querySingleDouble(sql);
    }

    private List<RiskPosition> loadTopRiskPositions(int limit) {
        String sql = """
            SELECT instrument_id,
                   SUM(quantity) AS quantity,
                   SUM(quantity * last_price) AS market_value
            FROM portfolio_positions
            WHERE quantity > 0
            GROUP BY instrument_id
            ORDER BY market_value DESC
            LIMIT ?
            """;
        List<RiskPosition> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new RiskPosition(
                        rs.getString("instrument_id"),
                        rs.getDouble("quantity"),
                        rs.getDouble("market_value"),
                        0,  // individual VaR not computed at dashboard level
                        0   // concentration computed downstream if needed
                    ));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load top risk positions", ex);
        }
        return result;
    }

    private List<RiskAlert> buildAlerts(double varTotal, double marginUtil,
                                         int openCalls, double stressWorstCase) {
        List<RiskAlert> alerts = new ArrayList<>();
        if (varTotal > 10_000_000) {
            alerts.add(new RiskAlert("VAR_BREACH", "ALL", "Total VaR exceeds NPR 1 Cr threshold", "HIGH"));
        }
        if (marginUtil > 0.90) {
            alerts.add(new RiskAlert("MARGIN_HIGH_UTIL", "ALL",
                "Average margin utilization " + Math.round(marginUtil * 100) + "%", "MEDIUM"));
        }
        if (openCalls > 5) {
            alerts.add(new RiskAlert("MARGIN_CALLS_ELEVATED", "ALL",
                openCalls + " open margin calls require action", "HIGH"));
        }
        if (stressWorstCase < -50_000_000) {
            alerts.add(new RiskAlert("STRESS_THRESHOLD", "ALL",
                "Worst stress scenario P&L: NPR " + Math.round(stressWorstCase), "MEDIUM"));
        }
        return alerts;
    }

    private List<RiskPosition> loadClientPositions(String clientId) {
        String sql = """
            SELECT instrument_id, quantity, quantity * last_price AS market_value
            FROM portfolio_positions
            WHERE client_id = ? AND quantity > 0
            ORDER BY market_value DESC
            """;
        List<RiskPosition> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new RiskPosition(
                        rs.getString("instrument_id"),
                        rs.getDouble("quantity"),
                        rs.getDouble("market_value"),
                        0, 0
                    ));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load client positions for clientId={}", clientId, ex);
        }
        return result;
    }

    private double querySingleDouble(String sql) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException ex) {
            log.error("Dashboard query failed: {}", sql, ex);
        }
        return 0.0;
    }
}
