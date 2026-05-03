/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure — Data-Cloud Knowledge Graph Adapter
 */
package com.ghatana.yappc.knowledge.adapter;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.yappc.knowledge.spi.DataStorePort;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Adapter implementation of DataStorePort using Data-Cloud Knowledge Graph plugin.
 *
 * <p>This adapter bridges the YAPPC-internal DataStorePort interface with the Data-Cloud
 * Knowledge Graph plugin, allowing knowledge-graph to depend on the port instead of directly
 * on data-cloud implementation details.
 *
 * <p><b>Current Status:</b> Stub implementation - provides no-op returns for all operations.
 * Full type conversion between port and plugin model types is pending Data-Cloud plugin integration.
 *
 * @doc.type class
 * @doc.purpose Adapter for Data-Cloud Knowledge Graph plugin
 * @doc.layer infrastructure
 * @doc.pattern Adapter (Hexagonal Architecture / Ports & Adapters)
 */
@Slf4j
public class DataCloudDataStoreAdapter implements DataStorePort {

    public DataCloudDataStoreAdapter(@Nullable Object graphPlugin) {
        log.info("DataCloudDataStoreAdapter initialized (stub implementation)");
    }

    @Override
    public Promise<DataStorePort.GraphNode> createNode(TenantId tenantId, DataStorePort.GraphNode node) {
        log.warn("createNode called on stub adapter, returning node as-is");
        return Promise.of(node);
    }

    @Override
    public Promise<DataStorePort.GraphEdge> createEdge(TenantId tenantId, DataStorePort.GraphEdge edge) {
        log.warn("createEdge called on stub adapter, returning edge as-is");
        return Promise.of(edge);
    }

    @Override
    public Promise<List<DataStorePort.GraphEdge>> queryEdges(TenantId tenantId, DataStorePort.GraphQuery query) {
        log.warn("queryEdges called on stub adapter, returning empty list");
        return Promise.of(List.of());
    }

    @Override
    public Promise<List<DataStorePort.GraphNode>> queryNodes(TenantId tenantId, DataStorePort.GraphQuery query) {
        log.warn("queryNodes called on stub adapter, returning empty list");
        return Promise.of(List.of());
    }

    @Override
    public Promise<List<DataStorePort.GraphNode>> getNeighbors(TenantId tenantId, String nodeId, int depth) {
        log.warn("getNeighbors called on stub adapter, returning empty list");
        return Promise.of(List.of());
    }

    @Override
    public Promise<List<DataStorePort.GraphNode>> findShortestPath(TenantId tenantId, String sourceId, String targetId) {
        log.warn("findShortestPath called on stub adapter, returning empty list");
        return Promise.of(List.of());
    }
}
