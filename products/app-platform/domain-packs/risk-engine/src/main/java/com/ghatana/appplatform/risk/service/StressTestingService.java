package com.ghatana.appplatform.risk.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Applies hypothetical stress scenarios to a portfolio and calculates the P&L,
 *              margin impact, and VaR change. Ships four pre-defined scenarios (market crash -20%,
 *              sector decline -15%, interest rate shock +200bps, currency devaluation -10%)
 *              and supports custom scenarios via API.  Runs nightly for all portfolios.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; inner ScenarioPricingPort for scenario re-pricing;
 *              results stored in stress_test_results table.
 */
public class StressTestingService {

    private static final Logger log = LoggerFactory.getLogger(StressTestingService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private final HikariDataSource   dataSource;
    private final Executor           executor;
    private final ScenarioPricingPort scenarioPricer;
    private final Counter            stressTestCounter;
    private final Counter            nightlyBatchCounter;

    // Pre-defined scenarios
    static final List<StressScenario> PREDEFINED_SCENARIOS = List.of(
        new StressScenario("MARKET_CRASH",        "Market crash -20%",             -0.20, 0.0,     0.0,   0.0),
        new StressScenario("SECTOR_DECLINE",      "Sector decline -15%",           -0.15, 0.0,     0.0,   0.0),
        new StressScenario("RATE_SHOCK_200BPS",   "Interest rate shock +200bps",    0.0,  0.0200,  0.0,   0.0),
        new StressScenario("CURRENCY_DEVALUATION","Currency devaluation -10%",      0.0,  0.0,    -0.10,  0.0)
    );

    public StressTestingService(HikariDataSource dataSource, Executor executor,
                                ScenarioPricingPort scenarioPricer, MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.scenarioPricer   = scenarioPricer;
        this.stressTestCounter = registry.counter("risk.stress.test.run");
        this.nightlyBatchCounter = registry.counter("risk.stress.nightly.batch");
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /**
     * Port for re-pricing a position under a given stress scenario.
     */
    public interface ScenarioPricingPort {
        /** Returns the stressed market value of a position given the scenario shifts. */
        double stressedValue(String instrumentId, double quantity,
                             double equityShift, double rateShiftAbs,
                             double currencyShift, double volatilityShift);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    /**
     * Defines a stress scenario applied to all portfolio positions.
     *
     * @param scenarioId     unique identifier used in DB storage
     * @param description    human-readable name
     * @param equityShift    fractional equity price change (e.g., -0.20 = -20%)
     * @param rateShiftAbs   absolute rate shift in decimal (e.g., 0.02 = +200bps)
     * @param currencyShift  fractional FX shift (e.g., -0.10 = -10%)
     * @param volShift       fractional volatility shift
     */
    public record StressScenario(
        String scenarioId,
        String description,
        double equityShift,
        double rateShiftAbs,
        double currencyShift,
        double volShift
    ) {}

    public record StressTestResult(
        String portfolioId,
        String scenarioId,
        double baselineValue,
        double stressedValue,
        double absoluteImpact,
        double pctImpact,
        double marginImpact,
        double varChange
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run all pre-defined stress scenarios for a portfolio.
     */
    public Promise<List<StressTestResult>> runAllScenarios(String portfolioId) {
        return Promise.ofBlocking(executor, () -> {
            List<StressTestResult> results = new ArrayList<>();
            for (StressScenario scenario : PREDEFINED_SCENARIOS) {
                StressTestResult r = runScenario(portfolioId, scenario);
                results.add(r);
                persistResult(r);
                stressTestCounter.increment();
            }
            return results;
        });
    }

    /**
     * Run a custom scenario defined by the caller.
     */
    public Promise<StressTestResult> runCustomScenario(String portfolioId, StressScenario scenario) {
        return Promise.ofBlocking(executor, () -> {
            StressTestResult result = runScenario(portfolioId, scenario);
            persistResult(result);
            stressTestCounter.increment();
            return result;
        });
    }

    /**
     * Nightly batch: run all predefined scenarios for all active portfolios.
     */
    public Promise<Void> runNightlyBatch() {
        return Promise.ofBlocking(executor, () -> {
            List<String> portfolioIds = loadActivePortfolioIds();
            log.info("Stress test nightly batch: {} portfolios", portfolioIds.size());
            for (String pid : portfolioIds) {
                try {
                    for (StressScenario s : PREDEFINED_SCENARIOS) {
                        StressTestResult r = runScenario(pid, s);
                        persistResult(r);
                        nightlyBatchCounter.increment();
                    }
                } catch (Exception ex) {
                    log.error("Stress test nightly batch failed for portfolioId={}", pid, ex);
                }
            }
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private StressTestResult runScenario(String portfolioId, StressScenario scenario) {
        List<PositionRow> positions = loadPositions(portfolioId);
        double baseline  = positions.stream().mapToDouble(p -> p.quantity() * p.lastPrice()).sum();
        double stressed  = positions.stream()
            .mapToDouble(p -> scenarioPricer.stressedValue(
                p.instrumentId(), p.quantity(),
                scenario.equityShift(), scenario.rateShiftAbs(),
                scenario.currencyShift(), scenario.volShift()))
            .sum();

        double impact    = stressed - baseline;
        double pctImpact = baseline > 0 ? impact / baseline : 0;
        // Simplified: margin impact ~ 30% of equity loss for equities
        double marginImpact = Math.abs(impact) * 0.30;
        // VaR change: approximate as 10% of absolute scenario loss
        double varChange    = Math.abs(impact) * 0.10;

        log.debug("Stress {} portfolioId={} baseline={} stressed={} impact={}",
                  scenario.scenarioId(), portfolioId, baseline, stressed, impact);
        return new StressTestResult(portfolioId, scenario.scenarioId(),
                                    baseline, stressed, impact, pctImpact, marginImpact, varChange);
    }

    private record PositionRow(String instrumentId, double quantity, double lastPrice) {}

    private List<PositionRow> loadPositions(String portfolioId) {
        String sql = """
            SELECT instrument_id, quantity, last_price
            FROM portfolio_positions
            WHERE portfolio_id = ? AND quantity > 0
            """;
        List<PositionRow> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PositionRow(
                        rs.getString("instrument_id"),
                        rs.getDouble("quantity"),
                        rs.getDouble("last_price")
                    ));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load positions for portfolioId={}", portfolioId, ex);
        }
        return result;
    }

    private List<String> loadActivePortfolioIds() {
        String sql = "SELECT portfolio_id FROM portfolios WHERE status = 'ACTIVE'";
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("portfolio_id"));
        } catch (SQLException ex) {
            log.error("Failed to load active portfolio IDs for stress batch", ex);
        }
        return ids;
    }

    private void persistResult(StressTestResult r) {
        String sql = """
            INSERT INTO stress_test_results
                (result_id, portfolio_id, scenario_id, baseline_value, stressed_value,
                 absolute_impact, pct_impact, margin_impact, var_change, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (portfolio_id, scenario_id, date_trunc('day', created_at)) DO UPDATE
                SET absolute_impact = EXCLUDED.absolute_impact,
                    pct_impact      = EXCLUDED.pct_impact,
                    stressed_value  = EXCLUDED.stressed_value
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, r.portfolioId());
            ps.setString(3, r.scenarioId());
            ps.setDouble(4, r.baselineValue());
            ps.setDouble(5, r.stressedValue());
            ps.setDouble(6, r.absoluteImpact());
            ps.setDouble(7, r.pctImpact());
            ps.setDouble(8, r.marginImpact());
            ps.setDouble(9, r.varChange());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist stress result portfolioId={} scenario={}", r.portfolioId(), r.scenarioId(), ex);
        }
    }
}
