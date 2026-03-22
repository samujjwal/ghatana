/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.governance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Context provided to a policy evaluation — contains all information
 * the policy engine needs to make a governance decision.
 *
 * <p>This is governance-module level and does not depend on agent-core types.
 * The agent-core module bridges its domain types to this evaluation context.
 *
 * @doc.type record
 * @doc.purpose Policy evaluation input context
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PolicyEvaluationContext(
        /** Identifier of the entity requesting the action. */
        @NotNull String principalId,

        /** Tenant isolation scope. */
        @NotNull String tenantId,

        /** Classification of the action (maps to ActionClass values). */
        @NotNull String actionClass,

        /** Type of entity being acted upon. */
        @NotNull String targetType,

        /** Identifier of the target entity. */
        @Nullable String targetId,

        /** Tool being invoked. */
        @Nullable String toolId,

        /** Criticality level. */
        @NotNull String criticality,

        /** Reversibility classification. */
        @NotNull String reversibility,

        /** Additional contextual attributes for rule matching. */
        @NotNull Map<String, Object> attributes,

        /** When this context was created. */
        @NotNull Instant timestamp
) {
    public PolicyEvaluationContext {
        Objects.requireNonNull(principalId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actionClass);
        Objects.requireNonNull(targetType);
        Objects.requireNonNull(criticality);
        Objects.requireNonNull(reversibility);
        attributes = Map.copyOf(attributes);
        Objects.requireNonNull(timestamp);
    }
}
