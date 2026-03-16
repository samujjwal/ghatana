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
 * @doc.purpose Bulk replay of dead-letter messages matching a filter (topic, time range,
 *              error category). Delegates individual message replay to DeadLetterReplayService
 *              via ReplayPort. Tracks a bulk replay job with status
 *              PENDING → IN_PROGRESS → COMPLETED | PARTIAL_FAILURE. Applies rate limiting
 *              to avoid overwhelming downstream services during replay. Satisfies STORY-K19-005.
 * @doc.layer   Kernel
 * @doc.pattern Bulk job tracking; rate-limited replay batches; filter-based message selection;
 *              ReplayPort delegation; bulkStarted/completed/messagesReplayed Counters.
 */
public class BulkReplayService {

    private static final int DEFAULT_BATCH_SIZE        = 50;
    private static final int DEFAULT_RATE_LIMIT_PER_SEC = 20;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ReplayPort       replayPort;
    private final AuditPort        auditPort;
    private final Counter          bulkJobsStartedCounter;
    private final Counter          bulkJobsCompletedCounter;
    private final Counter          messagesReplayedCounter;

    public BulkReplayService(HikariDataSource dataSource, Executor executor,
                              ReplayPort replayPort, AuditPort auditPort,
                              MeterRegistry registry) {
        this.dataSource             = dataSource;
        this.executor               = executor;
        this.replayPort             = replayPort;
        this.auditPort              = auditPort;
        this.bulkJobsStartedCounter   = Counter.builder("dlq.bulk_replay.jobs_started_total").register(registry);
        this.bulkJobsCompletedCounter = Counter.builder("dlq.bulk_replay.jobs_completed_total").register(registry);
        this.messagesReplayedCounter  = Counter.builder("dlq.bulk_replay.messages_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Replays a single dead-letter message. */
    public interface ReplayPort {
        void replayMessage(String deadLetterId, String bulkJobId);
    }

    /** K-07 audit trail. */
    public interface AuditPort {
        void log(String action, String resourceType, String resourceId, Map<String, Object> details);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum BulkJobStatus { PENDING, IN_PROGRESS, COMPLETED, PARTIAL_FAILURE }

    public record BulkReplayFilter(
        String topicName, Instant from, Instant to,
        String errorCategory, int maxMessages
    ) {}

    public record BulkReplayJob(
        String jobId, BulkReplayFilter filter,
        BulkJobStatus status, int totalMessages,
        int replayed, int failed,
        Instant startedAt, Instant completedAt, String initiatedBy
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Start a bulk replay job. Finds all dead-letter messages matching the filter,
     * persists a job record, then replays in batches with rate limiting.
     */
    public Promise<BulkReplayJob> startBulkReplay(BulkReplayFilter filter, String initiatedBy) {
        return Promise.ofBlocking(executor, () -> {
            String jobId   = UUID.randomUUID().toString();
            Instant now    = Instant.now();
            List<String> messageIds = findMatchingMessages(filter);

            persistJob(jobId, filter, BulkJobStatus.IN_PROGRESS, messageIds.size(), now, initiatedBy);
            bulkJobsStartedCounter.increment();

            auditPort.log("BULK_REPLAY_STARTED", "DlqBulkJob", jobId,
                Map.of("totalMessages", messageIds.size(), "topic", filter.topicName(),
                        "initiatedBy", initiatedBy));

            int replayed = 0;
            int failed   = 0;
            int batchCount = 0;

            for (String msgId : messageIds) {
                try {
                    replayPort.replayMessage(msgId, jobId);
                    replayed++;
                    messagesReplayedCounter.increment();
                } catch (Exception e) {
                    failed++;
                    recordFailure(jobId, msgId, e.getMessage());
                }

                batchCount++;
                if (batchCount % DEFAULT_BATCH_SIZE == 0) {
                    // Rate limit: sleep proportional to batch size / rate limit
                    Thread.sleep(DEFAULT_BATCH_SIZE * 1000L / DEFAULT_RATE_LIMIT_PER_SEC);
                }
            }

            BulkJobStatus finalStatus = failed == 0 ? BulkJobStatus.COMPLETED : BulkJobStatus.PARTIAL_FAILURE;
            updateJobCompletion(jobId, finalStatus, replayed, failed, Instant.now());
            bulkJobsCompletedCounter.increment();

            auditPort.log("BULK_REPLAY_COMPLETED", "DlqBulkJob", jobId,
                Map.of("status", finalStatus.name(), "replayed", replayed, "failed", failed));

            return new BulkReplayJob(jobId, filter, finalStatus, messageIds.size(),
                replayed, failed, now, Instant.now(), initiatedBy);
        });
    }

    /**
     * Get the current status of a bulk replay job.
     */
    public Promise<Optional<BulkReplayJob>> getJobStatus(String jobId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT topic_name, status, total_messages, replayed, failed, " +
                     "started_at, completed_at, initiated_by " +
                     "FROM dlq_bulk_replay_jobs WHERE job_id = ?")) {
                ps.setString(1, jobId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    Timestamp ct = rs.getTimestamp("completed_at");
                    BulkReplayFilter filter = new BulkReplayFilter(
                        rs.getString("topic_name"), null, null, null, 0);
                    return Optional.of(new BulkReplayJob(
                        jobId, filter,
                        BulkJobStatus.valueOf(rs.getString("status")),
                        rs.getInt("total_messages"),
                        rs.getInt("replayed"),
                        rs.getInt("failed"),
                        rs.getTimestamp("started_at").toInstant(),
                        ct != null ? ct.toInstant() : null,
                        rs.getString("initiated_by")
                    ));
                }
            }
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private List<String> findMatchingMessages(BulkReplayFilter filter) throws SQLException {
        List<String> ids = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT dead_letter_id FROM dead_letters " +
            "WHERE status = 'DEAD' AND topic_name = ? " +
            "AND captured_at BETWEEN ? AND ?");
        if (filter.errorCategory() != null) sql.append(" AND error_category = ?");
        sql.append(" LIMIT ?");

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setString(1, filter.topicName());
            ps.setTimestamp(2, Timestamp.from(filter.from() != null ? filter.from() : Instant.EPOCH));
            ps.setTimestamp(3, Timestamp.from(filter.to() != null ? filter.to() : Instant.now()));
            int paramIdx = 4;
            if (filter.errorCategory() != null) { ps.setString(paramIdx++, filter.errorCategory()); }
            ps.setInt(paramIdx, filter.maxMessages() > 0 ? filter.maxMessages() : 1000);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("dead_letter_id"));
            }
        }
        return ids;
    }

    private void persistJob(String jobId, BulkReplayFilter filter, BulkJobStatus status,
                             int total, Instant startedAt, String initiatedBy) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO dlq_bulk_replay_jobs " +
                 "(job_id, topic_name, status, total_messages, replayed, failed, " +
                 "started_at, initiated_by) VALUES (?, ?, ?, ?, 0, 0, ?, ?)")) {
            ps.setString(1, jobId);
            ps.setString(2, filter.topicName());
            ps.setString(3, status.name());
            ps.setInt(4, total);
            ps.setTimestamp(5, Timestamp.from(startedAt));
            ps.setString(6, initiatedBy);
            ps.executeUpdate();
        }
    }

    private void updateJobCompletion(String jobId, BulkJobStatus status,
                                      int replayed, int failed, Instant completedAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dlq_bulk_replay_jobs SET status = ?, replayed = ?, failed = ?, " +
                 "completed_at = ? WHERE job_id = ?")) {
            ps.setString(1, status.name());
            ps.setInt(2, replayed);
            ps.setInt(3, failed);
            ps.setTimestamp(4, Timestamp.from(completedAt));
            ps.setString(5, jobId);
            ps.executeUpdate();
        }
    }

    private void recordFailure(String jobId, String messageId, String reason) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO dlq_bulk_replay_failures (job_id, dead_letter_id, reason, failed_at) " +
                 "VALUES (?, ?, ?, NOW())")) {
            ps.setString(1, jobId);
            ps.setString(2, messageId);
            ps.setString(3, reason);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Best effort failure recording; don't abort the bulk job
        }
    }
}
