/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation.pack;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link EvaluationPack} instances.
 *
 * <p>Implementations must enforce tenant isolation: all read operations must
 * filter by {@code tenantId} and must never return packs from other tenants.
 *
 * @doc.type interface
 * @doc.purpose Persistence contract for skill-scoped evaluation packs
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
public interface EvaluationPackRepository {

    /**
     * Persists a new or updated evaluation pack.
     *
     * @param pack the pack to save
     * @return promise that completes when the pack is durably stored
     */
    @NotNull
    Promise<Void> save(@NotNull EvaluationPack pack);

    /**
     * Finds a pack by its composite key.
     *
     * @param tenantId          tenant that owns the pack
     * @param evaluationPackId  unique pack identifier
     * @return promise of the pack, or empty if not found
     */
    @NotNull
    Promise<Optional<EvaluationPack>> findById(
            @NotNull String tenantId,
            @NotNull String evaluationPackId);

    /**
     * Returns all evaluation packs targeting a specific skill for a tenant.
     *
     * @param tenantId tenant to query
     * @param skillId  skill identifier
     * @return promise of matching packs (empty list if none found)
     */
    @NotNull
    Promise<List<EvaluationPack>> findBySkill(
            @NotNull String tenantId,
            @NotNull String skillId);

    /**
     * Deletes an evaluation pack.
     *
     * @param tenantId         tenant that owns the pack
     * @param evaluationPackId pack to delete
     * @return promise that completes when the pack is removed (no-op if absent)
     */
    @NotNull
    Promise<Void> delete(
            @NotNull String tenantId,
            @NotNull String evaluationPackId);
}
