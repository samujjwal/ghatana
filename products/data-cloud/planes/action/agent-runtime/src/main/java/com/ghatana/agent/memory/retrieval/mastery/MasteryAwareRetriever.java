/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval.mastery;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Mastery-aware memory retriever that filters by version compatibility and lifecycle state.
 *
 * <p>Retrieval order:
 * <ol>
 *   <li>Negative knowledge (highest priority for safety)</li>
 *   <li>Version-compatible procedures</li>
 *   <li>Known failure modes</li>
 *   <li>Semantic facts</li>
 *   <li>Similar successful episodes</li>
 *   <li>Similar failed episodes</li>
 *   <li>Active task state/checkpoints</li>
 * </ol>
 *
 * @doc.type interface
 * @doc.purpose Mastery-aware memory retriever
 * @doc.layer agent-runtime
 * @doc.pattern Retriever
 */
public interface MasteryAwareRetriever {

    /**
     * Retrieves context with mastery-aware filtering.
     *
     * @param query mastery-aware memory query
     * @return promise of retrieved context
     */
    @NotNull
    Promise<RetrievedContext> retrieve(@NotNull MasteryAwareMemoryQuery query);
}
