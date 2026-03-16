package com.ghatana.appplatform.pricing.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
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
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Yield curve scenario and shift analysis: parallel shift (all tenors ±X bps),
 *              steepening (short -X bps, long +X bps), and flattening (short +X bps, long -X bps).
 *              Produces shifted curve rates and PV impact on portfolio positions.
 *              Pre-defined scenarios: +100bps, -100bps, bear steepener, bull flattener.
 *              Satisfies STORY-D05-006.
 * @doc.layer   Domain
 * @doc.pattern Scenario simulation; DiscountFactorService delegation; PV impact analysis;
 *              Counter for scenarios run.
 */
public class CurveScenarioService {

    private final HikariDataSource     dataSource;
    private final Executor             executor;
    private final DiscountFactorService dfService;
    private final Counter              scenarioCounter;

    public CurveScenarioService(HikariDataSource dataSource, Executor executor,
                                 DiscountFactorService dfService, MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.dfService       = dfService;
        this.scenarioCounter = Counter.builder("pricing.curve_scenario.runs_total").register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum ScenarioType { PARALLEL_SHIFT, STEEPENING, FLATTENING }

    public record CurveNode(double tenorYears, double baseRate, double shiftedRate) {}

    public record PvImpact(String instrumentId, BigDecimal basePv, BigDecimal shiftedPv,
                           BigDecimal pvChange, BigDecimal pvChangePct) {}

    public record ScenarioResult(String scenarioId, String curveId, LocalDate curveDate,
                                  ScenarioType type, double shiftBps,
                                  List<CurveNode> shiftedCurve, List<PvImpact> pvImpacts,
                                  BigDecimal totalPvChange) {}

    // ─── Pre-defined scenarios ───────────────────────────────────────────────

    public Promise<List<ScenarioResult>> runStandardScenarios(String curveId, LocalDate curveDate,
                                                               String portfolioId) {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario(curveId, curveDate, portfolioId, ScenarioType.PARALLEL_SHIFT,  100));
            results.add(runScenario(curveId, curveDate, portfolioId, ScenarioType.PARALLEL_SHIFT, -100));
            results.add(runScenario(curveId, curveDate, portfolioId, ScenarioType.STEEPENING,       50));
            results.add(runScenario(curveId, curveDate, portfolioId, ScenarioType.FLATTENING,       50));
            return results;
        });
    }

    public Promise<ScenarioResult> runCustomScenario(String curveId, LocalDate curveDate,
                                                      String portfolioId, ScenarioType type,
                                                      double shiftBps) {
        return Promise.ofBlocking(executor, () -> runScenario(curveId, curveDate, portfolioId, type, shiftBps));
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private ScenarioResult runScenario(String curveId, LocalDate curveDate,
                                        String portfolioId, ScenarioType type, double shiftBps)
            throws Exception {
        List<double[]> baseNodes = loadCurveNodes(curveId, curveDate);
        List<CurveNode> shiftedNodes = applyShift(baseNodes, type, shiftBps / 10_000.0);

        String tempCurveId = "SCENARIO-" + UUID.randomUUID();
        persistTempCurve(tempCurveId, curveDate, shiftedNodes);

        List<DiscountFactorService.PresentValue> basePvs =
                dfService.batchPv(curveId, curveDate, portfolioId,
                        DiscountFactorService.DayCountConvention.ACT_365).get();
        List<DiscountFactorService.PresentValue> scenPvs =
                dfService.batchPv(tempCurveId, curveDate, portfolioId,
                        DiscountFactorService.DayCountConvention.ACT_365).get();

        List<PvImpact> impacts = computeImpacts(basePvs, scenPvs);
        BigDecimal totalChange  = impacts.stream().map(PvImpact::pvChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cleanupTempCurve(tempCurveId, curveDate);
        scenarioCounter.increment();

        return new ScenarioResult(UUID.randomUUID().toString(), curveId, curveDate,
                type, shiftBps, shiftedNodes, impacts, totalChange);
    }

    private List<CurveNode> applyShift(List<double[]> nodes, ScenarioType type, double shiftRate) {
        double pivotTenor = 2.0; // 2-year pivot for steepen/flatten
        List<CurveNode> shifted = new ArrayList<>();
        for (double[] node : nodes) {
            double tenor    = node[0];
            double baseRate = node[1];
            double delta = switch (type) {
                case PARALLEL_SHIFT -> shiftRate;
                case STEEPENING     -> tenor < pivotTenor ? -shiftRate * 0.5 : shiftRate;
                case FLATTENING     -> tenor < pivotTenor ?  shiftRate       : -shiftRate * 0.5;
            };
            shifted.add(new CurveNode(tenor, baseRate, Math.max(0.0, baseRate + delta)));
        }
        return shifted;
    }

    private List<PvImpact> computeImpacts(List<DiscountFactorService.PresentValue> base,
                                           List<DiscountFactorService.PresentValue> scenario) {
        List<PvImpact> impacts = new ArrayList<>();
        for (int i = 0; i < Math.min(base.size(), scenario.size()); i++) {
            DiscountFactorService.PresentValue b = base.get(i);
            DiscountFactorService.PresentValue s = scenario.get(i);
            BigDecimal change = s.pv().subtract(b.pv());
            BigDecimal pct = b.pv().compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : change.divide(b.pv().abs(), 6, RoundingMode.HALF_UP);
            impacts.add(new PvImpact(b.instrumentId(), b.pv(), s.pv(), change, pct));
        }
        return impacts;
    }

    private List<double[]> loadCurveNodes(String curveId, LocalDate curveDate) throws SQLException {
        List<double[]> nodes = new ArrayList<>();
        String sql = "SELECT tenor_years, rate FROM yield_curve_rates WHERE curve_id = ? AND curve_date = ? ORDER BY tenor_years";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, curveId);
            ps.setObject(2, curveDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) nodes.add(new double[]{rs.getDouble(1), rs.getDouble(2)});
            }
        }
        return nodes;
    }

    private void persistTempCurve(String curveId, LocalDate curveDate,
                                   List<CurveNode> nodes) throws SQLException {
        String sql = "INSERT INTO yield_curve_rates (curve_id, curve_date, tenor_years, rate) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CurveNode n : nodes) {
                ps.setString(1, curveId);
                ps.setObject(2, curveDate);
                ps.setDouble(3, n.tenorYears());
                ps.setDouble(4, n.shiftedRate());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void cleanupTempCurve(String curveId, LocalDate curveDate) throws SQLException {
        String sql = "DELETE FROM yield_curve_rates WHERE curve_id = ? AND curve_date = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, curveId);
            ps.setObject(2, curveDate);
            ps.executeUpdate();
        }
    }
}
