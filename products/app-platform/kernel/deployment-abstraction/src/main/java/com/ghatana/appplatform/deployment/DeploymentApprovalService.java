package com.ghatana.appplatform.deployment;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Manages deployment approval workflows. Deployment requests transition
 *              through: REQUESTED → PENDING_APPROVAL → APPROVED | REJECTED → (scheduler
 *              picks up APPROVED to start deployment via DeploymentOrchestratorService).
 *              Emergency bypass is supported: the deployment is allowed immediately but
 *              a mandatory post-deploy review task is created via K-01 WorkflowPort.
 *              All approvals and rejections are audit-logged via AuditPort.
 *              Satisfies STORY-K10-003.
 * @doc.layer   Kernel
 * @doc.pattern K-01 WorkflowPort maker-checker; AuditPort; emergency bypass + post-deploy
 *              review; EventPort; ON CONFLICT DO NOTHING; approvalsCounter.
 */
public class DeploymentApprovalService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final WorkflowPort     workflowPort;
    private final AuditPort        auditPort;
    private final EventPort        eventPort;
    private final Counter          approvalsGrantedCounter;
    private final Counter          approvalsRejectedCounter;
    private final Counter          emergencyBypassCounter;

    public DeploymentApprovalService(HikariDataSource dataSource, Executor executor,
                                      WorkflowPort workflowPort, AuditPort auditPort,
                                      EventPort eventPort, MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.workflowPort            = workflowPort;
        this.auditPort               = auditPort;
        this.eventPort               = eventPort;
        this.approvalsGrantedCounter  = Counter.builder("deploy.approvals.granted_total").register(registry);
        this.approvalsRejectedCounter = Counter.builder("deploy.approvals.rejected_total").register(registry);
        this.emergencyBypassCounter   = Counter.builder("deploy.approvals.emergency_bypasses_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface WorkflowPort {
        String submitForApproval(String resourceId, String resourceType,
                                  String submittedBy, String rationale);
        void createReviewTask(String resourceId, String taskType, String assignee, String reason);
    }

    public interface AuditPort {
        void record(String actorId, String action, String resourceId, String resourceType,
                    Map<String, Object> context);
    }

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ApprovalRequest(String approvalId, String deploymentId, String serviceId,
                                   String environment, String requestedBy, String rationale,
                                   String status, LocalDateTime requestedAt,
                                   LocalDateTime decidedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ApprovalRequest> requestApproval(String deploymentId, String serviceId,
                                                     String environment, String requestedBy,
                                                     String rationale) {
        return Promise.ofBlocking(executor, () -> {
            String approvalId = UUID.randomUUID().toString();
            String workflowRef = workflowPort.submitForApproval(deploymentId,
                    "DEPLOYMENT", requestedBy, rationale);
            ApprovalRequest req = persist(approvalId, deploymentId, serviceId, environment,
                    requestedBy, rationale, "PENDING_APPROVAL", workflowRef);
            auditPort.record(requestedBy, "DEPLOYMENT_APPROVAL_REQUESTED", deploymentId,
                    "DEPLOYMENT", Map.of("environment", environment, "service", serviceId));
            eventPort.publish("deployments", "DeploymentApprovalRequested",
                    Map.of("approvalId", approvalId, "deploymentId", deploymentId,
                           "environment", environment));
            return req;
        });
    }

    public Promise<ApprovalRequest> approve(String approvalId, String approvedBy,
                                             String notes) {
        return Promise.ofBlocking(executor, () -> {
            ApprovalRequest req = decide(approvalId, "APPROVED", approvedBy, notes);
            approvalsGrantedCounter.increment();
            auditPort.record(approvedBy, "DEPLOYMENT_APPROVED", req.deploymentId(),
                    "DEPLOYMENT", Map.of("approvalId", approvalId, "notes", notes));
            eventPort.publish("deployments", "DeploymentApproved",
                    Map.of("approvalId", approvalId, "deploymentId", req.deploymentId()));
            return req;
        });
    }

    public Promise<ApprovalRequest> reject(String approvalId, String rejectedBy,
                                            String reason) {
        return Promise.ofBlocking(executor, () -> {
            ApprovalRequest req = decide(approvalId, "REJECTED", rejectedBy, reason);
            approvalsRejectedCounter.increment();
            auditPort.record(rejectedBy, "DEPLOYMENT_REJECTED", req.deploymentId(),
                    "DEPLOYMENT", Map.of("approvalId", approvalId, "reason", reason));
            eventPort.publish("deployments", "DeploymentRejected",
                    Map.of("approvalId", approvalId, "reason", reason));
            return req;
        });
    }

    /**
     * Emergency bypass: grants approval immediately without a second authoriser.
     * Creates a mandatory post-deploy review task to preserve accountability.
     */
    public Promise<ApprovalRequest> emergencyBypass(String deploymentId, String serviceId,
                                                     String environment, String requestedBy,
                                                     String incidentRef, String reviewerAssignee) {
        return Promise.ofBlocking(executor, () -> {
            String approvalId = UUID.randomUUID().toString();
            ApprovalRequest req = persist(approvalId, deploymentId, serviceId, environment,
                    requestedBy, "EMERGENCY:" + incidentRef, "APPROVED", null);
            workflowPort.createReviewTask(deploymentId, "POST_DEPLOY_REVIEW",
                    reviewerAssignee, "Emergency bypass for incident " + incidentRef);
            emergencyBypassCounter.increment();
            auditPort.record(requestedBy, "DEPLOYMENT_EMERGENCY_BYPASS", deploymentId,
                    "DEPLOYMENT", Map.of("incidentRef", incidentRef, "approvalId", approvalId));
            eventPort.publish("deployments", "DeploymentEmergencyBypass",
                    Map.of("approvalId", approvalId, "incidentRef", incidentRef));
            return req;
        });
    }

    public Promise<Optional<ApprovalRequest>> getApproval(String approvalId) {
        return Promise.ofBlocking(executor, () -> load(approvalId));
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    private ApprovalRequest persist(String id, String deploymentId, String serviceId,
                                     String environment, String requestedBy, String rationale,
                                     String status, String workflowRef) throws SQLException {
        String sql = """
                INSERT INTO deployment_approvals
                    (approval_id, deployment_id, service_id, environment, requested_by,
                     rationale, status, workflow_ref, requested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (approval_id) DO NOTHING
                RETURNING *
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id); ps.setString(2, deploymentId);
            ps.setString(3, serviceId); ps.setString(4, environment);
            ps.setString(5, requestedBy); ps.setString(6, rationale);
            ps.setString(7, status); ps.setString(8, workflowRef);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return mapRow(rs); }
        }
    }

    private ApprovalRequest decide(String approvalId, String status,
                                    String actor, String notes) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE deployment_approvals " +
                     "SET status=?, decision_actor=?, decision_notes=?, decided_at=NOW() " +
                     "WHERE approval_id=? AND status='PENDING_APPROVAL' RETURNING *")) {
            ps.setString(1, status); ps.setString(2, actor);
            ps.setString(3, notes); ps.setString(4, approvalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException(
                        "Approval " + approvalId + " not in PENDING_APPROVAL state");
                return mapRow(rs);
            }
        }
    }

    private Optional<ApprovalRequest> load(String id) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM deployment_approvals WHERE approval_id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapRow(rs));
            }
        }
    }

    private ApprovalRequest mapRow(ResultSet rs) throws SQLException {
        return new ApprovalRequest(rs.getString("approval_id"), rs.getString("deployment_id"),
                rs.getString("service_id"), rs.getString("environment"),
                rs.getString("requested_by"), rs.getString("rationale"),
                rs.getString("status"),
                rs.getObject("requested_at", LocalDateTime.class),
                rs.getObject("decided_at", LocalDateTime.class));
    }
}
