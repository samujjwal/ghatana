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
 * @doc.purpose Retention management for archived dead-letter messages. Dead letters in
 *              DISCARDED or RESOLVED status older than the configured retention period are
 *              eligible for archival or permanent deletion. Integrates with K-08
 *              RetentionEnforcementSchedulerService pattern: per-topic or global retention
 *              policies, daily scan, 7-day grace period before deletion, audit proof.
 *              Satisfies STORY-K19-010.
 * @doc.layer   Kernel
 * @doc.pattern Retention policy per topic; daily scan + grace period; archive-then-delete;
 *              K-07 audit trail; archived/deleted Counters.
 */
public class DlqArchiveRetentionService {

    private static final int DEFAULT_RETENTION_DAYS = 90;
    private static final int GRACE_PERIOD_DAYS       = 7;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ArchivePort      archivePort;
    private final AuditPort        auditPort;
    private final Counter          archivedCounter;
    private final Counter          deletedCounter;

    public DlqArchiveRetentionService(HikariDataSource dataSource, Executor executor,
                                       ArchivePort archivePort,
                                       AuditPort auditPort,
                                       MeterRegistry registry) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.archivePort    = archivePort;
        this.auditPort      = auditPort;
        this.archivedCounter = Counter.builder("dlq.retention.archived_total").register(registry);
        this.deletedCounter  = Counter.builder("dlq.retention.deleted_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Cold-storage archive delegation. */
    public interface ArchivePort {
        String archiveBatch(List<String> deadLetterIds, String storageClass);
    }

    /** K-07 audit trail. */
    public interface AuditPort {
        void log(String action, String resourceType, String resourceId, Map<String, Object> details);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public record RetentionPolicy(
        String policyId, String topicName,
        int retentionDays, boolean archiveBeforeDelete
    ) {}

    public record RetentionRunResult(
        String runId, Instant ranAt,
        int scanned, int archived, int deleted
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Daily retention enforcement run. Finds messages past retention + grace period.
     * Archives first, then deletes archived records.
     */
    public Promise<RetentionRunResult> runDailyRetention() {
        return Promise.ofBlocking(executor, () -> {
            String runId = UUID.randomUUID().toString();
            Instant now  = Instant.now();

            List<String> toArchive = findExpiredMessages(now, true);
            List<String> toDelete  = findAlreadyArchived();

            int archivedCount = 0;
            int deletedCount  = 0;

            // Archive first
            if (!toArchive.isEmpty()) {
                String archiveJobId = archivePort.archiveBatch(toArchive, "GLACIER");
                markAsArchived(toArchive, archiveJobId);
                archivedCounter.increment(toArchive.size());
                archivedCount = toArchive.size();
                auditPort.log("DLQ_BATCH_ARCHIVED", "DlqRetentionRun", runId,
                    Map.of("count", toArchive.size(), "archiveJobId", archiveJobId));
            }

            // Delete messages that were archived in previous runs
            if (!toDelete.isEmpty()) {
                permanentlyDelete(toDelete);
                deletedCounter.increment(toDelete.size());
                deletedCount = toDelete.size();
                auditPort.log("DLQ_BATCH_DELETED", "DlqRetentionRun", runId,
                    Map.of("count", toDelete.size()));
            }

            persistRunRecord(runId, now, toArchive.size() + toDelete.size(), archivedCount, deletedCount);
            return new RetentionRunResult(runId, now, toArchive.size() + toDelete.size(),
                archivedCount, deletedCount);
        });
    }

    /**
     * Register or update a per-topic retention policy.
     */
    public Promise<Void> upsertRetentionPolicy(RetentionPolicy policy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO dlq_retention_policies " +
                     "(policy_id, topic_name, retention_days, archive_before_delete) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT (topic_name) DO UPDATE SET " +
                     "retention_days = EXCLUDED.retention_days, " +
                     "archive_before_delete = EXCLUDED.archive_before_delete")) {
                ps.setString(1, policy.policyId() != null ? policy.policyId() : UUID.randomUUID().toString());
                ps.setString(2, policy.topicName());
                ps.setInt(3, policy.retentionDays());
                ps.setBoolean(4, policy.archiveBeforeDelete());
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private List<String> findExpiredMessages(Instant now, boolean archiveFirst) throws SQLException {
        List<String> ids = new ArrayList<>();
        Instant graceCutoff = now.minusSeconds((long) GRACE_PERIOD_DAYS * 86400);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT dl.dead_letter_id FROM dead_letters dl " +
                 "LEFT JOIN dlq_retention_policies p ON p.topic_name = dl.topic_name " +
                 "WHERE dl.status IN ('DISCARDED', 'RESOLVED') " +
                 "AND dl.archived_at IS NULL " +
                 "AND dl.captured_at < NOW() - " +
                 "  (COALESCE(p.retention_days, ?) || ' days')::INTERVAL " +
                 "AND dl.captured_at < ? " +
                 "LIMIT 1000")) {
            ps.setInt(1, DEFAULT_RETENTION_DAYS);
            ps.setTimestamp(2, Timestamp.from(graceCutoff));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("dead_letter_id"));
            }
        }
        return ids;
    }

    private List<String> findAlreadyArchived() throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT dead_letter_id FROM dead_letters " +
                 "WHERE archived_at IS NOT NULL AND archived_at < NOW() - INTERVAL '7 days' " +
                 "AND deleted_at IS NULL LIMIT 500")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("dead_letter_id"));
            }
        }
        return ids;
    }

    private void markAsArchived(List<String> ids, String archiveJobId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dead_letters SET archived_at = NOW(), archive_job_id = ? " +
                 "WHERE dead_letter_id = ANY(?)")) {
            ps.setString(1, archiveJobId);
            ps.setArray(2, c.createArrayOf("text", ids.toArray()));
            ps.executeUpdate();
        }
    }

    private void permanentlyDelete(List<String> ids) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM dead_letters WHERE dead_letter_id = ANY(?)")) {
            ps.setArray(1, c.createArrayOf("text", ids.toArray()));
            ps.executeUpdate();
        }
    }

    private void persistRunRecord(String runId, Instant ranAt, int scanned,
                                   int archived, int deleted) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO dlq_retention_runs (run_id, ran_at, scanned, archived, deleted) " +
                 "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, runId);
            ps.setTimestamp(2, Timestamp.from(ranAt));
            ps.setInt(3, scanned);
            ps.setInt(4, archived);
            ps.setInt(5, deleted);
            ps.executeUpdate();
        }
    }
}
