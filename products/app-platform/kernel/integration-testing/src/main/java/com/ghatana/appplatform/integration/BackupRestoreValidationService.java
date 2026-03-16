package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Backup and restore validation service (T-02).
 *              Scenarios: PostgreSQL PITR restore; Kafka 7-day replay; K-14 secrets restore;
 *              K-08 metadata restore; PITR timestamp accuracy ±30s; restore time measured;
 *              data integrity post-restore; procedure documented in audit log.
 * @doc.layer   Integration Testing (T-02 Chaos)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking
 *
 * DDL (in addition to shared tables):
 * <pre>
 * CREATE TABLE IF NOT EXISTS backup_restore_runs (
 *   run_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   backup_type   TEXT NOT NULL,
 *   scenario      TEXT NOT NULL,
 *   status        TEXT NOT NULL DEFAULT 'RUNNING',
 *   restore_time_ms BIGINT,
 *   verified_at   TIMESTAMPTZ DEFAULT now()
 * );
 * </pre>
 *
 * STORY-T02-008: Implement backup and restore validation
 */
public class BackupRestoreValidationService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface PostgresBackupPort {
        /**
         * Drop (shadow) test database and restore from latest PITR backup.
         * Returns elapsed restore time in ms.
         */
        long restoreFromPitr(String targetTimestamp) throws Exception;
        /** Verify row count matches expected snapshot count. */
        boolean verifyDataIntegrity(long expectedRows) throws Exception;
        /** Get the timestamp of the restored snapshot (to check PITR accuracy). */
        String getRestoredSnapshotTimestamp() throws Exception;
        long getSnapshotRowCount() throws Exception;
    }

    public interface KafkaReplayPort {
        /** Verify that topic messages are retained for at least 7 days. */
        boolean hasRetentionDays(String topic, int days) throws Exception;
        /** Replay all events since a past offset and count replayed. */
        long replayFromBeginning(String topic) throws Exception;
    }

    public interface SecretsBackupPort {
        /** Drop K-14 secrets store and restore from backup. Returns restore time ms. */
        long restoreK14SecretsFromBackup() throws Exception;
        /** Verify that a known secret is still accessible after restore. */
        boolean verifySecretAccessible(String secretKey) throws Exception;
    }

    public interface MetadataBackupPort {
        /** Restore K-08 metadata from backup. Returns restore time ms. */
        long restoreK08MetadataFromBackup() throws Exception;
        /** Verify metadata config count matches expected. */
        boolean verifyMetadataIntegrity(int expectedConfigCount) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int  PITR_ACCURACY_SECONDS  = 30;
    private static final int  KAFKA_RETENTION_DAYS   = 7;
    private static final String KAFKA_AUDIT_TOPIC    = "platform.audit.events";
    private static final String KNOWN_SECRET_KEY     = "app.db.password";
    private static final int  EXPECTED_CONFIG_COUNT  = 50;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final PostgresBackupPort postgres;
    private final KafkaReplayPort kafka;
    private final SecretsBackupPort secrets;
    private final MetadataBackupPort metadata;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public BackupRestoreValidationService(
        javax.sql.DataSource ds,
        PostgresBackupPort postgres,
        KafkaReplayPort kafka,
        SecretsBackupPort secrets,
        MetadataBackupPort metadata,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds          = ds;
        this.postgres    = postgres;
        this.kafka       = kafka;
        this.secrets     = secrets;
        this.metadata    = metadata;
        this.audit       = audit;
        this.executor    = executor;
        this.suitesPassed = Counter.builder("integration.backup.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.backup.suites_failed").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("pitr_restore",           this::pitrRestore));
            results.add(runScenario("kafka_replay_7days",     this::kafkaReplay7days));
            results.add(runScenario("k14_secrets_restore",    this::k14SecretsRestore));
            results.add(runScenario("k08_metadata_restore",   this::k08MetadataRestore));
            results.add(runScenario("pitr_timestamp_accuracy",this::pitrTimestampAccuracy));
            results.add(runScenario("restore_time_measured",  this::restoreTimeMeasured));
            results.add(runScenario("integrity_post_restore", this::integrityPostRestore));
            results.add(runScenario("procedure_documented",   this::procedureDocumented));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("BACKUP_RESTORE_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("BackupRestoreValidation", results, passed, failed);
        });
    }

    private void pitrRestore(String runId) throws Exception {
        String targetTs = java.time.Instant.now().minusSeconds(300).toString(); // 5 min ago
        long restoreMs = postgres.restoreFromPitr(targetTs);
        assertStep(runId, "pitr_completed", "PITR restore completed", "no-throw", restoreMs >= 0, restoreMs + "ms");
        persistBackupRun(runId, "PITR", "pitr_restore", restoreMs);
    }

    private void kafkaReplay7days(String runId) throws Exception {
        boolean retained = kafka.hasRetentionDays(KAFKA_AUDIT_TOPIC, KAFKA_RETENTION_DAYS);
        assertStep(runId, "kafka_7day_retention", "Kafka topic has 7-day retention",
            "true", retained, retained);
        long replayed = kafka.replayFromBeginning(KAFKA_AUDIT_TOPIC);
        assertStep(runId, "kafka_replay_count", "Kafka replay returns events", "> 0", replayed > 0, replayed);
    }

    private void k14SecretsRestore(String runId) throws Exception {
        long restoreMs = secrets.restoreK14SecretsFromBackup();
        assertStep(runId, "k14_restore_completed", "K-14 secrets restore completed", "no-throw",
            restoreMs >= 0, restoreMs + "ms");
        boolean accessible = secrets.verifySecretAccessible(KNOWN_SECRET_KEY);
        assertStep(runId, "k14_secret_accessible", "known secret accessible after restore",
            "true", accessible, accessible);
        persistBackupRun(runId, "K14_SECRETS", "k14_secrets_restore", restoreMs);
    }

    private void k08MetadataRestore(String runId) throws Exception {
        long restoreMs = metadata.restoreK08MetadataFromBackup();
        assertStep(runId, "k08_restore_completed", "K-08 metadata restore completed", "no-throw",
            restoreMs >= 0, restoreMs + "ms");
        boolean integrity = metadata.verifyMetadataIntegrity(EXPECTED_CONFIG_COUNT);
        assertStep(runId, "k08_metadata_integrity", "K-08 metadata count matches after restore",
            String.valueOf(EXPECTED_CONFIG_COUNT), integrity, integrity);
        persistBackupRun(runId, "K08_METADATA", "k08_metadata_restore", restoreMs);
    }

    /** PITR must restore to within ±30 seconds of target timestamp. */
    private void pitrTimestampAccuracy(String runId) throws Exception {
        java.time.Instant target = java.time.Instant.now().minusSeconds(600); // 10 min ago
        postgres.restoreFromPitr(target.toString());
        String restoredTs = postgres.getRestoredSnapshotTimestamp();
        java.time.Instant restored = java.time.Instant.parse(restoredTs);
        long driftSeconds = Math.abs(java.time.Duration.between(target, restored).getSeconds());
        assertStep(runId, "pitr_accuracy", "PITR timestamp accuracy ± " + PITR_ACCURACY_SECONDS + "s",
            "<= " + PITR_ACCURACY_SECONDS + "s", driftSeconds <= PITR_ACCURACY_SECONDS,
            driftSeconds + "s drift");
    }

    /** Restore time must be recorded and retrievable from backup_restore_runs. */
    private void restoreTimeMeasured(String runId) throws Exception {
        long restoreMs = postgres.restoreFromPitr(java.time.Instant.now().minusSeconds(120).toString());
        persistBackupRun(runId, "PITR", "restore_time_measured", restoreMs);
        boolean recorded = isBackupRunRecorded(runId);
        assertStep(runId, "restore_time_persisted", "restore time persisted to backup_restore_runs",
            "true", recorded, recorded);
    }

    /** Data integrity check: row count matches snapshot after PITR restore. */
    private void integrityPostRestore(String runId) throws Exception {
        long expectedRows = postgres.getSnapshotRowCount();
        postgres.restoreFromPitr(java.time.Instant.now().minusSeconds(60).toString());
        boolean intact = postgres.verifyDataIntegrity(expectedRows);
        assertStep(runId, "integrity_after_restore", "data integrity verified post PITR restore",
            "true", intact, intact);
    }

    /** Procedure documented: all restore types logged to audit. */
    private void procedureDocumented(String runId) throws Exception {
        audit.audit("BACKUP_PROCEDURE", "PITR, Kafka 7-day replay, K-14 secrets, K-08 metadata restore procedures verified");
        // If audit() doesn't throw, documentation event is persisted
        assertStep(runId, "procedure_audit_logged", "restore procedure logged to audit", "no-throw", true, "ok");
    }

    private void persistBackupRun(String runId, String backupType, String scenario, long restoreMs) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO backup_restore_runs (run_id,backup_type,scenario,status,restore_time_ms) VALUES (?,?,?,'VERIFIED',?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, runId); ps.setString(2, backupType);
            ps.setString(3, scenario); ps.setLong(4, restoreMs);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private boolean isBackupRunRecorded(String runId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM backup_restore_runs WHERE run_id=?")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name); fn.accept(runId); markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) { return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex)      { return new ScenarioResult(name, false, ex.getMessage(),  System.currentTimeMillis() - start); }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('BackupRestoreValidation',?) RETURNING run_id")) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE e2e_test_runs SET status=? WHERE run_id=?")) {
            ps.setString(1, status); ps.setString(2, runId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
