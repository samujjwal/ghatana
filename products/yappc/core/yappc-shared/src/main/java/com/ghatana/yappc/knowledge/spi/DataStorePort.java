/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Shared — Knowledge Graph Data Store Port
 */
package com.ghatana.yappc.knowledge.spi;

import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * YAPPC-internal port interface for knowledge graph data store operations.
 *
 * <p>This port is the adapter seam between YAPPC knowledge graph domain logic and the underlying
 * data store implementation (e.g., Data-Cloud Knowledge Graph plugin). All YAPPC modules that need
 * to perform graph operations must depend on this port, <em>not</em> directly on data-cloud classes.
 *
 * <p>Implementations live in the infrastructure layer (e.g., {@code yappc-infrastructure}) and
 * adapt the external service to this contract.
 *
 * @doc.type interface
 * @doc.purpose YAPPC-internal port for knowledge graph data store operations
 * @doc.layer platform
 * @doc.pattern Port (Hexagonal Architecture / Ports & Adapters)
 */
public interface DataStorePort {

    /**
     * Create a graph node in the data store.
     *
     * @param tenantId tenant scope
     * @param node the node data (platform-agnostic representation)
     * @return promise of the created node with server-assigned fields
     */
    Promise<GraphNode> createNode(TenantId tenantId, GraphNode node);

    /**
     * Create a graph edge in the data store.
     *
     * @param tenantId tenant scope
     * @param edge the edge data (platform-agnostic representation)
     * @return promise of the created edge with server-assigned fields
     */
    Promise<GraphEdge> createEdge(TenantId tenantId, GraphEdge edge);

    /**
     * Query graph edges based on the provided criteria.
     *
     * @param tenantId tenant scope
     * @param query query criteria
     * @return promise of matching edges
     */
    Promise<List<GraphEdge>> queryEdges(TenantId tenantId, GraphQuery query);

    /**
     * Query graph nodes based on the provided criteria.
     *
     * @param tenantId tenant scope
     * @param query query criteria
     * @return promise of matching nodes
     */
    Promise<List<GraphNode>> queryNodes(TenantId tenantId, GraphQuery query);

    /**
     * Get neighboring nodes for a given node up to a specified depth.
     *
     * @param tenantId tenant scope
     * @param nodeId the source node identifier
     * @param depth maximum traversal depth
     * @return promise of neighboring nodes
     */
    Promise<List<GraphNode>> getNeighbors(TenantId tenantId, String nodeId, int depth);

    /**
     * Find the shortest path between two nodes.
     *
     * @param tenantId tenant scope
     * @param sourceId source node identifier
     * @param targetId target node identifier
     * @return promise of the path as a list of nodes
     */
    Promise<List<GraphNode>> findShortestPath(TenantId tenantId, String sourceId, String targetId);

    /**
     * Platform-agnostic representation of a graph node.
     */
    record GraphNode(
        String id,
        String type,
        Map<String, Object> properties,
        Set<String> labels,
        TenantId tenantId,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        long version
    ) {}

    /**
     * Platform-agnostic representation of a graph edge.
     */
    record GraphEdge(
        String id,
        String sourceNodeId,
        String targetNodeId,
        String relationshipType,
        Map<String, Object> properties,
        TenantId tenantId,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        long version
    ) {}

    /**
     * Query criteria for graph operations.
     */
    record GraphQuery(
        String sourceNodeId,
        String targetNodeId,
        Set<String> relationshipTypes,
        Set<String> nodeTypes,
        Map<String, Object> propertyFilters,
        TenantId tenantId,
        int limit
    ) {
        public static GraphQueryBuilder builder() {
            return new GraphQueryBuilder();
        }

        public static final class GraphQueryBuilder {
            private String sourceNodeId;
            private String targetNodeId;
            private Set<String> relationshipTypes = Set.of();
            private Set<String> nodeTypes = Set.of();
            private Map<String, Object> propertyFilters = Map.of();
            private TenantId tenantId;
            private int limit = 1000;

            public GraphQueryBuilder sourceNodeId(String sourceNodeId) {
                this.sourceNodeId = sourceNodeId;
                return this;
            }

            public GraphQueryBuilder targetNodeId(String targetNodeId) {
                this.targetNodeId = targetNodeId;
                return this;
            }

            public GraphQueryBuilder relationshipTypes(Set<String> relationshipTypes) {
                this.relationshipTypes = relationshipTypes;
                return this;
            }

            public GraphQueryBuilder nodeTypes(Set<String> nodeTypes) {
                this.nodeTypes = nodeTypes;
                return this;
            }

            public GraphQueryBuilder propertyFilters(Map<String, Object> propertyFilters) {
                this.propertyFilters = propertyFilters;
                return this;
            }

            public GraphQueryBuilder tenantId(TenantId tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public GraphQueryBuilder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public GraphQuery build() {
                return new GraphQuery(
                    sourceNodeId,
                    targetNodeId,
                    relationshipTypes,
                    nodeTypes,
                    propertyFilters,
                    tenantId,
                    limit
                );
            }
        }
    }
}
