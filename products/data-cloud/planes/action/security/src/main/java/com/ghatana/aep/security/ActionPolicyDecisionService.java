/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import com.ghatana.aep.action.security.ActionPolicyDecisionContract;
import com.ghatana.aep.action.ActionRunCapability;
import com.ghatana.aep.agent.capability.ToolCapability;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * WS4-11: Implementation of action policy decision service.
 *
 * <p>Provides policy decision evaluation for action execution, tool invocation,
 * memory writes, and approval gates. This service owns the canonical action
 * policy decision logic for the AEP action plane.
 *
 * @doc.type class
 * @doc.purpose Implementation of action policy decision evaluation
 * @doc.layer product
 * @doc.pattern Service
 */
public class ActionPolicyDecisionService implements ActionPolicyDecisionContract {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ActionPolicyDecisionService.class);

    public ActionPolicyDecisionService() {
        log.info("[WS4-11] ActionPolicyDecisionService initialized");
    }

    @Override
    public ActionPolicyDecision evaluateExecution(
            String tenantId,
            String actionId,
            ActionRunCapability.ActionRunRequest request,
            EvaluationContext context) {
        
        log.debug("[WS4-11] Evaluating execution policy for tenant={}, actionId={}", tenantId, actionId);

        // Check for side-effect safety
        if (request.sideEffectDeclaration() != null && request.sideEffectDeclaration().isDestructive()) {
            // Destructive actions require approval
            return ActionPolicyDecision.requireApproval(Set.of("action.approver", "admin"));
        }

        // Check for resource quota
        if (context.tenantContext() != null) {
            Object quotaLimit = context.tenantContext().get("action.quota.limit");
            Object quotaUsed = context.tenantContext().get("action.quota.used");
            if (quotaLimit != null && quotaUsed != null) {
                try {
                    long limit = Long.parseLong(quotaLimit.toString());
                    long used = Long.parseLong(quotaUsed.toString());
                    if (used >= limit) {
                        return ActionPolicyDecision.deny("Action quota exceeded");
                    }
                } catch (NumberFormatException e) {
                    log.warn("[WS4-11] Invalid quota values for tenant={}", tenantId);
                }
            }
        }

        // Default: allow with idempotency for production
        boolean requiresIdempotency = "production".equalsIgnoreCase(context.environment())
            || "staging".equalsIgnoreCase(context.environment());
        
        return new ActionPolicyDecision(
            true,
            null,
            Set.of(),
            Set.of(),
            Map.of("evaluationTime", Instant.now().toString()),
            java.util.Optional.empty(),
            requiresIdempotency,
            requiresIdempotency ? "key-based" : "none"
        );
    }

    @Override
    public ToolPolicyDecision evaluateToolInvocation(
            String tenantId,
            String actionId,
            ToolCapability toolCapability,
            Map<String, Object> toolInput,
            EvaluationContext context) {
        
        log.debug("[WS4-11] Evaluating tool invocation policy for tenant={}, actionId={}, tool={}", 
            tenantId, actionId, toolCapability != null ? toolCapability.id() : "unknown");

        // Check for restricted tools
        if (toolCapability != null) {
            String toolId = toolCapability.id();
            if (toolId != null && (toolId.contains("admin") || toolId.contains("system"))) {
                // Admin tools require special permissions
                return ToolPolicyDecision.denyMissingPermissions(Set.of("tool:admin", "admin"));
            }
        }

        // Check for sensitive data in input
        if (toolInput != null) {
            String inputStr = toolInput.toString().toLowerCase();
            if (inputStr.contains("password") || inputStr.contains("secret") || inputStr.contains("token")) {
                // Sensitive data requires redaction
                return new ToolPolicyDecision(
                    true,
                    "Tool invocation allowed with redaction",
                    Set.of(),
                    Set.of(),
                    true,
                    Set.of("password", "secret", "token", "api_key"),
                    Map.of("redactionApplied", true)
                );
            }
        }

        return ToolPolicyDecision.allow();
    }

    @Override
    public MemoryPolicyDecision evaluateMemoryWrite(
            String tenantId,
            String actionId,
            String memoryType,
            String operation,
            Map<String, Object> content,
            EvaluationContext context) {
        
        log.debug("[WS4-11] Evaluating memory write policy for tenant={}, actionId={}, memoryType={}, operation={}", 
            tenantId, actionId, memoryType, operation);

        // Check for sensitive memory types
        if ("EPISODIC".equalsIgnoreCase(memoryType) || "SEMANTIC".equalsIgnoreCase(memoryType)) {
            // Long-term memory requires classification
            return MemoryPolicyDecision.allowWithClassification("CONFIDENTIAL", "30_DAYS");
        }

        // Check for destructive operations
        if ("DELETE".equalsIgnoreCase(operation) || "CLEAR".equalsIgnoreCase(operation)) {
            // Destructive operations require approval
            return new MemoryPolicyDecision(
                true,
                "Destructive operation allowed with audit",
                "UNCLASSIFIED",
                "DEFAULT",
                false,
                Set.of(),
                true,
                "audit-log",
                Map.of("auditRequired", true)
            );
        }

        return MemoryPolicyDecision.allow();
    }

    @Override
    public ApprovalPolicyDecision evaluateApprovalGate(
            String tenantId,
            String actionId,
            SideEffectDeclaration sideEffectDeclaration,
            EvaluationContext context) {
        
        log.debug("[WS4-11] Evaluating approval gate for tenant={}, actionId={}", tenantId, actionId);

        if (sideEffectDeclaration == null || !sideEffectDeclaration.hasSideEffects()) {
            return ApprovalPolicyDecision.noApprovalRequired();
        }

        // High-risk side effects require approval
        if ("HIGH".equals(sideEffectDeclaration.riskLevel()) 
            || "CRITICAL".equals(sideEffectDeclaration.riskLevel())) {
            return ApprovalPolicyDecision.requireHighRiskApproval(
                Set.of("admin", "action.approver"),
                "high-risk-workflow"
            );
        }

        // Medium-risk side effects require approval
        if ("MEDIUM".equals(sideEffectDeclaration.riskLevel())) {
            return ApprovalPolicyDecision.requireApproval(
                Set.of("action.approver"),
                "standard-workflow"
            );
        }

        // Low-risk side effects may not require approval
        return ApprovalPolicyDecision.noApprovalRequired();
    }
}
