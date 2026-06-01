package com.ghatana.aep.policy;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.pattern.spec.PatternSpec;
import com.ghatana.aep.operator.contract.OperatorSpec;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical policy decision contracts for pattern activation and agent execution.
 *
 * <p>WS2: Defines the contract for evaluating policy decisions before pattern activation
 * or agent execution. This includes:
 * <ul>
 *   <li>Activation policies for patterns (tenant-level, resource quotas, approval gates)</li>
 *   <li>Execution policies for agents (side-effect controls, data governance, security)</li>
 *   <li>Replay-safe policy evaluation (deterministic, auditable, explainable)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Policy decision contracts for pattern activation and agent execution
 * @doc.layer product
 * @doc.pattern Contract
 */
public interface PolicyDecisionContract {

    /**
     * Evaluate policy decision for pattern activation.
     *
     * <p>WS2: Determines whether a pattern can be activated based on:
     * <ul>
     *   <li>Tenant-level activation quotas</li>
     *   <li>Pattern classification and risk level</li>
     *   <li>Required approvals and governance controls</li>
     *   <li>Resource availability and capacity</li>
 * </ul>
     *
     * @param tenantId tenant identifier
     * @param patternSpec pattern specification to activate
     * @param context evaluation context (user, environment, etc.)
     * @return policy decision for activation
     */
    PolicyDecision evaluateActivation(String tenantId, PatternSpec patternSpec, EvaluationContext context);

    /**
     * Evaluate policy decision for agent execution.
     *
     * <p>WS2: Determines whether an agent can execute based on:
     * <ul>
     *   <li>Side-effect declarations and safety controls</li>
     *   <li>Data governance policies (classification, retention)</li>
     *   <li>Security policies (encryption, access control)</li>
     *   <li>Resource quotas and throttling</li>
     * </ul>
     *
     * @param tenantId tenant identifier
     * @param agentId agent identifier
     * @param operatorSpec operator specification
     * @param eventContext event context for execution
     * @param context evaluation context
     * @return policy decision for execution
     */
    PolicyDecision evaluateExecution(String tenantId, String agentId, OperatorSpec operatorSpec, EventContext<?> eventContext, EvaluationContext context);

    /**
     * Evaluate policy decision for replay operations.
     *
     * <p>WS2: Determines whether a replay operation is allowed based on:
     * <ul>
     *   <li>Replay mode (dry-run vs replay-with-side-effects)</li>
     *   <li>Side-effect safety and idempotency</li>
     *   <li>Audit trail requirements</li>
     *   <li>Compensation strategy availability</li>
     * </ul>
     *
     * @param tenantId tenant identifier
     * @param operationId original operation ID
     * @param replayMode replay mode
     * @param context evaluation context
     * @return policy decision for replay
     */
    ReplayPolicyDecision evaluateReplay(String tenantId, String operationId, ReplayMode replayMode, EvaluationContext context);

    // ==================== Supporting Types ====================

    /**
     * Policy decision result.
     */
    record PolicyDecision(
            boolean allowed,
            String reason,
            Set<String> violatedPolicies,
            Set<String> requiredApprovals,
            Map<String, Object> metadata,
            Optional<String> escalationPath
    ) {
        public PolicyDecision {
            violatedPolicies = Set.copyOf(violatedPolicies != null ? violatedPolicies : Set.of());
            requiredApprovals = Set.copyOf(requiredApprovals != null ? requiredApprovals : Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
            escalationPath = escalationPath != null ? escalationPath : Optional.empty();
        }

        public boolean requiresApproval() {
            return !requiredApprovals.isEmpty();
        }

        public boolean isDenied() {
            return !allowed;
        }

        public static PolicyDecision allow() {
            return new PolicyDecision(true, null, Set.of(), Set.of(), Map.of(), Optional.empty());
        }

        public static PolicyDecision deny(String reason) {
            return new PolicyDecision(false, reason, Set.of("general"), Set.of(), Map.of(), Optional.empty());
        }

        public static PolicyDecision requireApproval(Set<String> approvals) {
            return new PolicyDecision(true, "Requires approval", Set.of(), approvals, Map.of(), Optional.empty());
        }
    }

    /**
     * Replay-specific policy decision.
     */
    record ReplayPolicyDecision(
            boolean allowed,
            String reason,
            boolean requiresCompensation,
            String compensationStrategy,
            boolean requiresAuditTrail,
            Map<String, Object> metadata
    ) {
        public ReplayPolicyDecision {
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }

        public boolean isSafeToReplay() {
            return allowed && !requiresCompensation;
        }

        public static ReplayPolicyDecision allow() {
            return new ReplayPolicyDecision(true, null, false, "none", false, Map.of());
        }

        public static ReplayPolicyDecision deny(String reason) {
            return new ReplayPolicyDecision(false, reason, false, "none", false, Map.of());
        }

        public static ReplayPolicyDecision allowWithCompensation(String strategy) {
            return new ReplayPolicyDecision(true, null, true, strategy, true, Map.of());
        }
    }

    /**
     * Evaluation context for policy decisions.
     */
    record EvaluationContext(
            String userId,
            String environment,
            Map<String, Object> requestMetadata,
            Map<String, Object> tenantContext,
            Instant evaluationTime
    ) {
        public EvaluationContext {
            requestMetadata = Map.copyOf(requestMetadata != null ? requestMetadata : Map.of());
            tenantContext = Map.copyOf(tenantContext != null ? tenantContext : Map.of());
            evaluationTime = evaluationTime != null ? evaluationTime : java.time.Instant.now();
        }

        public static EvaluationContext of(String userId, String environment) {
            return new EvaluationContext(userId, environment, Map.of(), Map.of(), java.time.Instant.now());
        }
    }

    /**
     * Replay mode enumeration.
     */
    enum ReplayMode {
        DRY_RUN,                  // Evaluate without side effects
        REPLAY_WITH_SIDE_EFFECTS, // Full replay with side effects
        RECORDED_AGENT_OUTPUT     // Use recorded agent outputs
    }

    /**
     * Policy categories for violation tracking.
     */
    enum PolicyCategory {
        ACTIVATION_QUOTA,         // Tenant-level activation limits
        RESOURCE_QUOTA,          // Resource capacity limits
        DATA_GOVERNANCE,          // Data classification and retention
        SECURITY,                 // Encryption and access control
        SIDE_EFFECT_CONTROL,      // Side-effect safety controls
        APPROVAL_GATE,            // Required human approvals
        COMPLIANCE,               // Regulatory compliance
        TENANT_ISOLATION          // Multi-tenant isolation
    }
}
