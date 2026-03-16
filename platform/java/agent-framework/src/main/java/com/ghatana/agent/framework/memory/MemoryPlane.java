/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.memory;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Typed bridge interface between the agent framework's {@link MemoryStore} and the
 * GAA memory plane abstraction.
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
 * if (raw instanceof MemoryPlane plane) {
 *     plane.consolidate(agentId, episodes).await();
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Typed bridge from MemoryStore to GAA memory plane operations
 * @doc.layer framework
 * @doc.pattern Bridge
 * @doc.gaa.memory episodic|semantic|procedural
 */
public interface MemoryPlane {

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
}
