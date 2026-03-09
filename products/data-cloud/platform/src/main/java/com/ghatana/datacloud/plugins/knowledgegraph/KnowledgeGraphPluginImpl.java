package com.ghatana.datacloud.plugins.knowledgegraph;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
import com.ghatana.datacloud.plugins.knowledgegraph.storage.GraphStorageAdapter;
import com.ghatana.datacloud.plugins.knowledgegraph.traversal.GraphTraversalEngine;
import com.ghatana.platform.plugin.HealthStatus;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static com.ghatana.platform.observability.util.BlockingExecutors.blockingExecutor;

/**
 * Implementation of the Knowledge Graph plugin for Data-Cloud.
 * 
 * <p>Provides comprehensive graph database capabilities including:
 * <ul>
 *   <li>Node and edge CRUD operations with validation</li>
 *   <li>Graph queries with filtering and pagination</li>
 *   <li>Graph traversal algorithms (BFS, DFS, shortest path)</li>
 *   <li>Multi-tenant isolation and security</li>
 *   <li>Async operations with ActiveJ Promises</li>
 *   <li>Integration with Data-Cloud storage and events</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> All operations are thread-safe
 * 
 * <p><b>Lifecycle:</b>
 * <pre>
 * UNLOADED → DISCOVERED → INITIALIZED → STARTED → RUNNING
 * </pre>
 * 
 * @doc.type class
 * @doc.purpose Knowledge Graph plugin implementation
 * @doc.layer plugin
 * @doc.pattern Plugin, Facade
 */
@Slf4j
public class KnowledgeGraphPluginImpl implements KnowledgeGraphPlugin {
    
    private static final String PLUGIN_ID = "knowledge-graph";
    private static final String PLUGIN_NAME = "Knowledge Graph";
    private static final String PLUGIN_VERSION = "1.0.0";
    
    private final AtomicReference<PluginState> state = new AtomicReference<>(PluginState.UNLOADED);
    private final GraphStorageAdapter storageAdapter;
    private final GraphTraversalEngine traversalEngine;
    private PluginContext context;
    
    public KnowledgeGraphPluginImpl(
            GraphStorageAdapter storageAdapter,
            GraphTraversalEngine traversalEngine) {
        this.storageAdapter = storageAdapter;
        this.traversalEngine = traversalEngine;
        this.state.set(PluginState.DISCOVERED);
        log.info("Knowledge Graph plugin discovered");
    }
    
    // ========================================================================
    // Plugin Lifecycle
    // ========================================================================
    
    @Override
    public PluginMetadata metadata() {
        return PluginMetadata.builder()
                .id(PLUGIN_ID)
                .name(PLUGIN_NAME)
                .version(PLUGIN_VERSION)
                .description("Graph database capabilities for Data-Cloud platform")
                .vendor("Ghatana Platform Team")
                .type(PluginType.PROCESSING)
                .capabilities(Set.of(
                        "node-operations",
                        "edge-operations",
                        "graph-queries",
                        "graph-traversal",
                        "batch-operations",
                        "multi-tenant"
                ))
                .build();
    }
    
    @Override
    public PluginState getState() {
        return state.get();
    }
    
