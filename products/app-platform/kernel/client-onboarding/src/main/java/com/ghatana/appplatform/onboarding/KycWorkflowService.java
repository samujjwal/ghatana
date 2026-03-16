package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Defines and manages the KYC (Know Your Customer) onboarding workflow.
 *              Triggered by ClientOnboardingRequested event via the W-01 workflow engine.
 *              Supports INDIVIDUAL and INSTITUTIONAL client types with different document sets.
 *              Steps: document collection → document verification → identity verification →
 *                     risk assessment → compliance review → account creation → welcome notification.
 *              Dual-calendar date tracking (BS + Gregorian) throughout.
 *              Idempotent trigger handling (same request_id → same instance).
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class KycWorkflowService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface WorkflowEnginePort {
        /** Launch a KYC workflow instance via the W-01 engine. Returns the instance_id. */
        Promise<String> launchKycWorkflow(String clientType, String requestId, String contextJson);

        /** Resume a WAITING instance (e.g., after documents are uploaded or reviewed). */
        Promise<Void> resumeInstance(String instanceId, String stepSignal, String payloadJson);

        /** Query the current status of a KYC instance. */
        Promise<InstanceStatus> getStatus(String instanceId);
    }

    public interface EventBusPort {
        Promise<Void> publish(String topic, String eventType, String payloadJson);
    }

    public interface AuditPort {
        Promise<Void> log(String action, String actor, String entityId, String entityType,
                          String beforeJson, String afterJson);
    }

    // -----------------------------------------------------------------------
    // Records and Enums
    // -----------------------------------------------------------------------

    public enum ClientType { INDIVIDUAL, INSTITUTIONAL }
    public enum KycStatus {
        PENDING, DOCUMENT_COLLECTION, DOCUMENT_REVIEW, IDENTITY_VERIFICATION,
        RISK_ASSESSMENT, COMPLIANCE_REVIEW, EDD, ACCOUNT_SETUP, COMPLETED, REJECTED
    }

    public record InstanceStatus(String instanceId, String status, String currentStep,
                                  String startedAt, String completedAt) {}

    public record KycRequest(
        String requestId,
        ClientType clientType,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String nationality,
        String dateOfBirthGregorian,
        String dateOfBirthBs,
        String expectedTransactionVolume,
        String sourceOfFunds,
        String occupation,
        String requestedAt
    ) {}

    public record KycInstance(
        String instanceId,
        String requestId,
        ClientType clientType,
        KycStatus status,
        String workflowInstanceId,
        List<String> requiredDocuments,
        List<String> uploadedDocuments,
        int completionPercent,
        String riskScore,     // LOW | MEDIUM | HIGH
        String startedAt,
        String updatedAt
    ) {}

    /** Document types per client type. */
    public static List<String> requiredDocumentsFor(ClientType clientType) {
        return switch (clientType) {
            case INDIVIDUAL -> List.of(
                "PASSPORT_OR_CITIZENSHIP",
                "PROOF_OF_ADDRESS",
                "PAN_CARD"
            );
            case INSTITUTIONAL -> List.of(
                "COMPANY_REGISTRATION_CERTIFICATE",
                "TAX_REGISTRATION_CERTIFICATE",
                "BOARD_RESOLUTION",
                "AUTHORIZED_SIGNATORY_ID",
                "BENEFICIAL_OWNERSHIP_DECLARATION",
                "PROOF_OF_REGISTERED_ADDRESS",
                "AUDITED_FINANCIAL_STATEMENTS"
            );
        };
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final WorkflowEnginePort workflowEngine;
    private final EventBusPort eventBus;
    private final AuditPort auditPort;

    private final Counter onboardingStartedTotal;
    private final Counter onboardingCompletedTotal;
    private final Counter onboardingRejectedTotal;
    private final Counter duplicateRequestIgnoredTotal;

    private static final String TOPIC_ONBOARDING_EVENTS = "platform.onboarding.events";

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public KycWorkflowService(DataSource dataSource,
                               Executor executor,
                               MeterRegistry meterRegistry,
                               WorkflowEnginePort workflowEngine,
                               EventBusPort eventBus,
                               AuditPort auditPort) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.workflowEngine = workflowEngine;
        this.eventBus       = eventBus;
        this.auditPort      = auditPort;

        this.onboardingStartedTotal      = Counter.builder("kyc.onboarding.started_total")
                .description("Total KYC onboarding workflows started")
                .register(meterRegistry);
        this.onboardingCompletedTotal    = Counter.builder("kyc.onboarding.completed_total")
                .description("Total KYC onboarding workflows completed successfully")
                .register(meterRegistry);
        this.onboardingRejectedTotal     = Counter.builder("kyc.onboarding.rejected_total")
                .description("Total KYC onboarding workflows rejected")
                .register(meterRegistry);
        this.duplicateRequestIgnoredTotal = Counter.builder("kyc.onboarding.duplicate_request_total")
                .description("Duplicate KYC requests ignored (idempotent)")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Start a KYC onboarding workflow for a new client.
     * Idempotent: if request_id already exists, returns the existing instance.
     */
    public Promise<KycInstance> startOnboarding(KycRequest request) {
        return Promise.ofBlocking(executor, () -> findExistingInstance(request.requestId()))
            .then(existing -> {
                if (existing != null) {
                    duplicateRequestIgnoredTotal.increment();
                    return Promise.of(existing);
                }
                String contextJson = buildContextJson(request);
                return workflowEngine.launchKycWorkflow(request.clientType().name(),
                                                         request.requestId(), contextJson)
                    .then(workflowInstanceId -> Promise.ofBlocking(executor, () -> {
                        List<String> requiredDocs = requiredDocumentsFor(request.clientType());
                        String instanceId = insertKycInstanceBlocking(request, workflowInstanceId,
                                                                       requiredDocs);
                        onboardingStartedTotal.increment();
                        eventBus.publish(TOPIC_ONBOARDING_EVENTS, "KycOnboardingStarted",
                            buildStartedEventJson(instanceId, request.requestId(),
                                                  request.clientType().name()));
                        auditPort.log("KYC_ONBOARDING_STARTED", "system", instanceId,
                                      "KYC_INSTANCE", null, contextJson);
                        return queryKycInstance(instanceId);
                    }));
            });
    }

    /** Mark the KYC onboarding as completed (called after successful account setup). */
    public Promise<Void> markCompleted(String instanceId, String reviewedBy) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(instanceId, KycStatus.COMPLETED);
            onboardingCompletedTotal.increment();
            auditPort.log("KYC_COMPLETED", reviewedBy, instanceId, "KYC_INSTANCE", null, null);
            return null;
        });
    }

    /** Mark the KYC onboarding as rejected with a reason. */
    public Promise<Void> reject(String instanceId, String reason, String rejectedBy) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(instanceId, KycStatus.REJECTED);
            onboardingRejectedTotal.increment();
            auditPort.log("KYC_REJECTED", rejectedBy, instanceId, "KYC_INSTANCE", null,
                          "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}");
            eventBus.publish(TOPIC_ONBOARDING_EVENTS, "KycOnboardingRejected",
                buildRejectedEventJson(instanceId, reason));
            return null;
        });
    }

    /** Advance the KYC instance to EDD (Enhanced Due Diligence) stage. */
    public Promise<Void> escalateToEdd(String instanceId, String reason, String escalatedBy) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(instanceId, KycStatus.EDD);
            auditPort.log("KYC_ESCALATED_TO_EDD", escalatedBy, instanceId, "KYC_INSTANCE", null,
                          "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}");
            return null;
        });
    }

    /** Get a KYC instance by its ID. */
    public Promise<KycInstance> getInstance(String instanceId) {
        return Promise.ofBlocking(executor, () -> queryKycInstance(instanceId));
    }

    /** List KYC instances filtered by status and optionally by client type. */
    public Promise<List<KycInstance>> listInstances(KycStatus statusFilter, ClientType clientTypeFilter,
                                                     int limit, int offset) {
        return Promise.ofBlocking(executor, () -> queryInstances(statusFilter, clientTypeFilter, limit, offset));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String insertKycInstanceBlocking(KycRequest request, String workflowInstanceId,
                                              List<String> requiredDocs) {
        String sql = """
            INSERT INTO kyc_instances
                (instance_id, request_id, client_type, status, workflow_instance_id,
                 required_documents, uploaded_documents, completion_percent, started_at, updated_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?::jsonb, '[]'::jsonb, 0, now(), now())
            RETURNING instance_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, request.requestId());
            ps.setString(2, request.clientType().name());
            ps.setString(3, KycStatus.DOCUMENT_COLLECTION.name());
            ps.setString(4, workflowInstanceId);
            ps.setString(5, toJsonArray(requiredDocs));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("instance_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert KYC instance for " + request.requestId(), e);
        }
    }

    private void updateStatus(String instanceId, KycStatus status) {
        String sql = "UPDATE kyc_instances SET status = ?, updated_at = now() WHERE instance_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, instanceId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update KYC status for " + instanceId, e);
        }
    }

    private KycInstance findExistingInstance(String requestId) {
        String sql = """
            SELECT instance_id, request_id, client_type, status, workflow_instance_id,
                   required_documents::text, uploaded_documents::text,
                   completion_percent, started_at::text, updated_at::text
              FROM kyc_instances
             WHERE request_id = ?
             LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapInstance(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find KYC instance for requestId " + requestId, e);
        }
    }

    private KycInstance queryKycInstance(String instanceId) {
        String sql = """
            SELECT instance_id, request_id, client_type, status, workflow_instance_id,
                   required_documents::text, uploaded_documents::text,
                   completion_percent, started_at::text, updated_at::text
              FROM kyc_instances
             WHERE instance_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("KYC instance not found: " + instanceId);
                return mapInstance(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query KYC instance " + instanceId, e);
        }
    }

    private List<KycInstance> queryInstances(KycStatus statusFilter, ClientType clientTypeFilter,
                                              int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT instance_id, request_id, client_type, status, workflow_instance_id,
                   required_documents::text, uploaded_documents::text,
                   completion_percent, started_at::text, updated_at::text
              FROM kyc_instances WHERE 1=1
            """);
        if (statusFilter != null) sql.append(" AND status = '").append(statusFilter.name()).append("'");
        if (clientTypeFilter != null) sql.append(" AND client_type = '").append(clientTypeFilter.name()).append("'");
        sql.append(" ORDER BY updated_at DESC LIMIT ").append(limit).append(" OFFSET ").append(offset);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString());
             ResultSet rs = ps.executeQuery()) {
            List<KycInstance> result = new ArrayList<>();
            while (rs.next()) result.add(mapInstance(rs));
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query KYC instances", e);
        }
    }

    private KycInstance mapInstance(ResultSet rs) throws Exception {
        return new KycInstance(
            rs.getString("instance_id"), rs.getString("request_id"),
            ClientType.valueOf(rs.getString("client_type")),
            KycStatus.valueOf(rs.getString("status")),
            rs.getString("workflow_instance_id"),
            List.of(),   // parsed from required_documents JSON in production
            List.of(),   // parsed from uploaded_documents JSON in production
            rs.getInt("completion_percent"),
            null,        // riskScore fetched separately
            rs.getString("started_at"),
            rs.getString("updated_at")
        );
    }

    private String buildContextJson(KycRequest req) {
        return String.format(
            "{\"requestId\":\"%s\",\"clientType\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\"," +
            "\"email\":\"%s\",\"nationality\":\"%s\",\"sourceOfFunds\":\"%s\"}",
            req.requestId(), req.clientType(), req.firstName(), req.lastName(),
            req.email(), req.nationality(), req.sourceOfFunds()
        );
    }

    private String buildStartedEventJson(String instanceId, String requestId, String clientType) {
        return String.format("{\"instanceId\":\"%s\",\"requestId\":\"%s\",\"clientType\":\"%s\"}",
                             instanceId, requestId, clientType);
    }

    private String buildRejectedEventJson(String instanceId, String reason) {
        return String.format("{\"instanceId\":\"%s\",\"reason\":\"%s\"}",
                             instanceId, reason.replace("\"", "\\\""));
    }

    private String toJsonArray(List<String> items) {
        if (items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i)).append("\"");
        }
        return sb.append("]").toString();
    }
}
