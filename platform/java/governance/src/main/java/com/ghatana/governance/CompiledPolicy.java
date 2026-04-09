/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.governance;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A compiled policy rule that can be evaluated against a
 * {@link PolicyEvaluationContext}.
 *
 * <p>Compiled policies are loaded from YAML policy definitions and matched
 * against evaluation contexts using their condition predicates.
 *
 * @doc.type record
 * @doc.purpose Compiled governance policy rule for runtime evaluation
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record CompiledPolicy(
        /** Unique policy identifier. */
        @NotNull String id,

        /** Human-readable policy name. */
        @NotNull String name,

        /** Priority (lower number = higher priority). */
        int priority,

        /** Action classes this policy applies to (empty = all). */
        @NotNull List<String> applicableActionClasses,

        /** Criticality levels this policy applies to (empty = all). */
        @NotNull List<String> applicableCriticalities,

        /** Tenant IDs this policy is scoped to (empty = all tenants). */
        @NotNull List<String> tenantScopes,

        /** Decision to render when conditions match. */
        @NotNull String decision,

        /** Roles required for approval (when decision is ALLOW_WITH_APPROVAL). */
        @NotNull List<String> requiredApprovalRoles,

        /** Obligation types to impose. */
        @NotNull List<String> obligations,

        /** Additional match conditions as key-value predicates. */
        @NotNull Map<String, String> conditions,

        /** Whether this policy is currently active. */
        boolean enabled
) {
    public CompiledPolicy {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        applicableActionClasses = List.copyOf(applicableActionClasses);
        applicableCriticalities = List.copyOf(applicableCriticalities);
        tenantScopes = List.copyOf(tenantScopes);
        Objects.requireNonNull(decision);
        requiredApprovalRoles = List.copyOf(requiredApprovalRoles);
        obligations = List.copyOf(obligations);
        conditions = Map.copyOf(conditions);
    }

    /**
     * Returns {@code true} if this policy applies to the given evaluation context.
     */
    public boolean matches(@NotNull PolicyEvaluationContext ctx) {
        if (!enabled) return false;

        // Action class filter
        if (!applicableActionClasses.isEmpty()
                && !applicableActionClasses.contains(ctx.actionClass())) {
            return false;
        }

        // Criticality filter
        if (!applicableCriticalities.isEmpty()
                && !applicableCriticalities.contains(ctx.criticality())) {
            return false;
        }

        // Tenant scope filter
        if (!tenantScopes.isEmpty()
                && !tenantScopes.contains(ctx.tenantId())) {
            return false;
        }

        // Additional attribute conditions
        for (Map.Entry<String, String> condition : conditions.entrySet()) {
            Object actual = ctx.attributes().get(condition.getKey());
            if (actual == null || !actual.toString().equals(condition.getValue())) {
                return false;
            }
        }

        return true;
    }
}
