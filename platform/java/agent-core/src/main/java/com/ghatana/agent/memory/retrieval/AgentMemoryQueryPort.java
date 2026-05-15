/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.memory.model.MemoryItem;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Product-side adapter contract for querying canonical memory items.
 *
 * <p>This port allows {@link MasteryAwareMemoryRetriever} to consume runtime memory
 * sources without coupling agent-core to product runtime implementations.
 *
 * @doc.type interface
 * @doc.purpose Boundary port for canonical memory retrieval
 * @doc.layer agent-core
 * @doc.pattern Port
 */
public interface AgentMemoryQueryPort {

    /**
     * Queries memory items for an agent/skill in a tenant context.
     *
     * @param agentId agent identifier
     * @param tenantId tenant identifier
     * @param skillId skill identifier
     * @param versionContext version context for applicability
     * @param limit max number of items requested
     * @return promise of memory items from the canonical runtime source
     */
    @NotNull
    Promise<List<MemoryItem>> queryMemoryItems(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String skillId,
            @NotNull VersionContext versionContext,
            int limit);
}