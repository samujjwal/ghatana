/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Registry for tracking skill mastery, version applicability, and lifecycle state.
 *
 * <p>The MasteryRegistry provides operations for:
 * <ul>
 *   <li>Finding mastery by skill and environment</li>
 *   <li>Querying mastery items with filters</li>
 *   <li>Saving mastery items</li>
 *   <li>Transitioning mastery between states</li>
 *   <li>Finding stale mastery items</li>
 * </ul>
 *
 * <p>Transitions are append-only and require evidence for certain state changes
 * (e.g., MASTERED requires evaluation evidence).
 *
 * @doc.type interface
 * @doc.purpose Registry for tracking skill mastery and lifecycle
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
public interface MasteryRegistry {

    /**
     * Finds the best mastery item matching the query, ranked by version applicability,
     * mastery state, execution score, and freshness.
     *
     * <p>Tenant ID is required in the query. Version context participates in match/rank.
     *
     * @param query mastery query parameters (tenantId required)
     * @return promise of optional best-matching mastery item
     */
    @NotNull
    Promise<Optional<MasteryItem>> findBest(@NotNull MasteryQuery query);

    /**
     * Finds a mastery item by skill ID and environment fingerprint.
     *
     * @param skillId skill identifier
     * @param env environment fingerprint (tenant, versions, etc.)
     * @return promise of optional mastery item
     * @deprecated Use {@link #findBest(MasteryQuery)} instead; construct a {@link MasteryQuery}
     *             with explicit tenantId and skillId so tenant isolation is enforced at the call site.
     */
    @Deprecated
    @NotNull
    Promise<Optional<MasteryItem>> findBySkill(
            @NotNull String skillId,
            @NotNull EnvironmentFingerprint env
    );

    /**
     * Queries mastery items with the given query parameters.
     *
     * @param query mastery query parameters
     * @return promise of list of mastery items
     */
    @NotNull
    Promise<List<MasteryItem>> query(@NotNull MasteryQuery query);

    /**
     * Queries mastery items and returns a mastery decision for the first match.
     *
     * @param query mastery query parameters
     * @return promise of optional mastery decision
     */
    @NotNull
    Promise<Optional<MasteryDecision>> queryMastery(@NotNull MasteryQuery query);

    /**
     * Makes a mastery decision based on the query parameters.
     *
     * <p>Evaluates mastery state, version context, and environment to determine
     * the appropriate execution mode and whether the skill is executable.
     *
     * @param query mastery query parameters
     * @return promise of mastery decision
     */
    @NotNull
    Promise<MasteryDecision> decide(@NotNull MasteryQuery query);

    /**
     * Saves a mastery item (create or update).
     *
     * @param item mastery item to save
     * @return promise of saved mastery item
     */
    @NotNull
    Promise<MasteryItem> save(@NotNull MasteryItem item);

    /**
     * Transitions a mastery item to a new state.
     *
     * <p>Transitions are validated against policy rules:
     * <ul>
     *   <li>UNKNOWN → OBSERVED: requires one trace or verified source</li>
     *   <li>OBSERVED → PRACTICED: requires repeated episodes or sandbox experiments</li>
     *   <li>PRACTICED → COMPETENT: requires procedure exists and basic eval passes</li>
     *   <li>COMPETENT → MASTERED: requires regression, safety, recovery, and compatibility tests pass</li>
     *   <li>MASTERED → MAINTENANCE_ONLY: new active version exists; old version still used</li>
     *   <li>Any → OBSOLETE: docs/API/security/runtime contradiction or repeated failures</li>
     *   <li>Any → QUARANTINED: unsafe behavior or failed safety eval</li>
     *   <li>OBSOLETE → RETIRED: no active retrieval/use case remains</li>
     * </ul>
     *
     * @param transition mastery transition request
     * @return promise of transition result
     */
    @NotNull
    Promise<MasteryTransitionResult> transition(@NotNull MasteryTransition transition);

    /**
     * Finds mastery items that are stale (past their staleAfter timestamp).
     *
     * @param now current time
     * @return promise of list of stale mastery items
     * @deprecated Use {@link #findStale(String, Instant)} for tenant-scoped stale detection
     */
    @Deprecated
    @NotNull
    Promise<List<MasteryItem>> findStale(@NotNull Instant now);

    /**
     * Finds mastery items that are stale (past their staleAfter timestamp) for a specific tenant.
     *
     * @param tenantId tenant identifier
     * @param now current time
     * @return promise of list of stale mastery items
     */
    @NotNull
    Promise<List<MasteryItem>> findStale(@NotNull String tenantId, @NotNull Instant now);

    /**
     * Finds a mastery item by ID for a specific tenant.
     *
     * @param tenantId tenant identifier
     * @param masteryId mastery item identifier
     * @return promise of optional mastery item
     */
    @NotNull
    Promise<Optional<MasteryItem>> getById(@NotNull String tenantId, @NotNull String masteryId);
}
