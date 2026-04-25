/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure — Data-Cloud Knowledge Graph Adapter
 */
package com.ghatana.yappc.knowledge.adapter;

import com.ghatana.core.activej.promise.PromiseUtils;
import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge as DataCloudGraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode as DataCloudGraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery as DataCloudGraphQuery;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.yappc.knowledge.spi.DataStorePort;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter implementation of DataStorePort using Data-Cloud Knowledge Graph plugin.
 *
 * <p>This adapter bridges the YAPPC-internal DataStorePort interface with the Data-Cloud
 * Knowledge Graph plugin, allowing knowledge-graph to depend on the port instead of directly
 * on data-cloud implementation details.
 *
 * @doc.type class
 * @doc.purpose Adapter for Data-Cloud Knowledge Graph plugin
 * @doc.layer infrastructure
 * @doc.pattern Adapter (Hexagonal Architecture / Ports & Adapters)
 */
@Slf4j
public class DataCloudDataStoreAdapter implements DataStorePort {

    private final @Nullable KnowledgeGraphPlugin graphPlugin;

    public DataCloudDataStoreAdapter(@Nullable KnowledgeGraphPlugin graphPlugin) {
        this.graphPlugin = graphPlugin;
        log.info("DataCloudDataStoreAdapter initialized with plugin: {}", graphPlugin != null);
    }

    @Override
    public Promise<GraphNode> createNode(TenantId tenantId, GraphNode node) {
        if (graphPlugin == null) {
            log.warn("KnowledgeGraphPlugin not available, returning node without persistence");
            return Promise.of(node);
        }

        DataCloudGraphNode dcNode = toDataCloudNode(node, tenantId);
        return PromiseUtils.fromCompletableFuture(
            graphPlugin.createNode(dcNode).toCompletableFuture()
        ).map(created -> fromDataCloudNode(created));
    }

    @Override
    public Promise<GraphEdge> createEdge(TenantId tenantId, GraphEdge edge) {
        if (graphPlugin == null) {
            log.warn("KnowledgeGraphPlugin not available, returning edge without persistence");
            return Promise.of(edge);
        }

        DataCloudGraphEdge dcEdge = toDataCloudEdge(edge, tenantId);
        return PromiseUtils.fromCompletableFuture(
            graphPlugin.createEdge(dcEdge).toCompletableFuture()
        ).map(created -> fromDataCloudEdge(created));
    }

    @Override
    public Promise<List<GraphEdge>> queryEdges(TenantId tenantId, GraphQuery query) {
        if (graphPlugin == null) {
            log.warn("KnowledgeGraphPlugin not available, returning empty list");
            return Promise.of(List.of());
        }

        DataCloudGraphQuery dcQuery = toDataCloudQuery(query, tenantId);
        return graphPlugin.queryEdges(dcQuery)
            .map(edges -> edges.stream()
                .map(DataCloudDataStoreAdapter::fromDataCloudEdge)
                .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<GraphNode>> queryNodes(TenantId tenantId, GraphQuery query) {
        if (graphPlugin == null) {
            log.warn("KnowledgeGraphPlugin not available, returning empty list");
            return Promise.of(List.of());
        }

        DataCloudGraphQuery dcQuery = toDataCloudQuery(query, tenantId);
        return graphPlugin.queryNodes(dcQuery)
            .map(nodes -> nodes.stream()
                .map(DataCloudDataStoreAdapter::fromDataCloudNode)
                .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<GraphNode>> getNeighbors(TenantId tenantId, String nodeId, int depth) {
        if (graphPlugin == null) {
            log.warn("KnowledgeGraphPlugin not available, returning empty list");
            return Promise.of(List.of());
        }

        return graphPlugin.getNeighbors(nodeId, depth, tenantId.value())
            .map(neighbors -> neighbors.stream()
                .map(DataCloudDataStoreAdapter::fromDataCloudNode)
                .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<GraphNode>> findShortestPath(TenantId tenantId, String sourceId, String targetId) {
        if (graphPlugin == null) {
            log.warn("KnowledgeGraphPlugin not available, returning empty list");
            return Promise.of(List.of());
        }

        return graphPlugin.findShortestPath(sourceId, targetId, tenantId.value())
            .map(path -> path.stream()
                .map(DataCloudDataStoreAdapter::fromDataCloudNode)
                .collect(Collectors.toList()));
    }

    // ---------------------------------------------------------------------------
    // Conversion methods between platform-agnostic and data-cloud models
    // ---------------------------------------------------------------------------

    private static DataCloudGraphNode toDataCloudNode(GraphNode node, TenantId tenantId) {
        return DataCloudGraphNode.builder()
            .id(node.id())
            .type(node.type())
            .properties(node.properties())
            .labels(node.labels())
            .tenantId(tenantId.value())
            .createdAt(node.createdAt())
            .updatedAt(node.updatedAt())
            .version(node.version())
            .build();
    }

    private static GraphNode fromDataCloudNode(DataCloudGraphNode dcNode) {
        return new GraphNode(
            dcNode.getId(),
            dcNode.getType(),
            dcNode.getProperties(),
            dcNode.getLabels(),
            TenantId.of(dcNode.getTenantId()),
            dcNode.getCreatedAt(),
            dcNode.getUpdatedAt(),
            dcNode.getVersion()
        );
    }

    private static DataCloudGraphEdge toDataCloudEdge(GraphEdge edge, TenantId tenantId) {
        return DataCloudGraphEdge.builder()
            .id(edge.id())
            .sourceNodeId(edge.sourceNodeId())
            .targetNodeId(edge.targetNodeId())
            .relationshipType(edge.relationshipType())
            .properties(edge.properties())
            .tenantId(tenantId.value())
            .createdAt(edge.createdAt())
            .updatedAt(edge.updatedAt())
            .version(edge.version())
            .build();
    }

    private static GraphEdge fromDataCloudEdge(DataCloudGraphEdge dcEdge) {
        return new GraphEdge(
            dcEdge.getId(),
            dcEdge.getSourceNodeId(),
            dcEdge.getTargetNodeId(),
            dcEdge.getRelationshipType(),
            dcEdge.getProperties(),
            TenantId.of(dcEdge.getTenantId()),
            dcEdge.getCreatedAt(),
            dcEdge.getUpdatedAt(),
            dcEdge.getVersion()
        );
    }

    private static DataCloudGraphQuery toDataCloudQuery(GraphQuery query, TenantId tenantId) {
        return DataCloudGraphQuery.builder()
            .sourceNodeId(query.sourceNodeId())
            .targetNodeId(query.targetNodeId())
            .relationshipTypes(query.relationshipTypes())
            .nodeTypes(query.nodeTypes())
            .propertyFilters(query.propertyFilters())
            .tenantId(tenantId.value())
            .limit(query.limit())
            .build();
    }
}
