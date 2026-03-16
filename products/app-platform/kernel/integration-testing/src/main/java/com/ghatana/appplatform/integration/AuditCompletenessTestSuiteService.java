package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Audit completeness test suite.
 *              Verifies 100% of WRITE operations produce a K-07 audit event.
 *              Shadow mode: captures all API calls, compares with audit log.
 *              Tamper detection: modify audit event → detected as corrupt.
 *              Retention: events cannot be deleted before retention policy expires.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; scenario-execution; assertion
 *
 * STORY-T01-007: Implement audit completeness test suite
 */
public class AuditCompletenessTestSuiteService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ShadowCapturePort {
        /** Start capturing all API calls in shadow mode. Returns sessionId. */
        String startCapture() throws Exception;
        /** Stop shadow capture. Returns list of captured operation IDs. */
        List<String> stopCapture(String sessionId) throws Exception;
    }

    public interface AuditLogPort {
        /** Check if an audit event exists for the given operationId. */
        boolean hasAuditEventForOperation(String operationId) throws Exception;
        /** Get audit event body for tamper-detection check. */
        String getAuditEventBody(String operationId) throws Exception;
        /** Attempt to delete an audit event before policy window ends. Returns false if blocked. */
        boolean tryDeleteAuditEvent(String operationId) throws Exception;
        /** Attempt to modify an audit event body. */
        void modifyAuditEventBody(String operationId, String newBody) throws Exception;
        /** Check if the audit system detected tampering for the given event. */
        boolean isTamperingDetected(String operationId) throws Exception;
        /** Return count of audit events for this entity in the correct sequence. */
        boolean isSequenceIntact(String entityId) throws Exception;
    }

    public interface WriteOperationPort {
        /** Execute a set of sample WRITE operations (500 total). Returns list of operationIds. */
        List<String> execute500WriteOperations() throws Exception;
        String submitOrder(String clientId, String symbol, int qty) throws Exception;
        String updateConfig(String key, String value) throws Exception;
        String createTenant(String tenantId) throws Exception;
    }

    public interface RetentionPolicyPort {
        int getRetentionDays() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ShadowCapturePort shadowCapture;
    private final AuditLogPort auditLog;
    private final WriteOperationPort writeOps;
    private final RetentionPolicyPort retentionPolicy;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Counter missedAuditEvents;

    public AuditCompletenessTestSuiteService(
        javax.sql.DataSource ds,
        ShadowCapturePort shadowCapture,
        AuditLogPort auditLog,
        WriteOperationPort writeOps,
        RetentionPolicyPort retentionPolicy,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds              = ds;
        this.shadowCapture   = shadowCapture;
        this.auditLog        = auditLog;
        this.writeOps        = writeOps;
        this.retentionPolicy = retentionPolicy;
        this.audit           = audit;
        this.executor        = executor;
        this.suitesPassed    = Counter.builder("integration.audit.suites_passed").register(registry);
        this.suitesFailed    = Counter.builder("integration.audit.suites_failed").register(registry);
        this.missedAuditEvents = Counter.builder("integration.audit.missed_events").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("write_op_audit_coverage",   this::writeOpAuditCoverage));
            results.add(runScenario("shadow_mode_compare",       this::shadowModeCompare));
            results.add(runScenario("tamper_detection",          this::tamperDetection));
            results.add(runScenario("retention_reject_delete",   this::retentionRejectDelete));
            results.add(runScenario("500_op_coverage",           this::fiveHundredOpCoverage));
            results.add(runScenario("immutability",              this::immutabilityCheck));
            results.add(runScenario("event_sequence_integrity",  this::eventSequenceIntegrity));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("AUDIT_COMPLETENESS_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("AuditCompleteness", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /** Core WRITE operations each produce an audit event. */
    private void writeOpAuditCoverage(String runId) throws Exception {
        String orderId = writeOps.submitOrder("AUDIT-CLIENT-001", "NABIL", 100);
        Thread.sleep(100);
        boolean hasEvent = auditLog.hasAuditEventForOperation(orderId);
        assertStep(runId, "order_audited", "order submission audited", "true", hasEvent, hasEvent);

        String configOpId = writeOps.updateConfig("recon.tolerance", "0.01");
        Thread.sleep(100);
        boolean configAudited = auditLog.hasAuditEventForOperation(configOpId);
        assertStep(runId, "config_update_audited", "config update audited", "true", configAudited, configAudited);

        String tenantOpId = writeOps.createTenant("TEST-TENANT-AUDIT");
        Thread.sleep(100);
        boolean tenantAudited = auditLog.hasAuditEventForOperation(tenantOpId);
        assertStep(runId, "tenant_create_audited", "tenant creation audited", "true", tenantAudited, tenantAudited);
    }

    /** Shadow mode: captured API calls all have corresponding audit events. */
    private void shadowModeCompare(String runId) throws Exception {
        String sessionId = shadowCapture.startCapture();
        // Execute a batch of operations under shadow capture
        writeOps.submitOrder("AUDIT-CLIENT-002", "NTC", 50);
        writeOps.submitOrder("AUDIT-CLIENT-002", "NLIC", 200);
        writeOps.updateConfig("cob.cutoff_time", "17:00");
        Thread.sleep(300); // allow audit events to propagate
        List<String> capturedOps = shadowCapture.stopCapture(sessionId);
        assertStep(runId, "shadow_ops_captured", "shadow mode captured > 0 operations", "> 0",
            !capturedOps.isEmpty(), capturedOps.size());
        int missed = 0;
        for (String opId : capturedOps) {
            if (!auditLog.hasAuditEventForOperation(opId)) { missed++; missedAuditEvents.increment(); }
        }
        assertStep(runId, "no_missed_audit_events", "all shadow-captured ops have audit events", "0 missed",
            missed == 0, missed + " missed out of " + capturedOps.size());
    }

    /** Modify an audit event body → tamper detection fires. */
    private void tamperDetection(String runId) throws Exception {
        String opId = writeOps.submitOrder("AUDIT-CLIENT-003", "NIMB", 100);
        Thread.sleep(100);
        boolean hasEvent = auditLog.hasAuditEventForOperation(opId);
        assertStep(runId, "event_exists_before_tamper", "audit event exists before tamper attempt", "true", hasEvent, hasEvent);
        // Tamper with the event body
        auditLog.modifyAuditEventBody(opId, "{\"tampered\":true}");
        Thread.sleep(200); // allow tamper detection to kick in
        boolean detected = auditLog.isTamperingDetected(opId);
        assertStep(runId, "tamper_detected", "tampering detected after body modification", "true", detected, detected);
    }

    /** Attempt to delete an audit event before retention window → denied. */
    private void retentionRejectDelete(String runId) throws Exception {
        String opId = writeOps.submitOrder("AUDIT-CLIENT-004", "NABIL", 100);
        Thread.sleep(100);
        boolean deleted = auditLog.tryDeleteAuditEvent(opId);
        assertStep(runId, "delete_rejected", "audit event delete rejected before retention expires", "false", !deleted, deleted);
    }

    /** 500 write operations → 100% audit coverage. */
    private void fiveHundredOpCoverage(String runId) throws Exception {
        List<String> opIds = writeOps.execute500WriteOperations();
        assertStep(runId, "500_ops_submitted", "500 write operations submitted", "500", opIds.size() >= 500, opIds.size());
        Thread.sleep(500); // allow event propagation
        int missed = 0;
        for (String opId : opIds) {
            if (!auditLog.hasAuditEventForOperation(opId)) missed++;
        }
        int coveragePct = opIds.isEmpty() ? 0 : (int) ((opIds.size() - missed) * 100.0 / opIds.size());
        assertStep(runId, "audit_coverage_100pct", "100% of 500 write ops audited", "100%",
            missed == 0, coveragePct + "% (" + missed + " missed)");
    }

    /** Audit event body cannot be overwritten without tamper detection. */
    private void immutabilityCheck(String runId) throws Exception {
        String opId = writeOps.submitOrder("AUDIT-CLIENT-005", "NTC", 300);
        Thread.sleep(100);
        String originalBody = auditLog.getAuditEventBody(opId);
        assertStep(runId, "original_body_exists", "original audit body exists", "not null",
            originalBody != null && !originalBody.isEmpty(), originalBody == null ? "null" : "present");
        auditLog.modifyAuditEventBody(opId, originalBody + "_MODIFIED");
        String bodyAfter = auditLog.getAuditEventBody(opId);
        // Body should NOT have changed (immutable), or tamper should be flagged
        boolean immutable = originalBody.equals(bodyAfter) || auditLog.isTamperingDetected(opId);
        assertStep(runId, "audit_immutable", "audit event body is immutable or tamper flagged", "true", immutable, immutable);
    }

    /** Audit events for the same entity appear in correct chronological sequence. */
    private void eventSequenceIntegrity(String runId) throws Exception {
        String entityId = "SEQ-ORDER-001";
        writeOps.submitOrder(entityId, "NABIL", 100);
        Thread.sleep(50);
        writeOps.submitOrder(entityId, "NABIL", 200);
        Thread.sleep(50);
        writeOps.submitOrder(entityId, "NABIL", 300);
        Thread.sleep(200);
        boolean intactSeq = auditLog.isSequenceIntact(entityId);
        assertStep(runId, "event_sequence_intact", "audit events in correct sequence for entity", "true", intactSeq, intactSeq);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name);
            fn.accept(runId);
            markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) {
            return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return new ScenarioResult(name, false, ex.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('AuditCompleteness',?) RETURNING run_id"
             )) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE e2e_test_runs SET status=? WHERE run_id=?"
             )) { ps.setString(1, status); ps.setString(2, runId); ps.executeUpdate(); }
        catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
