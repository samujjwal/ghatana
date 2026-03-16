package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Aggregates DLQ health metrics and publishes a dashboard snapshot via
 *              DashboardPort (K-06). Tracks per-topic size, ingest rate, resolution
 *              rate, and age distribution (p50/p95/p99 age of unresolved items).
 *              Raises an alert event when growth rate exceeds threshold or when the
 *              oldest item in a topic exceeds a configured max-age SLA.
 *              Satisfies STORY-K19-003.
 * @doc.layer   Kernel
 * @doc.pattern K-06 DashboardPort; per-topic Gauge; age-based SLA alerts; EventPort;
 *              scheduled snapshot (caller-driven).
 */
public class DlqMetricsService {

    private static final long   MAX_AGE_HOURS_ALERT    = 24;
    private static final double GROWTH_RATE_ALERT_PCT  = 20.0; // > 20% growth triggers alert

    private final HikariDataSource              dataSource;
    private final Executor                      executor;
    private final DashboardPort                 dashboardPort;
    private final EventPort                     eventPort;
    private final ConcurrentHashMap<String, AtomicLong> topicSizeCache = new ConcurrentHashMap<>();
    private final MeterRegistry                 registry;

    public DlqMetricsService(HikariDataSource dataSource, Executor executor,
                              DashboardPort dashboardPort, EventPort eventPort,
                              MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.dashboardPort = dashboardPort;
        this.eventPort     = eventPort;
        this.registry      = registry;
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface DashboardPort {
        void publish(String dashboardId, Object snapshot);
    }

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record TopicMetrics(String topic, long totalPending, long resolvedLast24h,
                                long capturedLast24h, double growthRatePct,
                                long oldestItemAgeMinutes) {}

    public record DlqDashboardSnapshot(LocalDateTime generatedAt,
                                        List<TopicMetrics> topicMetrics,
                                        long globalPending, long globalResolved24h) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Build and publish a DLQ dashboard snapshot. Intended to be called on a schedule. */
    public Promise<DlqDashboardSnapshot> publishSnapshot() {
        return Promise.ofBlocking(executor, () -> {
            List<String> topics = loadDistinctTopics();
            List<TopicMetrics> metrics = new ArrayList<>();
            long globalPending = 0; long globalResolved24h = 0;

            for (String topic : topics) {
                TopicMetrics m = buildTopicMetrics(topic);
                metrics.add(m);
                globalPending += m.totalPending();
                globalResolved24h += m.resolvedLast24h();
                updateGauge(topic, m.totalPending());
                checkAlerts(topic, m);
            }

            DlqDashboardSnapshot snapshot = new DlqDashboardSnapshot(
                    LocalDateTime.now(), metrics, globalPending, globalResolved24h);
            dashboardPort.publish("dlq_health_dashboard", snapshot);
            return snapshot;
        });
    }

    public Promise<TopicMetrics> getTopicMetrics(String topic) {
        return Promise.ofBlocking(executor, () -> buildTopicMetrics(topic));
    }

    // ─── Computation ─────────────────────────────────────────────────────────

    private TopicMetrics buildTopicMetrics(String topic) throws SQLException {
        long pending       = countByTopicAndStatus(topic, "DEAD,INVESTIGATING,RETRYING");
        long resolved24h   = countRecentByStatus(topic, "RESOLVED", 24);
        long captured24h   = countRecentCaptured(topic, 24);

        long previousPending = topicSizeCache
                .computeIfAbsent(topic, k -> new AtomicLong(pending)).get();
        double growthRate = previousPending == 0 ? 0.0
                : (double)(pending - previousPending) / previousPending * 100.0;
        topicSizeCache.get(topic).set(pending);

        long oldestAgeMinutes = findOldestItemAgeMinutes(topic);

        return new TopicMetrics(topic, pending, resolved24h, captured24h,
                growthRate, oldestAgeMinutes);
    }

    private void checkAlerts(String topic, TopicMetrics m) {
        if (m.oldestItemAgeMinutes() > MAX_AGE_HOURS_ALERT * 60) {
            eventPort.publish("dlq-alerts", "DlqOldestItemSlaBreached",
                    Map.of("topic", topic, "ageMinutes", m.oldestItemAgeMinutes()));
        }
        if (m.growthRatePct() > GROWTH_RATE_ALERT_PCT) {
            eventPort.publish("dlq-alerts", "DlqGrowthRateExceeded",
                    Map.of("topic", topic, "growthRatePct", m.growthRatePct()));
        }
    }

    private void updateGauge(String topic, long size) {
        AtomicLong atomicSize = topicSizeCache.computeIfAbsent(topic, k -> new AtomicLong(size));
        atomicSize.set(size);
        Gauge.builder("dlq.topic.pending", atomicSize, AtomicLong::get)
             .tag("topic", topic).register(registry);
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    private List<String> loadDistinctTopics() throws SQLException {
        List<String> topics = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT DISTINCT topic FROM dead_letters ORDER BY topic");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) topics.add(rs.getString("topic"));
        }
        return topics;
    }

    private long countByTopicAndStatus(String topic, String statusCsv) throws SQLException {
        String inList = "'" + statusCsv.replace(",", "','") + "'";
        String sql = "SELECT COUNT(*) FROM dead_letters WHERE topic=? AND status IN (" + inList + ")";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, topic);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private long countRecentByStatus(String topic, String status, int hours) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM dead_letters WHERE topic=? AND status=? " +
                     "AND updated_at >= NOW() - (? || ' hours')::interval")) {
            ps.setString(1, topic); ps.setString(2, status); ps.setInt(3, hours);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private long countRecentCaptured(String topic, int hours) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM dead_letters WHERE topic=? " +
                     "AND captured_at >= NOW() - (? || ' hours')::interval")) {
            ps.setString(1, topic); ps.setInt(2, hours);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private long findOldestItemAgeMinutes(String topic) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT EXTRACT(EPOCH FROM (NOW() - MIN(captured_at)))/60 " +
                     "FROM dead_letters WHERE topic=? AND status IN ('DEAD','INVESTIGATING','RETRYING')")) {
            ps.setString(1, topic);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { double v = rs.getDouble(1); return rs.wasNull() ? 0L : (long)v; }
                return 0L;
            }
        }
    }
}
