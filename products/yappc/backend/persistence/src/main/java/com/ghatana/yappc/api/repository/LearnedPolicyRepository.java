/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Backend Persistence
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.LearnedPolicy;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for persistence of {@link LearnedPolicy} records.
 *
 * <p>Implementations MUST NOT block the ActiveJ eventloop.
 * All blocking JDBC calls must be wrapped in {@code Promise.ofBlocking(executor, ...)}.
 *
 * @doc.type interface
 * @doc.purpose Repository port for learned policies (procedural memory — YAPPC product layer)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface LearnedPolicyRepository {

    /**
     * Persists a learned policy. If a policy with the same {@code id} already exists,
     * it is updated (upsert semantics).
     *
     * @param policy the policy to save; must have non-null {@code id}, {@code agentId},
     *               {@code tenantId}, and {@code procedure}
     * @return the saved policy (may enrich timestamps)
     */
    @NotNull
    Promise<LearnedPolicy> save(@NotNull LearnedPolicy policy);

    /**
     * Finds a policy by its unique ID.
     *
     * @param id the policy identifier
     * @return the policy, or {@link Optional#empty()} if not found
     */
    @NotNull
    Promise<Optional<LearnedPolicy>> findById(@NotNull String id);

    /**
     * Returns all learned policies for a given agent within a tenant scope.
     *
     * @param tenantId the owning tenant
     * @param agentId  the agent whose policies to retrieve
     * @return list of learned policies, newest-first by {@code created_at}
     */
    @NotNull
    Promise<List<LearnedPolicy>> findByAgent(@NotNull String tenantId, @NotNull String agentId);

    /**
     * Returns all learned policies in a tenant with confidence ≥ {@code minConfidence}.
     *
     * <p>Used by inference layers to retrieve only high-quality policies for application.
     *
     * @param tenantId      the owning tenant
     * @param minConfidence minimum confidence threshold (inclusive, 0.0–1.0)
     * @return matching policies ordered by confidence descending
     */
    @NotNull
    Promise<List<LearnedPolicy>> findAboveConfidence(
            @NotNull String tenantId, double minConfidence);

    /**
     * Deletes a policy by ID.
     *
     * @param id the policy identifier
     * @return completed promise (no error if the policy did not exist)
     */
    @NotNull
    Promise<Void> delete(@NotNull String id);
}
