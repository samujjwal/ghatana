package com.ghatana.yappc.knowledge;

import com.ghatana.core.activej.promise.PromiseUtils;
import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
import com.ghatana.yappc.knowledge.embedding.KGEmbeddingService;
import com.ghatana.yappc.knowledge.persistence.KGEdgeRepository;
import com.ghatana.yappc.knowledge.persistence.KGNodeRepository;
import com.ghatana.yappc.knowledge.query.KGQueryService;
import com.ghatana.yappc.knowledge.query.KGSemanticSearchService;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCImpactAnalysis;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main service facade for YAPPC Knowledge Graph functionality.
 * 
 * <p>Wraps the Data-Cloud Knowledge Graph plugin with YAPPC-specific
 * business logic, validation, and integration patterns.
 * 
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>YAPPC-specific node and edge creation with validation</li>
 *   <li>Code dependency tracking and analysis</li>
 *   <li>Architecture relationship mapping</li>
 *   <li>Impact analysis for changes</li>
 *   <li>Workspace and project graph management</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose YAPPC knowledge graph service facade
 * @doc.layer service
 * @doc.pattern Facade, Adapter
 */
@Slf4j
public class YAPPCGraphService {
    
    private final KnowledgeGraphPlugin graphPlugin;
    private final YAPPCGraphMapper mapper;
    private final YAPPCGraphValidator validator;
        private final @Nullable KGNodeRepository nodeRepository;
        private final @Nullable KGEdgeRepository edgeRepository;
        private final @Nullable KGEmbeddingService embeddingService;
        private final @Nullable KGSemanticSearchService semanticSearchService;
        private final @Nullable KGQueryService queryService;
    
    public YAPPCGraphService(
            KnowledgeGraphPlugin graphPlugin,
            YAPPCGraphMapper mapper,
            YAPPCGraphValidator validator) {
                this(graphPlugin, mapper, validator, null, null, null, null, null);
        }

        public YAPPCGraphService(
                        @Nullable KnowledgeGraphPlugin graphPlugin,
                        YAPPCGraphMapper mapper,
                        YAPPCGraphValidator validator,
                        @Nullable KGNodeRepository nodeRepository,
                        @Nullable KGEdgeRepository edgeRepository) {
                this(graphPlugin, mapper, validator, nodeRepository, edgeRepository, null, null, null);
        }

        public YAPPCGraphService(
                        @Nullable KnowledgeGraphPlugin graphPlugin,
                        YAPPCGraphMapper mapper,
                        YAPPCGraphValidator validator,
                        @Nullable KGNodeRepository nodeRepository,
                        @Nullable KGEdgeRepository edgeRepository,
                        @Nullable KGEmbeddingService embeddingService,
                        @Nullable KGSemanticSearchService semanticSearchService) {
                this(graphPlugin, mapper, validator, nodeRepository, edgeRepository, embeddingService, semanticSearchService, null);
        }

        public YAPPCGraphService(
                        @Nullable KnowledgeGraphPlugin graphPlugin,
                        YAPPCGraphMapper mapper,
                        YAPPCGraphValidator validator,
                        @Nullable KGNodeRepository nodeRepository,
                        @Nullable KGEdgeRepository edgeRepository,
                        @Nullable KGEmbeddingService embeddingService,
                        @Nullable KGSemanticSearchService semanticSearchService,
                        @Nullable KGQueryService queryService) {
        this.graphPlugin = graphPlugin;
                this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
                this.validator = Objects.requireNonNull(validator, "validator must not be null");
                this.nodeRepository = nodeRepository;
                this.edgeRepository = edgeRepository;
                this.embeddingService = embeddingService;
                this.semanticSearchService = semanticSearchService;
                this.queryService = queryService;
        log.info("YAPPCGraphService initialized");
    }
    
