/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.governance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Typed description of an action an agent intends to perform.
 *
 * <p>An {@code ActionIntent} is created before the action is executed and passed
 * through the policy evaluation pipeline. It carries enough context for the
 * governance engine to classify, evaluate, and route the action.
 *
 * @doc.type record
 * @doc.purpose Typed action intent for pre-execution governance evaluation
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record ActionIntent(
        /** Distributed trace ID for correlation. */
        @NotNull String traceId,

        /** Agent requesting the action. */
        @NotNull String agentId,

        /** Tenant context for isolation. */
        @NotNull String tenantId,

        /** Classification of the action being performed. */
        @NotNull ActionClass actionClass,

        /** Type of entity being acted upon (e.g., "purchase_order", "user_profile"). */
        @NotNull String targetType,

        /** Identifier of the specific target entity. */
        @Nullable String targetId,

        /** Tool ID being invoked, if applicable. */
        @Nullable String toolId,

        /** SHA-256 hash of the action arguments for evidence recording. */
        @Nullable String argsHash,

        /** Reversibility classification of this action. */
        @NotNull ReversibilityClass reversibilityClass,

        /** Criticality level (low, medium, high, critical). */
        @NotNull String criticality,

        /** Principal or agent that requested the action. */
        @NotNull String requestedBy,

        /** Agent that delegated this action, if applicable. */
        @Nullable String delegatedFrom,

        /** Version of the agent datasheet used for this evaluation. */
        @Nullable String datasheetVersion,

        /** When this intent was created. */
        @NotNull Instant createdAt
) {

    public ActionIntent {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(actionClass, "actionClass must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(reversibilityClass, "reversibilityClass must not be null");
        Objects.requireNonNull(criticality, "criticality must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Returns {@code true} if this action is classified as privileged. */
    public boolean isPrivileged() {
        return actionClass.isPrivileged();
    }
}
