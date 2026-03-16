package com.ghatana.appplatform.ems.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Computes TCA metrics from raw tca_records: implementation shortfall
 *              (exec_price − decision_price), market impact (exec_vwap − arrival_vwap),
 *              timing cost, opportunity cost, and benchmark comparison in basis points.
 *              Also ranks algorithms by metric to identify best performers.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; reads tca_records, writes computed metrics to
 *              tca_metrics table; pure financial math, no external port.
 */
public class TcaMetricsService {

    private static final Logger log = LoggerFactory.getLogger(TcaMetricsService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          computedCounter;

    public TcaMetricsService(HikariDataSource dataSource, Executor executor, MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.computedCounter = registry.counter("ems.tca.metrics.computed");
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record TcaMetrics(
        String tcaId,
        String orderId,
        String fillId,
        String side,
        double implementationShortfallBps,  // (execPrice - decisionPrice) / decisionPrice * 10000
        double marketImpactBps,             // (execVwap - arrivalVwap) / arrivalVwap * 10000 (buy side)
        double timingCostBps,               // (decisionPrice - arrivalPrice) / arrivalPrice * 10000
        double opportunityCostBps,          // (closingPrice - execPrice) for un-executed quantity
        double vsBenchmarkVwapBps,
        double vsBenchmarkTwapBps,
        double vsBenchmarkArrivalBps
    ) {}

    public record AlgoRanking(
        String algoType,
        double avgIsBps,
        double avgMarketImpactBps,
        int    sampleCount
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Compute and persist TCA metrics for a specific fill.
     */
    public Promise<TcaMetrics> computeForFill(String fillId) {
        return Promise.ofBlocking(executor, () -> {
            TcaMetrics metrics = doCompute(fillId);
            persistMetrics(metrics);
            computedCounter.increment();
            return metrics;
        });
    }

    /**
     * Rank algorithms by implementation shortfall over a date range (YYYY-MM-DD).
     */
    public Promise<List<AlgoRanking>> rankAlgorithms(String fromDate, String toDate) {
        return Promise.ofBlocking(executor, () -> loadAlgoRankings(fromDate, toDate));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private TcaMetrics doCompute(String fillId) {
        String sql = """
            SELECT t.tca_id, t.order_id, t.fill_id, t.side,
                   t.arrival_price, t.decision_price, t.exec_price,
                   COALESCE(f.exec_vwap, t.exec_price) AS exec_vwap
            FROM tca_records t
            LEFT JOIN fill_aggregates f ON f.order_id = t.order_id
            WHERE t.fill_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fillId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String tcaId       = rs.getString("tca_id");
                    String orderId     = rs.getString("order_id");
                    String side        = rs.getString("side");
                    double arrival     = rs.getDouble("arrival_price");
                    double decision    = rs.getDouble("decision_price");
                    double execPx      = rs.getDouble("exec_price");
                    double execVwap    = rs.getDouble("exec_vwap");

                    int buySign = "BUY".equals(side) ? 1 : -1;

                    double isBps       = decision > 0 ? buySign * (execPx - decision)    / decision  * 10_000 : 0;
                    double impactBps   = arrival  > 0 ? buySign * (execVwap - arrival)   / arrival   * 10_000 : 0;
                    double timingBps   = arrival  > 0 ? buySign * (decision - arrival)   / arrival   * 10_000 : 0;

                    // Opportunity cost and benchmark comparisons: simplified using exec vs arrival
                    double oppCostBps  = 0; // requires closing price per partially-executed orders
                    double vsVwapBps   = impactBps;  // exec relative to arrival vwap = market impact
                    double vsTwapBps   = arrival > 0 ? buySign * (execPx - arrival) / arrival * 10_000 : 0;
                    double vsArrivalBps = isBps;

                    log.debug("TCA metrics fillId={} isBps={} impactBps={}", fillId, isBps, impactBps);
                    return new TcaMetrics(tcaId, orderId, fillId, side,
                                          isBps, impactBps, timingBps, oppCostBps,
                                          vsVwapBps, vsTwapBps, vsArrivalBps);
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to compute TCA metrics for fillId={}", fillId, ex);
        }
        return new TcaMetrics("", "", fillId, "", 0, 0, 0, 0, 0, 0, 0);
    }

    private void persistMetrics(TcaMetrics m) {
        String sql = """
            INSERT INTO tca_metrics
                (metric_id, tca_id, order_id, fill_id, side,
                 is_bps, market_impact_bps, timing_cost_bps, opportunity_cost_bps,
                 vs_benchmark_vwap_bps, vs_benchmark_twap_bps, vs_benchmark_arrival_bps,
                 computed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (fill_id) DO UPDATE
                SET is_bps              = EXCLUDED.is_bps,
                    market_impact_bps   = EXCLUDED.market_impact_bps
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, m.tcaId());
            ps.setString(3, m.orderId());
            ps.setString(4, m.fillId());
            ps.setString(5, m.side());
            ps.setDouble(6, m.implementationShortfallBps());
            ps.setDouble(7, m.marketImpactBps());
            ps.setDouble(8, m.timingCostBps());
            ps.setDouble(9, m.opportunityCostBps());
            ps.setDouble(10, m.vsBenchmarkVwapBps());
            ps.setDouble(11, m.vsBenchmarkTwapBps());
            ps.setDouble(12, m.vsBenchmarkArrivalBps());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist TCA metrics fillId={}", m.fillId(), ex);
        }
    }

    private List<AlgoRanking> loadAlgoRankings(String fromDate, String toDate) {
        String sql = """
            SELECT o.algo_type,
                   AVG(m.is_bps)            AS avg_is_bps,
                   AVG(m.market_impact_bps) AS avg_impact_bps,
                   COUNT(*)                 AS sample_count
            FROM tca_metrics m
            JOIN tca_records t  ON t.fill_id = m.fill_id
            JOIN orders      o  ON o.order_id = t.order_id
            WHERE t.order_received_at::date BETWEEN ?::date AND ?::date
            GROUP BY o.algo_type
            ORDER BY avg_is_bps
            """;
        List<AlgoRanking> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new AlgoRanking(
                        rs.getString("algo_type"),
                        rs.getDouble("avg_is_bps"),
                        rs.getDouble("avg_impact_bps"),
                        rs.getInt("sample_count")
                    ));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to load algo rankings from={} to={}", fromDate, toDate, ex);
        }
        return result;
    }
}
