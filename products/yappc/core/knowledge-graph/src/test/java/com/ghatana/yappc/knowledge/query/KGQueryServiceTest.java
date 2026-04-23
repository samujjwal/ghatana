package com.ghatana.yappc.knowledge.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import com.ghatana.yappc.knowledge.persistence.KGEdgeRepository;
import com.ghatana.yappc.knowledge.persistence.KGNodeRepository;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@DisplayName("KGQueryService Tests")
class KGQueryServiceTest extends EventloopTestBase {

  @Mock private KGNodeRepository nodeRepository;
  @Mock private KGEdgeRepository edgeRepository;
  @Mock private KGSemanticSearchService semanticSearchService;

  private KGQueryService service;

  @BeforeEach
  void setUp() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
    service = new KGQueryService(nodeRepository, edgeRepository, semanticSearchService); // GH-90000
  }

  @Test
  @DisplayName("traverse returns empty list when max hops is nonpositive")
  void traverseReturnsEmptyListWhenMaxHopsIsNonpositive() { // GH-90000
    assertThat(runPromise(() -> service.traverse("node-a", 0, "tenant-a"))).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("traverse follows outgoing edges across hops")
  void traverseFollowsOutgoingEdgesAcrossHops() { // GH-90000
    YAPPCGraphEdge edge1 = edge("node-a", "node-b"); // GH-90000
    YAPPCGraphEdge edge2 = edge("node-b", "node-c"); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())).thenReturn(Promise.of(List.of(edge1))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())).thenReturn(Promise.of(List.of(edge2))); // GH-90000
    when(nodeRepository.findNodesByIds(List.of("node-b", "node-c"), "tenant-a")) // GH-90000
        .thenReturn(Promise.of(List.of(node("node-b"), node("node-c"))));

    List<YAPPCGraphNode> result = runPromise(() -> service.traverse("node-a", 2, "tenant-a")); // GH-90000

    assertThat(result).extracting(YAPPCGraphNode::id).containsExactly("node-b", "node-c"); // GH-90000
  }

  @Test
  @DisplayName("traverse returns empty when there are no reachable nodes")
  void traverseReturnsEmptyWhenThereAreNoReachableNodes() { // GH-90000
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())).thenReturn(Promise.of(List.of())); // GH-90000

    assertThat(runPromise(() -> service.traverse("node-a", 2, "tenant-a"))).isEmpty(); // GH-90000
    verifyNoInteractions(nodeRepository); // GH-90000
  }

  @Test
  @DisplayName("traverse stops when hop limit is reached before the frontier is exhausted")
  void traverseStopsWhenHopLimitIsReachedBeforeTheFrontierIsExhausted() { // GH-90000
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b")))); // GH-90000
    when(nodeRepository.findNodesByIds(List.of("node-b"), "tenant-a"))
        .thenReturn(Promise.of(List.of(node("node-b"))));

    List<YAPPCGraphNode> result = runPromise(() -> service.traverse("node-a", 1, "tenant-a")); // GH-90000

    assertThat(result).extracting(YAPPCGraphNode::id).containsExactly("node-b");
  }

  @Test
  @DisplayName("traverse ignores duplicate targets that were already visited")
  void traverseIgnoresDuplicateTargetsThatWereAlreadyVisited() { // GH-90000
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b"), edge("node-a", "node-b")))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) // GH-90000
      .thenReturn(Promise.of(List.of())); // GH-90000
    when(nodeRepository.findNodesByIds(List.of("node-b"), "tenant-a"))
        .thenReturn(Promise.of(List.of(node("node-b"))));

    List<YAPPCGraphNode> result = runPromise(() -> service.traverse("node-a", 2, "tenant-a")); // GH-90000

    assertThat(result).extracting(YAPPCGraphNode::id).containsExactly("node-b");
  }

  @Test
  @DisplayName("semanticSearch delegates and returns empty for blank queries")
  void semanticSearchDelegatesAndReturnsEmptyForBlankQueries() { // GH-90000
    KGSemanticSearchService.SemanticNodeMatch match =
        new KGSemanticSearchService.SemanticNodeMatch(node("node-b"), 0.92, Map.of("tenantId", "tenant-a"));
    when(semanticSearchService.findSimilarNodes("billing", "tenant-a", 5, 0.75)) // GH-90000
        .thenReturn(Promise.of(List.of(match))); // GH-90000

    assertThat(runPromise(() -> service.semanticSearch(" ", "tenant-a", 5, 0.75))).isEmpty(); // GH-90000
    assertThat(runPromise(() -> service.semanticSearch("billing", "tenant-a", 5, 0.75))).containsExactly(match); // GH-90000
    verify(semanticSearchService).findSimilarNodes("billing", "tenant-a", 5, 0.75); // GH-90000
  }

  @Test
  @DisplayName("findPaths returns node paths and avoids cycles")
  void findPathsReturnsNodePathsAndAvoidsCycles() { // GH-90000
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b"), edge("node-a", "node-c")))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-b", "node-c")))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-c", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-c", "node-a")))); // GH-90000
    when(nodeRepository.findNodesByIds(List.of("node-a", "node-c"), "tenant-a")) // GH-90000
        .thenReturn(Promise.of(List.of(node("node-a"), node("node-b"), node("node-c"))));

    List<List<YAPPCGraphNode>> result =
        runPromise(() -> service.findPaths("node-a", "node-c", "tenant-a")); // GH-90000

    assertThat(result).hasSize(1); // GH-90000
    assertThat(result.getFirst()).extracting(YAPPCGraphNode::id).containsExactly("node-a", "node-c"); // GH-90000
  }

  @Test
  @DisplayName("findPaths returns empty when no path exists")
  void findPathsReturnsEmptyWhenNoPathExists() { // GH-90000
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b")))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of())); // GH-90000

    assertThat(runPromise(() -> service.findPaths("node-a", "node-c", "tenant-a"))).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("findPaths ignores cyclic edges already present in the current path")
  void findPathsIgnoresCyclicEdgesAlreadyPresentInTheCurrentPath() { // GH-90000
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b")))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-b", "node-a")))); // GH-90000

    assertThat(runPromise(() -> service.findPaths("node-a", "node-z", "tenant-a"))).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("findPaths stops when the search depth budget is exhausted")
  void findPathsStopsWhenTheSearchDepthBudgetIsExhausted() { // GH-90000
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b")))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-b", "node-c")))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-c", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-c", "node-d")))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-d", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-d", "node-e")))); // GH-90000
    when(edgeRepository.findEdgesFromSource("node-e", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-e", "node-f")))); // GH-90000

    assertThat(runPromise(() -> service.findPaths("node-a", "node-z", "tenant-a"))).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("findPaths returns the trivial path when source equals target")
  void findPathsReturnsTheTrivialPathWhenSourceEqualsTarget() { // GH-90000
    when(nodeRepository.findNodesByIds(List.of("node-a"), "tenant-a"))
        .thenReturn(Promise.of(List.of(node("node-a"))));

    List<List<YAPPCGraphNode>> result = runPromise(() -> service.findPaths("node-a", "node-a", "tenant-a")); // GH-90000

    assertThat(result).singleElement().satisfies(path -> assertThat(path).extracting(YAPPCGraphNode::id).containsExactly("node-a"));
  }

  @Test
  @DisplayName("findPaths drops ids that cannot be resolved back to nodes")
  void findPathsDropsIdsThatCannotBeResolvedBackToNodes() { // GH-90000
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) // GH-90000
        .thenReturn(Promise.of(List.of(edge("node-a", "node-c")))); // GH-90000
    when(nodeRepository.findNodesByIds(List.of("node-a", "node-c"), "tenant-a")) // GH-90000
        .thenReturn(Promise.of(List.of(node("node-a"))));

    List<List<YAPPCGraphNode>> result = runPromise(() -> service.findPaths("node-a", "node-c", "tenant-a")); // GH-90000

    assertThat(result).singleElement().satisfies(path -> assertThat(path).extracting(YAPPCGraphNode::id).containsExactly("node-a"));
  }

  private static YAPPCGraphNode node(String id) { // GH-90000
    return YAPPCGraphNode.builder() // GH-90000
        .id(id) // GH-90000
        .type(YAPPCGraphNode.YAPPCNodeType.COMPONENT) // GH-90000
        .name(id) // GH-90000
        .description(id) // GH-90000
        .properties(Map.of()) // GH-90000
        .tags(Set.of()) // GH-90000
        .metadata(new YAPPCGraphMetadata("tenant-a", "project-a", "workspace-a", "tester", Instant.EPOCH, Instant.EPOCH, "1", Map.of())) // GH-90000
        .build(); // GH-90000
  }

  private static YAPPCGraphEdge edge(String sourceId, String targetId) { // GH-90000
    return YAPPCGraphEdge.builder() // GH-90000
        .id(sourceId + '-' + targetId) // GH-90000
        .sourceNodeId(sourceId) // GH-90000
        .targetNodeId(targetId) // GH-90000
        .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.DEPENDS_ON) // GH-90000
        .properties(Map.of()) // GH-90000
        .metadata(new YAPPCGraphMetadata("tenant-a", "project-a", "workspace-a", "tester", Instant.EPOCH, Instant.EPOCH, "1", Map.of())) // GH-90000
        .build(); // GH-90000
  }
}
