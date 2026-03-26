package com.ghatana.datacloud.plugins.knowledgegraph.storage;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Data-Cloud based storage adapter for graph data.
 * 
 * <p>Uses Data-Cloud EntityRepository for persistent storage of graph nodes and edges.
 * Provides efficient querying, indexing, and multi-tenant isolation.
 * 
 * <p><b>Collections:</b>
 * <ul>
 *   <li>kg_nodes: Stores graph nodes</li>
 *   <li>kg_edges: Stores graph edges</li>
 * </ul>
 * 
 * <p><b>Indexes:</b>
 * <ul>
 *   <li>Node type index for efficient type-based queries</li>
 *   <li>Edge relationship type index</li>
 *   <li>Tenant ID index for multi-tenant isolation</li>
 *   <li>Source/target node indexes for edge queries</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Data-Cloud storage adapter for graph data
 * @doc.layer storage
 * @doc.pattern Adapter
 */
@Slf4j
public class DataCloudGraphStorageAdapter implements GraphStorageAdapter {
    
    private static final String NODES_COLLECTION = "kg_nodes";
    private static final String EDGES_COLLECTION = "kg_edges";
    
    private final EntityRepository entityRepository;
    private final Map<String, GraphNode> nodeCache;
    private final Map<String, GraphEdge> edgeCache;
    private volatile boolean initialized = false;
    private volatile boolean running = false;
    
    public DataCloudGraphStorageAdapter(EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
        this.nodeCache = new ConcurrentHashMap<>();
        this.edgeCache = new ConcurrentHashMap<>();
        log.info("DataCloudGraphStorageAdapter created");
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        log.info("Initializing DataCloudGraphStorageAdapter with config: {}", config);
        
        // Create collections if they don't exist
        createCollectionsIfNeeded();
        
        // Create indexes for efficient queries
        createIndexes();
        
        initialized = true;
        log.info("DataCloudGraphStorageAdapter initialized successfully");
    }
    
    @Override
    public void start() {
        if (!initialized) {
            throw new IllegalStateException("Storage adapter must be initialized before starting");
        }
        
        running = true;
        log.info("DataCloudGraphStorageAdapter started");
    }
    
    @Override
    public void stop() {
        running = false;
        
        // Clear caches
        nodeCache.clear();
        edgeCache.clear();
        
        log.info("DataCloudGraphStorageAdapter stopped");
    }
    
    @Override
    public void shutdown() {
        stop();
        log.info("DataCloudGraphStorageAdapter shutdown");
    }
    
    @Override
    public boolean isHealthy() {
        return initialized && running;
    }
    
    // ========================================================================
    // Node Operations
    // ========================================================================
    
    @Override
    public GraphNode createNode(GraphNode node) {
        ensureRunning();
        
        log.debug("Creating node: id={}, type={}", node.getId(), node.getType());
        
        // Persist to EntityRepository
        Entity entity = nodeToEntity(node);
        entityRepository.save(node.getTenantId(), entity);
        
        // Update write-through cache
        String cacheKey = getCacheKey(node.getId(), node.getTenantId());
        nodeCache.put(cacheKey, node);
        
        log.debug("Node created successfully: id={}", node.getId());
        return node;
    }
    
    @Override
    public GraphNode getNode(String nodeId, String tenantId) {
        ensureRunning();
        
        log.debug("Getting node: id={}, tenantId={}", nodeId, tenantId);
        
        // Check cache first
        String cacheKey = getCacheKey(nodeId, tenantId);
        GraphNode cached = nodeCache.get(cacheKey);
        if (cached != null) {
            log.debug("Node found in cache: id={}", nodeId);
            return cached;
        }
        
        // Retrieve from EntityRepository
        UUID entityId = toUUID(nodeId);
        Optional<Entity> maybeEntity = entityRepository.findById(tenantId, NODES_COLLECTION, entityId)
                .getResult();
        
        if (maybeEntity != null && maybeEntity.isPresent()) {
            GraphNode node = entityToNode(maybeEntity.get());
            nodeCache.put(cacheKey, node);
            log.debug("Node found in storage: id={}", nodeId);
            return node;
        }
        
        log.debug("Node not found: id={}", nodeId);
        return null;
    }
    
