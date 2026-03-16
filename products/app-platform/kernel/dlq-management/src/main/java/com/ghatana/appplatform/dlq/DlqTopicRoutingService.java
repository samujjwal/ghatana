package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type    DomainService
 * @doc.purpose Manages per-topic DLQ isolation. Each business topic is mapped to a named
 *              DLQ with a configurable priority (CRITICAL, HIGH, NORMAL, LOW). Settlement
 *              and payment events are CRITICAL by default. Overflow protection: when a
 *              topic DLQ exceeds its maxCapacity, an alert event is published and the
 *              oldest LOW-priority items become eligible for archival.
 *              Satisfies STORY-K19-002.
 * @doc.layer   Kernel
 * @doc.pattern Topic-to-DLQ routing registry; priority lanes; overflow alert;
 *              per-topic capacity Gauge; ON CONFLICT DO UPDATE routing upsert.
 */
public class DlqTopicRoutingService {

    private static final int DEFAULT_MAX_CAPACITY = 10_000;

    private final HikariDataSource            dataSource;
    private final Executor                    executor;
    private final EventPort                   eventPort;
    private final Counter                     routedCounter;
    private final ConcurrentHashMap<String, AtomicInteger> topicSizes = new ConcurrentHashMap<>();
    private final MeterRegistry               registry;

    public DlqTopicRoutingService(HikariDataSource dataSource, Executor executor,
                                   EventPort eventPort, MeterRegistry registry) {
        this.dataSource   = dataSource;
        this.executor     = executor;
        this.eventPort    = eventPort;
        this.registry     = registry;
        this.routedCounter = Counter.builder("dlq.routed_total").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Enums / Records ─────────────────────────────────────────────────────

    public enum Priority { CRITICAL, HIGH, NORMAL, LOW }

    public record DlqRoute(String routeId, String sourceTopic, String dlqName,
                            Priority priority, int maxCapacity, boolean enabled,
                            LocalDateTime createdAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Register or update routing for a source topic. */
    public Promise<DlqRoute> upsertRoute(String sourceTopic, Priority priority,
                                          Integer maxCapacity) {
        return Promise.ofBlocking(executor, () -> {
            int cap = maxCapacity != null ? maxCapacity : DEFAULT_MAX_CAPACITY;
            String dlqName = buildDlqName(sourceTopic, priority);
            String sql = """
                    INSERT INTO dlq_topic_routes
                        (route_id, source_topic, dlq_name, priority, max_capacity, enabled, created_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, ?, true, NOW())
                    ON CONFLICT (source_topic) DO UPDATE
                        SET dlq_name=EXCLUDED.dlq_name, priority=EXCLUDED.priority,
                            max_capacity=EXCLUDED.max_capacity
                    RETURNING *
                    """;
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, sourceTopic); ps.setString(2, dlqName);
                ps.setString(3, priority.name()); ps.setInt(4, cap);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    DlqRoute route = mapRow(rs);
                    registerGauge(sourceTopic);
                    return route;
                }
            }
        });
    }

    /** Resolve which DLQ a dead letter from sourceTopic should be placed into. */
    public Promise<String> resolveTargetDlq(String sourceTopic) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT dlq_name FROM dlq_topic_routes " +
                         "WHERE source_topic=? AND enabled=true")) {
                ps.setString(1, sourceTopic);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("dlq_name");
                }
            }
            // fallback: default DLQ for unknown topics
            return "dlq.unrouted." + sourceTopic.replace('.', '-');
        });
    }

    /** Called when a dead letter is accepted into a DLQ to enforce capacity. */
    public Promise<Void> recordAccepted(String sourceTopic) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE dlq_topic_routes " +
                         "SET current_size = current_size + 1, updated_at=NOW() " +
                         "WHERE source_topic=?")) {
                ps.setString(1, sourceTopic);
                ps.executeUpdate();
            }
            int newSize = incrementLocalSize(sourceTopic);
            routedCounter.increment();
            int cap = loadCapacity(sourceTopic);
            if (cap > 0 && newSize >= cap) {
                eventPort.publish("dlq-events", "DlqCapacityExceeded",
                        Map.of("sourceTopic", sourceTopic, "size", newSize, "capacity", cap));
            }
            return null;
        });
    }

    /** Mark low-priority items eligible for archival when overflow occurs. */
    public Promise<List<String>> getArchivalCandidates(String sourceTopic, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<String> ids = new ArrayList<>();
            String sql = """
                    SELECT dl.dead_letter_id FROM dead_letters dl
                    JOIN dlq_topic_routes r ON r.source_topic=dl.topic
                    WHERE dl.topic=? AND dl.status='DEAD' AND r.priority='LOW'
                    ORDER BY dl.captured_at ASC LIMIT ?
                    """;
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, sourceTopic); ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) ids.add(rs.getString("dead_letter_id"));
                }
            }
            return ids;
        });
    }

    public Promise<List<DlqRoute>> listAllRoutes() {
        return Promise.ofBlocking(executor, () -> {
            List<DlqRoute> list = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT * FROM dlq_topic_routes ORDER BY priority, source_topic");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String buildDlqName(String sourceTopic, Priority priority) {
        String safeSource = sourceTopic.replace('.', '-').replace('_', '-');
        return "dlq." + priority.name().toLowerCase() + "." + safeSource;
    }

    private int incrementLocalSize(String topic) {
        return topicSizes.computeIfAbsent(topic, k -> new AtomicInteger(0))
                         .incrementAndGet();
    }

    private void registerGauge(String topic) {
        AtomicInteger ai = topicSizes.computeIfAbsent(topic, k -> new AtomicInteger(0));
        Gauge.builder("dlq.topic.size", ai, AtomicInteger::get)
             .tag("topic", topic).register(registry);
    }

    private int loadCapacity(String topic) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT max_capacity FROM dlq_topic_routes WHERE source_topic=?")) {
            ps.setString(1, topic);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("max_capacity") : DEFAULT_MAX_CAPACITY;
            }
        }
    }

    private DlqRoute mapRow(ResultSet rs) throws SQLException {
        return new DlqRoute(rs.getString("route_id"), rs.getString("source_topic"),
                rs.getString("dlq_name"), Priority.valueOf(rs.getString("priority")),
                rs.getInt("max_capacity"), rs.getBoolean("enabled"),
                rs.getObject("created_at", LocalDateTime.class));
    }
}
