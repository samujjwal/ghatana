package com.ghatana.yappc.knowledge.benchmark;

import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import com.ghatana.yappc.knowledge.persistence.KGEdgeRepository;
import com.ghatana.yappc.knowledge.persistence.KGNodeRepository;
import com.ghatana.yappc.knowledge.query.KGQueryService;
import com.ghatana.yappc.knowledge.query.KGSemanticSearchService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Performance benchmarks for knowledge-graph query operations.
 *
 * <p>Validates latency targets for the critical KG query paths:
 * <ul>
 *   <li>Single-hop traversal: p99 &lt; 10 ms</li>
 *   <li>Multi-hop traversal (5 hops): p95 &lt; 30 ms</li>
 *   <li>Semantic search (top-10): p95 &lt; 20 ms</li>
 *   <li>findArtifactsByType (100-node result): p95 &lt; 15 ms</li>
 *   <li>Cycle detection (50-node graph): p99 &lt; 50 ms</li>
 * </ul>
 *
 * <p>All benchmarks use deterministic in-memory mocks for repositories to
 * isolate query-layer logic from I/O. Real DB performance is validated separately
 * by {@code KGScaleValidationTest}.
 *
 * @doc.type    class
 * @doc.purpose Knowledge-graph query performance benchmarks
 * @doc.layer   product
 * @doc.pattern Benchmark
 */
@DisplayName("Knowledge Graph Query Benchmarks")
@Tag("benchmark")
@ExtendWith(MockitoExtension.class)
class KGQueryBenchmark {

    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASURED_ITERATIONS = 200;
    private static final String TENANT = "bench-tenant";

    @Mock
    private KGNodeRepository nodeRepository;

    @Mock
    private KGEdgeRepository edgeRepository;

    @Mock
    private KGSemanticSearchService semanticSearchService;

    private KGQueryService queryService;

    // Pre-built node/edge fixtures
    private static List<YAPPCGraphNode> nodes100;
    private static List<YAPPCGraphEdge> edges50;
    private static List<KGSemanticSearchService.SemanticNodeMatch> semanticMatches10;

    @BeforeAll
    static void buildFixtures() {
        nodes100 = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Instant now = Instant.now();
            nodes100.add(YAPPCGraphNode.builder()
                .id("node-" + i)
                .type(YAPPCGraphNode.YAPPCNodeType.ARTIFACT_COMPONENT)
                .name("Component " + i)
                .description("benchmark node")
                .properties(Map.of("index", String.valueOf(i)))
                .tags(Set.of())
                .metadata(new com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata(
                    TENANT, null, null, "benchmark", now, now, "1", Map.of()))
                .build());
        }