    @Override
    public GraphNode updateNode(GraphNode node) {
        ensureRunning();
        
        log.debug("Updating node: id={}, type={}", node.getId(), node.getType());
        
        GraphNode updatedNode = node.withUpdated();
        
        // Persist updated node
        Entity entity = nodeToEntity(updatedNode);
        entityRepository.save(updatedNode.getTenantId(), entity);
        
        // Update cache
        String cacheKey = getCacheKey(node.getId(), node.getTenantId());
        nodeCache.put(cacheKey, updatedNode);
        
        log.debug("Node updated successfully: id={}", node.getId());
        return updatedNode;
    }
    
    @Override
    public boolean deleteNode(String nodeId, String tenantId) {
        ensureRunning();
        
        log.debug("Deleting node: id={}, tenantId={}", nodeId, tenantId);
        
        // Delete from EntityRepository
        UUID entityId = toUUID(nodeId);
        entityRepository.delete(tenantId, NODES_COLLECTION, entityId);
        
        // Remove from cache
        String cacheKey = getCacheKey(nodeId, tenantId);
        GraphNode removed = nodeCache.remove(cacheKey);
        
        // Also delete all edges connected to this node
        List<GraphEdge> connectedEdges = getNodeEdges(nodeId, tenantId);
        for (GraphEdge edge : connectedEdges) {
            deleteEdge(edge.getId(), tenantId);
        }
        
        boolean deleted = removed != null || connectedEdges.size() > 0;
        log.debug("Node deletion result: id={}, deleted={}, edgesRemoved={}", 
                nodeId, deleted, connectedEdges.size());
        return true; // Entity deletion is idempotent via soft-delete
    }
    
    @Override
    public List<GraphNode> queryNodes(GraphQuery query) {
        ensureRunning();
        
        log.debug("Querying nodes: tenantId={}, types={}", query.getTenantId(), query.getNodeTypes());
        
        // Build filter from query
        Map<String, Object> filter = new LinkedHashMap<>();
        if (query.getNodeTypes() != null && !query.getNodeTypes().isEmpty()) {
            filter.put("type", query.getNodeTypes().iterator().next());
        }
        if (query.hasPropertyFilters()) {
            filter.putAll(query.getPropertyFilters());
        }
        
        // Query via EntityRepository
        List<Entity> entities = entityRepository.findAll(
                query.getTenantId(),
                NODES_COLLECTION,
                filter,
                null,
                query.getEffectiveOffset(),
                query.getEffectiveLimit()
        ).getResult();
        
        List<GraphNode> results;
        if (entities != null && !entities.isEmpty()) {
            results = entities.stream()
                    .map(this::entityToNode)
                    .filter(node -> matchesQuery(node, query))
                    .collect(Collectors.toList());
        } else {
            // Fallback: filter from cache (e.g., if repository is temporarily unavailable)
            results = nodeCache.values().stream()
                    .filter(node -> matchesQuery(node, query))
                    .skip(query.getEffectiveOffset())
                    .limit(query.getEffectiveLimit())
                    .collect(Collectors.toList());
        }
        
        log.debug("Query returned {} nodes", results.size());
        return results;
    }
    
    @Override
    public List<GraphNode> batchCreateNodes(List<GraphNode> nodes) {
        ensureRunning();
        
        log.debug("Batch creating {} nodes", nodes.size());
        
        // Batch persist using saveAll grouped by tenantId
        Map<String, List<GraphNode>> byTenant = nodes.stream()
                .collect(Collectors.groupingBy(GraphNode::getTenantId));
        
        List<GraphNode> created = new ArrayList<>();
        for (Map.Entry<String, List<GraphNode>> entry : byTenant.entrySet()) {
            List<Entity> entities = entry.getValue().stream()
                    .map(this::nodeToEntity)
                    .collect(Collectors.toList());
            entityRepository.saveAll(entry.getKey(), entities);
            
            for (GraphNode node : entry.getValue()) {
                String cacheKey = getCacheKey(node.getId(), node.getTenantId());
                nodeCache.put(cacheKey, node);
                created.add(node);
            }
        }
        
        log.debug("Batch created {} nodes successfully", created.size());
        return created;
    }
    
    @Override
    public int batchDeleteNodes(List<String> nodeIds, String tenantId) {
        ensureRunning();
        
        log.debug("Batch deleting {} nodes for tenantId={}", nodeIds.size(), tenantId);
        
        int deleted = 0;
        for (String nodeId : nodeIds) {
            if (deleteNode(nodeId, tenantId)) {
                deleted++;
            }
        }
        
        log.debug("Batch deleted {} nodes", deleted);
        return deleted;
    }
    
    // ========================================================================
    // Edge Operations
    // ========================================================================
    