    /**
     * Creates a YAPPC-specific node with validation.
     */
    public Promise<YAPPCGraphNode> createYAPPCNode(YAPPCGraphNode yappcNode) {
        log.debug("Creating YAPPC node: id={}, type={}", yappcNode.id(), yappcNode.type());

        validator.validateNode(yappcNode);

                Promise<YAPPCGraphNode> persisted = nodeRepository != null
                                ? nodeRepository.saveNode(yappcNode)
                                : Promise.of(yappcNode);

                Promise<YAPPCGraphNode> published = graphPlugin == null
                                ? persisted
                                : persisted.then(savedNode -> {
                                        GraphNode dcNode = mapper.toDataCloudNode(savedNode);
                                        return PromiseUtils.fromCompletableFuture(
                                                        graphPlugin.createNode(dcNode).toCompletableFuture()
                                        ).map(mapper::fromDataCloudNode);
                                });

                if (embeddingService == null) {
                        return published;
                }

                return published.then(savedNode -> embeddingService.indexNode(savedNode).map(ignored -> savedNode));
    }
    
    /**
     * Finds code dependencies for a component.
     */
    public Promise<List<YAPPCGraphEdge>> findCodeDependencies(String componentId, String tenantId) {
        log.debug("Finding code dependencies for component: {}", componentId);

                if (edgeRepository != null) {
                        return edgeRepository.findEdgesFromSource(
                                        componentId,
                                        tenantId,
                                        Set.of("DEPENDS_ON", "IMPORTS", "EXTENDS", "IMPLEMENTS"));
                }
        
        GraphQuery query = GraphQuery.builder()
                .sourceNodeId(componentId)
                .relationshipTypes(Set.of("DEPENDS_ON", "IMPORTS", "EXTENDS", "IMPLEMENTS"))
                .tenantId(tenantId)
                .build();
        
        return graphPlugin.queryEdges(query)
                .map(edges -> edges.stream()
                        .map(mapper::fromDataCloudEdge)
                        .collect(Collectors.toList()));
    }
    
    /**
     * Analyzes the impact of changing a component.
     */
    public Promise<YAPPCImpactAnalysis> analyzeChangeImpact(String componentId, String tenantId) {
        log.debug("Analyzing change impact for component: {}", componentId);

        if (queryService != null) {
            return queryService.traverse(componentId, 3, tenantId)
                    .map(affectedNodes -> new YAPPCImpactAnalysis(
                            componentId,
                            affectedNodes,
                            calculateImpactScore(affectedNodes),
                            generateImpactRecommendations(affectedNodes)
                    ));
        }
        
        return graphPlugin.getNeighbors(componentId, 5, tenantId)
                .map(neighbors -> {
                    List<YAPPCGraphNode> affectedNodes = neighbors.stream()
                            .map(mapper::fromDataCloudNode)
                            .collect(Collectors.toList());
                    
                    return new YAPPCImpactAnalysis(
                            componentId,
                            affectedNodes,
                            calculateImpactScore(affectedNodes),
                            generateImpactRecommendations(affectedNodes)
                    );
                });
    }
    
    /**
     * Finds all components of a specific type.
     */
    public Promise<List<YAPPCGraphNode>> findComponentsByType(String type, String tenantId) {
        log.debug("Finding components by type: {}", type);

                if (nodeRepository != null) {
                        return nodeRepository.findNodesByType(type, tenantId, 1000);
                }
        
        GraphQuery query = GraphQuery.builder()
                .nodeTypes(Set.of(type))
                .tenantId(tenantId)
                .limit(1000)
                .build();
        
        return graphPlugin.queryNodes(query)
                .map(nodes -> nodes.stream()
                        .map(mapper::fromDataCloudNode)
                        .collect(Collectors.toList()));
    }

        public Promise<List<KGSemanticSearchService.SemanticNodeMatch>> semanticSearch(
                        String query,
                        String tenantId,
                        int limit,
                        double minSimilarity) {
                if (semanticSearchService == null) {
                        return Promise.of(List.of());
                }
                return semanticSearchService.findSimilarNodes(query, tenantId, limit, minSimilarity);
        }
    
    /**
     * Creates a code relationship between components.
     */
    public Promise<YAPPCGraphEdge> createCodeRelationship(
            String sourceId,
            String targetId,
            String relationshipType,
            String tenantId) {
        
        log.debug("Creating code relationship: {} -> {} ({})", sourceId, targetId, relationshipType);
        
        GraphEdge edge = GraphEdge.builder()
                .id(generateEdgeId(sourceId, targetId, relationshipType))
                .sourceNodeId(sourceId)
                .targetNodeId(targetId)
                .relationshipType(relationshipType)
                .properties(Map.of("createdBy", "yappc"))
                .tenantId(tenantId)
                .build();

        YAPPCGraphEdge yappcEdge = YAPPCGraphEdge.builder()
                .id(edge.getId())
                .sourceNodeId(sourceId)
                .targetNodeId(targetId)
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.valueOf(relationshipType))
                .properties(edge.getProperties())
                .metadata(new com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata(
                        tenantId,
                        null,
                        null,
                        "yappc",
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        "1.0",
                        Map.of()))
                .build();

