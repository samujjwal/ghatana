package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Data quality score dashboard: per-asset quality scores, trend over time,
 *              worst-performing assets. Aggregate score per service/domain. Color-coded:
 *              green (>90%), yellow (70-90%), red (<70%). Via K-06 DashboardPort.
 *              Satisfies STORY-K08-007.
 * @doc.layer   Kernel
 * @doc.pattern K-06 DashboardPort; quality score aggregation; color classification;
 *              time-series trend; Gauge.
 */
public class DataQualityDashboardService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final DashboardPort    dashboardPort;
    private final AtomicLong       avgScoreGauge = new AtomicLong(0);

    public DataQualityDashboardService(HikariDataSource dataSource, Executor executor,
                                        DashboardPort dashboardPort, MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.dashboardPort = dashboardPort;
        Gauge.builder("governance.dq.avg_score_bps", avgScoreGauge, v -> v.get() / 100.0)
                .register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** K-06 observability dashboard port. */
    public interface DashboardPort {
        void publish(String dashboardId, Object payload);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum QualityColor { GREEN, YELLOW, RED }

    public record AssetQualityScore(String assetId, String assetName, double score,
                                     QualityColor color, int ruleCount, int failedRuleCount) {}

    public record QualityTrend(LocalDate date, double avgScore) {}

    public record QualityDashboard(List<AssetQualityScore> assetScores,
                                    List<AssetQualityScore> worstPerforming,
                                    List<ServiceAggregate> serviceAggregates,
                                    List<QualityTrend> trend30Days,
                                    double overallAvgScore) {}

    public record ServiceAggregate(String serviceOwner, double avgScore, int assetCount,
                                    QualityColor color) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<QualityDashboard> buildDashboard() {
        return Promise.ofBlocking(executor, () -> {
            List<AssetQualityScore> allScores = computeAllAssetScores();
            List<AssetQualityScore> worst = allScores.stream()
                    .sorted((a, b) -> Double.compare(a.score(), b.score()))
                    .limit(10).toList();
            List<ServiceAggregate> svcAgg = computeServiceAggregates();
            List<QualityTrend> trend = loadTrend30Days();
            double overallAvg = allScores.stream()
                    .mapToDouble(AssetQualityScore::score).average().orElse(0.0);
            avgScoreGauge.set((long) (overallAvg * 100));

            QualityDashboard dash = new QualityDashboard(allScores, worst, svcAgg, trend, overallAvg);
            dashboardPort.publish("data_quality_dashboard", dash);
            return dash;
        });
    }

    public Promise<List<AssetQualityScore>> getAssetScores(String serviceOwner) {
        return Promise.ofBlocking(executor, () -> computeAssetScoresForService(serviceOwner));
    }

    // ─── Computation ─────────────────────────────────────────────────────────

    private List<AssetQualityScore> computeAllAssetScores() throws SQLException {
        String sql = """
                SELECT dc.asset_id, dc.name,
                       AVG(qcr.score) AS avg_score,
                       COUNT(qcr.check_id) AS rule_count,
                       SUM(CASE WHEN qcr.passed=FALSE THEN 1 ELSE 0 END) AS failed_count
                FROM data_catalog dc
                LEFT JOIN quality_check_results qcr ON qcr.asset_id=dc.asset_id
                    AND qcr.executed_at >= NOW() - INTERVAL '24 hours'
                GROUP BY dc.asset_id, dc.name
                ORDER BY avg_score ASC NULLS FIRST
                """;
        return fetchScores(sql, null);
    }

    private List<AssetQualityScore> computeAssetScoresForService(String serviceOwner) throws SQLException {
        String sql = """
                SELECT dc.asset_id, dc.name,
                       AVG(qcr.score) AS avg_score,
                       COUNT(qcr.check_id) AS rule_count,
                       SUM(CASE WHEN qcr.passed=FALSE THEN 1 ELSE 0 END) AS failed_count
                FROM data_catalog dc
                LEFT JOIN quality_check_results qcr ON qcr.asset_id=dc.asset_id
                    AND qcr.executed_at >= NOW() - INTERVAL '24 hours'
                WHERE dc.service_owner=?
                GROUP BY dc.asset_id, dc.name
                """;
        return fetchScores(sql, serviceOwner);
    }

    private List<AssetQualityScore> fetchScores(String sql, String param) throws SQLException {
        List<AssetQualityScore> scores = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double score = rs.getDouble("avg_score");
                    if (rs.wasNull()) score = 0.0;
                    scores.add(new AssetQualityScore(rs.getString("asset_id"),
                            rs.getString("name"), score, classify(score),
                            rs.getInt("rule_count"), rs.getInt("failed_count")));
                }
            }
        }
        return scores;
    }

    private List<ServiceAggregate> computeServiceAggregates() throws SQLException {
        String sql = """
                SELECT dc.service_owner, AVG(qcr.score) AS avg_score, COUNT(DISTINCT dc.asset_id) AS cnt
                FROM data_catalog dc
                LEFT JOIN quality_check_results qcr ON qcr.asset_id=dc.asset_id
                    AND qcr.executed_at >= NOW() - INTERVAL '24 hours'
                GROUP BY dc.service_owner
                """;
        List<ServiceAggregate> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double score = rs.getDouble("avg_score");
                result.add(new ServiceAggregate(rs.getString("service_owner"), score,
                        rs.getInt("cnt"), classify(score)));
            }
        }
        return result;
    }

    private List<QualityTrend> loadTrend30Days() throws SQLException {
        String sql = """
                SELECT DATE_TRUNC('day', executed_at) AS day, AVG(score) AS avg_score
                FROM quality_check_results
                WHERE executed_at >= NOW() - INTERVAL '30 days'
                GROUP BY day ORDER BY day ASC
                """;
        List<QualityTrend> trend = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) trend.add(new QualityTrend(
                    rs.getObject("day", java.sql.Timestamp.class).toLocalDateTime().toLocalDate(),
                    rs.getDouble("avg_score")));
        }
        return trend;
    }

    private QualityColor classify(double score) {
        if (score >= 90.0) return QualityColor.GREEN;
        if (score >= 70.0) return QualityColor.YELLOW;
        return QualityColor.RED;
    }
}