    @Override
    public GraphEdge createEdge(GraphEdge edge) {
        ensureRunning();
        
        log.debug("Creating edge: id={}, source={}, target={}", 
                edge.getId(), edge.getSourceNodeId(), edge.getTargetNodeId());
        
        // Persist to EntityRepository
        Entity entity = edgeToEntity(edge);
        entityRepository.save(edge.getTenantId(), entity);
        
        // Update write-through cache
        String cacheKey = getCacheKey(edge.getId(), edge.getTenantId());
        edgeCache.put(cacheKey, edge);
        
        log.debug("Edge created successfully: id={}", edge.getId());
        return edge;
    }
    
    @Override
    public GraphEdge getEdge(String edgeId, String tenantId) {
        ensureRunning();
        
        log.debug("Getting edge: id={}, tenantId={}", edgeId, tenantId);
        
        // Check cache first
        String cacheKey = getCacheKey(edgeId, tenantId);
        GraphEdge cached = edgeCache.get(cacheKey);
        if (cached != null) {
            log.debug("Edge found in cache: id={}", edgeId);
            return cached;
        }
        
        // Retrieve from EntityRepository
        UUID entityId = toUUID(edgeId);
        Optional<Entity> maybeEntity = entityRepository.findById(tenantId, EDGES_COLLECTION, entityId)
                .getResult();
        
        if (maybeEntity != null && maybeEntity.isPresent()) {
            GraphEdge edge = entityToEdge(maybeEntity.get());
            edgeCache.put(cacheKey, edge);
            log.debug("Edge found in storage: id={}", edgeId);
            return edge;
        }
        
        log.debug("Edge not found: id={}", edgeId);
        return null;
    }
    
    @Override
    public GraphEdge updateEdge(GraphEdge edge) {
        ensureRunning();
        
        log.debug("Updating edge: id={}", edge.getId());
        
        GraphEdge updatedEdge = edge.withUpdated();
        
        // Persist
        Entity entity = edgeToEntity(updatedEdge);
        entityRepository.save(updatedEdge.getTenantId(), entity);
        
        String cacheKey = getCacheKey(edge.getId(), edge.getTenantId());
        edgeCache.put(cacheKey, updatedEdge);
        
        log.debug("Edge updated successfully: id={}", edge.getId());
        return updatedEdge;
    }
    
    @Override
    public boolean deleteEdge(String edgeId, String tenantId) {
        ensureRunning();
        
        log.debug("Deleting edge: id={}, tenantId={}", edgeId, tenantId);
        
        // Delete from EntityRepository
        UUID entityId = toUUID(edgeId);
        entityRepository.delete(tenantId, EDGES_COLLECTION, entityId);
        
        // Remove from cache
        String cacheKey = getCacheKey(edgeId, tenantId);
        edgeCache.remove(cacheKey);
        
        log.debug("Edge deleted: id={}", edgeId);
        return true;
    }
    
    @Override
    public List<GraphEdge> queryEdges(GraphQuery query) {
        ensureRunning();
        
        log.debug("Querying edges: tenantId={}, types={}", query.getTenantId(), query.getRelationshipTypes());
        
        // Build filter for edge query
        Map<String, Object> filter = new LinkedHashMap<>();
        if (query.getRelationshipTypes() != null && !query.getRelationshipTypes().isEmpty()) {
            filter.put("relationshipType", query.getRelationshipTypes().iterator().next());
        }
        if (query.getSourceNodeId() != null) {
            filter.put("sourceNodeId", query.getSourceNodeId());
        }
        if (query.getTargetNodeId() != null) {
            filter.put("targetNodeId", query.getTargetNodeId());
        }
        
        List<Entity> entities = entityRepository.findAll(
                query.getTenantId(),
                EDGES_COLLECTION,
                filter,
                null,
                query.getEffectiveOffset(),
                query.getEffectiveLimit()
        ).getResult();
        
        List<GraphEdge> results;
        if (entities != null && !entities.isEmpty()) {
            results = entities.stream()
                    .map(this::entityToEdge)
                    .filter(edge -> matchesQuery(edge, query))
                    .collect(Collectors.toList());
        } else {
            results = edgeCache.values().stream()
                    .filter(edge -> matchesQuery(edge, query))
                    .skip(query.getEffectiveOffset())
                    .limit(query.getEffectiveLimit())
                    .collect(Collectors.toList());
        }
        
        log.debug("Query returned {} edges", results.size());
        return results;
    }
    
