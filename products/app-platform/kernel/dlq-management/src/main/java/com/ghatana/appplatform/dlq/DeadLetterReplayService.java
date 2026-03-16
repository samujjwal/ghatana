package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Replays a single dead-lettered event back to its original topic via the
 *              EventPublishPort. Pre-replay validation hooks reject structurally invalid
 *              payloads before re-emission. Concurrent replay of the same dead letter
 *              is prevented by a database-level status CAS (DEAD → RETRYING). On
 *              success, status transitions to RESOLVED; on failure, status reverts to
 *              DEAD with an incremented replay_attempt_count.
 *              Satisfies STORY-K19-004.
 * @doc.layer   Kernel
 * @doc.pattern Optimistic CAS status transition; ValidationPort pre-check;
 *              EventPublishPort re-emit; replay_attempts table; Counter + Timer.
 */
public class DeadLetterReplayService {

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final EventPublishPort    publishPort;
    private final ValidationPort      validationPort;
    private final Counter             replaysAttemptedCounter;
    private final Counter             replaysSucceededCounter;
    private final Counter             replaysFailedCounter;
    private final Timer               replayTimer;

    public DeadLetterReplayService(HikariDataSource dataSource, Executor executor,
                                    EventPublishPort publishPort, ValidationPort validationPort,
                                    MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.publishPort             = publishPort;
        this.validationPort          = validationPort;
        this.replaysAttemptedCounter = Counter.builder("dlq.replays.attempted_total").register(registry);
        this.replaysSucceededCounter = Counter.builder("dlq.replays.succeeded_total").register(registry);
        this.replaysFailedCounter    = Counter.builder("dlq.replays.failed_total").register(registry);
        this.replayTimer             = Timer.builder("dlq.replay.duration").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface EventPublishPort {
        void publish(String topic, String payloadJson, Map<String, String> metadata);
    }

    public interface ValidationPort {
        /** Throws ValidationException if payload is structurally invalid for the topic. */
        void validate(String topic, String payloadJson);

        class ValidationException extends RuntimeException {
            public ValidationException(String msg) { super(msg); }
        }
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ReplayResult(String deadLetterId, boolean succeeded,
                                String errorMessage, int attemptNumber,
                                LocalDateTime replayedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ReplayResult> replay(String deadLetterId, String replayedBy) {
        return Promise.ofBlocking(executor, () -> replayTimer.recordCallable(() -> {
            replaysAttemptedCounter.increment();

            DeadLetterRow dl = loadAndLock(deadLetterId);
            if (dl == null) throw new IllegalArgumentException("DeadLetter not found: " + deadLetterId);

            // Pre-replay validation
            try {
                validationPort.validate(dl.topic(), dl.payload());
            } catch (ValidationPort.ValidationException e) {
                int attempts = recordAttempt(deadLetterId, false, e.getMessage());
                revertToDead(deadLetterId, "Validation failed: " + e.getMessage());
                replaysFailedCounter.increment();
                return new ReplayResult(deadLetterId, false, e.getMessage(), attempts, LocalDateTime.now());
            }

            // Publish with replay metadata
            int attemptNumber = dl.replayAttemptCount() + 1;
            Map<String, String> meta = Map.of(
                    "x-dlq-replay", "true",
                    "x-dlq-dead-letter-id", deadLetterId,
                    "x-dlq-replay-attempt", String.valueOf(attemptNumber),
                    "x-dlq-replayed-by", replayedBy);

            try {
                publishPort.publish(dl.topic(), dl.payload(), meta);
                recordAttempt(deadLetterId, true, null);
                markResolved(deadLetterId, replayedBy);
                replaysSucceededCounter.increment();
                return new ReplayResult(deadLetterId, true, null, attemptNumber, LocalDateTime.now());
            } catch (Exception e) {
                int attempts = recordAttempt(deadLetterId, false, e.getMessage());
                revertToDead(deadLetterId, "Publish failed: " + e.getMessage());
                replaysFailedCounter.increment();
                return new ReplayResult(deadLetterId, false, e.getMessage(), attempts, LocalDateTime.now());
            }
        }));
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    private record DeadLetterRow(String deadLetterId, String topic, String payload,
                                  int replayAttemptCount) {}

    /** CAS: atomically transitions DEAD → RETRYING and returns the row. Returns null if already locked. */
    private DeadLetterRow loadAndLock(String deadLetterId) throws SQLException {
        String sql = """
                UPDATE dead_letters
                SET status='RETRYING', updated_at=NOW(),
                    replay_attempt_count = replay_attempt_count + 1
                WHERE dead_letter_id=? AND status='DEAD'
                RETURNING dead_letter_id, topic, payload, replay_attempt_count
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new DeadLetterRow(rs.getString("dead_letter_id"),
                        rs.getString("topic"), rs.getString("payload"),
                        rs.getInt("replay_attempt_count") - 1);
            }
        }
    }

    private int recordAttempt(String deadLetterId, boolean succeeded,
                               String errorMessage) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO dead_letter_replay_attempts " +
                     "(dead_letter_id, succeeded, error_message, attempted_at) " +
                     "VALUES (?, ?, ?, NOW())")) {
            ps.setString(1, deadLetterId); ps.setBoolean(2, succeeded);
            ps.setString(3, errorMessage);
            ps.executeUpdate();
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT replay_attempt_count FROM dead_letters WHERE dead_letter_id=?")) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private void markResolved(String deadLetterId, String resolvedBy) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE dead_letters SET status='RESOLVED', updated_at=NOW() " +
                     "WHERE dead_letter_id=?")) {
            ps.setString(1, deadLetterId); ps.executeUpdate();
        }
    }

    private void revertToDead(String deadLetterId, String reason) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE dead_letters SET status='DEAD', error_message=?, updated_at=NOW() " +
                     "WHERE dead_letter_id=?")) {
            ps.setString(1, reason); ps.setString(2, deadLetterId); ps.executeUpdate();
        }
    }
}
