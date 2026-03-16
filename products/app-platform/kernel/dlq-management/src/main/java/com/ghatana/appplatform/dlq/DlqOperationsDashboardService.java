package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @doc.type    DomainService
 * @doc.purpose DLQ operations dashboard publisher. Aggregates key operational metrics:
 *              backlog by topic, OPEN/INVESTIGATING/RESOLVED/DISCARDED counts, top error
 *              categories, mean time to resolution (MTTR), auto-retry success rate, and
 *              poison pill quarantine count. Publishes snapshots to K-06 DashboardPort.
 *              Satisfies STORY-K19-011.
 * @doc.layer   Kernel
 * @doc.pattern K-06 DashboardPort publishing; operational KPIs; per-topic backlog Gauges;
 *              MTTR computation; snapshot persistence.
 */
public class DlqOperationsDashboardService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final DashboardPort    dashboardPort;
    private final AtomicReference<Double> totalBacklog        = new AtomicReference<>(0.0);
    private final AtomicReference<Double> poisonPillCount     = new AtomicReference<>(0.0);
    private final AtomicReference<Double> mttrSeconds         = new AtomicReference<>(0.0);
    private final AtomicReference<Double> autoRetrySuccessRate = new AtomicReference<>(0.0);

    public DlqOperationsDashboardService(HikariDataSource dataSource, Executor executor,
                                          DashboardPort dashboardPort,
                                          MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.dashboardPort = dashboardPort;

        Gauge.builder("dlq.dashboard.total_backlog",         totalBacklog,        AtomicReference::get).register(registry);
        Gauge.builder("dlq.dashboard.poison_pill_count",     poisonPillCount,     AtomicReference::get).register(registry);
        Gauge.builder("dlq.dashboard.mttr_seconds",          mttrSeconds,         AtomicReference::get).register(registry);
        Gauge.builder("dlq.dashboard.auto_retry_success_rate", autoRetrySuccessRate, AtomicReference::get).register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** K-06 DashboardPort. */
    public interface DashboardPort {
        void publishMetrics(String dashboardId, Map<String, Object> metrics);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public record TopicBacklog(String topicName, long open, long investigating, long total) {}

    public record DlqOperationsSnapshot(
        long totalOpen, long totalInvestigating, long totalResolved, long totalDiscarded,
        long poisonPills, double mttrSeconds, double autoRetrySuccessRate,
        List<TopicBacklog> backlogByTopic,
        Map<String, Long> topErrorCategories,
        Instant capturedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Compute DLQ operations snapshot and publish to K-06 DashboardPort.
     */
    public Promise<DlqOperationsSnapshot> publishDashboard() {
        return Promise.ofBlocking(executor, () -> {
            DlqOperationsSnapshot snapshot = computeSnapshot();
            updateGauges(snapshot);

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("totalOpen",             snapshot.totalOpen());
            metrics.put("totalInvestigating",    snapshot.totalInvestigating());
            metrics.put("totalResolved",         snapshot.totalResolved());
            metrics.put("totalDiscarded",        snapshot.totalDiscarded());
            metrics.put("poisonPills",           snapshot.poisonPills());
            metrics.put("mttrSeconds",           snapshot.mttrSeconds());
            metrics.put("autoRetrySuccessRate",  snapshot.autoRetrySuccessRate());
            metrics.put("topicBacklogs",         snapshot.backlogByTopic().stream()
                .map(b -> Map.of("topic", b.topicName(), "open", b.open(), "total", b.total()))
                .toList());
            metrics.put("topErrorCategories",    snapshot.topErrorCategories());
            metrics.put("capturedAt",            snapshot.capturedAt().toString());

            dashboardPort.publishMetrics("dlq-operations", metrics);
            persistSnapshot(snapshot);
            return snapshot;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private DlqOperationsSnapshot computeSnapshot() throws SQLException {
        Map<String, Long> statusCounts = queryStatusCounts();
        long open           = statusCounts.getOrDefault("DEAD", 0L);
        long investigating  = statusCounts.getOrDefault("INVESTIGATING", 0L);
        long resolved       = statusCounts.getOrDefault("RESOLVED", 0L);
        long discarded      = statusCounts.getOrDefault("DISCARDED", 0L);
        long poisons        = queryPoisonPillCount();
        double mttr         = queryMttr();
        double successRate  = queryAutoRetrySuccessRate();
        List<TopicBacklog>   backlogs    = queryTopicBacklogs();
        Map<String, Long>    errorCats   = queryTopErrorCategories();

        return new DlqOperationsSnapshot(open, investigating, resolved, discarded,
            poisons, mttr, successRate, backlogs, errorCats, Instant.now());
    }

    private Map<String, Long> queryStatusCounts() throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT status, COUNT(*) FROM dead_letters GROUP BY status")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) counts.put(rs.getString("status"), rs.getLong(2));
            }
        }
        return counts;
    }

    private long queryPoisonPillCount() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM dead_letters WHERE is_poison_pill = TRUE AND status = 'DEAD'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private double queryMttr() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT AVG(EXTRACT(EPOCH FROM (resolved_at - captured_at))) " +
                 "FROM dead_letters WHERE status = 'RESOLVED' AND resolved_at > NOW() - INTERVAL '30 days'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private double queryAutoRetrySuccessRate() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'RESOLVED' AND retry_count > 0) " +
                 "/ NULLIF(COUNT(*) FILTER (WHERE retry_count > 0), 0), 2) " +
                 "FROM dead_letters WHERE captured_at > NOW() - INTERVAL '7 days'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private List<TopicBacklog> queryTopicBacklogs() throws SQLException {
        List<TopicBacklog> results = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT topic_name, " +
                 "COUNT(*) FILTER (WHERE status = 'DEAD') AS open, " +
                 "COUNT(*) FILTER (WHERE status = 'INVESTIGATING') AS investigating, " +
                 "COUNT(*) AS total " +
                 "FROM dead_letters GROUP BY topic_name ORDER BY total DESC LIMIT 20")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new TopicBacklog(rs.getString("topic_name"),
                        rs.getLong("open"), rs.getLong("investigating"), rs.getLong("total")));
                }
            }
        }
        return results;
    }

    private Map<String, Long> queryTopErrorCategories() throws SQLException {
        Map<String, Long> categories = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT error_category, COUNT(*) FROM dead_letters " +
                 "WHERE status = 'DEAD' AND error_category IS NOT NULL " +
                 "GROUP BY error_category ORDER BY COUNT(*) DESC LIMIT 10")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) categories.put(rs.getString("error_category"), rs.getLong(2));
            }
        }
        return categories;
    }

    private void updateGauges(DlqOperationsSnapshot snap) {
        totalBacklog.set((double) (snap.totalOpen() + snap.totalInvestigating()));
        poisonPillCount.set((double) snap.poisonPills());
        mttrSeconds.set(snap.mttrSeconds());
        autoRetrySuccessRate.set(snap.autoRetrySuccessRate());
    }

    private void persistSnapshot(DlqOperationsSnapshot snap) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO dlq_operations_snapshots " +
                 "(total_open, total_investigating, total_resolved, total_discarded, " +
                 "poison_pills, mttr_seconds, auto_retry_success_rate, captured_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, snap.totalOpen());
            ps.setLong(2, snap.totalInvestigating());
            ps.setLong(3, snap.totalResolved());
            ps.setLong(4, snap.totalDiscarded());
            ps.setLong(5, snap.poisonPills());
            ps.setDouble(6, snap.mttrSeconds());
            ps.setDouble(7, snap.autoRetrySuccessRate());
            ps.setTimestamp(8, Timestamp.from(snap.capturedAt()));
            ps.executeUpdate();
        }
    }
}
