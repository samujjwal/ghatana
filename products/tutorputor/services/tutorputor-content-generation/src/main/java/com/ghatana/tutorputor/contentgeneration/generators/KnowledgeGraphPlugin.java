package com.ghatana.tutorputor.contentgeneration.generators;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Stub interface for knowledge graph access within content generation.
 *
 * <p>Provides graph traversal to find related concepts that enrich
 * generated educational content.
 *
 * @doc.type interface
 * @doc.purpose Knowledge graph plugin abstraction for content generators
 * @doc.layer product
 * @doc.pattern Plugin, Port
 */
public interface KnowledgeGraphPlugin {

    /**
     * Returns neighboring nodes for a given node up to the specified depth.
     *
     * @param nodeId   the starting node identifier
     * @param depth    traversal depth (number of hops)
     * @param tenantId tenant context for data isolation
     * @return a Promise resolving to the list of neighbor nodes
     */
    Promise<List<GraphNode>> getNeighbors(String nodeId, int depth, String tenantId);
}