        Promise<YAPPCGraphEdge> persisted = edgeRepository != null
                ? edgeRepository.saveEdge(yappcEdge)
                : Promise.of(yappcEdge);

        if (graphPlugin == null) {
            return persisted;
        }

        return persisted.then(savedEdge -> graphPlugin.createEdge(edge)
                .map(mapper::fromDataCloudEdge));
    }
    
    /**
     * Finds the shortest dependency path between two components.
     */
    public Promise<List<YAPPCGraphNode>> findDependencyPath(
            String sourceId,
            String targetId,
            String tenantId) {
        
        log.debug("Finding dependency path: {} -> {}", sourceId, targetId);

        if (queryService != null) {
            return queryService.findPaths(sourceId, targetId, tenantId)
                    .map(paths -> paths.isEmpty() ? List.of() : paths.getFirst());
        }
        
        return graphPlugin.findShortestPath(sourceId, targetId, tenantId)
                .map(path -> path.stream()
                        .map(mapper::fromDataCloudNode)
                        .collect(Collectors.toList()));
    }
    
    /**
     * Gets all dependencies for a workspace.
     */
    public Promise<Map<String, List<YAPPCGraphEdge>>> getWorkspaceDependencies(
            String workspaceId,
            String tenantId) {
        
        log.debug("Getting workspace dependencies: {}", workspaceId);

        if (edgeRepository != null) {
            return edgeRepository.findEdgesForWorkspace(
                            workspaceId,
                            tenantId,
                            Set.of("DEPENDS_ON", "USES", "CALLS"))
                    .map(edges -> edges.stream()
                            .collect(Collectors.groupingBy(edge -> edge.relationshipType().name())));
        }
        
        GraphQuery query = GraphQuery.builder()
                .propertyFilters(Map.of("workspaceId", workspaceId))
                .relationshipTypes(Set.of("DEPENDS_ON", "USES", "CALLS"))
                .tenantId(tenantId)
                .limit(10000)
                .build();
        
        return graphPlugin.queryEdges(query)
                .map(edges -> {
                    Map<String, List<YAPPCGraphEdge>> grouped = edges.stream()
                            .map(mapper::fromDataCloudEdge)
                            .collect(Collectors.groupingBy(edge -> edge.relationshipType().name()));
                    return grouped;
                });
    }
    
    // Private helper methods
    
    private double calculateImpactScore(List<YAPPCGraphNode> affectedNodes) {
        if (affectedNodes.isEmpty()) {
            return 0.0;
        }
        
        // Simple impact score based on number of affected nodes and their types
        long criticalNodes = affectedNodes.stream()
                .filter(node -> node.type() == YAPPCGraphNode.YAPPCNodeType.SERVICE
                        || node.type() == YAPPCGraphNode.YAPPCNodeType.API)
                .count();
        
        return Math.min(1.0, (affectedNodes.size() * 0.1) + (criticalNodes * 0.2));
    }
    
    private List<String> generateImpactRecommendations(List<YAPPCGraphNode> affectedNodes) {
        List<String> recommendations = new java.util.ArrayList<>();
        
        if (affectedNodes.size() > 10) {
            recommendations.add("High impact change - consider breaking into smaller changes");
        }
        
        long serviceNodes = affectedNodes.stream()
                .filter(node -> node.type() == YAPPCGraphNode.YAPPCNodeType.SERVICE)
                .count();
        
        if (serviceNodes > 0) {
            recommendations.add("Update service contracts and API documentation");
        }
        
        long testNodes = affectedNodes.stream()
                .filter(node -> node.type() == YAPPCGraphNode.YAPPCNodeType.TEST)
                .count();
        
        if (testNodes > 0) {
            recommendations.add("Review and update affected tests");
        }
        
        return recommendations;
    }
    
    private String generateEdgeId(String sourceId, String targetId, String type) {
        return String.format("%s_%s_%s", sourceId, targetId, type);
    }
}
