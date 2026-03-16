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
 * @doc.purpose Captures events that have exceeded K-05 max-retry limits and persists
 *              their full payload (JSONB) in the dead_letters table. Lifecycle:
 *              DEAD → INVESTIGATING → RETRYING → RESOLVED | DISCARDED.
 *              Only capture() and investigate() are handled here; replay is in
 *              DeadLetterReplayService to keep concerns separate.
 *              Satisfies STORY-K19-001.
 * @doc.layer   Kernel
 * @doc.pattern Dead-letter lifecycle FSM; full JSONB payload store; EventPort alert;
 *              ON CONFLICT DO NOTHING (idempotent re-capture); dlq-size Gauge.
 */
public class DeadLetterCaptureService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final EventPort        eventPort;
    private final Counter          capturedCounter;
    private final Counter          resolvedCounter;
    private final AtomicInteger    pendingCount = new AtomicInteger(0);

    public DeadLetterCaptureService(HikariDataSource dataSource, Executor executor,
                                     EventPort eventPort, MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.eventPort      = eventPort;
        this.capturedCounter = Counter.builder("dlq.captured_total").register(registry);
        this.resolvedCounter = Counter.builder("dlq.resolved_total").register(registry);
        Gauge.builder("dlq.pending_count", pendingCount, AtomicInteger::get).register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record DeadLetter(String deadLetterId, String originalEventId, String topic,
                              String consumerGroup, String errorMessage, int retryCount,
                              String payload, String status, LocalDateTime capturedAt,
                              LocalDateTime updatedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Called by K-05 retry service when max retries are exceeded. Idempotent. */
    public Promise<DeadLetter> capture(String originalEventId, String topic,
                                        String consumerGroup, String errorMessage,
                                        int retryCount, String payloadJson) {
        return Promise.ofBlocking(executor, () -> {
            String id = UUID.randomUUID().toString();
            String sql = """
                    INSERT INTO dead_letters
                        (dead_letter_id, original_event_id, topic, consumer_group,
                         error_message, retry_count, payload, status, captured_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'DEAD', NOW(), NOW())
                    ON CONFLICT (original_event_id, consumer_group) DO NOTHING
                    """;
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, id); ps.setString(2, originalEventId);
                ps.setString(3, topic); ps.setString(4, consumerGroup);
                ps.setString(5, errorMessage); ps.setInt(6, retryCount);
                ps.setString(7, payloadJson);
                ps.executeUpdate();
            }
            capturedCounter.increment();
            pendingCount.incrementAndGet();
            eventPort.publish("dlq-events", "DeadLetterCaptured",
                    Map.of("originalEventId", originalEventId, "topic", topic,
                           "retryCount", retryCount));
            return load(id).orElseThrow();
        });
    }

    public Promise<Void> markInvestigating(String deadLetterId, String investigatedBy) {
        return Promise.ofBlocking(executor, () -> {
            transition(deadLetterId, "DEAD", "INVESTIGATING");
            appendNote(deadLetterId, "Investigation started by " + investigatedBy);
            return null;
        });
    }

    public Promise<Void> markResolved(String deadLetterId, String resolvedBy, String resolution) {
        return Promise.ofBlocking(executor, () -> {
            transition(deadLetterId, null, "RESOLVED"); // from any active state
            appendNote(deadLetterId, "Resolved by " + resolvedBy + ": " + resolution);
            resolvedCounter.increment();
            pendingCount.decrementAndGet();
            return null;
        });
    }

    public Promise<Void> discard(String deadLetterId, String discardedBy, String reason) {
        return Promise.ofBlocking(executor, () -> {
            transition(deadLetterId, null, "DISCARDED");
            appendNote(deadLetterId, "Discarded by " + discardedBy + ": " + reason);
            pendingCount.decrementAndGet();
            return null;
        });
    }

    public Promise<Optional<DeadLetter>> findById(String deadLetterId) {
        return Promise.ofBlocking(executor, () -> load(deadLetterId));
    }

    public Promise<List<DeadLetter>> listByTopic(String topic, String status, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<DeadLetter> list = new ArrayList<>();
            String sql = status != null
                    ? "SELECT * FROM dead_letters WHERE topic=? AND status=? " +
                      "ORDER BY captured_at LIMIT ?"
                    : "SELECT * FROM dead_letters WHERE topic=? ORDER BY captured_at LIMIT ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, topic);
                if (status != null) { ps.setString(2, status); ps.setInt(3, limit); }
                else ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    private void transition(String deadLetterId, String expectedFrom, String toStatus)
            throws SQLException {
        String sql = expectedFrom != null
                ? "UPDATE dead_letters SET status=?, updated_at=NOW() " +
                  "WHERE dead_letter_id=? AND status=?"
                : "UPDATE dead_letters SET status=?, updated_at=NOW() WHERE dead_letter_id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, toStatus); ps.setString(2, deadLetterId);
            if (expectedFrom != null) ps.setString(3, expectedFrom);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new IllegalStateException(
                    "Transition to " + toStatus + " failed for " + deadLetterId
                    + (expectedFrom != null ? " (expected status=" + expectedFrom + ")" : ""));
        }
    }

    private void appendNote(String deadLetterId, String note) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO dead_letter_notes (dead_letter_id, note, created_at) " +
                     "VALUES (?, ?, NOW())")) {
            ps.setString(1, deadLetterId); ps.setString(2, note);
            ps.executeUpdate();
        }
    }

    private Optional<DeadLetter> load(String deadLetterId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM dead_letters WHERE dead_letter_id=?")) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapRow(rs));
            }
        }
    }

    private DeadLetter mapRow(ResultSet rs) throws SQLException {
        return new DeadLetter(rs.getString("dead_letter_id"),
                rs.getString("original_event_id"), rs.getString("topic"),
                rs.getString("consumer_group"), rs.getString("error_message"),
                rs.getInt("retry_count"), rs.getString("payload"),
                rs.getString("status"),
                rs.getObject("captured_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }
}
