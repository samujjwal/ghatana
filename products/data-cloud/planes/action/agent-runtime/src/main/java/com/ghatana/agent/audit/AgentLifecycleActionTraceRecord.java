/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable evidence record for a Kernel-governed agent lifecycle action.
 *
 * @doc.type record
 * @doc.purpose Structured lifecycle action evidence before conversion to a hash-chained TraceEvent
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record AgentLifecycleActionTraceRecord(
        @NotNull String correlationId,
        @NotNull String requestId,
        @NotNull String productUnitId,
        @NotNull String tenantId,
        @NotNull String workspaceId,
        @NotNull String projectId,
        @NotNull String agentId,
        @NotNull String agentVersion,
        @NotNull String action,
        @NotNull String masteryState,
        @NotNull String policyDecision,
        @NotNull List<String> toolPermissions,
        boolean approvalRequired,
        @NotNull String riskLevel,
        @NotNull List<String> inputRefs,
        @NotNull List<String> outputRefs,
        @NotNull List<String> verificationProofRefs,
        @NotNull List<String> evidenceRefs,
        @NotNull String rollbackPlanRef,
        @NotNull String fallbackMode,
        @NotNull AgentLifecycleActionOutcome outcome,
        @Nullable String reasonCode
) {
    public AgentLifecycleActionTraceRecord {
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(productUnitId, "productUnitId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(agentVersion, "agentVersion");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(masteryState, "masteryState");
        Objects.requireNonNull(policyDecision, "policyDecision");
        toolPermissions = List.copyOf(Objects.requireNonNull(toolPermissions, "toolPermissions"));
        Objects.requireNonNull(riskLevel, "riskLevel");
        inputRefs = List.copyOf(Objects.requireNonNull(inputRefs, "inputRefs"));
        outputRefs = List.copyOf(Objects.requireNonNull(outputRefs, "outputRefs"));
        verificationProofRefs = List.copyOf(Objects.requireNonNull(verificationProofRefs, "verificationProofRefs"));
        evidenceRefs = List.copyOf(Objects.requireNonNull(evidenceRefs, "evidenceRefs"));
        Objects.requireNonNull(rollbackPlanRef, "rollbackPlanRef");
        Objects.requireNonNull(fallbackMode, "fallbackMode");
        Objects.requireNonNull(outcome, "outcome");
    }

    @NotNull
    Map<String, String> toPayload() {
        return Map.ofEntries(
                Map.entry("correlationId", correlationId),
                Map.entry("requestId", requestId),
                Map.entry("productUnitId", productUnitId),
                Map.entry("workspaceId", workspaceId),
                Map.entry("projectId", projectId),
                Map.entry("agentVersion", agentVersion),
                Map.entry("action", action),
                Map.entry("masteryState", masteryState),
                Map.entry("policyDecision", policyDecision),
                Map.entry("toolPermissions", String.join(",", toolPermissions)),
                Map.entry("approvalRequired", String.valueOf(approvalRequired)),
                Map.entry("riskLevel", riskLevel),
                Map.entry("inputRefs", String.join(",", inputRefs)),
                Map.entry("outputRefs", String.join(",", outputRefs)),
                Map.entry("verificationProofRefs", String.join(",", verificationProofRefs)),
                Map.entry("evidenceRefs", String.join(",", evidenceRefs)),
                Map.entry("rollbackPlanRef", rollbackPlanRef),
                Map.entry("fallbackMode", fallbackMode),
                Map.entry("outcome", outcome.name()),
                Map.entry("reasonCode", reasonCode == null ? "" : reasonCode));
    }
}
