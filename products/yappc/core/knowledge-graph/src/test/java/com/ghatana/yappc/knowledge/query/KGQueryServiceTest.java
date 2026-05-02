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
  void setUp() { 
    MockitoAnnotations.openMocks(this); 
    service = new KGQueryService(nodeRepository, edgeRepository, semanticSearchService); 
  }

  @Test
  @DisplayName("traverse returns empty list when max hops is nonpositive")
  void traverseReturnsEmptyListWhenMaxHopsIsNonpositive() { 
    assertThat(runPromise(() -> service.traverse("node-a", 0, "tenant-a"))).isEmpty(); 
  }

  @Test
  @DisplayName("traverse follows outgoing edges across hops")
  void traverseFollowsOutgoingEdgesAcrossHops() { 
    YAPPCGraphEdge edge1 = edge("node-a", "node-b"); 
    YAPPCGraphEdge edge2 = edge("node-b", "node-c"); 
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())).thenReturn(Promise.of(List.of(edge1))); 
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())).thenReturn(Promise.of(List.of(edge2))); 
    when(nodeRepository.findNodesByIds(List.of("node-b", "node-c"), "tenant-a")) 
        .thenReturn(Promise.of(List.of(node("node-b"), node("node-c"))));

    List<YAPPCGraphNode> result = runPromise(() -> service.traverse("node-a", 2, "tenant-a")); 

    assertThat(result).extracting(YAPPCGraphNode::id).containsExactly("node-b", "node-c"); 
  }

  @Test
  @DisplayName("traverse returns empty when there are no reachable nodes")
  void traverseReturnsEmptyWhenThereAreNoReachableNodes() { 
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())).thenReturn(Promise.of(List.of())); 

    assertThat(runPromise(() -> service.traverse("node-a", 2, "tenant-a"))).isEmpty(); 
    verifyNoInteractions(nodeRepository); 
  }

  @Test
  @DisplayName("traverse stops when hop limit is reached before the frontier is exhausted")
  void traverseStopsWhenHopLimitIsReachedBeforeTheFrontierIsExhausted() { 
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b")))); 
    when(nodeRepository.findNodesByIds(List.of("node-b"), "tenant-a"))
        .thenReturn(Promise.of(List.of(node("node-b"))));

    List<YAPPCGraphNode> result = runPromise(() -> service.traverse("node-a", 1, "tenant-a")); 

    assertThat(result).extracting(YAPPCGraphNode::id).containsExactly("node-b");
  }

  @Test
  @DisplayName("traverse ignores duplicate targets that were already visited")
  void traverseIgnoresDuplicateTargetsThatWereAlreadyVisited() { 
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b"), edge("node-a", "node-b")))); 
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) 
      .thenReturn(Promise.of(List.of())); 
    when(nodeRepository.findNodesByIds(List.of("node-b"), "tenant-a"))
        .thenReturn(Promise.of(List.of(node("node-b"))));

    List<YAPPCGraphNode> result = runPromise(() -> service.traverse("node-a", 2, "tenant-a")); 

    assertThat(result).extracting(YAPPCGraphNode::id).containsExactly("node-b");
  }

  @Test
  @DisplayName("semanticSearch delegates and returns empty for blank queries")
  void semanticSearchDelegatesAndReturnsEmptyForBlankQueries() { 
    KGSemanticSearchService.SemanticNodeMatch match =
        new KGSemanticSearchService.SemanticNodeMatch(node("node-b"), 0.92, Map.of("tenantId", "tenant-a"));
    when(semanticSearchService.findSimilarNodes("billing", "tenant-a", 5, 0.75)) 
        .thenReturn(Promise.of(List.of(match))); 

    assertThat(runPromise(() -> service.semanticSearch(" ", "tenant-a", 5, 0.75))).isEmpty(); 
    assertThat(runPromise(() -> service.semanticSearch("billing", "tenant-a", 5, 0.75))).containsExactly(match); 
    verify(semanticSearchService).findSimilarNodes("billing", "tenant-a", 5, 0.75); 
  }

  @Test
  @DisplayName("findPaths returns node paths and avoids cycles")
  void findPathsReturnsNodePathsAndAvoidsCycles() { 
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b"), edge("node-a", "node-c")))); 
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-b", "node-c")))); 
    when(edgeRepository.findEdgesFromSource("node-c", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-c", "node-a")))); 
    when(nodeRepository.findNodesByIds(List.of("node-a", "node-c"), "tenant-a")) 
        .thenReturn(Promise.of(List.of(node("node-a"), node("node-b"), node("node-c"))));

    List<List<YAPPCGraphNode>> result =
        runPromise(() -> service.findPaths("node-a", "node-c", "tenant-a")); 

    assertThat(result).hasSize(1); 
    assertThat(result.getFirst()).extracting(YAPPCGraphNode::id).containsExactly("node-a", "node-c"); 
  }

  @Test
  @DisplayName("findPaths returns empty when no path exists")
  void findPathsReturnsEmptyWhenNoPathExists() { 
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b")))); 
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of())); 

    assertThat(runPromise(() -> service.findPaths("node-a", "node-c", "tenant-a"))).isEmpty(); 
  }

  @Test
  @DisplayName("findPaths ignores cyclic edges already present in the current path")
  void findPathsIgnoresCyclicEdgesAlreadyPresentInTheCurrentPath() { 
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b")))); 
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-b", "node-a")))); 

    assertThat(runPromise(() -> service.findPaths("node-a", "node-z", "tenant-a"))).isEmpty(); 
  }

  @Test
  @DisplayName("findPaths stops when the search depth budget is exhausted")
  void findPathsStopsWhenTheSearchDepthBudgetIsExhausted() { 
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-a", "node-b")))); 
    when(edgeRepository.findEdgesFromSource("node-b", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-b", "node-c")))); 
    when(edgeRepository.findEdgesFromSource("node-c", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-c", "node-d")))); 
    when(edgeRepository.findEdgesFromSource("node-d", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-d", "node-e")))); 
    when(edgeRepository.findEdgesFromSource("node-e", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-e", "node-f")))); 

    assertThat(runPromise(() -> service.findPaths("node-a", "node-z", "tenant-a"))).isEmpty(); 
  }

  @Test
  @DisplayName("findPaths returns the trivial path when source equals target")
  void findPathsReturnsTheTrivialPathWhenSourceEqualsTarget() { 
    when(nodeRepository.findNodesByIds(List.of("node-a"), "tenant-a"))
        .thenReturn(Promise.of(List.of(node("node-a"))));

    List<List<YAPPCGraphNode>> result = runPromise(() -> service.findPaths("node-a", "node-a", "tenant-a")); 

    assertThat(result).singleElement().satisfies(path -> assertThat(path).extracting(YAPPCGraphNode::id).containsExactly("node-a"));
  }

  @Test
  @DisplayName("findPaths drops ids that cannot be resolved back to nodes")
  void findPathsDropsIdsThatCannotBeResolvedBackToNodes() { 
    when(edgeRepository.findEdgesFromSource("node-a", "tenant-a", Set.of())) 
        .thenReturn(Promise.of(List.of(edge("node-a", "node-c")))); 
    when(nodeRepository.findNodesByIds(List.of("node-a", "node-c"), "tenant-a")) 
        .thenReturn(Promise.of(List.of(node("node-a"))));

    List<List<YAPPCGraphNode>> result = runPromise(() -> service.findPaths("node-a", "node-c", "tenant-a")); 

    assertThat(result).singleElement().satisfies(path -> assertThat(path).extracting(YAPPCGraphNode::id).containsExactly("node-a"));
  }

  private static YAPPCGraphNode node(String id) { 
    return YAPPCGraphNode.builder() 
        .id(id) 
        .type(YAPPCGraphNode.YAPPCNodeType.COMPONENT) 
        .name(id) 
        .description(id) 
        .properties(Map.of()) 
        .tags(Set.of()) 
        .metadata(new YAPPCGraphMetadata("tenant-a", "project-a", "workspace-a", "tester", Instant.EPOCH, Instant.EPOCH, "1", Map.of())) 
        .build(); 
  }

  private static YAPPCGraphEdge edge(String sourceId, String targetId) { 
    return YAPPCGraphEdge.builder() 
        .id(sourceId + '-' + targetId) 
        .sourceNodeId(sourceId) 
        .targetNodeId(targetId) 
        .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.DEPENDS_ON) 
        .properties(Map.of()) 
        .metadata(new YAPPCGraphMetadata("tenant-a", "project-a", "workspace-a", "tester", Instant.EPOCH, Instant.EPOCH, "1", Map.of())) 
        .build(); 
  }
}
