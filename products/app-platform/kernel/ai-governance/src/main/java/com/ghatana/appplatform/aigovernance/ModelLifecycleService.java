package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Orchestrates ML model lifecycle state transitions. Valid progression:
 *              DRAFT → VALIDATED (requires validation report) → DEPLOYED (requires K-01
 *              approval) → DEPRECATED → RETIRED. Emergency promotion via bypass flag
 *              still fires a post-deploy review task. Delegates actual status update to
 *              ModelRegistryService.transition(). Publishes ModelStatusChanged events.
 *              Satisfies STORY-K09-003.
 * @doc.layer   Kernel
 * @doc.pattern State-machine guard; K-01 WorkflowPort maker-checker; EventPort;
 *              lifecycle_events audit; Counter instrumentation.
 */
public class ModelLifecycleService {

    private static final int DEFAULT_DEPRECATION_GRACE_DAYS = 30;

    /** Allowed forward transitions — same as ModelRegistryService.TRANSITIONS */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "DRAFT",       Set.of("VALIDATED"),
            "VALIDATED",   Set.of("DEPLOYED", "DRAFT"),
            "DEPLOYED",    Set.of("DEPRECATED"),
            "DEPRECATED",  Set.of("RETIRED", "DEPLOYED"),
            "RETIRED",     Set.of()
    );

    private final HikariDataSource      dataSource;
    private final Executor              executor;
    private final ModelRegistryService  registryService;
    private final WorkflowPort          workflowPort;
    private final EventPort             eventPort;
    private final Counter               promotionsCounter;
    private final Counter               rollbacksCounter;

    public ModelLifecycleService(HikariDataSource dataSource, Executor executor,
                                  ModelRegistryService registryService,
                                  WorkflowPort workflowPort, EventPort eventPort,
                                  MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.registryService  = registryService;
        this.workflowPort     = workflowPort;
        this.eventPort        = eventPort;
        this.promotionsCounter = Counter.builder("ai.lifecycle.promotions_total").register(registry);
        this.rollbacksCounter  = Counter.builder("ai.lifecycle.rollbacks_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface WorkflowPort {
        String requestApproval(String resourceId, String resourceType, String requestedBy,
                               String rationale);
        boolean isApproved(String approvalId);
    }

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ValidationReport(String reportId, String modelId, double accuracy,
                                    double f1Score, String validatedBy) {}

    public record LifecycleEvent(String eventId, String modelId, String fromStatus,
                                  String toStatus, String triggeredBy, LocalDateTime occurredAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Validate and move from DRAFT → VALIDATED. A validation report is required. */
    public Promise<Void> promoteToValidated(String modelId, ValidationReport report,
                                             String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            persistValidationReport(report);
            registryService.transition(modelId, "VALIDATED", requestedBy).get();
            recordLifecycleEvent(modelId, "DRAFT", "VALIDATED", requestedBy);
            promotionsCounter.increment();
            eventPort.publish("model-lifecycle", "ModelValidated",
                    Map.of("modelId", modelId, "reportId", report.reportId()));
            return null;
        });
    }

    /** Request approval to move VALIDATED → DEPLOYED. Returns approvalId. */
    public Promise<String> requestDeploymentApproval(String modelId, String requestedBy,
                                                      String rationale) {
        return Promise.ofBlocking(executor,
                () -> workflowPort.requestApproval(modelId, "ML_MODEL_DEPLOYMENT",
                        requestedBy, rationale));
    }

    /** Apply an obtained approval to complete VALIDATED → DEPLOYED. */
    public Promise<Void> applyDeploymentApproval(String modelId, String approvalId,
                                                   String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            if (!workflowPort.isApproved(approvalId)) {
                throw new IllegalStateException("Approval " + approvalId + " not yet granted");
            }
            registryService.transition(modelId, "DEPLOYED", requestedBy).get();
            recordLifecycleEvent(modelId, "VALIDATED", "DEPLOYED", requestedBy);
            promotionsCounter.increment();
            eventPort.publish("model-lifecycle", "ModelDeployed",
                    Map.of("modelId", modelId, "approvalId", approvalId));
            return null;
        });
    }

    /**
     * Emergency deployment bypass — no pre-approval required. Creates a post-deploy
     * review task via WorkflowPort so accountability is preserved.
     */
    public Promise<Void> emergencyDeploy(String modelId, String requestedBy,
                                          String incidentReference) {
        return Promise.ofBlocking(executor, () -> {
            registryService.transition(modelId, "DEPLOYED", requestedBy).get();
            recordLifecycleEvent(modelId, "VALIDATED", "DEPLOYED", requestedBy + ":EMERGENCY");
            workflowPort.requestApproval(modelId, "POST_DEPLOY_REVIEW",
                    requestedBy, "Emergency deploy: " + incidentReference);
            promotionsCounter.increment();
            eventPort.publish("model-lifecycle", "ModelEmergencyDeployed",
                    Map.of("modelId", modelId, "incidentRef", incidentReference));
            return null;
        });
    }

    /** Deprecate with an optional grace period before retirement. */
    public Promise<Void> deprecate(String modelId, String requestedBy, Integer graceDays) {
        return Promise.ofBlocking(executor, () -> {
            int days = graceDays != null ? graceDays : DEFAULT_DEPRECATION_GRACE_DAYS;
            registryService.transition(modelId, "DEPRECATED", requestedBy).get();
            recordLifecycleEvent(modelId, "DEPLOYED", "DEPRECATED", requestedBy);
            persistGracePeriod(modelId, days);
            eventPort.publish("model-lifecycle", "ModelDeprecated",
                    Map.of("modelId", modelId, "graceDays", days));
            return null;
        });
    }

    /** Retire a deprecated model — only allowed after grace period. */
    public Promise<Void> retire(String modelId, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            if (!gracePeriodExpired(modelId)) {
                throw new IllegalStateException("Grace period has not expired for model " + modelId);
            }
            registryService.transition(modelId, "RETIRED", requestedBy).get();
            recordLifecycleEvent(modelId, "DEPRECATED", "RETIRED", requestedBy);
            rollbacksCounter.increment();
            eventPort.publish("model-lifecycle", "ModelRetired", Map.of("modelId", modelId));
            return null;
        });
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    private void persistValidationReport(ValidationReport r) throws SQLException {
        String sql = """
                INSERT INTO model_validation_reports
                    (report_id, model_id, accuracy, f1_score, validated_by, created_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (report_id) DO NOTHING
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.reportId()); ps.setString(2, r.modelId());
            ps.setDouble(3, r.accuracy()); ps.setDouble(4, r.f1Score());
            ps.setString(5, r.validatedBy());
            ps.executeUpdate();
        }
    }

    private void recordLifecycleEvent(String modelId, String from, String to,
                                       String triggeredBy) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO model_lifecycle_events (event_id,model_id,from_status,to_status," +
                     "triggered_by,occurred_at) VALUES (?,?,?,?,?,NOW())")) {
            ps.setString(1, UUID.randomUUID().toString()); ps.setString(2, modelId);
            ps.setString(3, from); ps.setString(4, to); ps.setString(5, triggeredBy);
            ps.executeUpdate();
        }
    }

    private void persistGracePeriod(String modelId, int graceDays) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO model_deprecation_schedules (model_id,retire_after) " +
                     "VALUES (?,NOW() + (? || ' days')::interval) " +
                     "ON CONFLICT (model_id) DO UPDATE SET retire_after=EXCLUDED.retire_after")) {
            ps.setString(1, modelId); ps.setInt(2, graceDays);
            ps.executeUpdate();
        }
    }

    private boolean gracePeriodExpired(String modelId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT retire_after <= NOW() FROM model_deprecation_schedules WHERE model_id=?")) {
            ps.setString(1, modelId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }
}
