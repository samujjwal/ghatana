/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Applicability scope defining where a mastery item can be applied.
 *
 * <p>Defines tenant, environment, and domain constraints for skill applicability.
 *
 * @doc.type record
 * @doc.purpose Applicability scope for mastery deployment
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record ApplicabilityScope(
        @NotNull String tenantId,
        @NotNull String environment,
        @NotNull Map<String, String> domainConstraints
) {
    public ApplicabilityScope {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(domainConstraints, "domainConstraints must not be null");
        domainConstraints = Map.copyOf(domainConstraints);
    }

    /**
     * Creates an applicability scope with minimal constraints.
     *
     * @param tenantId tenant identifier
     * @param environment environment (e.g., "production", "staging")
     * @return applicability scope
     */
    @NotNull
    public static ApplicabilityScope minimal(@NotNull String tenantId, @NotNull String environment) {
        return new ApplicabilityScope(tenantId, environment, Map.of());
    }
}
