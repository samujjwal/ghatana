/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Port for retrieving memory items relevant to a given query and agent context.
 *
 * <p>Implementations may apply mastery-aware ranking, freshness filtering,
 * or other domain-specific retrieval strategies.</p>
 *
 * @doc.type interface
 * @doc.purpose Retrieve contextually relevant memory items for an agent turn.
 * @doc.layer platform
 * @doc.pattern Port
 */
public interface MemoryRetriever {

    /**
     * Retrieves memory items relevant to the given query and context.
     *
     * @param query   the retrieval query
     * @param context the agent execution context identifier
     * @return a promise resolving to the list of matching items
     */
    @NotNull
    Promise<List<Object>> retrieve(@NotNull String query, @NotNull String context);

    /**
     * Retrieves memory items with additional retrieval options.
     *
     * @param query   the retrieval query
     * @param context the agent execution context identifier
     * @param options additional retrieval parameters (e.g. limit, filters)
     * @return a promise resolving to the list of matching items
     */
    @NotNull
    Promise<List<Object>> retrieve(
            @NotNull String query,
            @NotNull String context,
            @NotNull Map<String, String> options);
}
