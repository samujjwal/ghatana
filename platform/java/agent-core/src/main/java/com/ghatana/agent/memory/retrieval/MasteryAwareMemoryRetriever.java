/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.memory.model.MemoryQuery;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Mastery-aware memory retriever that filters memory based on mastery state and version applicability.
 *
 * @doc.type class
 * @doc.purpose Mastery-aware memory retrieval
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class MasteryAwareMemoryRetriever {

    private final MasteryRegistry masteryRegistry;

    /**
     * Creates a mastery-aware memory retriever.
     *
     * @param masteryRegistry mastery registry
     */
    public MasteryAwareMemoryRetriever(@NotNull MasteryRegistry masteryRegistry) {
        this.masteryRegistry = masteryRegistry;
    }

    /**
     * Filters memory query results based on mastery state.
     *
     * @param query memory query
     * @param versionContext version context
     * @return promise of filtered memory items
     */
    @NotNull
    public Promise<List<Object>> filterByMastery(
            @NotNull MemoryQuery query,
            @NotNull VersionContext versionContext) {
        // Build mastery query from memory query parameters
        MasteryQuery masteryQuery = MasteryQuery.bySkill(query.agentId())
                .withAgentId(query.agentId())
                .withTenantId(query.agentId());

        return masteryRegistry.query(masteryQuery)
                .then(masteryItems -> {
                    // Filter memory items based on mastery state
                    return Promise.of(masteryItems.stream()
                            .filter(MasteryItem::isActiveForRetrieval)
                            .filter(item -> item.isFresh(java.time.Instant.now()))
                            .map(item -> (Object) item)
                            .toList());
                });
    }

    /**
     * Returns true if memory should be retrieved based on mastery state.
     *
     * @param skillId skill identifier
     * @param versionContext version context
     * @return promise of retrieval decision
     */
    @NotNull
    public Promise<Boolean> shouldRetrieve(
            @NotNull String skillId,
            @NotNull VersionContext versionContext) {
        MasteryQuery query = MasteryQuery.bySkill(skillId);

        return masteryRegistry.query(query)
                .then(items -> Promise.of(!items.isEmpty()));
    }
}
