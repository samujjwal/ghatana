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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type    DomainService
 * @doc.purpose Detects poison-pill events — events that consistently fail across multiple
 *              consumer-group instances. A poison pill is identified when the same
 *              original_event_id appears in dead_letters for ≥ MIN_INSTANCE_HITS distinct
 *              consumer-group-instance combinations within a rolling time window.
 *              Detected items are immediately moved to a quarantine DLQ and a
 *              PoisonPillDetected event is published. When the poison-pill rate across a
 *              topic exceeds a threshold, a circuit-breaker open request is sent via K-18
 *              CircuitBreakerPort to pause consumption.  Satisfies STORY-K19-013.
 * @doc.layer   Kernel
 * @doc.pattern Multi-instance consensus detection; quarantine DLQ; K-18 CircuitBreakerPort;
 *              EventPort; poisonPillGauge; ON CONFLICT DO NOTHING quarantine insert.
 */
public class PoisonPillDetectionService {

    private static final int    MIN_INSTANCE_HITS         = 3;
    private static final int    DETECTION_WINDOW_MINUTES  = 30;
    private static final double CB_OPEN_RATE_THRESHOLD    = 0.05; // 5% of topic events

    private final HikariDataSource      dataSource;
    private final Executor              executor;
    private final EventPort             eventPort;
    private final CircuitBreakerPort    circuitBreakerPort;
    private final Counter               poisonPillsDetectedCounter;
    private final Counter               circuitBreakerOpenCounter;
    private final AtomicInteger         activePoisonPills = new AtomicInteger(0);

    public PoisonPillDetectionService(HikariDataSource dataSource, Executor executor,
                                       EventPort eventPort, CircuitBreakerPort circuitBreakerPort,
                                       MeterRegistry registry) {
        this.dataSource                 = dataSource;
        this.executor                   = executor;
        this.eventPort                  = eventPort;
        this.circuitBreakerPort         = circuitBreakerPort;
        this.poisonPillsDetectedCounter  = Counter.builder("dlq.poison_pills.detected_total").register(registry);
        this.circuitBreakerOpenCounter   = Counter.builder("dlq.circuit_breaker.opens_total").register(registry);
        Gauge.builder("dlq.poison_pills.active", activePoisonPills, AtomicInteger::get).register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    public interface CircuitBreakerPort {
        void requestOpen(String topicId, String reason);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record PoisonPill(String poisonPillId, String originalEventId, String topic,
                              int instanceHitCount, String quarantineDlqName,
                              LocalDateTime detectedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Analyse recently captured dead letters for a topic to find poison pills.
     * Intended to be called periodically or triggered by DlqMetricsService threshold.
     */
    public Promise<List<PoisonPill>> detectAndQuarantine(String topic) {
        return Promise.ofBlocking(executor, () -> {
            List<PoisonPillCandidate> candidates = findCandidates(topic);
            List<PoisonPill> detected = new ArrayList<>();

            for (PoisonPillCandidate c : candidates) {
                if (!alreadyQuarantined(c.originalEventId())) {
                    PoisonPill pill = quarantine(c, topic);
                    detected.add(pill);
                    poisonPillsDetectedCounter.increment();
                    activePoisonPills.incrementAndGet();
                    eventPort.publish("dlq-alerts", "PoisonPillDetected",
                            Map.of("originalEventId", c.originalEventId(),
                                   "topic", topic,
                                   "instanceHits", c.instanceCount(),
                                   "quarantineDlq", pill.quarantineDlqName()));
                }
            }

            if (!candidates.isEmpty()) {
                checkCircuitBreaker(topic, candidates.size());
            }

            return detected;
        });
    }

    public Promise<Void> resolveQuarantine(String poisonPillId, String resolvedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE poison_pill_quarantine SET resolved=true, " +
                         "resolved_by=?, resolved_at=NOW() WHERE poison_pill_id=?")) {
                ps.setString(1, resolvedBy); ps.setString(2, poisonPillId);
                ps.executeUpdate();
            }
            activePoisonPills.decrementAndGet();
            return null;
        });
    }

    // ─── Detection logic ─────────────────────────────────────────────────────

    private record PoisonPillCandidate(String originalEventId, int instanceCount) {}

    private List<PoisonPillCandidate> findCandidates(String topic) throws SQLException {
        List<PoisonPillCandidate> list = new ArrayList<>();
        String sql = """
                SELECT original_event_id, COUNT(DISTINCT consumer_group) as instance_count
                FROM dead_letters
                WHERE topic=?
                  AND captured_at >= NOW() - (? || ' minutes')::interval
                GROUP BY original_event_id
                HAVING COUNT(DISTINCT consumer_group) >= ?
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, topic);
            ps.setInt(2, DETECTION_WINDOW_MINUTES);
            ps.setInt(3, MIN_INSTANCE_HITS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PoisonPillCandidate(rs.getString("original_event_id"),
                            rs.getInt("instance_count")));
                }
            }
        }
        return list;
    }

    private boolean alreadyQuarantined(String originalEventId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM poison_pill_quarantine " +
                     "WHERE original_event_id=? AND resolved=false")) {
            ps.setString(1, originalEventId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private PoisonPill quarantine(PoisonPillCandidate candidate, String topic) throws SQLException {
        String pillId = UUID.randomUUID().toString();
        String quarantineDlq = "dlq.quarantine." + topic.replace('.', '-');
        String sql = """
                INSERT INTO poison_pill_quarantine
                    (poison_pill_id, original_event_id, topic, instance_hit_count,
                     quarantine_dlq_name, resolved, detected_at)
                VALUES (?, ?, ?, ?, ?, false, NOW())
                ON CONFLICT (original_event_id) DO NOTHING
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pillId); ps.setString(2, candidate.originalEventId());
            ps.setString(3, topic); ps.setInt(4, candidate.instanceCount());
            ps.setString(5, quarantineDlq);
            ps.executeUpdate();
        }
        return new PoisonPill(pillId, candidate.originalEventId(), topic,
                candidate.instanceCount(), quarantineDlq, LocalDateTime.now());
    }

    private void checkCircuitBreaker(String topic, int pillCount) throws SQLException {
        long totalRecent = countRecentCaptures(topic);
        if (totalRecent > 0) {
            double rate = (double) pillCount / totalRecent;
            if (rate >= CB_OPEN_RATE_THRESHOLD) {
                circuitBreakerPort.requestOpen(topic,
                        "Poison pill rate " + String.format("%.1f%%", rate * 100)
                        + " exceeds " + (CB_OPEN_RATE_THRESHOLD * 100) + "% threshold");
                circuitBreakerOpenCounter.increment();
            }
        }
    }

    private long countRecentCaptures(String topic) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM dead_letters WHERE topic=? " +
                     "AND captured_at >= NOW() - (? || ' minutes')::interval")) {
            ps.setString(1, topic); ps.setInt(2, DETECTION_WINDOW_MINUTES);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }
}
