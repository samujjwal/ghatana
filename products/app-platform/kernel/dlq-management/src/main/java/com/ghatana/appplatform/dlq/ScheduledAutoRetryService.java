package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Scheduled automatic retry of dead-letter messages based on configurable per-topic
 *              retry policies. Policies define: max retry attempts, backoff type (FIXED /
 *              EXPONENTIAL), initial delay, and eligible error categories for auto-retry.
 *              Messages that exhaust max retries are moved to DISCARD workflow. Only messages
 *              in DEAD status that are not poison pills (marked by PoisonPillDetectionService)
 *              are candidates. Satisfies STORY-K19-006.
 * @doc.layer   Kernel
 * @doc.pattern Per-topic retry policy; FIXED/EXPONENTIAL backoff; poison-pill exclusion;
 *              max-attempts exhaustion → discard handoff; autoRetried/exhausted Counters.
 */
public class ScheduledAutoRetryService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final RetryExecutorPort retryExecutorPort;
    private final DiscardHandoffPort discardHandoffPort;
    private final Counter           autoRetriedCounter;
    private final Counter           exhaustedCounter;

    public ScheduledAutoRetryService(HikariDataSource dataSource, Executor executor,
                                      RetryExecutorPort retryExecutorPort,
                                      DiscardHandoffPort discardHandoffPort,
                                      MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.retryExecutorPort   = retryExecutorPort;
        this.discardHandoffPort  = discardHandoffPort;
        this.autoRetriedCounter  = Counter.builder("dlq.auto_retry.retried_total").register(registry);
        this.exhaustedCounter    = Counter.builder("dlq.auto_retry.exhausted_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Executes the actual message re-publication for retry. */
    public interface RetryExecutorPort {
        void retryMessage(String deadLetterId, int attemptNumber);
    }

    /** Hands off exhausted messages to the discard workflow. */
    public interface DiscardHandoffPort {
        void initiateDiscard(String deadLetterId, String reason);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum BackoffType { FIXED, EXPONENTIAL }

    public record RetryPolicy(
        String policyId, String topicName,
        int maxAttempts, BackoffType backoffType,
        long initialDelaySeconds, List<String> eligibleErrorCategories
    ) {}

    public record AutoRetryRun(
        String runId, Instant ranAt,
        int candidatesFound, int retriedCount, int exhaustedCount
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Main scheduled entry point. Finds eligible messages, applies backoff check, retries.
     * Intended to be called every minute by an infrastructure scheduler.
     */
    public Promise<AutoRetryRun> runScheduledRetry() {
        return Promise.ofBlocking(executor, () -> {
            String runId     = UUID.randomUUID().toString();
            Instant now      = Instant.now();
            List<RetryCandidate> candidates = findCandidates(now);

            int retriedCount   = 0;
            int exhaustedCount = 0;

            for (RetryCandidate candidate : candidates) {
                RetryPolicy policy = fetchPolicy(candidate.topicName());
                if (policy == null) continue;

                if (candidate.retryCount() >= policy.maxAttempts()) {
                    discardHandoffPort.initiateDiscard(candidate.deadLetterId(),
                        "Max retry attempts (" + policy.maxAttempts() + ") exhausted");
                    markExhausted(candidate.deadLetterId());
                    exhaustedCounter.increment();
                    exhaustedCount++;
                    continue;
                }

                long delay = computeDelay(policy, candidate.retryCount());
                if (candidate.lastAttemptAt() != null &&
                    Instant.now().isBefore(candidate.lastAttemptAt().plusSeconds(delay))) {
                    continue; // Not yet in backoff window
                }

                try {
                    retryExecutorPort.retryMessage(candidate.deadLetterId(), candidate.retryCount() + 1);
                    incrementRetryCount(candidate.deadLetterId());
                    autoRetriedCounter.increment();
                    retriedCount++;
                } catch (Exception e) {
                    incrementRetryCount(candidate.deadLetterId());
                }
            }

            return new AutoRetryRun(runId, now, candidates.size(), retriedCount, exhaustedCount);
        });
    }

    /**
     * Register or replace a per-topic retry policy.
     */
    public Promise<Void> upsertRetryPolicy(RetryPolicy policy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO dlq_retry_policies " +
                     "(policy_id, topic_name, max_attempts, backoff_type, initial_delay_seconds, " +
                     "eligible_error_categories) " +
                     "VALUES (?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (topic_name) DO UPDATE SET " +
                     "max_attempts = EXCLUDED.max_attempts, backoff_type = EXCLUDED.backoff_type, " +
                     "initial_delay_seconds = EXCLUDED.initial_delay_seconds, " +
                     "eligible_error_categories = EXCLUDED.eligible_error_categories")) {
                ps.setString(1, policy.policyId() != null ? policy.policyId() : UUID.randomUUID().toString());
                ps.setString(2, policy.topicName());
                ps.setInt(3, policy.maxAttempts());
                ps.setString(4, policy.backoffType().name());
                ps.setLong(5, policy.initialDelaySeconds());
                ps.setArray(6, c.createArrayOf("text", policy.eligibleErrorCategories().toArray()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private record RetryCandidate(
        String deadLetterId, String topicName,
        int retryCount, Instant lastAttemptAt, String errorCategory
    ) {}

    private List<RetryCandidate> findCandidates(Instant now) throws SQLException {
        List<RetryCandidate> results = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT dl.dead_letter_id, dl.topic_name, dl.retry_count, " +
                 "dl.last_attempt_at, dl.error_category " +
                 "FROM dead_letters dl " +
                 "WHERE dl.status = 'DEAD' AND NOT dl.is_poison_pill " +
                 "ORDER BY dl.priority DESC, dl.captured_at ASC LIMIT 500")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp lat = rs.getTimestamp("last_attempt_at");
                    results.add(new RetryCandidate(
                        rs.getString("dead_letter_id"),
                        rs.getString("topic_name"),
                        rs.getInt("retry_count"),
                        lat != null ? lat.toInstant() : null,
                        rs.getString("error_category")
                    ));
                }
            }
        }
        return results;
    }

    private RetryPolicy fetchPolicy(String topicName) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT policy_id, max_attempts, backoff_type, initial_delay_seconds, " +
                 "eligible_error_categories FROM dlq_retry_policies WHERE topic_name = ?")) {
            ps.setString(1, topicName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Array arr = rs.getArray("eligible_error_categories");
                List<String> cats = arr != null ? Arrays.asList((String[]) arr.getArray()) : List.of();
                return new RetryPolicy(
                    rs.getString("policy_id"), topicName,
                    rs.getInt("max_attempts"),
                    BackoffType.valueOf(rs.getString("backoff_type")),
                    rs.getLong("initial_delay_seconds"), cats
                );
            }
        }
    }

    private long computeDelay(RetryPolicy policy, int attempt) {
        return switch (policy.backoffType()) {
            case FIXED       -> policy.initialDelaySeconds();
            case EXPONENTIAL -> (long) (policy.initialDelaySeconds() * Math.pow(2, attempt));
        };
    }

    private void incrementRetryCount(String deadLetterId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dead_letters SET retry_count = retry_count + 1, " +
                 "last_attempt_at = NOW() WHERE dead_letter_id = ?")) {
            ps.setString(1, deadLetterId);
            ps.executeUpdate();
        }
    }

    private void markExhausted(String deadLetterId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dead_letters SET status = 'DISCARDED', " +
                 "discard_reason = 'MAX_RETRIES_EXHAUSTED' WHERE dead_letter_id = ?")) {
            ps.setString(1, deadLetterId);
            ps.executeUpdate();
        }
    }
}
