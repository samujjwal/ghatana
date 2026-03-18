package com.ghatana.softwareorg.compliance.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for Compliance department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for compliance and governance operations (audit
 * initiation, policy enforcement, risk assessment, data protection).
 *
 * @doc.type class
 * @doc.purpose Compliance domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class ComplianceEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public ComplianceEventController(EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Initiates audit process.
     *
     * @param tenantId tenant context
     * @param auditId audit identifier
     * @param auditType type of audit (INTERNAL, EXTERNAL, REGULATORY)
     * @param scope audit scope
     * @return audit ID
     */
    public String initiateAudit(String tenantId, String auditId, String auditType, String scope) {
        if (auditId == null) {
            auditId = UUID.randomUUID().toString();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("auditId", auditId);
        payload.put("auditType", auditType);
        payload.put("scope", scope);
        payload.put("status", "INITIATED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("compliance.audit.initiated", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("compliance.audits.initiated", "type", auditType);
        return auditId;
    }

    /**
     * Reports policy violation.
     *
     * @param tenantId tenant context
     * @param violationId violation identifier
     * @param policyName policy that was violated
     * @param severity severity level (CRITICAL, HIGH, MEDIUM, LOW)
     * @param violatingEntity entity that violated policy (e.g., user, service)
     */
    public void reportPolicyViolation(
            String tenantId, String violationId, String policyName, String severity,
            String violatingEntity) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("violationId", violationId);
        payload.put("policyName", policyName);
        payload.put("severity", severity);
        payload.put("violatingEntity", violatingEntity);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("compliance.policy.violated", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("compliance.violations", "severity", severity, "policy", policyName);
    }

    /**
     * Records data protection action.
     *
     * @param tenantId tenant context
     * @param actionId data protection action identifier
     * @param dataClassification data classification level (PUBLIC, INTERNAL,
     * CONFIDENTIAL, RESTRICTED)
     * @param actionType type of action (ENCRYPTED, MASKED, ANONYMIZED, DELETED)
     * @param recordCount number of records affected
     */
    public void recordDataProtectionAction(
            String tenantId, String actionId, String dataClassification, String actionType,
            long recordCount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("actionId", actionId);
        payload.put("dataClassification", dataClassification);
        payload.put("actionType", actionType);
        payload.put("recordCount", recordCount);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("compliance.data_protection.action_taken", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("compliance.data_protected", recordCount, "classification", dataClassification);
    }

    /**
     * Records risk assessment.
     *
     * @param tenantId tenant context
     * @param assessmentId risk assessment identifier
     * @param riskLevel risk level (LOW, MEDIUM, HIGH, CRITICAL)
     * @param riskFactor risk factor being assessed
     * @param mitigation mitigation strategy
     */
    public void recordRiskAssessment(
            String tenantId, String assessmentId, String riskLevel, String riskFactor,
            String mitigation) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("assessmentId", assessmentId);
        payload.put("riskLevel", riskLevel);
        payload.put("riskFactor", riskFactor);
        payload.put("mitigation", mitigation);

        String eventType
                = "CRITICAL".equals(riskLevel) || "HIGH".equals(riskLevel)
                ? "compliance.risk.high_severity"
                : "compliance.risk.assessed";
        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish(eventType, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("compliance.risk_assessments", "level", riskLevel);
    }

    /**
     * Records training completion for compliance.
     *
     * @param tenantId tenant context
     * @param trainingId training identifier
     * @param employeeId employee completing training
     * @param trainingTopic topic (DATA_PRIVACY, SECURITY, ETHICS,
     * ANTI_CORRUPTION)
     */
    public void recordComplianceTraining(
            String tenantId, String trainingId, String employeeId, String trainingTopic) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("trainingId", trainingId);
        payload.put("employeeId", employeeId);
        payload.put("trainingTopic", trainingTopic);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("compliance.training.completed", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("compliance.training_completed", "topic", trainingTopic);
    }
}