    @Override
    public Promise<Void> initialize(PluginContext context) {
        log.info("Initializing Knowledge Graph plugin");
        
        try {
            // Initialize storage adapter
            storageAdapter.initialize(context.getConfigMap());
            
            // Initialize traversal engine
            traversalEngine.initialize(context.getConfigMap());
            
            state.set(PluginState.INITIALIZED);
            log.info("Knowledge Graph plugin initialized successfully");
            return Promise.of((Void) null);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<Void> start() {
        log.info("Starting Knowledge Graph plugin");
        
        try {
            if (state.get() != PluginState.INITIALIZED) {
                throw new IllegalStateException("Plugin must be initialized before starting");
            }
            
            // Start storage adapter
            storageAdapter.start();
            
            // Start traversal engine
            traversalEngine.start();
            
            state.set(PluginState.RUNNING);
            log.info("Knowledge Graph plugin started successfully");
            return Promise.of((Void) null);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<Void> stop() {
        log.info("Stopping Knowledge Graph plugin");
        
        try {
            // Stop traversal engine
            traversalEngine.stop();
            
            // Stop storage adapter
            storageAdapter.stop();
            
            state.set(PluginState.STOPPED);
            log.info("Knowledge Graph plugin stopped successfully");
            return Promise.of((Void) null);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<Void> shutdown() {
        log.info("Shutting down Knowledge Graph plugin");
        
        try {
            // Shutdown traversal engine
            traversalEngine.shutdown();
            
            // Shutdown storage adapter
            storageAdapter.shutdown();
            
            state.set(PluginState.STOPPED);
            log.info("Knowledge Graph plugin shutdown successfully");
            return Promise.of((Void) null);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<HealthStatus> healthCheck() {
        try {
            if (state.get() != PluginState.RUNNING) {
                return Promise.of(new HealthStatus(false, "Plugin is not running", Map.of()));
            }
            
            // Check storage adapter health
            boolean storageHealthy = storageAdapter.isHealthy();
            
            // Check traversal engine health
            boolean traversalHealthy = traversalEngine.isHealthy();
            
            if (storageHealthy && traversalHealthy) {
                return Promise.of(new HealthStatus(true, "All components healthy", Map.of(
                    "storage", "healthy",
                    "traversal", "healthy",
                    "state", state.get().name()
                )));
            } else {
                return Promise.of(new HealthStatus(false, "Plugin running but some components unhealthy", Map.of(
                    "storage", storageHealthy ? "healthy" : "unhealthy",
                    "traversal", traversalHealthy ? "healthy" : "unhealthy",
                    "state", state.get().name()
                )));
            }
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    // ========================================================================
    // Node Operations
    // ========================================================================
    
    @Override
    public Promise<GraphNode> createNode(GraphNode node) {
        log.debug("Creating node: id={}, type={}, tenantId={}", node.getId(), node.getType(), node.getTenantId());
        
        try {
            ensureRunning();
            node.validate();
            return Promise.of(storageAdapter.createNode(node));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<GraphNode> getNode(String nodeId, String tenantId) {
        log.debug("Getting node: id={}, tenantId={}", nodeId, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            return Promise.of(storageAdapter.getNode(nodeId, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<GraphNode> updateNode(GraphNode node) {
        log.debug("Updating node: id={}, type={}, tenantId={}", node.getId(), node.getType(), node.getTenantId());
        
        try {
            ensureRunning();
            node.validate();
            return Promise.of(storageAdapter.updateNode(node));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<Boolean> deleteNode(String nodeId, String tenantId) {
        log.debug("Deleting node: id={}, tenantId={}", nodeId, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            
            // Delete all connected edges first
            List<GraphEdge> edges = storageAdapter.getNodeEdges(nodeId, tenantId);
            for (GraphEdge edge : edges) {
                storageAdapter.deleteEdge(edge.getId(), tenantId);
            }
            
            // Delete the node
            return Promise.of(storageAdapter.deleteNode(nodeId, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<List<GraphNode>> queryNodes(GraphQuery query) {
        log.debug("Querying nodes: tenantId={}, types={}", query.getTenantId(), query.getNodeTypes());
        
        try {
            ensureRunning();
            query.validate();
            return Promise.of(storageAdapter.queryNodes(query));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    // ========================================================================
    // Edge Operations
    // ========================================================================
    
    @Override
    public Promise<GraphEdge> createEdge(GraphEdge edge) {
        log.debug("Creating edge: id={}, source={}, target={}, type={}, tenantId={}", 
                edge.getId(), edge.getSourceNodeId(), edge.getTargetNodeId(), 
                edge.getRelationshipType(), edge.getTenantId());
        
        try {
            ensureRunning();
            edge.validate();
            
            // Verify both nodes exist
            GraphNode sourceNode = storageAdapter.getNode(edge.getSourceNodeId(), edge.getTenantId());
            if (sourceNode == null) {
                throw new IllegalArgumentException("Source node not found: " + edge.getSourceNodeId());
            }
            
            GraphNode targetNode = storageAdapter.getNode(edge.getTargetNodeId(), edge.getTenantId());
            if (targetNode == null) {
                throw new IllegalArgumentException("Target node not found: " + edge.getTargetNodeId());
            }
            
            return Promise.of(storageAdapter.createEdge(edge));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<GraphEdge> getEdge(String edgeId, String tenantId) {
        log.debug("Getting edge: id={}, tenantId={}", edgeId, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            return Promise.of(storageAdapter.getEdge(edgeId, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<GraphEdge> updateEdge(GraphEdge edge) {
        log.debug("Updating edge: id={}, tenantId={}", edge.getId(), edge.getTenantId());
        
        try {
            ensureRunning();
            edge.validate();
            return Promise.of(storageAdapter.updateEdge(edge));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<Boolean> deleteEdge(String edgeId, String tenantId) {
        log.debug("Deleting edge: id={}, tenantId={}", edgeId, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            return Promise.of(storageAdapter.deleteEdge(edgeId, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<List<GraphEdge>> queryEdges(GraphQuery query) {
        log.debug("Querying edges: tenantId={}, types={}", query.getTenantId(), query.getRelationshipTypes());
        
        try {
            ensureRunning();
            query.validate();
            return Promise.of(storageAdapter.queryEdges(query));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<List<GraphEdge>> getNodeEdges(String nodeId, String tenantId) {
        log.debug("Getting node edges: nodeId={}, tenantId={}", nodeId, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            return Promise.of(storageAdapter.getNodeEdges(nodeId, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<List<GraphEdge>> getOutgoingEdges(String nodeId, String tenantId) {
        log.debug("Getting outgoing edges: nodeId={}, tenantId={}", nodeId, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            return Promise.of(storageAdapter.getOutgoingEdges(nodeId, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<List<GraphEdge>> getIncomingEdges(String nodeId, String tenantId) {
        log.debug("Getting incoming edges: nodeId={}, tenantId={}", nodeId, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            return Promise.of(storageAdapter.getIncomingEdges(nodeId, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    // ========================================================================
    // Traversal Operations
    // ========================================================================
    
    @Override
    public Promise<List<GraphNode>> getNeighbors(String nodeId, int depth, String tenantId) {
        log.debug("Getting neighbors: nodeId={}, depth={}, tenantId={}", nodeId, depth, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            
            if (depth <= 0) {
                throw new IllegalArgumentException("Depth must be positive");
            }
            
            return Promise.of(traversalEngine.getNeighbors(nodeId, depth, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<List<GraphNode>> findShortestPath(String sourceNodeId, String targetNodeId, String tenantId) {
        log.debug("Finding shortest path: source={}, target={}, tenantId={}", sourceNodeId, targetNodeId, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            return Promise.of(traversalEngine.findShortestPath(sourceNodeId, targetNodeId, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<List<List<GraphNode>>> findAllPaths(String sourceNodeId, String targetNodeId, int maxLength, String tenantId) {
        log.debug("Finding all paths: source={}, target={}, maxLength={}, tenantId={}", 
                sourceNodeId, targetNodeId, maxLength, tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            
            if (maxLength <= 0) {
                throw new IllegalArgumentException("Max length must be positive");
            }
            
            return Promise.of(traversalEngine.findAllPaths(sourceNodeId, targetNodeId, maxLength, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    // ========================================================================
    // Batch Operations
    // ========================================================================
    
    @Override
    public Promise<List<GraphNode>> batchCreateNodes(List<GraphNode> nodes) {
        log.debug("Batch creating {} nodes", nodes.size());
        
        try {
            ensureRunning();
            
            // Validate all nodes
            for (GraphNode node : nodes) {
                node.validate();
            }
            
            return Promise.of(storageAdapter.batchCreateNodes(nodes));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<List<GraphEdge>> batchCreateEdges(List<GraphEdge> edges) {
        log.debug("Batch creating {} edges", edges.size());
        
        try {
            ensureRunning();
            
            // Validate all edges
            for (GraphEdge edge : edges) {
                edge.validate();
            }
            
            return Promise.of(storageAdapter.batchCreateEdges(edges));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<Integer> batchDeleteNodes(List<String> nodeIds, String tenantId) {
        log.debug("Batch deleting {} nodes for tenantId={}", nodeIds.size(), tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            return Promise.of(storageAdapter.batchDeleteNodes(nodeIds, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<Integer> batchDeleteEdges(List<String> edgeIds, String tenantId) {
        log.debug("Batch deleting {} edges for tenantId={}", edgeIds.size(), tenantId);
        
        try {
            ensureRunning();
            validateTenantId(tenantId);
            return Promise.of(storageAdapter.batchDeleteEdges(edgeIds, tenantId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    // ========================================================================
    // Private Helper Methods
    // ========================================================================
    
    private void ensureRunning() {
        if (state.get() != PluginState.RUNNING) {
            throw new IllegalStateException("Plugin is not running: " + state.get());
        }
    }
    
    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("TenantId cannot be null or blank");
        }
    }
    
    private void validateConfig(Map<String, Object> config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        
        // Validate required configuration keys
        // Add specific validation as needed
    }
}
