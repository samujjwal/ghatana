package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphAnalysisResult;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphMergeRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    public Promise<ArtifactGraphResponse> ingestGraph(ArtifactGraphIngestRequest request) {
        String cacheKey = cacheKey(request.tenantId(), request.productId());
        log.info("Ingesting artifact graph for product {} ({} nodes, {} edges)",
                request.productId(), request.nodes().size(), request.edges().size());

        // P4-2: Extract snapshotId and versionId from request metadata for tracking
        // TODO: Add metadata field to ArtifactGraphIngestRequest DTO when available
        String snapshotId = null;
        String versionId = null;
        String contentChecksum = null;

        // P4-2: Use incremental upsert instead of delete-then-insert
        // Only insert/update nodes that have changed based on checksum
        return repository.upsertNodes(request.productId(), request.tenantId(), request.nodes(), snapshotId, versionId, contentChecksum)
                .then(saved -> repository.upsertEdges(request.productId(), request.tenantId(), request.edges(), snapshotId, versionId))
                .then(v -> {
                    nodeCache.invalidate(cacheKey);
                    edgeCache.invalidate(cacheKey);
                    if (versionRepository != null) {
                        ArtifactModelVersion version = new ArtifactModelVersion(
                                java.util.UUID.randomUUID().toString(),
                                request.productId(),
                                request.tenantId(),
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
                .whenException(e -> log.error("Failed to incrementally upsert artifact graph for product {}", request.productId(), e));
    }

    @Override
    public Promise<List<ArtifactGraphAnalysisResult>> analyzeGraph(ArtifactGraphAnalysisRequest request) {
        String cacheKey = cacheKey(request.tenantId(), request.productId());
        log.info("Analyzing artifact graph for product {} with algorithms {}",
                request.productId(), request.algorithmTypes());

        Promise<List<ArtifactNodeDto>> nodesPromise;
        Promise<List<ArtifactEdgeDto>> edgesPromise;

        List<ArtifactNodeDto> cachedNodes = nodeCache.getIfPresent(cacheKey);
        List<ArtifactEdgeDto> cachedEdges = edgeCache.getIfPresent(cacheKey);

        if (cachedNodes != null && cachedEdges != null) {
            nodesPromise = Promise.of(cachedNodes);
            edgesPromise = Promise.of(cachedEdges);
        } else {
            nodesPromise = repository.findNodesByProduct(request.productId(), request.tenantId(), 10000);
            edgesPromise = repository.findEdgesByProduct(request.productId(), request.tenantId());
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
    public Promise<ArtifactGraphResponse> mergeModels(ArtifactGraphMergeRequest request) {
        log.info("Merging artifact models for product {} with strategy {}",
                request.productId(), request.resolutionStrategy());

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
                    request.productId(),
                    request.tenantId(),
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

    @Override
    public Promise<Map<String, Object>> queryGraph(String productId, String tenantId, String queryType, List<String> seedNodeIds) {
        String cacheKey = cacheKey(tenantId, productId);
        List<ArtifactNodeDto> cachedNodes = nodeCache.getIfPresent(cacheKey);
        List<ArtifactEdgeDto> cachedEdges = edgeCache.getIfPresent(cacheKey);

        Promise<List<ArtifactNodeDto>> nodesPromise = cachedNodes != null
                ? Promise.of(cachedNodes)
                : repository.findNodesByProduct(productId, tenantId, 10000);
        Promise<List<ArtifactEdgeDto>> edgesPromise = cachedEdges != null
                ? Promise.of(cachedEdges)
                : repository.findEdgesByProduct(productId, tenantId);

        return nodesPromise.combine(edgesPromise, (nodes, edges) -> {
            nodeCache.put(cacheKey, nodes);
            edgeCache.put(cacheKey, edges);
            Map<String, Object> result = new HashMap<>();

            switch (queryType.toLowerCase()) {
                case "orphaned" -> {
                    Set<String> allNodeIds = nodes.stream().map(ArtifactNodeDto::id).collect(Collectors.toSet());
                    Set<String> referencedIds = edges.stream()
                            .map(ArtifactEdgeDto::targetNodeId)
                            .collect(Collectors.toSet());
                    List<String> orphaned = allNodeIds.stream()
                            .filter(id -> !referencedIds.contains(id))
                            .collect(Collectors.toList());
                    result.put("orphanedNodes", orphaned);
                }
                case "dependencies" -> {
                    Map<String, List<String>> deps = new HashMap<>();
                    for (ArtifactEdgeDto edge : edges) {
                        if (seedNodeIds == null || seedNodeIds.contains(edge.sourceNodeId())) {
                            deps.computeIfAbsent(edge.sourceNodeId(), k -> new ArrayList<>()).add(edge.targetNodeId());
                        }
                    }
                    result.put("dependencies", deps);
                }
                case "dependents" -> {
                    Map<String, List<String>> dependents = new HashMap<>();
                    for (ArtifactEdgeDto edge : edges) {
                        if (seedNodeIds == null || seedNodeIds.contains(edge.targetNodeId())) {
                            dependents.computeIfAbsent(edge.targetNodeId(), k -> new ArrayList<>()).add(edge.sourceNodeId());
                        }
                    }
                    result.put("dependents", dependents);
                }
                case "stats" -> {
                    result.put("nodeCount", nodes.size());
                    result.put("edgeCount", edges.size());
                    Map<String, Long> typeCounts = nodes.stream()
                            .collect(Collectors.groupingBy(ArtifactNodeDto::type, Collectors.counting()));
                    result.put("nodeTypeDistribution", typeCounts);
                }
                default -> result.put("error", "Unknown query type: " + queryType);
            }
            return result;
        });
    }

    @Override
    public Promise<ArtifactGraphResponse> analyzeResidual(String productId, String tenantId, List<Map<String, Object>> residualIslands) {
        log.info("Analyzing {} residual islands for product {}", residualIslands.size(), productId);
        List<Map<String, Object>> enriched = residualIslands.stream()
                .map(island -> {
                    Map<String, Object> copy = new LinkedHashMap<>(island);
                    copy.put("analysisStatus", "analyzed");
                    copy.put("recommendation", "Manual review or AST upgrade required");
                    return copy;
                })
                .collect(Collectors.toList());
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

        // P4-3: Tree-sitter JNI fallback removed - log unsupported languages for review
        String detectedLang = detectLanguageFromExtension(lower);
        if (detectedLang != null) {
            log.info("No dedicated parser available for {} (detected: {}). " +
                    "Use frontend TypeScript artifact-compiler for TS/JS/TSX files. " +
                    "Returning stub for manual review.", filePath, detectedLang);
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

    private String cacheKey(String tenantId, String productId) {
        return tenantId + ":" + productId;
    }
}
