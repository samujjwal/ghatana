/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.governance;

import com.ghatana.agent.framework.runtime.AutonomyLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable compliance artifact describing an agent's identity, permissions,
 * risk profile, governance bindings, and operational constraints.
 *
 * <p>An {@code AgentDatasheet} is the single source of truth consumed by
 * governance UIs, approval routing, operator inventories, and compliance
 * auditors. It is generated from the agent's {@code AgentSpec}, runtime
 * registration metadata, policy registry, and deployment configuration.
 *
 * @doc.type record
 * @doc.purpose Immutable compliance datasheet for an agent deployment
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record AgentDatasheet(
        /** Unique agent identifier (e.g., "agent.procurement-assistant"). */
        @NotNull String agentId,

        /** Semantic version of this agent. */
        @NotNull String version,

        /** Namespace for organizational scoping (e.g., "platform.procurement"). */
        @NotNull String namespace,

        /** Owners: list of maps with keys "team", "role", "contact". */
        @NotNull List<Map<String, String>> owners,

        /** Risk tier classification (e.g., "tier-1", "tier-2", "tier-3"). */
        @NotNull String riskTier,

        /** Canonical autonomy tier. */
        @NotNull AutonomyLevel autonomyTier,

        /** Business criticality level. */
        @NotNull String criticality,

        /** Action classes this agent is permitted to perform. */
        @NotNull List<ActionClass> allowedActionClasses,

        /** Per-tool permissions specifying which action classes are allowed. */
        @NotNull List<ToolPermission> toolPermissions,

        /** Memory namespace bindings with access mode. */
        @NotNull List<MemoryBindingEntry> memoryBindings,

        /** Data classification level for information this agent handles. */
        @NotNull String dataClassification,

        /** Default retention policy in days. */
        int retentionDays,

        /** Approval rules by action class. */
        @NotNull List<ApprovalRule> approvalRules,

        /** References to evaluation packs required for release gates. */
        @NotNull List<String> evaluationPackRefs,

        /** Runbook URL for the emergency kill-switch procedure. */
        @Nullable String killSwitchProcedure,

        /** Rollback strategy description. */
        @Nullable String rollbackStrategy,

        /** Audit mode: "standard" or "regulated". */
        @NotNull String auditMode,

        /** Deployment contexts where this agent may run. */
        @NotNull List<String> deploymentContexts,

        /** When this datasheet was last reviewed. */
        @Nullable Instant lastReviewedAt,

        /** When the next review is due. */
        @Nullable Instant nextReviewAt
) {

    public AgentDatasheet {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(namespace, "namespace must not be null");
        owners = List.copyOf(owners);
        Objects.requireNonNull(riskTier, "riskTier must not be null");
        Objects.requireNonNull(autonomyTier, "autonomyTier must not be null");
        Objects.requireNonNull(criticality, "criticality must not be null");
        allowedActionClasses = List.copyOf(allowedActionClasses);
        toolPermissions = List.copyOf(toolPermissions);
        memoryBindings = List.copyOf(memoryBindings);
        Objects.requireNonNull(dataClassification, "dataClassification must not be null");
        approvalRules = List.copyOf(approvalRules);
        evaluationPackRefs = List.copyOf(evaluationPackRefs);
        Objects.requireNonNull(auditMode, "auditMode must not be null");
        deploymentContexts = List.copyOf(deploymentContexts);
    }

    /**
     * Per-tool permission declaration.
     *
     * @param toolId  the tool identifier
     * @param actions permitted action classes for this tool
     */
    public record ToolPermission(
            @NotNull String toolId,
            @NotNull List<ActionClass> actions
    ) {
        public ToolPermission {
            Objects.requireNonNull(toolId);
            actions = List.copyOf(actions);
        }
    }

    /**
     * Memory namespace binding with access mode.
     *
     * @param namespace the memory namespace
     * @param access    access mode: "read", "write", "read-write", "append-only"
     */
    public record MemoryBindingEntry(
            @NotNull String namespace,
            @NotNull String access
    ) {
        public MemoryBindingEntry {
            Objects.requireNonNull(namespace);
            Objects.requireNonNull(access);
        }
    }

    /**
     * Approval rule binding an action class to required approver roles.
     *
     * @param actionClass   the action class requiring approval
     * @param requiredRoles roles that must approve
     */
    public record ApprovalRule(
            @NotNull ActionClass actionClass,
            @NotNull List<String> requiredRoles
    ) {
        public ApprovalRule {
            Objects.requireNonNull(actionClass);
            requiredRoles = List.copyOf(requiredRoles);
        }
    }
}
