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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Replays actual historical market movements from major crises onto the current
 *              portfolio to assess resilience. Pre-loaded scenarios: NEPSE 2008 crash,
 *              COVID-19 March 2020, and the 2015 Nepal earthquake market impact.
 *              Supports configurable date ranges and exposes worst-case comparison output.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; reads historical_price_moves table for scenario data;
 *              delegates portfolio re-pricing to StressTestingService.ScenarioPricingPort.
 */
public class HistoricalScenarioService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalScenarioService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    // Pre-loaded historical scenario identifiers and date ranges (AD calendar)
    private static final String NEPSE_2008_ID   = "NEPSE_2008";
    private static final LocalDate NEPSE_2008_START = LocalDate.of(2007, 9, 1);
    private static final LocalDate NEPSE_2008_END   = LocalDate.of(2008, 9, 30);

    private static final String COVID_2020_ID   = "COVID_2020";
    private static final LocalDate COVID_2020_START = LocalDate.of(2020, 2, 20);
    private static final LocalDate COVID_2020_END   = LocalDate.of(2020, 4, 30);

    private static final String EARTHQUAKE_2015_ID  = "EARTHQUAKE_2015";
    private static final LocalDate EARTHQUAKE_START = LocalDate.of(2015, 4, 25);
    private static final LocalDate EARTHQUAKE_END   = LocalDate.of(2015, 7, 31);

    private final HikariDataSource                    dataSource;
    private final Executor                            executor;
    private final StressTestingService.ScenarioPricingPort scenarioPricer;
    private final Counter                             replayCounter;

    public HistoricalScenarioService(HikariDataSource dataSource, Executor executor,
                                     StressTestingService.ScenarioPricingPort scenarioPricer,
                                     MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.scenarioPricer = scenarioPricer;
        this.replayCounter  = registry.counter("risk.historical.scenario.replay");
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record HistoricalScenarioResult(
        String    portfolioId,
        String    scenarioId,
        LocalDate scenarioStart,
        LocalDate scenarioEnd,
        double    baselineValue,
        double    worstCaseValue,
        double    absolutePnl,
        double    pctPnl,
        double    maxDrawdown
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Replay NEPSE 2008 crash against current portfolio. */
    public Promise<HistoricalScenarioResult> replayNepse2008(String portfolioId) {
        return replayScenario(portfolioId, NEPSE_2008_ID, NEPSE_2008_START, NEPSE_2008_END);
    }

    /** Replay COVID-19 March 2020 against current portfolio. */
    public Promise<HistoricalScenarioResult> replayCovid2020(String portfolioId) {
        return replayScenario(portfolioId, COVID_2020_ID, COVID_2020_START, COVID_2020_END);
    }

    /** Replay 2015 Nepal earthquake market impact against current portfolio. */
    public Promise<HistoricalScenarioResult> replayEarthquake2015(String portfolioId) {
        return replayScenario(portfolioId, EARTHQUAKE_2015_ID, EARTHQUAKE_START, EARTHQUAKE_END);
    }

    /**
     * Run all three pre-loaded scenarios and return worst-case result.
     */
    public Promise<HistoricalScenarioResult> worstCaseHistorical(String portfolioId) {
        return Promise.ofBlocking(executor, () -> {
            List<HistoricalScenarioResult> all = new ArrayList<>();
            all.add(runScenario(portfolioId, NEPSE_2008_ID, NEPSE_2008_START, NEPSE_2008_END));
            all.add(runScenario(portfolioId, COVID_2020_ID, COVID_2020_START, COVID_2020_END));
            all.add(runScenario(portfolioId, EARTHQUAKE_2015_ID, EARTHQUAKE_START, EARTHQUAKE_END));
            return all.stream()
                .min((a, b) -> Double.compare(a.absolutePnl(), b.absolutePnl()))
                .orElse(all.getFirst());
        });
    }

    /**
     * Replay a custom historical period.
     */
    public Promise<HistoricalScenarioResult> replayCustom(String portfolioId, String scenarioId,
                                                           LocalDate from, LocalDate to) {
        return replayScenario(portfolioId, scenarioId, from, to);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Promise<HistoricalScenarioResult> replayScenario(String portfolioId, String scenarioId,
                                                              LocalDate from, LocalDate to) {
        return Promise.ofBlocking(executor, () -> {
            HistoricalScenarioResult result = runScenario(portfolioId, scenarioId, from, to);
            persistResult(result);
            replayCounter.increment();
            return result;
        });
    }

    private HistoricalScenarioResult runScenario(String portfolioId, String scenarioId,
                                                  LocalDate from, LocalDate to) {
        // Load worst equity move as percentage over the historical period
        WorstMoveStats stats = loadWorstHistoricalMove(scenarioId, from, to);
        List<PositionRow> positions = loadCurrentPositions(portfolioId);

        double baseline = positions.stream().mapToDouble(p -> p.quantity() * p.lastPrice()).sum();
        double stressed = positions.stream()
            .mapToDouble(p -> scenarioPricer.stressedValue(
                p.instrumentId(), p.quantity(),
                stats.equityPctMove(), 0.0, 0.0, 0.0
            ))
            .sum();

        double absolutePnl = stressed - baseline;
        double pctPnl      = baseline > 0 ? absolutePnl / baseline : 0;
        double maxDrawdown  = Math.abs(Math.min(absolutePnl, 0));  // only losses count as drawdown

        log.info("Historical scenario replay portfolioId={} scenario={} pct_pnl={}",
                  portfolioId, scenarioId, pctPnl);
        return new HistoricalScenarioResult(portfolioId, scenarioId, from, to,
                                            baseline, stressed, absolutePnl, pctPnl, maxDrawdown);
    }

    private record WorstMoveStats(double equityPctMove) {}

    private WorstMoveStats loadWorstHistoricalMove(String scenarioId, LocalDate from, LocalDate to) {
        // Preferred: pre-loaded scenario move stored in historical_scenarios table
        String sql = """
            SELECT COALESCE(worst_equity_move_pct, 0)
            FROM historical_scenarios
            WHERE scenario_id = ? AND period_start = ? AND period_end = ?
            LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scenarioId);
            ps.setObject(2, from);
            ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new WorstMoveStats(rs.getDouble(1));
            }
        } catch (SQLException ex) {
            log.error("Failed to load historical scenario stats id={}", scenarioId, ex);
        }
        // Fallback: derive from price_history data as trough-to-peak percentage
        return loadDerivedWorstMove(from, to);
    }

    private WorstMoveStats loadDerivedWorstMove(LocalDate from, LocalDate to) {
        String sql = """
            SELECT (MIN(close_price) - MAX(close_price)) / NULLIF(MAX(close_price), 0)
                AS worst_move
            FROM price_history
            WHERE price_date_ad BETWEEN ? AND ?
              AND is_official_close = true
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double move = rs.getDouble(1);
                    return new WorstMoveStats(rs.wasNull() ? -0.20 : move);
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to derive worst move from price_history", ex);
        }
        return new WorstMoveStats(-0.20);  // fallback: assume -20%
    }

    private record PositionRow(String instrumentId, double quantity, double lastPrice) {}

    private List<PositionRow> loadCurrentPositions(String portfolioId) {
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
            log.error("Failed to load current positions for portfolioId={}", portfolioId, ex);
        }
        return result;
    }

    private void persistResult(HistoricalScenarioResult r) {
        String sql = """
            INSERT INTO historical_scenario_results
                (result_id, portfolio_id, scenario_id, scenario_start, scenario_end,
                 baseline_value, worst_case_value, absolute_pnl, pct_pnl, max_drawdown, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (portfolio_id, scenario_id) DO UPDATE
                SET absolute_pnl    = EXCLUDED.absolute_pnl,
                    worst_case_value = EXCLUDED.worst_case_value,
                    max_drawdown    = EXCLUDED.max_drawdown
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, r.portfolioId());
            ps.setString(3, r.scenarioId());
            ps.setObject(4, r.scenarioStart());
            ps.setObject(5, r.scenarioEnd());
            ps.setDouble(6, r.baselineValue());
            ps.setDouble(7, r.worstCaseValue());
            ps.setDouble(8, r.absolutePnl());
            ps.setDouble(9, r.pctPnl());
            ps.setDouble(10, r.maxDrawdown());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist historical scenario result portfolioId={}", r.portfolioId(), ex);
        }
    }
}
