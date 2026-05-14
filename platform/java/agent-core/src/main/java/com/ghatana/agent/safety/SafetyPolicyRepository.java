/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.safety;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Persistence contract for {@link SafetyPolicy} instances.
 *
 * <p>Implementations must enforce tenant isolation: all read operations must
 * filter by {@code tenantId} and must never return policies from other tenants.
 *
 * @doc.type interface
 * @doc.purpose Persistence contract for safety policies
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
public interface SafetyPolicyRepository {

    /**
     * Persists a new or updated safety policy.
     *
     * @param policy the policy to save
     * @return promise that completes when the policy is durably stored
     */
    @NotNull
    Promise<Void> save(@NotNull SafetyPolicy policy);

    /**
     * Finds a policy by its ID.
     *
     * @param tenantId  tenant that owns the policy
     * @param policyId  unique policy identifier
     * @return promise of the policy, or empty if not found
     */
    @NotNull
    Promise<Optional<SafetyPolicy>> findById(
            @NotNull String tenantId,
            @NotNull String policyId);

    /**
     * Returns the active safety policy for a tenant.
     *
     * @param tenantId tenant to query
     * @return promise of the active policy, or empty if none found
     */
    @NotNull
    Promise<Optional<SafetyPolicy>> findActive(@NotNull String tenantId);

    /**
     * Deletes a safety policy.
     *
     * @param tenantId tenant that owns the policy
     * @param policyId policy to delete
     * @return promise that completes when the policy is removed (no-op if absent)
     */
    @NotNull
    Promise<Void> delete(
            @NotNull String tenantId,
            @NotNull String policyId);
}
