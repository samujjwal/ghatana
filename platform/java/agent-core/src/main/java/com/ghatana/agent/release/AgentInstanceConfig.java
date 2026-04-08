/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import java.time.Instant;
import java.util.Map;

/**
 * Tenant-scoped runtime configuration overlay for an agent release.
 *
 * <p>{@code AgentInstanceConfig} allows per-tenant or per-environment runtime
 * customizations on top of a fixed {@link AgentRelease}. Examples include
 * model overrides, cost/token budgets, feature flags, and environment bindings.
 *
 * <p>When {@code killSwitch} is {@code true} the dispatch layer must reject any
 * invocation of the linked release for the tenant, regardless of the release state.
 *
 * @param instanceConfigId   unique ID for this config instance
 * @param agentReleaseId     references the {@link AgentRelease} this config applies to
 * @param tenantId           the tenant this config is scoped to
 * @param environment        target environment: {@code dev}, {@code staging}, {@code production}
 * @param modelOverrides     optional runtime model overrides (e.g., provider, model name)
 * @param budgets            token, cost, and time budget constraints
 * @param featureFlags       rollout toggles for feature-flagged behavior
 * @param environmentBindings environment variable bindings
 * @param killSwitch         when {@code true} all dispatch to this release+tenant is rejected
 * @param createdAt          creation timestamp
 * @param updatedAt          last-update timestamp
 *
 * @doc.type record
 * @doc.purpose Tenant-scoped runtime configuration overlay for an agent release
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentInstanceConfig(
        String instanceConfigId,
        String agentReleaseId,
        String tenantId,
        String environment,
        Map<String, String> modelOverrides,
        Map<String, Object> budgets,
        Map<String, String> featureFlags,
        Map<String, String> environmentBindings,
        boolean killSwitch,
        Instant createdAt,
        Instant updatedAt
) {
    public AgentInstanceConfig {
        if (instanceConfigId == null || instanceConfigId.isBlank()) {
            throw new IllegalArgumentException("instanceConfigId must not be blank");
        }
        if (agentReleaseId == null || agentReleaseId.isBlank()) {
            throw new IllegalArgumentException("agentReleaseId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        modelOverrides       = Map.copyOf(modelOverrides);
        budgets              = Map.copyOf(budgets);
        featureFlags         = Map.copyOf(featureFlags);
        environmentBindings  = Map.copyOf(environmentBindings);
    }
}