        edges50 = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Instant now = Instant.now();
            edges50.add(YAPPCGraphEdge.builder()
                .id(UUID.randomUUID().toString())
                .sourceNodeId("node-" + i)
                .targetNodeId("node-" + (i + 1))
                .relationshipType(YAPPCGraphEdge.YAPPCRelationshipType.USES)
                .properties(Map.of())
                .metadata(new com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata(
                    TENANT, null, null, "benchmark", now, now, "1", Map.of()))
                .build());
        }

        semanticMatches10 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            semanticMatches10.add(new KGSemanticSearchService.SemanticNodeMatch(
                    nodes100.get(i), 0.9 - i * 0.05, Map.of("tenantId", TENANT)));
        }
    }

    private void setUpService() {
        lenient().when(nodeRepository.findNodesByIds(any(), anyString()))
                .thenReturn(Promise.of(nodes100.subList(0, 5)));
        lenient().when(nodeRepository.findNodesByType(anyString(), anyString(), anyInt()))
                .thenReturn(Promise.of(nodes100));
        lenient().when(edgeRepository.findEdgesFromSource(anyString(), anyString(), any()))
                .thenReturn(Promise.of(List.of()));
        lenient().when(edgeRepository.findAllTargetIds(anyString()))
                .thenReturn(Promise.of(List.of()));
        lenient().when(edgeRepository.findArtifactEdges(anyString()))
                .thenReturn(Promise.of(edges50));
        lenient().when(semanticSearchService.findSimilarNodes(anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn(Promise.of(semanticMatches10));

        queryService = new KGQueryService(nodeRepository, edgeRepository, semanticSearchService);
    }

    // -------------------------------------------------------------------------
    // Benchmark 1: Single-hop traversal
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("single-hop traversal: p99 < 10 ms")
    void singleHopTraversalP99Under10ms() {
        setUpService();
        when(edgeRepository.findEdgesFromSource(anyString(), anyString(), any()))
                .thenReturn(Promise.of(List.of()));

        warmUp(() -> queryService.traverse("node-0", 1, TENANT).getResult(), WARMUP_ITERATIONS);

        long[] latenciesNs = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            queryService.traverse("node-" + (i % 100), 1, TENANT).getResult();
            latenciesNs[i] = System.nanoTime() - start;
        }

        long p99Ms = toMs(percentile(latenciesNs, 99));
        assertThat(p99Ms)
                .as("single-hop traversal p99 must be < 10 ms, was %d ms".formatted(p99Ms))
                .isLessThan(10);
    }

    // -------------------------------------------------------------------------
    // Benchmark 2: findArtifactsByType (100 nodes)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findArtifactsByType (100 nodes): p95 < 15 ms")
    void findArtifactsByTypeP95Under15ms() {
        setUpService();

        warmUp(() -> queryService.findArtifactsByType("ARTIFACT_COMPONENT", TENANT, 100).getResult(), WARMUP_ITERATIONS);

        long[] latenciesNs = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            queryService.findArtifactsByType("ARTIFACT_COMPONENT", TENANT, 100).getResult();
            latenciesNs[i] = System.nanoTime() - start;
        }

        long p95Ms = toMs(percentile(latenciesNs, 95));
        assertThat(p95Ms)
                .as("findArtifactsByType p95 must be < 15 ms, was %d ms".formatted(p95Ms))
                .isLessThan(15);
    }

    // -------------------------------------------------------------------------
    // Benchmark 3: Semantic search (top-10)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("semantic search (top-10): p95 < 20 ms")
    void semanticSearchP95Under20ms() {
        setUpService();

        warmUp(() -> queryService.semanticSearch("find payment components", TENANT, 10, 0.7).getResult(), WARMUP_ITERATIONS);

        long[] latenciesNs = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            queryService.semanticSearch("query-" + i, TENANT, 10, 0.7).getResult();
            latenciesNs[i] = System.nanoTime() - start;
        }

        long p95Ms = toMs(percentile(latenciesNs, 95));
        assertThat(p95Ms)
                .as("semantic search p95 must be < 20 ms, was %d ms".formatted(p95Ms))
                .isLessThan(20);
    }

    // -------------------------------------------------------------------------
    // Benchmark 4: Cycle detection (50-edge graph)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cycle detection (50-edge graph): p99 < 50 ms")
    void cycleDetectionP99Under50ms() {
        setUpService();

        warmUp(() -> queryService.findArtifactCycles(TENANT).getResult(), WARMUP_ITERATIONS);

        long[] latenciesNs = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            queryService.findArtifactCycles(TENANT).getResult();
            latenciesNs[i] = System.nanoTime() - start;
        }

        long p99Ms = toMs(percentile(latenciesNs, 99));
        assertThat(p99Ms)
                .as("cycle detection p99 must be < 50 ms, was %d ms".formatted(p99Ms))
                .isLessThan(50);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void warmUp(Runnable task, int iterations) {
        for (int i = 0; i < iterations; i++) {
            task.run();
        }
    }

    private static long percentile(long[] values, int pct) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int index = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static long toMs(long ns) {
        return TimeUnit.NANOSECONDS.toMillis(ns);
    }
}
