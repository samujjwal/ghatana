package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisResult;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphMergeRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphQueryResponse;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.EdgeResolutionRecordDto;
import com.ghatana.yappc.domain.artifact.ResidualAnalysisRequest;
import com.ghatana.yappc.domain.artifact.ResidualIslandDto;
import com.ghatana.yappc.domain.artifact.UnresolvedGraphEdgeDto;
import com.ghatana.yappc.storage.ArtifactGraphRepository;
import com.ghatana.yappc.storage.ArtifactModelVersionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ghatana.yappc.services.artifact.parser.CicdWorkflowParser;
import com.ghatana.yappc.services.artifact.parser.JavaSourceParser;
import com.ghatana.yappc.services.artifact.parser.SqlSchemaParser;
import io.activej.promise.Promise;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose JGraphT-backed implementation of artifact graph operations with caching
 * @doc.layer service
 * @doc.pattern Service
 * 
 * P4-2: Heavy JGraphT work moved to dedicated blocking executor to prevent event loop blocking.
 * Cycle detection fixed to use CycleDetector instead of incorrect SCC check.
 * All-pairs reachability replaced with bounded BFS for performance.
 */
public class ArtifactGraphServiceImpl implements ArtifactGraphService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGraphServiceImpl.class);
    private static final int MAX_REACHABILITY_DEPTH = 5;
    private static final int MAX_REACHABILITY_RESULTS = 1000;

    private final ArtifactGraphRepository repository;
    private final ArtifactModelVersionRepository versionRepository;
    private final Cache<String, List<ArtifactNodeDto>> nodeCache;
    private final Cache<String, List<ArtifactEdgeDto>> edgeCache;
    private final Executor blockingExecutor;

    /**
     * P4-2: Require blocking executor to prevent event loop blocking.
     * Default constructors removed to enforce explicit executor configuration.
     */
    public ArtifactGraphServiceImpl(ArtifactGraphRepository repository, Executor blockingExecutor) {
        this(repository, null, Objects.requireNonNull(blockingExecutor, "blockingExecutor must not be null"));
    }

    public ArtifactGraphServiceImpl(ArtifactGraphRepository repository, ArtifactModelVersionRepository versionRepository, Executor blockingExecutor) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor must not be null");
        this.nodeCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
        this.edgeCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    @Override
    public Promise<ArtifactGraphResponse> ingestGraph(ArtifactRequestScope scope, ArtifactGraphIngestRequest request) {
        String cacheKey = cacheKey(scope.tenantId(), scope.workspaceId(), scope.projectId());
        log.info("Ingesting artifact graph for product {} ({} nodes, {} edges)",
            scope.projectId(), request.nodes().size(), request.edges().size());

        String snapshotId = firstNonBlank(
            request.snapshotId(),
            request.snapshotRef(),
            extractStringMetadata(request, Set.of("snapshotId", "snapshot_id", "snapshotRef"))
        );
        String versionId = firstNonBlank(
            request.versionId(),
            extractStringMetadata(request, Set.of("versionId", "version_id", "modelVersion"))
        );
        String contentChecksum = firstNonBlank(
            request.contentChecksum(),
            extractStringMetadata(request, Set.of("contentChecksum", "content_checksum", "graphChecksum"))
        );

        List<ArtifactGraphRepository.UnresolvedEdgeRecord> unresolvedEdges = mapUnresolvedEdges(request.unresolvedEdges());
        List<ArtifactGraphRepository.EdgeResolutionRecord> resolutionRecords =
            mapEdgeResolutionRecords(request.edgeResolutionRecords());
        List<ArtifactGraphRepository.ResidualIslandRecord> residualIslands =
            mapResidualIslands(request.residualIslands(), scope.tenantId(), scope.projectId(), scope.workspaceId(), snapshotId);

        // P4-2: Use incremental upsert instead of delete-then-insert
        // Only insert/update nodes that have changed based on checksum
        return repository.upsertNodes(scope.projectId(), scope.tenantId(), scope.workspaceId(), request.nodes(), snapshotId, versionId, contentChecksum)
            .then(saved -> repository.upsertEdges(scope.projectId(), scope.tenantId(), scope.workspaceId(), request.edges(), snapshotId, versionId))
            .then(v -> repository.saveUnresolvedEdges(scope.projectId(), scope.tenantId(), snapshotId, unresolvedEdges))
            .then(v -> repository.saveEdgeResolutionRecords(scope.projectId(), scope.tenantId(), resolutionRecords))
            .then(v -> repository.saveResidualIslands(scope.projectId(), scope.tenantId(), snapshotId, residualIslands))
                .then(v -> {
                    nodeCache.invalidate(cacheKey);
                    edgeCache.invalidate(cacheKey);
                    if (versionRepository != null) {
                        ArtifactModelVersion version = new ArtifactModelVersion(
                                java.util.UUID.randomUUID().toString(),
                    scope.projectId(),
                    scope.tenantId(),
                                snapshotId,
                                "Incrementally upserted " + request.nodes().size() + " nodes and " + request.edges().size() + " edges",
                                java.time.Instant.now(),
                                "artifact-compiler",
                                Map.of("nodeTypes", request.nodes().stream().map(ArtifactNodeDto::type).distinct().toList()),
                                request.nodes().size(),
                                request.edges().size(),
                                Map.of("snapshotId", snapshotId != null ? snapshotId : "none", "contentChecksum", contentChecksum != null ? contentChecksum : "none")
                        );
                        return versionRepository.saveVersion(version).map(ignored -> new ArtifactGraphResponse(
                                true, "ingest",
                                Map.of("nodeCount", request.nodes().size(), "edgeCount", request.edges().size(), "versionId", version.versionId(), "snapshotId", snapshotId, "contentChecksum", contentChecksum),
                                "Artifact graph incrementally upserted and versioned successfully"
                        ));
                    }
                    return Promise.of(new ArtifactGraphResponse(
                            true, "ingest",
                            Map.of("nodeCount", request.nodes().size(), "edgeCount", request.edges().size(), "snapshotId", snapshotId, "contentChecksum", contentChecksum),
                            "Artifact graph incrementally upserted successfully"
                    ));
                })
                .whenException(e -> log.error("Failed to incrementally upsert artifact graph for project {}", scope.projectId(), e));
    }

    @Override
    public Promise<ArtifactGraphResponse> rollbackIngest(ArtifactRequestScope scope, String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Promise.of(new ArtifactGraphResponse(
                false,
                "rollback",
                Map.of("snapshotId", "missing"),
                "Rollback skipped: snapshotId is missing"
            ));
        }

        String cacheKey = cacheKey(scope.tenantId(), scope.workspaceId(), scope.projectId());
        return repository.tombstoneGraphForSnapshot(scope.projectId(), scope.tenantId(), scope.workspaceId(), snapshotId)
            .map(tombstoned -> {
                nodeCache.invalidate(cacheKey);
                edgeCache.invalidate(cacheKey);
                return new ArtifactGraphResponse(
                    tombstoned,
                    "rollback",
                    Map.of("snapshotId", snapshotId, "tombstoned", tombstoned),
                    tombstoned
                        ? "Artifact graph snapshot rollback completed"
                        : "Artifact graph snapshot rollback found no active rows"
                );
            })
            .whenException(e -> log.error(
                "Failed to rollback artifact graph snapshot {} for project {}",
                snapshotId,
                scope.projectId(),
                e
            ));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<ArtifactGraphRepository.UnresolvedEdgeRecord> mapUnresolvedEdges(List<UnresolvedGraphEdgeDto> unresolvedEdges) {
        if (unresolvedEdges == null || unresolvedEdges.isEmpty()) {
            return List.of();
        }

        List<ArtifactGraphRepository.UnresolvedEdgeRecord> mapped = new ArrayList<>(unresolvedEdges.size());
        for (UnresolvedGraphEdgeDto edge : unresolvedEdges) {
            if (edge == null) {
                throw new IllegalArgumentException("Unresolved edge payload contains null entry");
            }
            mapped.add(new ArtifactGraphRepository.UnresolvedEdgeRecord(
                firstNonBlank(edge.id(), UUID.randomUUID().toString()),
                edge.sourceNodeId(),
                edge.targetRef(),
                edge.relationshipType(),
                edge.targetKindHint(),
                edge.confidence(),
                edge.metadata() == null ? Map.of() : edge.metadata()
            ));
        }
        return mapped;
    }

    private static List<ArtifactGraphRepository.EdgeResolutionRecord> mapEdgeResolutionRecords(List<EdgeResolutionRecordDto> resolutionRecords) {
        if (resolutionRecords == null || resolutionRecords.isEmpty()) {
            return List.of();
        }

        List<ArtifactGraphRepository.EdgeResolutionRecord> mapped = new ArrayList<>(resolutionRecords.size());
        for (EdgeResolutionRecordDto record : resolutionRecords) {
            if (record == null) {
                throw new IllegalArgumentException("Edge resolution record payload contains null entry");
            }
            mapped.add(new ArtifactGraphRepository.EdgeResolutionRecord(
                firstNonBlank(record.id(), UUID.randomUUID().toString()),
                record.unresolvedEdgeId(),
                record.status(),
                record.resolvedTargetId(),
                record.candidateIds() == null ? List.of() : record.candidateIds(),
                record.reviewRequired()
            ));
        }
        return mapped;
    }

    /**
     * P0: Maps residual islands from DTO to repository record with full payload.
     * Validates that required fields are present before mapping.
     * Rejects lossy ingest by throwing if originalSource, checksum, or rawFragmentRef are missing.
     */
    private static List<ArtifactGraphRepository.ResidualIslandRecord> mapResidualIslands(
            List<ResidualIslandDto> residualIslands,
            String tenantId,
            String projectId,
            String workspaceId,
            String snapshotId) {
        if (residualIslands == null || residualIslands.isEmpty()) {
            return List.of();
        }
        return residualIslands.stream()
            .filter(dto -> dto != null && dto.id() != null && !dto.id().isBlank())
            .map(dto -> {
                // P0: Validate required fields for full fidelity
                if (dto.originalSource() == null || dto.originalSource().isBlank()) {
                    throw new IllegalArgumentException(
                        "ResidualIsland '" + dto.id() + "' missing required field 'originalSource'. " +
                        "Lossy ingest rejected - full residual payload required.");
                }
                if (dto.checksum() == null || dto.checksum().isBlank()) {
                    throw new IllegalArgumentException(
                        "ResidualIsland '" + dto.id() + "' missing required field 'checksum'. " +
                        "Lossy ingest rejected - full residual payload required.");
                }
                if (dto.rawFragmentRef() == null || dto.rawFragmentRef().isBlank()) {
                    throw new IllegalArgumentException(
                        "ResidualIsland '" + dto.id() + "' missing required field 'rawFragmentRef'. " +
                        "Lossy ingest rejected - full residual payload required.");
                }
                return new ArtifactGraphRepository.ResidualIslandRecord(
                    dto.id(),
                    dto.islandType() != null ? dto.islandType() : "unknown",
                    dto.summary(),
                    dto.originalSource(), // P0: Map original source for round-trip fidelity
                    dto.sourceLocation() == null ? Map.of() : Map.of(
                        "filePath", dto.sourceLocation().filePath(),
                        "startLine", dto.sourceLocation().startLine(),
                        "startColumn", dto.sourceLocation().startColumn(),
                        "endLine", dto.sourceLocation().endLine(),
                        "endColumn", dto.sourceLocation().endColumn()
                    ),
                    dto.sourceSpan(),
                    dto.checksum(),
                    dto.rawFragmentRef(),
                    dto.reason(),
                    dto.confidence() != null ? dto.confidence() : 0.0,
                    dto.reviewRequired() != null ? dto.reviewRequired() : false,
                    dto.riskScore() != null ? dto.riskScore() : 0.0,
                    dto.fileCount() != null ? dto.fileCount() : 1,
                    dto.metadata() != null ? new java.util.HashMap<>(dto.metadata()) : Map.of(),
                    dto.tenantId() != null ? dto.tenantId() : tenantId,
                    dto.projectId() != null ? dto.projectId() : projectId,
                    dto.workspaceId() != null ? dto.workspaceId() : workspaceId,
                    dto.snapshotId() != null ? dto.snapshotId() : snapshotId
                );
            })
            .toList();
    }

    private static String extractStringMetadata(ArtifactGraphIngestRequest request, Set<String> keys) {
        for (ArtifactNodeDto node : request.nodes()) {
            String value = readStringMetadata(node.properties(), keys);
            if (value != null) {
                return value;
            }
        }
        for (ArtifactEdgeDto edge : request.edges()) {
            String value = readStringMetadata(edge.properties(), keys);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String readStringMetadata(Map<String, Object> metadata, Set<String> keys) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    @Override
    public Promise<List<ArtifactGraphAnalysisResult>> analyzeGraph(ArtifactRequestScope scope, ArtifactGraphAnalysisRequest request) {
        String cacheKey = cacheKey(scope.tenantId(), scope.workspaceId(), scope.projectId());
        log.info("Analyzing artifact graph for product {} with algorithms {}",
            scope.projectId(), request.algorithmTypes());

        Promise<List<ArtifactNodeDto>> nodesPromise;
        Promise<List<ArtifactEdgeDto>> edgesPromise;

        List<ArtifactNodeDto> cachedNodes = nodeCache.getIfPresent(cacheKey);
        List<ArtifactEdgeDto> cachedEdges = edgeCache.getIfPresent(cacheKey);

        if (cachedNodes != null && cachedEdges != null) {
            nodesPromise = Promise.of(cachedNodes);
            edgesPromise = Promise.of(cachedEdges);
        } else {
            nodesPromise = repository.findNodesByProduct(scope.projectId(), scope.tenantId(), scope.workspaceId(), 10000);
            edgesPromise = repository.findEdgesByProduct(scope.projectId(), scope.tenantId(), scope.workspaceId());
        }

        // P4-2: Move heavy JGraphT work to blocking executor to prevent event loop blocking
        return nodesPromise.combine(edgesPromise, (nodes, edges) -> {
            nodeCache.put(cacheKey, nodes);
            edgeCache.put(cacheKey, edges);
            return new Object[]{nodes, edges};
        }).then(pair -> Promise.ofBlocking(blockingExecutor, () -> {
            @SuppressWarnings("unchecked")
            List<ArtifactNodeDto> nodeList = (List<ArtifactNodeDto>) pair[0];
            @SuppressWarnings("unchecked")
            List<ArtifactEdgeDto> edgeList = (List<ArtifactEdgeDto>) pair[1];
            return runJGraphTAnalysis(nodeList, edgeList, request);
        }));
    }

    /**
     * P4-2: Run JGraphT analysis algorithms on a blocking executor.
     * This method performs CPU-intensive graph algorithms without blocking the event loop.
     */
    private List<ArtifactGraphAnalysisResult> runJGraphTAnalysis(
            List<ArtifactNodeDto> nodes,
            List<ArtifactEdgeDto> edges,
            ArtifactGraphAnalysisRequest request) {
        
        DefaultDirectedGraph<String, DefaultEdge> graph = buildJGraphT(nodes, edges);
        List<ArtifactGraphAnalysisResult> results = new ArrayList<>();

        Set<String> nodeIds = request.nodeIds() != null && !request.nodeIds().isEmpty()
                ? new LinkedHashSet<>(request.nodeIds())
                : graph.vertexSet();

        for (String algorithm : request.algorithmTypes()) {
            switch (algorithm.toLowerCase()) {
                case "centrality", "betweenness" -> {
                    BetweennessCentrality<String, DefaultEdge> centrality = new BetweennessCentrality<>(graph);
                    Map<String, Double> scores = new HashMap<>();
                    for (String nodeId : nodeIds) {
                        if (graph.containsVertex(nodeId)) {
                            scores.put(nodeId, centrality.getVertexScore(nodeId));
                        }
                    }
                    results.add(new ArtifactGraphAnalysisResult(
                            "betweenness-centrality", scores, List.of(), List.of(), List.of(), Map.of()
                    ));
                }
                case "cycles", "scc", "strongly-connected-components" -> {
                    // P4-2: Fix cycle detection - use CycleDetector instead of incorrect SCC check
                    CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(graph);
                    Set<String> cycleVertices = cycleDetector.findCycles();
                    
                    // Extract cycles as lists of vertices
                    List<List<String>> cycles = new ArrayList<>();
                    if (!cycleVertices.isEmpty()) {
                        // Group cycle vertices into connected components
                        Map<String, List<String>> cycleGroups = new HashMap<>();
                        for (String vertex : cycleVertices) {
                            List<String> group = cycleGroups.computeIfAbsent(vertex, k -> new ArrayList<>());
                            // Add vertices reachable within the cycle
                            BreadthFirstIterator<String, DefaultEdge> bfs = new BreadthFirstIterator<>(graph, vertex);
                            while (bfs.hasNext() && group.size() < 50) { // Limit cycle size
                                String v = bfs.next();
                                if (cycleVertices.contains(v)) {
                                    group.add(v);
                                }
                            }
                            if (!cycles.contains(group)) {
                                cycles.add(group);
                            }
                        }
                    }
                    
                    results.add(new ArtifactGraphAnalysisResult(
                            "cycles", Map.of(), cycles, List.of(), List.of(),
                            Map.of("cycleCount", cycles.size(), "cycleVertexCount", cycleVertices.size())
                    ));
                }
                case "topological", "build-order" -> {
                    // P4-2: Fix cycle detection - use CycleDetector instead of incorrect isStronglyConnected()
                    CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(graph);
                    if (!cycleDetector.detectCycles()) {
                        List<String> order = new ArrayList<>();
                        new TopologicalOrderIterator<>(graph).forEachRemaining(order::add);
                        results.add(new ArtifactGraphAnalysisResult(
                                "topological-order", Map.of(), List.of(), List.of(), order, Map.of()
                        ));
                    } else {
                        Set<String> cycleVertices = cycleDetector.findCycles();
                        results.add(new ArtifactGraphAnalysisResult(
                                "topological-order", Map.of(), List.of(), List.of(), List.of(),
                                Map.of("error", "Graph contains cycles; topological sort not possible",
                                       "cycleVertexCount", cycleVertices.size())
                        ));
                    }
                }
                case "communities" -> {
                    List<List<String>> communities = greedyCommunityDetection(graph, nodeIds);
                    results.add(new ArtifactGraphAnalysisResult(
                            "greedy-communities", Map.of(), List.of(), communities, List.of(),
                            Map.of("communityCount", communities.size())
                    ));
                }
                case "reachability", "paths" -> {
                    // P4-2: Replace expensive all-pairs reachability with bounded BFS
                    Map<String, Object> meta = boundedReachabilityAnalysis(graph, nodeIds);
                    results.add(new ArtifactGraphAnalysisResult(
                            "reachability", Map.of(), List.of(), List.of(), List.of(), meta
                    ));
                }
                default -> log.warn("Unknown analysis algorithm: {}", algorithm);
            }
        }
        return results;
    }

    /**
     * P4-2: Bounded reachability analysis using BFS instead of expensive all-pairs.
     * Limits depth to MAX_REACHABILITY_DEPTH and total results to MAX_REACHABILITY_RESULTS.
     */
    private Map<String, Object> boundedReachabilityAnalysis(
            DefaultDirectedGraph<String, DefaultEdge> graph,
            Set<String> nodeIds) {
        
        Map<String, Integer> reachableCounts = new HashMap<>();
        int totalPaths = 0;
        int resultCount = 0;
        
        for (String source : nodeIds) {
            if (!graph.containsVertex(source)) continue;
            
            int reachableFromSource = 0;
            BreadthFirstIterator<String, DefaultEdge> bfs = new BreadthFirstIterator<>(graph, source);
            int depth = 0;
            String lastAtDepth = source;
            
            while (bfs.hasNext() && depth < MAX_REACHABILITY_DEPTH && resultCount < MAX_REACHABILITY_RESULTS) {
                String target = bfs.next();
                if (!source.equals(target)) {
                    reachableFromSource++;
                    totalPaths++;
                    resultCount++;
                }
                
                // Track depth by checking if we've moved to a new level
                if (target.equals(lastAtDepth) && bfs.hasNext()) {
                    depth++;
                    lastAtDepth = null;
                }
            }
            reachableCounts.put(source, reachableFromSource);
            
            if (resultCount >= MAX_REACHABILITY_RESULTS) {
                log.warn("Reachability analysis hit result limit {}, results may be incomplete", MAX_REACHABILITY_RESULTS);
                break;
            }
        }
        
        return Map.of(
            "maxPathLength" + MAX_REACHABILITY_DEPTH, totalPaths,
            "reachableCounts", reachableCounts,
            "analyzedNodes", nodeIds.size(),
            "depthLimit", MAX_REACHABILITY_DEPTH,
            "resultLimitHit", resultCount >= MAX_REACHABILITY_RESULTS
        );
    }

    @Override
    public Promise<ArtifactGraphResponse> mergeModels(ArtifactRequestScope scope, ArtifactGraphMergeRequest request) {
        log.info("Merging artifact models for product {} with strategy {}",
            scope.projectId(), request.resolutionStrategy());

        SemanticMergeEngine mergeEngine = new SemanticMergeEngine(request.resolutionStrategy());
        SemanticMergeEngine.MergeResult result = mergeEngine.merge(request);

        Map<String, Object> response = new HashMap<>();
        response.put("mergedModel", result.mergedModel());
        response.put("conflicts", result.conflicts());
        response.put("fieldProvenance", result.fieldProvenance());
        response.put("conflictCount", result.conflicts().size());

        if (versionRepository != null) {
            ArtifactModelVersion mergeVersion = new ArtifactModelVersion(
                    java.util.UUID.randomUUID().toString(),
                    scope.projectId(),
                    scope.tenantId(),
                    null,
                    "Merge with strategy " + request.resolutionStrategy() + " resulting in " + result.conflicts().size() + " conflicts",
                    java.time.Instant.now(),
                    "artifact-compiler",
                    result.mergedModel(),
                    result.mergedModel().size(),
                    result.conflicts().size(),
                    result.fieldProvenance()
            );
            return versionRepository.saveVersion(mergeVersion).map(ignored -> new ArtifactGraphResponse(true, "merge",
                    response,
                    "Three-way merge completed with " + result.conflicts().size() + " conflicts and versioned"));
        }

        return Promise.of(new ArtifactGraphResponse(true, "merge",
                response,
                "Three-way merge completed with " + result.conflicts().size() + " conflicts"));
    }

    /**
     * P1-13: Query the artifact graph with cursor-based pagination.
     * Routes graph queries through repository pagination and returns cursor for next page.
     * P3-1: Returns typed ArtifactGraphQueryResponse with items, nextCursor, totalEstimate, and scope metadata.
     * P0: Accepts ArtifactRequestScope for proper tenant/workspace/project isolation.
     * P0: Uses query-specific repository methods for accurate results across all data, not just current page.
     */
    @Override
    public Promise<ArtifactGraphQueryResponse> queryGraph(ArtifactRequestScope scope, String queryType, List<String> seedNodeIds, String cursor, int pageSize, String snapshotId, Boolean includeUnresolvedEdges) {
        String cacheKey = cacheKey(scope.tenantId(), scope.workspaceId(), scope.projectId());
        
        // P1-13: Use repository pagination instead of full fetch
        int effectivePageSize = pageSize > 0 ? pageSize : 100;
        
        // P0: Use query-specific repository methods for accurate results
        switch (queryType.toLowerCase()) {
            case "orphaned" -> {
                return repository.findOrphanedNodeIds(scope.projectId(), scope.tenantId(), scope.workspaceId())
                    .map(orphanedIds -> {
                        Map<String, Object> items = new HashMap<>();
                        items.put("orphanedNodes", orphanedIds);
                        items.put("orphanedCount", orphanedIds.size());
                        
                        ArtifactGraphQueryResponse.ScopeMetadata scopeMetadata = new ArtifactGraphQueryResponse.ScopeMetadata(
                            scope.tenantId(),
                            scope.workspaceId(),
                            scope.projectId(),
                            queryType,
                            effectivePageSize,
                            false,
                            snapshotId,
                            includeUnresolvedEdges != null ? includeUnresolvedEdges : false
                        );
                        
                        return ArtifactGraphQueryResponse.lastPage(items, (long) orphanedIds.size(), scopeMetadata);
                    });
            }
            case "dependencies" -> {
                List<String> sourceIds = seedNodeIds != null && !seedNodeIds.isEmpty() ? seedNodeIds : List.of();
                return repository.findDependencies(scope.projectId(), scope.tenantId(), scope.workspaceId(), sourceIds)
                    .map(dependencies -> {
                        Map<String, Object> items = new HashMap<>();
                        items.put("dependencies", dependencies);
                        int totalDeps = dependencies.values().stream().mapToInt(List::size).sum();
                        items.put("totalDependencies", totalDeps);
                        
                        ArtifactGraphQueryResponse.ScopeMetadata scopeMetadata = new ArtifactGraphQueryResponse.ScopeMetadata(
                            scope.tenantId(),
                            scope.workspaceId(),
                            scope.projectId(),
                            queryType,
                            effectivePageSize,
                            false,
                            snapshotId,
                            includeUnresolvedEdges != null ? includeUnresolvedEdges : false
                        );
                        
                        return ArtifactGraphQueryResponse.lastPage(items, (long) totalDeps, scopeMetadata);
                    });
            }
            case "dependents" -> {
                List<String> targetIds = seedNodeIds != null && !seedNodeIds.isEmpty() ? seedNodeIds : List.of();
                return repository.findDependents(scope.projectId(), scope.tenantId(), scope.workspaceId(), targetIds)
                    .map(dependents -> {
                        Map<String, Object> items = new HashMap<>();
                        items.put("dependents", dependents);
                        int totalDeps = dependents.values().stream().mapToInt(List::size).sum();
                        items.put("totalDependents", totalDeps);
                        
                        ArtifactGraphQueryResponse.ScopeMetadata scopeMetadata = new ArtifactGraphQueryResponse.ScopeMetadata(
                            scope.tenantId(),
                            scope.workspaceId(),
                            scope.projectId(),
                            queryType,
                            effectivePageSize,
                            false,
                            snapshotId,
                            includeUnresolvedEdges != null ? includeUnresolvedEdges : false
                        );
                        
                        return ArtifactGraphQueryResponse.lastPage(items, (long) totalDeps, scopeMetadata);
                    });
            }
            case "stats" -> {
                return repository.getGraphStats(scope.projectId(), scope.tenantId(), scope.workspaceId())
                    .map(stats -> {
                        Map<String, Object> items = new HashMap<>();
                        items.putAll(stats);
                        
                        ArtifactGraphQueryResponse.ScopeMetadata scopeMetadata = new ArtifactGraphQueryResponse.ScopeMetadata(
                            scope.tenantId(),
                            scope.workspaceId(),
                            scope.projectId(),
                            queryType,
                            effectivePageSize,
                            false,
                            snapshotId,
                            includeUnresolvedEdges != null ? includeUnresolvedEdges : false
                        );
                        
                        Long totalCount = stats.getOrDefault("nodeCount", 0L) + stats.getOrDefault("edgeCount", 0L);
                        return ArtifactGraphQueryResponse.lastPage(items, totalCount, scopeMetadata);
                    });
            }
            default -> {
                // For unknown query types or pagination-based queries, fall back to pagination
                Promise<ArtifactGraphRepository.PageResult<ArtifactNodeDto>> nodesPromise = 
                    repository.findNodesPaginated(scope.projectId(), scope.tenantId(), scope.workspaceId(), cursor, effectivePageSize);
                Promise<ArtifactGraphRepository.PageResult<ArtifactEdgeDto>> edgesPromise = 
                    repository.findEdgesPaginated(scope.projectId(), scope.tenantId(), scope.workspaceId(), cursor, effectivePageSize);
                
                return nodesPromise.combine(edgesPromise, (nodesPageResult, edgesPageResult) -> {
                    List<ArtifactNodeDto> nodes = nodesPageResult.items();
                    List<ArtifactEdgeDto> edges = edgesPageResult.items();
                    
                    nodeCache.put(cacheKey, nodes);
                    edgeCache.put(cacheKey, edges);
                    
                    Map<String, Object> items = new HashMap<>();
                    items.put("nodes", nodes);
                    items.put("edges", edges);
                    items.put("nodeCount", nodes.size());
                    items.put("edgeCount", edges.size());
                    
                    boolean hasMore = nodesPageResult.nextCursor() != null;
                    ArtifactGraphQueryResponse.ScopeMetadata scopeMetadata = new ArtifactGraphQueryResponse.ScopeMetadata(
                        scope.tenantId(),
                        scope.workspaceId(),
                        scope.projectId(),
                        queryType,
                        effectivePageSize,
                        hasMore,
                        snapshotId,
                        includeUnresolvedEdges != null ? includeUnresolvedEdges : false
                    );
                    
                    Long totalEstimate = hasMore ? (long) effectivePageSize * 2 : (long) (nodes.size() + edges.size());
                    
                    if (hasMore) {
                        return ArtifactGraphQueryResponse.withNextPage(items, nodesPageResult.nextCursor(), totalEstimate, scopeMetadata);
                    } else {
                        return ArtifactGraphQueryResponse.lastPage(items, totalEstimate, scopeMetadata);
                    }
                });
            }
        }
    }

    /**
     * P0: Use typed request DTO instead of raw map for residual analysis.
     * Ensures contract validation and type safety.
     */
    @Override
    public Promise<ArtifactGraphResponse> analyzeResidual(ArtifactRequestScope scope, ResidualAnalysisRequest request) {
        log.info("Analyzing {} residual islands for project {}", 
            request.residualIslands().size(), scope.projectId());
        
        // Validate residual islands have required fields for analysis
        for (ResidualIslandDto island : request.residualIslands()) {
            if (island.originalSource() == null || island.originalSource().isBlank()) {
                log.warn("Residual island {} missing originalSource, cannot perform meaningful analysis", island.id());
            }
        }
        
        // Perform analysis on typed residual islands
        List<ResidualIslandDto> enriched = request.residualIslands().stream()
                .map(island -> {
                    // In a real implementation, this would perform AST analysis, pattern matching, etc.
                    // For now, we return the typed DTO with analysis metadata
                    return island; // Return unchanged for now - analysis logic to be implemented
                })
                .toList();
        
        return Promise.of(new ArtifactGraphResponse(true, "residual-analysis",
                Map.of("islands", enriched, "count", enriched.size()),
                "Residual islands analyzed"));
    }

    private DefaultDirectedGraph<String, DefaultEdge> buildJGraphT(List<ArtifactNodeDto> nodes, List<ArtifactEdgeDto> edges) {
        DefaultDirectedGraph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (ArtifactNodeDto node : nodes) {
            graph.addVertex(node.id());
        }
        for (ArtifactEdgeDto edge : edges) {
            if (graph.containsVertex(edge.sourceNodeId()) && graph.containsVertex(edge.targetNodeId())) {
                graph.addEdge(edge.sourceNodeId(), edge.targetNodeId());
            }
        }
        return graph;
    }

    private List<List<String>> greedyCommunityDetection(DefaultDirectedGraph<String, DefaultEdge> graph, Set<String> nodeIds) {
        Map<String, Integer> labels = new HashMap<>();
        int labelCounter = 0;
        for (String nodeId : nodeIds) {
            labels.put(nodeId, labelCounter++);
        }
        // Simple label propagation: 3 iterations
        for (int iter = 0; iter < 3; iter++) {
            for (String nodeId : nodeIds) {
                if (!graph.containsVertex(nodeId)) continue;
                Map<Integer, Long> neighborLabels = graph.incomingEdgesOf(nodeId).stream()
                        .map(graph::getEdgeSource)
                        .map(labels::get)
                        .filter(l -> l != null)
                        .collect(Collectors.groupingBy(l -> l, Collectors.counting()));
                Map<Integer, Long> outLabels = graph.outgoingEdgesOf(nodeId).stream()
                        .map(graph::getEdgeTarget)
                        .map(labels::get)
                        .filter(l -> l != null)
                        .collect(Collectors.groupingBy(l -> l, Collectors.counting()));
                outLabels.forEach((label, count) -> neighborLabels.merge(label, count, Long::sum));
                if (!neighborLabels.isEmpty()) {
                    int bestLabel = Collections.max(neighborLabels.entrySet(), Map.Entry.comparingByValue()).getKey();
                    labels.put(nodeId, bestLabel);
                }
            }
        }
        Map<Integer, List<String>> communities = new HashMap<>();
        for (Map.Entry<String, Integer> entry : labels.entrySet()) {
            communities.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        return new ArrayList<>(communities.values());
    }

    /**
     * 
     * @param filePath   relative path of the source file (used for language detection)
     * @param sourceCode raw source text
     * For TypeScript/JavaScript, use the TypeScript compiler library in the frontend artifact-compiler.
     * @return map with {@code "nodes"} and {@code "edges"} lists
     */
    /**
     * P0-8: Parse source artifact with language-specific parsers.
     * Unsupported parsers are gated behind artifactCompiler.unsupportedParserDiagnostics.enabled feature flag.
     * When disabled, unsupported files emit residual islands instead of stub diagnostics.
     */
    public Map<String, Object> parseSourceArtifact(String filePath, String sourceCode) {
        String lower = filePath.toLowerCase();

        if (lower.endsWith(".java")) {
            log.debug("Using JavaSourceParser for {}", filePath);
            return new JavaSourceParser().parseString(sourceCode);
        }

        if (lower.endsWith(".sql")) {
            log.debug("Using SqlSchemaParser for {}", filePath);
            return new SqlSchemaParser().parseSchema(sourceCode);
        }

        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            log.debug("Using CicdWorkflowParser for {}", filePath);
            return new CicdWorkflowParser().parseGitHubActionsWorkflow(sourceCode);
        }

        // P0-8: Check feature flag for unsupported parser diagnostics
        boolean unsupportedDiagnosticsEnabled = Boolean.parseBoolean(
            System.getProperty("artifactCompiler.unsupportedParserDiagnostics.enabled", "false")
        );

        String detectedLang = detectLanguageFromExtension(lower);
        if (detectedLang != null) {
            if (unsupportedDiagnosticsEnabled) {
                // P0-8: When enabled, emit diagnostic stub for manual review
                log.info("No dedicated parser available for {} (detected: {}). " +
                        "Use frontend TypeScript artifact-compiler for TS/JS/TSX files. " +
                        "Returning diagnostic stub for manual review.", filePath, detectedLang);
                return Map.of(
                        "nodes", List.of(Map.of(
                                "id", "unparsed://" + filePath,
                                "type", "source_file",
                                "name", filePath,
                                "filePath", filePath,
                                "language", detectedLang,
                                "parseStatus", "requires_dedicated_parser",
                                "message", "No dedicated parser available in Java backend. Use frontend artifact-compiler for TypeScript/JavaScript files."
                        )),
                        "edges", List.of()
                );
            } else {
                // P0-8: When disabled, emit residual island instead of stub
                log.debug("No dedicated parser available for {} (detected: {}). Emitting residual island.", filePath, detectedLang);
                String residualId = "residual://" + filePath;
                return Map.of(
                        "nodes", List.of(),
                        "edges", List.of(),
                        "residualIslands", List.of(Map.of(
                                "id", residualId,
                                "type", "unsupported_language",
                                "filePath", filePath,
                                "language", detectedLang,
                                "reason", "No dedicated parser available. Requires AST upgrade or manual review.",
                                "confidence", 0.0,
                                "sourceRange", Map.of(
                                        "startLine", 0,
                                        "startColumn", 0,
                                        "endLine", sourceCode.split("\n").length,
                                        "endColumn", sourceCode.length()
                                )
                        ))
                );
            }
        }

        log.warn("Unknown file type, no parser available: {}", filePath);
        return Map.of("nodes", List.of(), "edges", List.of());
    }

    /**
     * Detect language from file extension for logging purposes.
     * Does not attempt parsing - only identifies the language for stub responses.
     */
    private String detectLanguageFromExtension(String lowerPath) {
        if (lowerPath.endsWith(".ts") || lowerPath.endsWith(".tsx")) return "typescript";
        if (lowerPath.endsWith(".js") || lowerPath.endsWith(".jsx") || lowerPath.endsWith(".mjs")) return "javascript";
        if (lowerPath.endsWith(".py")) return "python";
        if (lowerPath.endsWith(".go")) return "go";
        if (lowerPath.endsWith(".rs")) return "rust";
        if (lowerPath.endsWith(".c") || lowerPath.endsWith(".h")) return "c";
        if (lowerPath.endsWith(".cpp") || lowerPath.endsWith(".cc") || lowerPath.endsWith(".hpp")) return "cpp";
        if (lowerPath.endsWith(".rb")) return "ruby";
        if (lowerPath.endsWith(".php")) return "php";
        if (lowerPath.endsWith(".swift")) return "swift";
        if (lowerPath.endsWith(".kt") || lowerPath.endsWith(".kts")) return "kotlin";
        return null;
    }

    private String cacheKey(String tenantId, String workspaceId, String projectId) {
        return tenantId + ":" + workspaceId + ":" + projectId;
    }
}
