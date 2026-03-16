package com.ghatana.appplatform.aigovernance;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.governance.DataClassification;
import javax.sql.DataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose AI governance policy engine. Enforces organisational policies on model
 *              lifecycle operations. Configured policies: model card required before
 *              deployment, bias check required (no HIGH/MEDIUM unresolved bias), HITL
 *              required for TIER_1 models, all predictions must be logged. Policy evaluation
 *              blocks deployment on violation and emits PolicyViolation events.
 *              Satisfies STORY-K09-013.
 * @doc.layer   Kernel
 * @doc.pattern Policy rule evaluation; policy-violation event; deployment block via
 *              DeploymentGatePort; K-07 audit on every policy check; evaluations/violations Counters.
 */
public class AiGovernancePolicyEngineService {

    private final DataSource  dataSource;
    private final Executor          executor;
    private final PolicyCheckPort   policyCheckPort;
    private final DeploymentGatePort deploymentGatePort;
    private final AuditBusPort         auditPort;
    private final PolicyEventPort   policyEventPort;
    private final PolicyEngine      policyEngine;
    private final Counter           evaluationsCounter;
    private final Counter           violationsCounter;

    public AiGovernancePolicyEngineService(DataSource dataSource, Executor executor,
                                            PolicyCheckPort policyCheckPort,
                                            DeploymentGatePort deploymentGatePort,
                                            AuditBusPort auditPort,
                                            PolicyEventPort policyEventPort,
                                            PolicyEngine policyEngine,
                                            MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.policyCheckPort     = policyCheckPort;
        this.deploymentGatePort  = deploymentGatePort;
        this.auditPort           = auditPort;
        this.policyEventPort     = policyEventPort;
        this.policyEngine        = policyEngine;
        this.evaluationsCounter   = Counter.builder("aigovernance.policy.evaluations_total").register(registry);
        this.violationsCounter    = Counter.builder("aigovernance.policy.violations_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Checks prerequisite artifacts (model card, bias report, prediction log). */
    public interface PolicyCheckPort {
        boolean hasModelCard(String modelId, String version);
        boolean hasPassingBiasCheck(String modelId, String version);
        boolean hasPredictionLogging(String modelId);
        boolean requiresHitl(String modelTier);
        boolean hasHitlConfigured(String modelId, String version);
    }

    /** Blocks or allows deployment based on policy result. */
    public interface DeploymentGatePort {
        void blockDeployment(String modelId, String version, List<String> violations);
        void allowDeployment(String modelId, String version);
    }

    /** Publishes PolicyViolation events. */
    public interface PolicyEventPort {
        void publishPolicyViolation(String modelId, String version, List<PolicyViolation> violations);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public record PolicyViolation(String rule, String description, String severity) {}

    public record PolicyEvaluationResult(
        String modelId, String version,
        boolean passed, List<PolicyViolation> violations,
        Instant evaluatedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Evaluate all governance policies for a model version before deployment.
     * Blocks deployment if any violation is found.
     */
    public Promise<PolicyEvaluationResult> evaluateForDeployment(String modelId, String version,
                                                                   String modelTier) {
        return Promise.ofBlocking(executor, () -> {
            List<PolicyViolation> violations = new ArrayList<>();
            Instant now = Instant.now();

            // Rule 1: Model card required
            if (!policyCheckPort.hasModelCard(modelId, version)) {
                violations.add(new PolicyViolation(
                    "MODEL_CARD_REQUIRED",
                    "Model card must be generated before deployment",
                    "BLOCKING"
                ));
            }

            // Rule 2: Bias check required (no HIGH/MEDIUM unresolved bias)
            if (!policyCheckPort.hasPassingBiasCheck(modelId, version)) {
                violations.add(new PolicyViolation(
                    "BIAS_CHECK_REQUIRED",
                    "No passing bias evaluation found for this model version",
                    "BLOCKING"
                ));
            }

            // Rule 3: HITL required for TIER_1 models
            if (policyCheckPort.requiresHitl(modelTier) &&
                !policyCheckPort.hasHitlConfigured(modelId, version)) {
                violations.add(new PolicyViolation(
                    "HITL_REQUIRED_FOR_TIER_1",
                    "HITL review workflow must be configured for TIER_1 models",
                    "BLOCKING"
                ));
            }

            // Rule 4: All predictions must be logged
            if (!policyCheckPort.hasPredictionLogging(modelId)) {
                violations.add(new PolicyViolation(
                    "PREDICTION_LOGGING_REQUIRED",
                    "Prediction audit logging must be enabled for this model",
                    "BLOCKING"
                ));
            }

            evaluationsCounter.increment();

            boolean passed = violations.isEmpty();
            persistResult(modelId, version, modelTier, passed, violations, now);

            auditPort.emit(AuditEvent.builder()
                .eventType("POLICY_EVALUATED")
                .resourceType("Model")
                .resourceId(modelId)
                .details(Map.of("version", version, "tier", modelTier,
                        "passed", String.valueOf(passed), "violations", String.valueOf(violations.size())))
                .build());

            if (passed) {
                deploymentGatePort.allowDeployment(modelId, version);
            } else {
                violationsCounter.increment(violations.size());
                deploymentGatePort.blockDeployment(modelId, version, violations.stream()
                    .map(PolicyViolation::rule).toList());
                policyEventPort.publishPolicyViolation(modelId, version, violations);
            }

            return new PolicyEvaluationResult(modelId, version, passed, violations, now);
        });
    }

    /**
     * Check ongoing compliance (non-deployment triggered): prediction logging still active,
     * no new unresolved bias findings.
     */
    public Promise<PolicyEvaluationResult> checkOngoingCompliance(String modelId,
                                                                    String version,
                                                                    String modelTier) {
        return Promise.ofBlocking(executor, () -> {
            List<PolicyViolation> violations = new ArrayList<>();
            Instant now = Instant.now();

            if (!policyCheckPort.hasPredictionLogging(modelId)) {
                violations.add(new PolicyViolation(
                    "PREDICTION_LOGGING_DISABLED",
                    "Prediction logging was disabled for a deployed model",
                    "CRITICAL"
                ));
            }

            if (!policyCheckPort.hasPassingBiasCheck(modelId, version)) {
                violations.add(new PolicyViolation(
                    "ONGOING_BIAS_VIOLATION",
                    "New bias findings detected on deployed model",
                    "HIGH"
                ));
            }

            evaluationsCounter.increment();
            boolean passed = violations.isEmpty();

            if (!passed) {
                violationsCounter.increment(violations.size());
                policyEventPort.publishPolicyViolation(modelId, version, violations);
                auditPort.emit(AuditEvent.builder()
                    .eventType("ONGOING_POLICY_VIOLATION")
                    .resourceType("Model")
                    .resourceId(modelId)
                    .details(Map.of("version", version, "violations", String.valueOf(violations.size())))
                    .build());
            }

            return new PolicyEvaluationResult(modelId, version, passed, violations, now);
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void persistResult(String modelId, String version, String modelTier,
                                boolean passed, List<PolicyViolation> violations,
                                Instant evaluatedAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO ai_policy_evaluations " +
                 "(model_id, model_version, model_tier, passed, violation_count, evaluated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, modelId);
            ps.setString(2, version);
            ps.setString(3, modelTier);
            ps.setBoolean(4, passed);
            ps.setInt(5, violations.size());
            ps.setTimestamp(6, Timestamp.from(evaluatedAt));
            ps.executeUpdate();
        }
    }
}