    @Override
    public List<GraphEdge> getNodeEdges(String nodeId, String tenantId) {
        ensureRunning();
        
        log.debug("Getting node edges: nodeId={}, tenantId={}", nodeId, tenantId);
        
        // Query edges where sourceNodeId or targetNodeId matches
        Map<String, Object> sourceFilter = Map.of("sourceNodeId", nodeId);
        Map<String, Object> targetFilter = Map.of("targetNodeId", nodeId);
        
        List<Entity> sourceEntities = entityRepository.findAll(
                tenantId, EDGES_COLLECTION, sourceFilter, null, 0, 10000).getResult();
        List<Entity> targetEntities = entityRepository.findAll(
                tenantId, EDGES_COLLECTION, targetFilter, null, 0, 10000).getResult();
        
        Set<String> seen = new HashSet<>();
        List<GraphEdge> results = new ArrayList<>();
        
        if (sourceEntities != null) {
            for (Entity e : sourceEntities) {
                GraphEdge edge = entityToEdge(e);
                if (seen.add(edge.getId())) {
                    results.add(edge);
                }
            }
        }
        if (targetEntities != null) {
            for (Entity e : targetEntities) {
                GraphEdge edge = entityToEdge(e);
                if (seen.add(edge.getId())) {
                    results.add(edge);
                }
            }
        }
        
        // Merge with cache for any not yet persisted
        for (GraphEdge cached : edgeCache.values()) {
            if (cached.getTenantId().equals(tenantId) && cached.connects(nodeId)
                    && seen.add(cached.getId())) {
                results.add(cached);
            }
        }
        
        log.debug("Found {} edges for node {}", results.size(), nodeId);
        return results;
    }
    
    @Override
    public List<GraphEdge> getOutgoingEdges(String nodeId, String tenantId) {
        ensureRunning();
        
        log.debug("Getting outgoing edges: nodeId={}, tenantId={}", nodeId, tenantId);
        
        Map<String, Object> filter = Map.of("sourceNodeId", nodeId);
        List<Entity> entities = entityRepository.findAll(
                tenantId, EDGES_COLLECTION, filter, null, 0, 10000).getResult();
        
        List<GraphEdge> results = new ArrayList<>();
        if (entities != null) {
            results = entities.stream()
                    .map(this::entityToEdge)
                    .collect(Collectors.toList());
        }
        
        log.debug("Found {} outgoing edges for node {}", results.size(), nodeId);
        return results;
    }
    
    @Override
    public List<GraphEdge> getIncomingEdges(String nodeId, String tenantId) {
        ensureRunning();
        
        log.debug("Getting incoming edges: nodeId={}, tenantId={}", nodeId, tenantId);
        
        Map<String, Object> filter = Map.of("targetNodeId", nodeId);
        List<Entity> entities = entityRepository.findAll(
                tenantId, EDGES_COLLECTION, filter, null, 0, 10000).getResult();
        
        List<GraphEdge> results = new ArrayList<>();
        if (entities != null) {
            results = entities.stream()
                    .map(this::entityToEdge)
                    .collect(Collectors.toList());
        }
        
        log.debug("Found {} incoming edges for node {}", results.size(), nodeId);
        return results;
    }
    
    @Override
    public List<GraphEdge> batchCreateEdges(List<GraphEdge> edges) {
        ensureRunning();
        
        log.debug("Batch creating {} edges", edges.size());
        
        Map<String, List<GraphEdge>> byTenant = edges.stream()
                .collect(Collectors.groupingBy(GraphEdge::getTenantId));
        
        List<GraphEdge> created = new ArrayList<>();
        for (Map.Entry<String, List<GraphEdge>> entry : byTenant.entrySet()) {
            List<Entity> entities = entry.getValue().stream()
                    .map(this::edgeToEntity)
                    .collect(Collectors.toList());
            entityRepository.saveAll(entry.getKey(), entities);
            
            for (GraphEdge edge : entry.getValue()) {
                String cacheKey = getCacheKey(edge.getId(), edge.getTenantId());
                edgeCache.put(cacheKey, edge);
                created.add(edge);
            }
        }
        
        log.debug("Batch created {} edges successfully", created.size());
        return created;
    }
    
    @Override
    public int batchDeleteEdges(List<String> edgeIds, String tenantId) {
        ensureRunning();
        
        log.debug("Batch deleting {} edges for tenantId={}", edgeIds.size(), tenantId);
        
        int deleted = 0;
        for (String edgeId : edgeIds) {
            if (deleteEdge(edgeId, tenantId)) {
                deleted++;
            }
        }
        
        log.debug("Batch deleted {} edges", deleted);
        return deleted;
    }
    
