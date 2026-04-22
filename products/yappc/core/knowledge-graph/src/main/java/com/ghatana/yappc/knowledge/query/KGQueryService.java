package com.ghatana.yappc.knowledge.query;

import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import com.ghatana.yappc.knowledge.persistence.KGEdgeRepository;
import com.ghatana.yappc.knowledge.persistence.KGNodeRepository;
import io.activej.promise.Promise;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Provides traversal, path discovery, and semantic search over tenant-scoped knowledge graph data.
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KGQueryService {

  private static final int DEFAULT_MAX_PATH_HOPS = 5;

  private final KGNodeRepository nodeRepository;
  private final KGEdgeRepository edgeRepository;
  private final KGSemanticSearchService semanticSearchService;

  public KGQueryService(
      KGNodeRepository nodeRepository,
      KGEdgeRepository edgeRepository,
      KGSemanticSearchService semanticSearchService) {
    this.nodeRepository = Objects.requireNonNull(nodeRepository, "nodeRepository");
    this.edgeRepository = Objects.requireNonNull(edgeRepository, "edgeRepository");
    this.semanticSearchService = Objects.requireNonNull(semanticSearchService, "semanticSearchService");
  }

  public Promise<List<YAPPCGraphNode>> traverse(String nodeId, int maxHops, String tenantId) {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(tenantId, "tenantId");
    if (maxHops <= 0) {
      return Promise.of(List.of());
    }
    return collectReachable(Set.of(nodeId), maxHops, tenantId, new LinkedHashSet<>())
        .then(ids -> ids.isEmpty() ? Promise.of(List.of()) : nodeRepository.findNodesByIds(List.copyOf(ids), tenantId));
  }

  public Promise<List<KGSemanticSearchService.SemanticNodeMatch>> semanticSearch(
      String query, String tenantId, int limit, double minSimilarity) {
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(tenantId, "tenantId");
    if (query.isBlank()) {
      return Promise.of(List.of());
    }
    return semanticSearchService.findSimilarNodes(query, tenantId, limit, minSimilarity);
  }

  public Promise<List<List<YAPPCGraphNode>>> findPaths(String fromNodeId, String toNodeId, String tenantId) {
    Objects.requireNonNull(fromNodeId, "fromNodeId");
    Objects.requireNonNull(toNodeId, "toNodeId");
    Objects.requireNonNull(tenantId, "tenantId");
    return discoverPaths(List.of(List.of(fromNodeId)), toNodeId, tenantId, DEFAULT_MAX_PATH_HOPS)
        .then(paths -> mapPaths(paths, tenantId));
  }

  // ========== Artifact-Specific Pattern Queries ==========

  /**
   * Find artifact nodes that have no incoming edges (orphaned/unreferenced components, pages, etc.).
   */
  public Promise<List<YAPPCGraphNode>> findOrphanedArtifacts(String tenantId) {
    Objects.requireNonNull(tenantId, "tenantId");
    return nodeRepository.findNodesByType("ARTIFACT_COMPONENT", tenantId, 1000)
        .then(nodes -> edgeRepository.findAllTargetIds(tenantId)
            .map(targetIds -> {
              Set<String> referenced = new LinkedHashSet<>(targetIds);
              return nodes.stream()
                  .filter(n -> !referenced.contains(n.id()))
                  .collect(java.util.stream.Collectors.toList());
            }));
  }

  /**
   * Find all pages that render a given component (or any component in a module).
   */
  public Promise<List<YAPPCGraphNode>> findPagesForComponent(String componentNodeId, String tenantId) {
    Objects.requireNonNull(componentNodeId, "componentNodeId");
    Objects.requireNonNull(tenantId, "tenantId");
    return edgeRepository.findEdgesToTarget(componentNodeId, tenantId, Set.of(YAPPCGraphEdge.YAPPCRelationshipType.RENDERS_IN.name()))
        .then(edges -> {
          List<String> pageIds = edges.stream().map(YAPPCGraphEdge::sourceNodeId).toList();
          return pageIds.isEmpty()
              ? Promise.of(List.<YAPPCGraphNode>of())
              : nodeRepository.findNodesByIds(pageIds, tenantId);
        });
  }

  /**
   * Find the full dependency chain (transitive imports/uses) for an artifact node.
   */
  public Promise<List<YAPPCGraphNode>> findArtifactDependencyChain(String nodeId, String tenantId, int maxHops) {
    Objects.requireNonNull(nodeId, "nodeId");
    Objects.requireNonNull(tenantId, "tenantId");
    return collectReachable(Set.of(nodeId), maxHops > 0 ? maxHops : DEFAULT_MAX_PATH_HOPS, tenantId, new LinkedHashSet<>())
        .then(ids -> ids.isEmpty() ? Promise.of(List.<YAPPCGraphNode>of()) : nodeRepository.findNodesByIds(List.copyOf(ids), tenantId));
  }

  /**
   * Find artifacts by type filter (e.g. COMPONENT, PAGE, STATE_STORE).
   */
  public Promise<List<YAPPCGraphNode>> findArtifactsByType(String artifactTypePrefix, String tenantId, int limit) {
    Objects.requireNonNull(artifactTypePrefix, "artifactTypePrefix");
    Objects.requireNonNull(tenantId, "tenantId");
    return nodeRepository.findNodesByType(artifactTypePrefix, tenantId, limit);
  }

  /**
   * Find all circular dependency chains among artifacts.
   */
  public Promise<List<List<String>>> findArtifactCycles(String tenantId) {
    Objects.requireNonNull(tenantId, "tenantId");
    return edgeRepository.findArtifactEdges(tenantId)
        .map(edges -> {
          Map<String, List<String>> adjacency = new LinkedHashMap<>();
          for (YAPPCGraphEdge edge : edges) {
            adjacency.computeIfAbsent(edge.sourceNodeId(), k -> new ArrayList<>()).add(edge.targetNodeId());
          }
          List<List<String>> cycles = new ArrayList<>();
          Set<String> visited = new LinkedHashSet<>();
          Set<String> recStack = new LinkedHashSet<>();
          for (String node : adjacency.keySet()) {
            if (!visited.contains(node)) {
              detectCycle(node, adjacency, visited, recStack, new ArrayList<>(), cycles);
            }
          }
          return cycles;
        });
  }

  private void detectCycle(String node, Map<String, List<String>> adjacency, Set<String> visited, Set<String> recStack, List<String> path, List<List<String>> cycles) {
    visited.add(node);
    recStack.add(node);
    path.add(node);
    List<String> neighbors = adjacency.getOrDefault(node, List.of());
    for (String neighbor : neighbors) {
      if (!visited.contains(neighbor)) {
        detectCycle(neighbor, adjacency, visited, recStack, path, cycles);
      } else if (recStack.contains(neighbor)) {
        // Found a cycle; extract cycle nodes from path
        int index = path.indexOf(neighbor);
        if (index >= 0) {
          List<String> cycle = new ArrayList<>(path.subList(index, path.size()));
          cycles.add(List.copyOf(cycle));
        }
      }
    }
    path.remove(path.size() - 1);
    recStack.remove(node);
  }

  private Promise<LinkedHashSet<String>> collectReachable(
      Set<String> frontier, int hopsRemaining, String tenantId, LinkedHashSet<String> visited) {
    if (frontier.isEmpty() || hopsRemaining == 0) {
      return Promise.of(visited);
    }

    return fetchEdges(List.copyOf(frontier), tenantId)
        .then(
            edges -> {
              LinkedHashSet<String> nextFrontier = new LinkedHashSet<>();
              for (YAPPCGraphEdge edge : edges) {
                if (visited.add(edge.targetNodeId())) {
                  nextFrontier.add(edge.targetNodeId());
                }
              }
              return collectReachable(nextFrontier, hopsRemaining - 1, tenantId, visited);
            });
  }

  private Promise<List<YAPPCGraphEdge>> fetchEdges(List<String> sourceIds, String tenantId) {
    return fetchEdges(sourceIds, tenantId, 0, new ArrayList<>());
  }

  private Promise<List<YAPPCGraphEdge>> fetchEdges(
      List<String> sourceIds, String tenantId, int index, List<YAPPCGraphEdge> accumulated) {
    if (index >= sourceIds.size()) {
      return Promise.of(List.copyOf(accumulated));
    }
    return edgeRepository
        .findEdgesFromSource(sourceIds.get(index), tenantId, Set.of())
        .then(
            edges -> {
              accumulated.addAll(edges);
              return fetchEdges(sourceIds, tenantId, index + 1, accumulated);
            });
  }

  private Promise<List<List<String>>> discoverPaths(
      List<List<String>> frontier, String targetNodeId, String tenantId, int hopsRemaining) {
    if (frontier.isEmpty() || hopsRemaining == 0) {
      return Promise.of(List.of());
    }

    List<List<String>> matches =
        frontier.stream().filter(path -> path.get(path.size() - 1).equals(targetNodeId)).toList();
    if (!matches.isEmpty()) {
      return Promise.of(matches);
    }

    List<String> tailIds = frontier.stream().map(path -> path.get(path.size() - 1)).toList();
    return fetchEdges(tailIds, tenantId)
        .then(
            edges -> {
              Map<String, List<YAPPCGraphEdge>> bySource = new LinkedHashMap<>();
              for (YAPPCGraphEdge edge : edges) {
                bySource.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>()).add(edge);
              }

              List<List<String>> nextFrontier = new ArrayList<>();
              for (List<String> path : frontier) {
                List<YAPPCGraphEdge> outgoing = bySource.getOrDefault(path.get(path.size() - 1), List.of());
                for (YAPPCGraphEdge edge : outgoing) {
                  if (!path.contains(edge.targetNodeId())) {
                    List<String> nextPath = new ArrayList<>(path);
                    nextPath.add(edge.targetNodeId());
                    nextFrontier.add(List.copyOf(nextPath));
                  }
                }
              }
              return discoverPaths(nextFrontier, targetNodeId, tenantId, hopsRemaining - 1);
            });
  }

  private Promise<List<List<YAPPCGraphNode>>> mapPaths(List<List<String>> paths, String tenantId) {
    if (paths.isEmpty()) {
      return Promise.of(List.of());
    }
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    for (List<String> path : paths) {
      ids.addAll(path);
    }
    return nodeRepository
        .findNodesByIds(List.copyOf(ids), tenantId)
        .map(
            nodes -> {
              Map<String, YAPPCGraphNode> nodesById = new LinkedHashMap<>();
              for (YAPPCGraphNode node : nodes) {
                nodesById.put(node.id(), node);
              }
              List<List<YAPPCGraphNode>> mapped = new ArrayList<>();
              for (List<String> path : paths) {
                ArrayDeque<YAPPCGraphNode> resolved = new ArrayDeque<>();
                for (String id : path) {
                  YAPPCGraphNode node = nodesById.get(id);
                  if (node != null) {
                    resolved.add(node);
                  }
                }
                mapped.add(List.copyOf(resolved));
              }
              return List.copyOf(mapped);
            });
  }
}
