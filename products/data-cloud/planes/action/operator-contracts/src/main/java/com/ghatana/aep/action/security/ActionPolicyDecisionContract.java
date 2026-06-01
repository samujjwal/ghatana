/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.action.security;

import com.ghatana.aep.action.ActionRunCapability;
import com.ghatana.aep.agent.capability.ToolCapability;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * WS2-10: Action-specific policy decision contracts.
 *
 * <p>Defines policy decision contracts for action execution, including:
 * <ul>
 *   <li>Action execution policies (side-effect controls, data governance, security)</li>
 *   <li>Tool invocation policies (permission checks, resource access)</li>
 *   <li>Memory write policies (classification, retention, access control)</li>
 *   <li>Approval gate policies (human-in-the-loop requirements)</li>
 *   <li>Replay-safe policy evaluation (deterministic, auditable, explainable)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Action-specific policy decision contracts for execution governance
 * @doc.layer product
 * @doc.pattern Contract
 */
public interface ActionPolicyDecisionContract {

    /**
     * Evaluate policy decision for action execution.
     *
     * <p>WS2-10: Determines whether an action can execute based on:
     * <ul>
     *   <li>Side-effect declarations and safety controls</li>
     *   <li>Data governance policies (classification, retention)</li>
     *   <li>Security policies (encryption, access control)</li>
     *   <li>Resource quotas and throttling</li>
     *   <li>Required approvals and governance controls</li>
     * </ul>
     *
     * @param tenantId tenant identifier
     * @param actionId action identifier
     * @param request action execution request
     * @param context evaluation context
     * @return policy decision for action execution
     */
    ActionPolicyDecision evaluateExecution(
            String tenantId,
            String actionId,
            ActionRunCapability.ActionRunRequest request,
            EvaluationContext context);

    /**
     * Evaluate policy decision for tool invocation.
     *
     * <p>WS2-10: Determines whether a tool can be invoked based on:
     * <ul>
     *   <li>Tool capability and required permissions</li>
     *   <li>Resource access policies</li>
     *   <li>Side-effect safety controls</li>
     *   <li>Rate limiting and throttling</li>
     * </ul>
     *
     * @param tenantId tenant identifier
     * @param actionId action identifier
     * @param toolCapability tool capability definition
     * @param toolInput tool input data
     * @param context evaluation context
     * @return policy decision for tool invocation
     */
    ToolPolicyDecision evaluateToolInvocation(
            String tenantId,
            String actionId,
            ToolCapability toolCapability,
            Map<String, Object> toolInput,
            EvaluationContext context);

    /**
     * Evaluate policy decision for memory write.
     *
     * <p>WS2-10: Determines whether a memory write is allowed based on:
     * <ul>
     *   <li>Memory type and classification</li>
     *   <li>Data governance policies (classification, retention)</li>
     *   <li>Access control and permissions</li>
     *   <li>Content validation and redaction</li>
     * </ul>
     *
     * @param tenantId tenant identifier
     * @param actionId action identifier
     *   @param memoryType memory type (SEMANTIC, EPISODIC, WORKING, etc.)
     * @param operation memory operation type (CREATE, UPDATE, DELETE, etc.)
     * @param content memory content
     * @param context evaluation context
     * @return policy decision for memory write
     */
    MemoryPolicyDecision evaluateMemoryWrite(
            String tenantId,
            String actionId,
            String memoryType,
            String operation,
            Map<String, Object> content,
            EvaluationContext context);

    /**
     * Evaluate policy decision for approval gate.
     *
     * <p>WS2-10: Determines whether human approval is required based on:
     * <ul>
     *   <li>Risk level assessment</li>
     *   <li>Side-effect impact analysis</li>
     *   <li>Data sensitivity classification</li>
     *   <li>Tenant-specific approval policies</li>
     * </ul>
     *
     * @param tenantId tenant identifier
     * @param actionId action identifier
     * @param sideEffectDeclaration side-effect declaration
     * @param context evaluation context
     * @return policy decision for approval gate
     */
    ApprovalPolicyDecision evaluateApprovalGate(
            String tenantId,
            String actionId,
            SideEffectDeclaration sideEffectDeclaration,
            EvaluationContext context);

    // ==================== Supporting Types ====================