    // ========================================================================
    // Private Helper Methods
    // ========================================================================
    
    private void ensureRunning() {
        if (!running) {
            throw new IllegalStateException("Storage adapter is not running");
        }
    }
    
    private String getCacheKey(String id, String tenantId) {
        return tenantId + ":" + id;
    }
    
    private boolean matchesQuery(GraphNode node, GraphQuery query) {
        // Check tenant
        if (!node.getTenantId().equals(query.getTenantId())) {
            return false;
        }
        
        // Check node types
        if (query.getNodeTypes() != null && !query.getNodeTypes().isEmpty()) {
            if (!query.getNodeTypes().contains(node.getType())) {
                return false;
            }
        }
        
        // Check labels
        if (query.getLabels() != null && !query.getLabels().isEmpty()) {
            boolean hasAnyLabel = query.getLabels().stream()
                    .anyMatch(node::hasLabel);
            if (!hasAnyLabel) {
                return false;
            }
        }
        
        // Check property filters
        if (query.hasPropertyFilters()) {
            for (Map.Entry<String, Object> filter : query.getPropertyFilters().entrySet()) {
                Object nodeValue = node.getProperty(filter.getKey());
                if (nodeValue == null || !nodeValue.equals(filter.getValue())) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private boolean matchesQuery(GraphEdge edge, GraphQuery query) {
        // Check tenant
        if (!edge.getTenantId().equals(query.getTenantId())) {
            return false;
        }
        
        // Check relationship types
        if (query.getRelationshipTypes() != null && !query.getRelationshipTypes().isEmpty()) {
            if (!query.getRelationshipTypes().contains(edge.getRelationshipType())) {
                return false;
            }
        }
        
        // Check source node
        if (query.getSourceNodeId() != null) {
            if (!edge.getSourceNodeId().equals(query.getSourceNodeId())) {
                return false;
            }
        }
        
        // Check target node
        if (query.getTargetNodeId() != null) {
            if (!edge.getTargetNodeId().equals(query.getTargetNodeId())) {
                return false;
            }
        }
        
        // Check property filters
        if (query.hasPropertyFilters()) {
            for (Map.Entry<String, Object> filter : query.getPropertyFilters().entrySet()) {
                Object edgeValue = edge.getProperty(filter.getKey());
                if (edgeValue == null || !edgeValue.equals(filter.getValue())) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private void createCollectionsIfNeeded() {
        // Verify collections exist by attempting a count; EntityRepository implementations
        // create tables/collections on first access or via schema migration.
        try {
            entityRepository.count("_system", NODES_COLLECTION);
            entityRepository.count("_system", EDGES_COLLECTION);
            log.debug("Collections verified: {}, {}", NODES_COLLECTION, EDGES_COLLECTION);
        } catch (Exception e) {
            log.warn("Could not verify collections - they will be created on first write: {}", e.getMessage());
        }
    }
    
    private void createIndexes() {
        // Indexes are managed by the EntityRepository infrastructure layer (JPA/Flyway/Liquibase).
        // Log intent for observability; actual DDL is handled by schema migration scripts.
        log.info("Graph storage indexes expected: " 
                + "{}.type, {}.sourceNodeId, {}.targetNodeId, {}.relationshipType",
                NODES_COLLECTION, EDGES_COLLECTION, EDGES_COLLECTION, EDGES_COLLECTION);
    }

    // ========================================================================
    // Entity <-> Graph Model Conversion
    // ========================================================================

    /**
     * Convert GraphNode to Entity for persistence.
     */
    @SuppressWarnings("unchecked")
    private Entity nodeToEntity(GraphNode node) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", node.getType());
        data.put("labels", node.getLabels() != null ? new ArrayList<>(node.getLabels()) : List.of());
        if (node.getProperties() != null) {
            data.putAll(node.getProperties());
        }
        data.put("createdAt", node.getCreatedAt() != null ? node.getCreatedAt().toString() : Instant.now().toString());
        data.put("updatedAt", node.getUpdatedAt() != null ? node.getUpdatedAt().toString() : Instant.now().toString());
        data.put("version", node.getVersion());

        return Entity.builder()
                .id(toUUID(node.getId()))
                .tenantId(node.getTenantId())
                .collectionName(NODES_COLLECTION)
                .data(data)
                .build();
    }

    /**
     * Convert Entity back to GraphNode.
     */
    @SuppressWarnings("unchecked")
    private GraphNode entityToNode(Entity entity) {
        Map<String, Object> data = entity.getData();
        if (data == null) {
            data = Map.of();
        }

        // Extract graph-specific fields from data
        String type = (String) data.getOrDefault("type", "UNKNOWN");
        Collection<String> labelCol = (Collection<String>) data.get("labels");
        Set<String> labels = labelCol != null ? new HashSet<>(labelCol) : Set.of();
        Long version = data.get("version") instanceof Number
                ? ((Number) data.get("version")).longValue() : 1L;
        Instant createdAt = data.containsKey("createdAt")
                ? Instant.parse((String) data.get("createdAt")) : Instant.now();
        Instant updatedAt = data.containsKey("updatedAt")
                ? Instant.parse((String) data.get("updatedAt")) : Instant.now();

        // Properties = data minus graph metadata fields
        Map<String, Object> properties = new LinkedHashMap<>(data);
        properties.remove("type");
        properties.remove("labels");
        properties.remove("createdAt");
        properties.remove("updatedAt");
        properties.remove("version");

        return GraphNode.builder()
                .id(entity.getId() != null ? entity.getId().toString() : null)
                .type(type)
                .properties(properties)
                .labels(labels)
                .tenantId(entity.getTenantId())
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version)
                .build();
    }

    /**
     * Convert GraphEdge to Entity for persistence.
     */
    private Entity edgeToEntity(GraphEdge edge) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceNodeId", edge.getSourceNodeId());
        data.put("targetNodeId", edge.getTargetNodeId());
        data.put("relationshipType", edge.getRelationshipType());
        if (edge.getProperties() != null) {
            data.putAll(edge.getProperties());
        }
        data.put("createdAt", edge.getCreatedAt() != null ? edge.getCreatedAt().toString() : Instant.now().toString());
        data.put("updatedAt", edge.getUpdatedAt() != null ? edge.getUpdatedAt().toString() : Instant.now().toString());
        data.put("version", edge.getVersion());
        data.put("weight", edge.getProperties().getOrDefault("weight", 1.0));
        data.put("directed", edge.getProperties().getOrDefault("directed", true));

        return Entity.builder()
                .id(toUUID(edge.getId()))
                .tenantId(edge.getTenantId())
                .collectionName(EDGES_COLLECTION)
                .data(data)
                .build();
    }

    /**
     * Convert Entity back to GraphEdge.
     */
    private GraphEdge entityToEdge(Entity entity) {
        Map<String, Object> data = entity.getData();
        if (data == null) {
            data = Map.of();
        }

        String sourceNodeId = (String) data.getOrDefault("sourceNodeId", "");
        String targetNodeId = (String) data.getOrDefault("targetNodeId", "");
        String relationshipType = (String) data.getOrDefault("relationshipType", "RELATED");
        double weight = data.get("weight") instanceof Number
                ? ((Number) data.get("weight")).doubleValue() : 1.0;
        boolean directed = data.get("directed") instanceof Boolean
                ? (Boolean) data.get("directed") : true;
        Long version = data.get("version") instanceof Number
                ? ((Number) data.get("version")).longValue() : 1L;
        Instant createdAt = data.containsKey("createdAt")
                ? Instant.parse((String) data.get("createdAt")) : Instant.now();
        Instant updatedAt = data.containsKey("updatedAt")
                ? Instant.parse((String) data.get("updatedAt")) : Instant.now();

        // Properties = data minus edge metadata fields
        Map<String, Object> properties = new LinkedHashMap<>(data);
        for (String key : List.of("sourceNodeId", "targetNodeId", "relationshipType",
                "weight", "directed", "createdAt", "updatedAt", "version")) {
            properties.remove(key);
        }

        // Store weight and directed as properties since GraphEdge uses a flat properties map
        properties.put("weight", weight);
        properties.put("directed", directed);

        return GraphEdge.builder()
                .id(entity.getId() != null ? entity.getId().toString() : null)
                .sourceNodeId(sourceNodeId)
                .targetNodeId(targetNodeId)
                .relationshipType(relationshipType)
                .properties(properties)
                .tenantId(entity.getTenantId())
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version)
                .build();
    }

    /**
     * Convert string ID to UUID deterministically.
     */
    private UUID toUUID(String id) {
        if (id == null) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(id.getBytes());
        }
    }
}
