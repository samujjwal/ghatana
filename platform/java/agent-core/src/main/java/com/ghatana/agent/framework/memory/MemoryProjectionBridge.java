/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.memory;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * P1 FIX: Renamed from MemoryPlane to MemoryProjectionBridge to avoid conflict with
 * the canonical Data Cloud runtime MemoryPlane.
 *
 * <p>This is a compatibility facade for the framework's projection/consolidation/reflection
 * operations. The canonical memory plane is now {@link com.ghatana.agent.memory.store.MemoryPlane}
 * in the Data Cloud runtime, which supports episodic, semantic, procedural, typed artifacts,
 * cross-tier queries, semantic search, working memory, task state, checkpointing, and stats.
 *
 * <p>This interface resolves the type-erasure limitation of
 * {@link MemoryStore#asMemoryPlane()}, which previously returned raw {@code Object}
 * to avoid a cross-module compile-time dependency. By extracting a typed contract
 * here, callers in other modules can cast safely via this interface.
 *
 * <h2>Memory Plane roles</h2>
 * <ul>
 *   <li><b>Projection</b>: Materialise point-in-time views from the event log</li>
 *   <li><b>Consolidation</b>: Merge episodes into semantic/procedural facts</li>
 *   <li><b>Reflection</b>: Batch-process recent episodes to synthesise policies</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Object raw = memoryStore.asMemoryPlane();
 * if (raw instanceof MemoryProjectionBridge plane) {
 *     plane.consolidate(agentId, episodes).await();
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Compatibility facade for framework memory projection operations
 * @doc.layer framework
 * @doc.pattern Bridge
 * @doc.gaa.memory episodic|semantic|procedural
 * @deprecated Use {@link com.ghatana.agent.memory.store.MemoryPlane} for the canonical multi-tier memory plane
 */
@Deprecated
public interface MemoryProjectionBridge {

    /**
     * Consolidates a list of recent episodes into the semantic/procedural memory plane.
     * Extracts facts, patterns, and learned policies from raw experience records.
     *
     * @param agentId  identifier of the agent whose episodes are being consolidated
     * @param episodes recent episodes to process
     * @return promise of consolidation result describing what was extracted
     */
    @NotNull
    Promise<ConsolidationResult> consolidate(
            @NotNull String agentId,
            @NotNull List<Episode> episodes);

    /**
     * Projects the current state of memory for a given agent as a read-optimised
     * snapshot for use in prompts or reasoning steps.
     *
     * @param agentId identifier of the agent
     * @param filter  filter criteria for the snapshot
     * @param limit   maximum number of items per memory type
     * @return promise of memory snapshot
     */
    @NotNull
    Promise<MemorySnapshot> project(
            @NotNull String agentId,
            @NotNull MemoryFilter filter,
            int limit);

    /**
     * Triggers an asynchronous reflection pass that synthesises new policies from
     * recent episodes. This is the "REFLECT" phase of the GAA lifecycle.
     * Should be fire-and-forget — never block the user-response path.
     *
     * @param agentId identifier of the agent to reflect for
     * @return promise (fire-and-forget) that resolves when reflection is complete
     */
    @NotNull
    Promise<ReflectionResult> reflect(@NotNull String agentId);

    /**
     * Queries the version context for a given skill and environment.
     * Returns version information for tools, libraries, and ecosystem components
     * relevant to the agent's execution context.
     *
     * @param agentId identifier of the agent
     * @param skillId skill identifier to query version context for
     * @return promise of version context
     */
    @NotNull
    Promise<VersionContextQueryResult> queryVersionContext(
            @NotNull String agentId,
            @NotNull String skillId);

    /**
     * Queries the mastery state for a given skill and agent.
     * Returns current mastery information including state, level, and applicability.
     *
     * @param agentId identifier of the agent
     * @param skillId skill identifier to query mastery for
     * @return promise of mastery state query result
     */
    @NotNull
    Promise<MasteryStateQueryResult> queryMasteryState(
            @NotNull String agentId,
            @NotNull String skillId);

    // =========================================================================
    // Value types
    // =========================================================================

    /**
     * Result from a consolidation pass.
     *
     * @param agentId       agent identifier
     * @param factsExtracted  number of new semantic facts produced
     * @param policiesSynthesised number of new procedural policies synthesised
     * @param episodesProcessed  number of episodes consumed
     */
    record ConsolidationResult(
            @NotNull String agentId,
            int factsExtracted,
            int policiesSynthesised,
            int episodesProcessed) {}

    /**
     * Point-in-time read-optimised snapshot of agent memory.
     *
     * @param agentId    agent identifier
     * @param episodes   recent episodes (up to limit)
     * @param facts      semantic facts (up to limit)
     * @param policies   applicable policies (up to limit)
     * @param preferences key-value preferences
     */
    record MemorySnapshot(
            @NotNull String agentId,
            @NotNull List<Episode> episodes,
            @NotNull List<Fact> facts,
            @NotNull List<Policy> policies,
            @NotNull java.util.Map<String, String> preferences) {}

    /**
     * Result from an asynchronous reflection pass.
     *
     * @param agentId          agent identifier
     * @param policiesCreated  new policies synthesised
     * @param policiesUpdated  existing policies whose confidence was updated
     * @param lowConfidenceCount policies flagged for human review (confidence &lt; 0.7)
     */
    record ReflectionResult(
            @NotNull String agentId,
            int policiesCreated,
            int policiesUpdated,
            int lowConfidenceCount) {}

    /**
     * Result of a version context query.
     *
     * @param agentId agent identifier
     * @param skillId skill identifier
     * @param versionContext version context information
     * @param applicable true if the version context applies to the current execution environment
     */
    record VersionContextQueryResult(
            @NotNull String agentId,
            @NotNull String skillId,
            @NotNull com.ghatana.agent.context.version.VersionContext versionContext,
            boolean applicable) {}

    /**
     * Result of a mastery state query.
     *
     * @param agentId agent identifier
     * @param skillId skill identifier
     * @param masteryState current mastery state
     * @param masteryLevel mastery level (if available)
     * @param confidence confidence score for the mastery assessment
     * @param lastUpdated timestamp of last mastery state update
     */
    record MasteryStateQueryResult(
            @NotNull String agentId,
            @NotNull String skillId,
            @NotNull com.ghatana.agent.mastery.MasteryState masteryState,
            @NotNull String masteryLevel,
            double confidence,
            @NotNull java.time.Instant lastUpdated) {}
}
