package com.ghatana.appplatform.deployment;

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
 * @doc.purpose Automates artifact promotion through the DEV → QA → STAGING → PROD pipeline.
 *              Each promotion leg requires all configured gates to pass (integration tests,
 *              config drift check, security scan). Promotion into PROD additionally requires
 *              a K-01 maker-checker approval via WorkflowPort. Satisfies STORY-K10-005.
 * @doc.layer   Kernel
 * @doc.pattern Promotion pipeline state machine; gated stage transitions; K-01 WorkflowPort
 *              for PROD approvals; promotions_total Counter; AuditPort for every state change.
 */
public class EnvironmentPromotionPipelineService {

    static final List<String> STAGE_ORDER = List.of("DEV", "QA", "STAGING", "PROD");

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final PromotionGatePort promotionGatePort;
    private final WorkflowPort      workflowPort;
    private final AuditPort         auditPort;
    private final Counter           promotionsStartedCounter;
    private final Counter           promotionsCompletedCounter;
    private final Counter           promotionsFailedCounter;

    public EnvironmentPromotionPipelineService(HikariDataSource dataSource, Executor executor,
                                                PromotionGatePort promotionGatePort,
                                                WorkflowPort workflowPort, AuditPort auditPort,
                                                MeterRegistry registry) {
        this.dataSource               = dataSource;
        this.executor                 = executor;
        this.promotionGatePort        = promotionGatePort;
        this.workflowPort             = workflowPort;
        this.auditPort                = auditPort;
        this.promotionsStartedCounter  = Counter.builder("deployment.promotions.started_total").register(registry);
        this.promotionsCompletedCounter = Counter.builder("deployment.promotions.completed_total").register(registry);
        this.promotionsFailedCounter   = Counter.builder("deployment.promotions.failed_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface PromotionGatePort {
        GateResult evaluate(String artifactId, String artifactVersion, String targetEnv, String gateType);
    }

    public interface WorkflowPort {
        WorkflowApproval requestApproval(String entityType, String entityId, String requestedBy, String reason);
        Optional<WorkflowApproval> getApproval(String approvalId);
    }

    public interface AuditPort {
        void record(String entityType, String entityId, String event, String actor, String detail);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum PromotionStatus { PENDING_APPROVAL, GATES_RUNNING, IN_PROGRESS, PROMOTED, FAILED, REJECTED }

    public record GateResult(boolean passed, String gateType, String detail) {}
    public record WorkflowApproval(String approvalId, boolean approved, String approver) {}

    public record PromotionRecord(
        String promotionId, String artifactId, String artifactVersion,
        String fromEnv, String targetEnv,
        PromotionStatus status, String approvalId,
        String failureReason, Instant createdAt, Instant updatedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Initiate a promotion from one environment to the next stage.
     * For PROD, creates a K-01 approval request; otherwise runs gates immediately.
     */
    public Promise<PromotionRecord> initiatePromotion(String artifactId, String artifactVersion,
                                                       String targetEnv, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            String fromEnv = previousStage(targetEnv);
            String promotionId = UUID.randomUUID().toString();

            boolean needsApproval = "PROD".equals(targetEnv);
            String approvalId = null;
            PromotionStatus initialStatus;

            if (needsApproval) {
                WorkflowApproval approval = workflowPort.requestApproval(
                    "ARTIFACT_PROMOTION", promotionId, requestedBy,
                    String.format("Promote %s@%s to PROD", artifactId, artifactVersion));
                approvalId = approval.approvalId();
                initialStatus = PromotionStatus.PENDING_APPROVAL;
            } else {
                initialStatus = PromotionStatus.GATES_RUNNING;
            }

            insertPromotion(promotionId, artifactId, artifactVersion, fromEnv, targetEnv,
                initialStatus, approvalId);
            auditPort.record("PROMOTION", promotionId, "INITIATED", requestedBy,
                String.format("target=%s needs_approval=%s", targetEnv, needsApproval));
            promotionsStartedCounter.increment();

            if (!needsApproval) {
                runGatesAndPromote(promotionId, artifactId, artifactVersion, targetEnv, requestedBy);
            }

            return fetchPromotion(promotionId);
        });
    }

    /**
     * Check approval status and proceed with promotion if approved.
     * Called by a scheduler or webhook handler when the K-01 approval resolves.
     */
    public Promise<PromotionRecord> checkAndAdvance(String promotionId, String actor) {
        return Promise.ofBlocking(executor, () -> {
            PromotionRecord rec = fetchPromotion(promotionId);
            if (rec.status() != PromotionStatus.PENDING_APPROVAL) {
                return rec;
            }

            Optional<WorkflowApproval> approvalOpt = workflowPort.getApproval(rec.approvalId());
            if (approvalOpt.isEmpty()) return rec;

            WorkflowApproval approval = approvalOpt.get();
            if (!approval.approved()) {
                updatePromotionStatus(promotionId, PromotionStatus.REJECTED, "Approval denied by " + approval.approver());
                auditPort.record("PROMOTION", promotionId, "REJECTED", actor, "approver=" + approval.approver());
                promotionsFailedCounter.increment();
                return fetchPromotion(promotionId);
            }

            updatePromotionStatus(promotionId, PromotionStatus.GATES_RUNNING, null);
            auditPort.record("PROMOTION", promotionId, "APPROVED", actor, "approver=" + approval.approver());
            runGatesAndPromote(promotionId, rec.artifactId(), rec.artifactVersion(), rec.targetEnv(), actor);
            return fetchPromotion(promotionId);
        });
    }

    /** Retrieve a promotion record by ID. */
    public Promise<PromotionRecord> getPromotion(String promotionId) {
        return Promise.ofBlocking(executor, () -> fetchPromotion(promotionId));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void runGatesAndPromote(String promotionId, String artifactId, String artifactVersion,
                                     String targetEnv, String actor) throws SQLException {
        List<String> gateTypes = List.of("INTEGRATION_TEST", "CONFIG_DRIFT", "SECURITY_SCAN");
        for (String gate : gateTypes) {
            GateResult result = promotionGatePort.evaluate(artifactId, artifactVersion, targetEnv, gate);
            if (!result.passed()) {
                updatePromotionStatus(promotionId, PromotionStatus.FAILED,
                    "Gate failed: " + gate + " — " + result.detail());
                auditPort.record("PROMOTION", promotionId, "GATE_FAILED", actor,
                    "gate=" + gate + " detail=" + result.detail());
                promotionsFailedCounter.increment();
                return;
            }
        }
        updatePromotionStatus(promotionId, PromotionStatus.PROMOTED, null);
        auditPort.record("PROMOTION", promotionId, "PROMOTED", actor,
            String.format("artifact=%s@%s to %s", artifactId, artifactVersion, targetEnv));
        promotionsCompletedCounter.increment();
    }

    private String previousStage(String targetEnv) {
        int idx = STAGE_ORDER.indexOf(targetEnv);
        if (idx <= 0) throw new IllegalArgumentException("No previous stage for: " + targetEnv);
        return STAGE_ORDER.get(idx - 1);
    }

    private void insertPromotion(String id, String artifactId, String artifactVersion,
                                  String fromEnv, String targetEnv, PromotionStatus status,
                                  String approvalId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO deployment_promotion_pipeline " +
                 "(promotion_id, artifact_id, artifact_version, from_env, target_env, " +
                 "status, approval_id, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())")) {
            ps.setString(1, id);
            ps.setString(2, artifactId);
            ps.setString(3, artifactVersion);
            ps.setString(4, fromEnv);
            ps.setString(5, targetEnv);
            ps.setString(6, status.name());
            ps.setString(7, approvalId);
            ps.executeUpdate();
        }
    }

    private void updatePromotionStatus(String id, PromotionStatus status,
                                        String failureReason) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE deployment_promotion_pipeline SET status = ?, failure_reason = ?, " +
                 "updated_at = NOW() WHERE promotion_id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, failureReason);
            ps.setString(3, id);
            ps.executeUpdate();
        }
    }

    private PromotionRecord fetchPromotion(String id) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT promotion_id, artifact_id, artifact_version, from_env, target_env, " +
                 "status, approval_id, failure_reason, created_at, updated_at " +
                 "FROM deployment_promotion_pipeline WHERE promotion_id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("Promotion not found: " + id);
                return new PromotionRecord(
                    rs.getString("promotion_id"), rs.getString("artifact_id"),
                    rs.getString("artifact_version"), rs.getString("from_env"),
                    rs.getString("target_env"),
                    PromotionStatus.valueOf(rs.getString("status")),
                    rs.getString("approval_id"), rs.getString("failure_reason"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            }
        }
    }
}
