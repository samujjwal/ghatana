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
import com.ghatana.yappc.services.artifact.parser.TreeSitterArtifactExtractor;
import io.activej.promise.Promise;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose JGraphT-backed implementation of artifact graph operations with caching
 * @doc.layer service
 * @doc.pattern Service
 */
public class ArtifactGraphServiceImpl implements ArtifactGraphService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGraphServiceImpl.class);

    private final ArtifactGraphRepository repository;
    private final ArtifactModelVersionRepository versionRepository;
    private final Cache<String, List<ArtifactNodeDto>> nodeCache;
    private final Cache<String, List<ArtifactEdgeDto>> edgeCache;

    public ArtifactGraphServiceImpl(ArtifactGraphRepository repository) {
        this(repository, null);
    }

    public ArtifactGraphServiceImpl(ArtifactGraphRepository repository, ArtifactModelVersionRepository versionRepository) {
        this.repository = repository;
        this.versionRepository = versionRepository;
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

        return repository.deleteGraphForProduct(request.productId(), request.tenantId())
                .then(deleted -> repository.saveNodes(request.productId(), request.tenantId(), request.nodes()))
                .then(saved -> repository.saveEdges(request.productId(), request.tenantId(), request.edges()))
                .then(v -> {
                    nodeCache.invalidate(cacheKey);
                    edgeCache.invalidate(cacheKey);
                    if (versionRepository != null) {
                        ArtifactModelVersion version = new ArtifactModelVersion(
                                java.util.UUID.randomUUID().toString(),
                                request.productId(),
                                request.tenantId(),
                                null,
                                "Ingested " + request.nodes().size() + " nodes and " + request.edges().size() + " edges",
                                java.time.Instant.now(),
                                "artifact-compiler",
                                Map.of("nodeTypes", request.nodes().stream().map(ArtifactNodeDto::type).distinct().toList()),
                                request.nodes().size(),
                                request.edges().size(),
                                Map.of()
                        );
                        return versionRepository.saveVersion(version).map(ignored -> new ArtifactGraphResponse(
                                true, "ingest",
                                Map.of("nodeCount", request.nodes().size(), "edgeCount", request.edges().size(), "versionId", version.versionId()),
                                "Artifact graph ingested and versioned successfully"
                        ));
                    }
                    return Promise.of(new ArtifactGraphResponse(
                            true, "ingest",
                            Map.of("nodeCount", request.nodes().size(), "edgeCount", request.edges().size()),
                            "Artifact graph ingested successfully"
                    ));
                })
                .whenException(e -> log.error("Failed to ingest artifact graph for product {}", request.productId(), e));
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

        return nodesPromise.combine(edgesPromise, (nodes, edges) -> {
            nodeCache.put(cacheKey, nodes);
            edgeCache.put(cacheKey, edges);

            List<ArtifactGraphAnalysisResult> results = new ArrayList<>();
            DefaultDirectedGraph<String, DefaultEdge> graph = buildJGraphT(nodes, edges);

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
                        KosarajuStrongConnectivityInspector<String, DefaultEdge> sccInspector =
                                new KosarajuStrongConnectivityInspector<>(graph);
                        List<List<String>> cycles = sccInspector.stronglyConnectedSets().stream()
                                .filter(set -> set.size() > 1)
                                .map(List::copyOf)
                                .collect(Collectors.toList());
                        results.add(new ArtifactGraphAnalysisResult(
                                "strongly-connected-components", Map.of(), cycles, List.of(), List.of(),
                                Map.of("totalSccCount", sccInspector.stronglyConnectedSets().size())
                        ));
                    }
                    case "topological", "build-order" -> {
                        if (!new KosarajuStrongConnectivityInspector<>(graph).isStronglyConnected()) {
                            List<String> order = new ArrayList<>();
                            new TopologicalOrderIterator<>(graph).forEachRemaining(order::add);
                            results.add(new ArtifactGraphAnalysisResult(
                                    "topological-order", Map.of(), List.of(), List.of(), order, Map.of()
                            ));
                        } else {
                            results.add(new ArtifactGraphAnalysisResult(
                                    "topological-order", Map.of(), List.of(), List.of(), List.of(),
                                    Map.of("error", "Graph contains cycles; topological sort not possible")
                            ));
                        }
                    }
                    case "communities" -> {
                        // Louvain/label propagation are complex; use a simple greedy approach for now
                        List<List<String>> communities = greedyCommunityDetection(graph, nodeIds);
                        results.add(new ArtifactGraphAnalysisResult(
                                "greedy-communities", Map.of(), List.of(), communities, List.of(),
                                Map.of("communityCount", communities.size())
                        ));
                    }
                    case "reachability", "paths" -> {
                        AllDirectedPaths<String, DefaultEdge> pathAlg = new AllDirectedPaths<>(graph);
                        Map<String, Object> meta = new HashMap<>();
                        int pathCount = 0;
                        for (String source : nodeIds) {
                            for (String target : nodeIds) {
                                if (!source.equals(target) && graph.containsVertex(source) && graph.containsVertex(target)) {
                                    try {
                                        var paths = pathAlg.getAllPaths(source, target, true, 5);
                                        pathCount += paths.size();
                                    } catch (Exception ignored) { }
                                }
                            }
                        }
                        meta.put("maxPathLength5", pathCount);
                        results.add(new ArtifactGraphAnalysisResult(
                                "reachability", Map.of(), List.of(), List.of(), List.of(), meta
                        ));
                    }
                    default -> log.warn("Unknown analysis algorithm: {}", algorithm);
                }
            }
            return results;
        });
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
     * Parse source text into artifact nodes and edges using the best available
     * language-specific parser. Falls back to the Tree-sitter JNI bridge for
     * languages without a dedicated hand-written parser.
     *
     * @param filePath   relative path of the source file (used for language detection)
     * @param sourceCode raw source text
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

        // Tree-sitter fallback for: TypeScript, JavaScript, Python, Go, Rust, C, C++, etc.
        String lang = detectTreeSitterLanguage(lower);
        if (lang != null) {
            log.info("Using Tree-sitter fallback ({}) for {}", lang, filePath);
            try {
                return TreeSitterArtifactExtractor.extractArtifacts(lang, sourceCode, filePath);
            } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError e) {
                log.warn("Tree-sitter JNI not available for {} — returning raw parse stub", filePath, e);
                return Map.of(
                        "nodes", List.of(Map.of(
                                "id", "ts-fallback://" + filePath,
                                "type", "source_file",
                                "name", filePath,
                                "filePath", filePath,
                                "language", lang,
                                "parseError", e.getClass().getSimpleName() + ": " + e.getMessage()
                        )),
                        "edges", List.of()
                );
            }
        }

        log.warn("No parser available for file: {}", filePath);
        return Map.of("nodes", List.of(), "edges", List.of());
    }

    private String detectTreeSitterLanguage(String lowerPath) {
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