    /**
     * Action execution policy decision.
     */
    record ActionPolicyDecision(
            boolean allowed,
            String reason,
            Set<String> violatedPolicies,
            Set<String> requiredApprovals,
            Map<String, Object> metadata,
            Optional<String> escalationPath,
            boolean requiresIdempotency,
            String idempotencyStrategy
    ) {
        public ActionPolicyDecision {
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

        public static ActionPolicyDecision allow() {
            return new ActionPolicyDecision(true, null, Set.of(), Set.of(), Map.of(), Optional.empty(), false, "none");
        }

        public static ActionPolicyDecision deny(String reason) {
            return new ActionPolicyDecision(false, reason, Set.of("general"), Set.of(), Map.of(), Optional.empty(), false, "none");
        }

        public static ActionPolicyDecision requireApproval(Set<String> approvals) {
            return new ActionPolicyDecision(true, "Requires approval", Set.of(), approvals, Map.of(), Optional.empty(), true, "key-based");
        }
    }

    /**
     * Tool invocation policy decision.
     */
    record ToolPolicyDecision(
            boolean allowed,
            String reason,
            Set<String> missingPermissions,
            Set<String> restrictedResources,
            boolean requiresRedaction,
            Set<String> redactionPatterns,
            Map<String, Object> metadata
    ) {
        public ToolPolicyDecision {
            missingPermissions = Set.copyOf(missingPermissions != null ? missingPermissions : Set.of());
            restrictedResources = Set.copyOf(restrictedResources != null ? restrictedResources : Set.of());
            redactionPatterns = Set.copyOf(redactionPatterns != null ? redactionPatterns : Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }

        public boolean isDenied() {
            return !allowed;
        }

        public static ToolPolicyDecision allow() {
            return new ToolPolicyDecision(true, null, Set.of(), Set.of(), false, Set.of(), Map.of());
        }

        public static ToolPolicyDecision deny(String reason) {
            return new ToolPolicyDecision(false, reason, Set.of(), Set.of(), false, Set.of(), Map.of());
        }

        public static ToolPolicyDecision denyMissingPermissions(Set<String> permissions) {
            return new ToolPolicyDecision(false, "Missing required permissions", permissions, Set.of(), false, Set.of(), Map.of());
        }
    }

    /**
     * Memory write policy decision.
     */
    record MemoryPolicyDecision(
            boolean allowed,
            String reason,
            String dataClassification,
            String retentionPolicy,
            boolean requiresRedaction,
            Set<String> redactionPatterns,
            boolean requiresEncryption,
            String encryptionLevel,
            Map<String, Object> metadata
    ) {
        public MemoryPolicyDecision {
            redactionPatterns = Set.copyOf(redactionPatterns != null ? redactionPatterns : Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        }

        public boolean isDenied() {
            return !allowed;
        }

        public static MemoryPolicyDecision allow() {
            return new MemoryPolicyDecision(true, null, "UNCLASSIFIED", "DEFAULT", false, Set.of(), false, "none", Map.of());
        }

        public static MemoryPolicyDecision deny(String reason) {
            return new MemoryPolicyDecision(false, reason, null, null, false, Set.of(), false, "none", Map.of());
        }

        public static MemoryPolicyDecision allowWithClassification(String classification, String retention) {
            return new MemoryPolicyDecision(true, null, classification, retention, false, Set.of(), false, "none", Map.of());
        }
    }

    /**
     * Approval gate policy decision.
     */
    record ApprovalPolicyDecision(
            boolean requiresApproval,
            String riskLevel,
            Set<String> requiredApprovers,
            String approvalWorkflow,
            Instant approvalDeadline,
            boolean canAutoApprove,
            String autoApprovalCondition,
            Map<String, Object> metadata
    ) {
        public ApprovalPolicyDecision {
            requiredApprovers = Set.copyOf(requiredApprovers != null ? requiredApprovers : Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
            approvalDeadline = approvalDeadline != null ? approvalDeadline : Instant.now().plus(java.time.Duration.ofHours(24));
        }

        public boolean isHighRisk() {
            return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
        }

        public static ApprovalPolicyDecision noApprovalRequired() {
            return new ApprovalPolicyDecision(false, "LOW", Set.of(), "none", null, false, null, Map.of());
        }

        public static ApprovalPolicyDecision requireApproval(Set<String> approvers, String workflow) {
            return new ApprovalPolicyDecision(true, "MEDIUM", approvers, workflow, null, false, null, Map.of());
        }

        public static ApprovalPolicyDecision requireHighRiskApproval(Set<String> approvers, String workflow) {
            return new ApprovalPolicyDecision(true, "HIGH", approvers, workflow, null, false, null, Map.of());
        }
    }

    /**
     * Side-effect declaration for approval evaluation.
     */
    record SideEffectDeclaration(
            boolean hasSideEffects,
            boolean isDestructive,
            boolean isReversible,
            String compensationStrategy,
            Set<String> affectedResources,
            String riskLevel
    ) {
        public SideEffectDeclaration {
            affectedResources = Set.copyOf(affectedResources != null ? affectedResources : Set.of());
            riskLevel = riskLevel != null ? riskLevel : "LOW";
        }

        public boolean isSafeToReplay() {
            return !isDestructive || isReversible;
        }

        public static SideEffectDeclaration none() {
            return new SideEffectDeclaration(false, false, true, "none", Set.of(), "LOW");
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
     * Policy categories for violation tracking.
     */
    enum PolicyCategory {
        ACTION_EXECUTION,          // Action execution policies
        TOOL_INVOCATION,           // Tool invocation policies
        MEMORY_WRITE,              // Memory write policies
        APPROVAL_GATE,             // Approval gate policies
        DATA_GOVERNANCE,           // Data classification and retention
        SECURITY,                  // Encryption and access control
        SIDE_EFFECT_CONTROL,       // Side-effect safety controls
        RESOURCE_QUOTA,            // Resource capacity limits
        TENANT_ISOLATION,          // Multi-tenant isolation
        IDEMPOTENCY,               // Idempotency requirements
        COMPLIANCE                 // Regulatory compliance
    }
}
