/**
 * Canvas Performance Tests
 * 
 * Production-grade performance tests for large canvas documents.
 * Ensures canvas operations remain performant with large datasets.
 * 
 * @doc.type test
 * @doc.purpose Canvas performance tests
 * @doc.layer test
 * @doc.pattern Performance Test
 */

package com.ghatana.yappc.services.canvas;

import com.ghatana.yappc.api.CanvasDocument;
import com.ghatana.yappc.services.canvas.CanvasValidationService.CanvasValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-grade performance tests for canvas operations with large datasets.
 */
@DisplayName("Canvas Performance Tests")
class CanvasPerformanceTest {

    private CanvasValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new CanvasValidationServiceImpl();
    }

    @Test
    @DisplayName("Should validate canvas with 100 nodes in under 100ms")
    void shouldValidateCanvasWith100NodesInUnder100ms() {
        List<CanvasDocument.CanvasNode> nodes = createNodes(100);
        CanvasDocument document = createCanvasDocument(nodes, List.of());

        long startTime = System.currentTimeMillis();
        CanvasValidationResult result = validationService.validate(document);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(result.isValid(), "Document should be valid");
        assertTrue(duration < 100, "Validation should complete in under 100ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("Should validate canvas with 1000 nodes in under 500ms")
    void shouldValidateCanvasWith1000NodesInUnder500ms() {
        List<CanvasDocument.CanvasNode> nodes = createNodes(1000);
        CanvasDocument document = createCanvasDocument(nodes, List.of());

        long startTime = System.currentTimeMillis();
        CanvasValidationResult result = validationService.validate(document);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(result.isValid(), "Document should be valid");
        assertTrue(duration < 500, "Validation should complete in under 500ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("Should validate canvas with 5000 nodes in under 2000ms")
    void shouldValidateCanvasWith5000NodesInUnder2000ms() {
        List<CanvasDocument.CanvasNode> nodes = createNodes(5000);
        CanvasDocument document = createCanvasDocument(nodes, List.of());

        long startTime = System.currentTimeMillis();
        CanvasValidationResult result = validationService.validate(document);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(result.isValid(), "Document should be valid");
        assertTrue(duration < 2000, "Validation should complete in under 2000ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("Should validate canvas with 1000 edges in under 300ms")
    void shouldValidateCanvasWith1000EdgesInUnder300ms() {
        List<CanvasDocument.CanvasNode> nodes = createNodes(100);
        List<CanvasDocument.CanvasEdge> edges = createEdges(1000, nodes);
        CanvasDocument document = createCanvasDocument(nodes, edges);

        long startTime = System.currentTimeMillis();
        CanvasValidationResult result = validationService.validate(document);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(result.isValid(), "Document should be valid");
        assertTrue(duration < 300, "Validation should complete in under 300ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("Should validate canvas with 10000 edges in under 2000ms")
    void shouldValidateCanvasWith10000EdgesInUnder2000ms() {
        List<CanvasDocument.CanvasNode> nodes = createNodes(500);
        List<CanvasDocument.CanvasEdge> edges = createEdges(10000, nodes);
        CanvasDocument document = createCanvasDocument(nodes, edges);

        long startTime = System.currentTimeMillis();
        CanvasValidationResult result = validationService.validate(document);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(result.isValid(), "Document should be valid");
        assertTrue(duration < 2000, "Validation should complete in under 2000ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("Should handle large canvas memory efficiently")
    void shouldHandleLargeCanvasMemoryEfficiently() {
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        List<CanvasDocument.CanvasNode> nodes = createNodes(10000);
        List<CanvasDocument.CanvasEdge> edges = createEdges(20000, nodes);
        CanvasDocument document = createCanvasDocument(nodes, edges);

        CanvasValidationResult result = validationService.validate(document);

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        assertTrue(result.isValid(), "Document should be valid");
        assertTrue(memoryUsed < 100_000_000, 
                "Memory usage should be under 100MB, used: " + (memoryUsed / 1024 / 1024) + "MB");
    }

    // Helper methods to create test data

    private List<CanvasDocument.CanvasNode> createNodes(int count) {
        List<CanvasDocument.CanvasNode> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            nodes.add(createValidNode("node-" + i));
        }
        return nodes;
    }

    private List<CanvasDocument.CanvasEdge> createEdges(int count, List<CanvasDocument.CanvasNode> nodes) {
        List<CanvasDocument.CanvasEdge> edges = new ArrayList<>();
        int nodeCount = nodes.size();
        for (int i = 0; i < count; i++) {
            String sourceId = nodes.get(i % nodeCount).id();
            String targetId = nodes.get((i + 1) % nodeCount).id();
            edges.add(createValidEdge("edge-" + i, sourceId, targetId));
        }
        return edges;
    }

    private CanvasDocument createCanvasDocument(
            List<CanvasDocument.CanvasNode> nodes,
            List<CanvasDocument.CanvasEdge> edges
    ) {
        return new CanvasDocument(
                "canvas-1",
                "project-1",
                "workspace-1",
                "tenant-1",
                "1.0.0",
                createValidMetadata(),
                nodes,
                edges,
                createValidViewport(),
                createValidDocumentState(),
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1",
                0L
        );
    }

    private CanvasDocument.CanvasMetadata createValidMetadata() {
        return new CanvasDocument.CanvasMetadata(
                "Performance Test Canvas",
                "Canvas for performance testing",
                "intent",
                Set.of("performance", "test"),
                Map.of("test", "true")
        );
    }

    private CanvasDocument.CanvasViewport createValidViewport() {
        return new CanvasDocument.CanvasViewport(
                new CanvasDocument.CanvasPoint(0, 0),
                1.0,
                0.0
        );
    }

    private CanvasDocument.CanvasDocumentState createValidDocumentState() {
        return new CanvasDocument.CanvasDocumentState(
                CanvasDocument.CanvasDocumentState.DocumentStatus.DRAFT,
                false,
                true,
                List.of(),
                null,
                null
        );
    }

    private CanvasDocument.CanvasNode createValidNode(String id) {
        return new CanvasDocument.CanvasNode(
                id,
                "default",
                "artifact-" + id,
                new CanvasDocument.CanvasPoint(
                        Math.random() * 1000,
                        Math.random() * 1000
                ),
                new CanvasDocument.CanvasBounds(0, 0, 100, 50),
                new CanvasDocument.CanvasTransform(
                        new CanvasDocument.CanvasPoint(0, 0),
                        1.0,
                        0.0
                ),
                new CanvasDocument.CanvasNodeData(
                        "Node " + id,
                        "Test node",
                        Map.of("index", id)
                ),
                new CanvasDocument.CanvasNodeStyle(
                        "#ffffff",
                        "#000000",
                        1.0,
                        "#000000",
                        "Arial",
                        12.0,
                        4.0,
                        1.0
                ),
                new CanvasDocument.CanvasNodeState(
                        CanvasDocument.CanvasNodeState.NodeStatus.NORMAL,
                        true,
                        false,
                        false
                ),
                Set.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private CanvasDocument.CanvasEdge createValidEdge(String id, String sourceId, String targetId) {
        return new CanvasDocument.CanvasEdge(
                id,
                sourceId,
                targetId,
                "source-handle",
                "target-handle",
                List.of(
                        new CanvasDocument.CanvasPoint(0, 0),
                        new CanvasDocument.CanvasPoint(100, 100)
                ),
                new CanvasDocument.CanvasEdgeStyle(
                        "#000000",
                        1.0,
                        "solid",
                        "arrow",
                        null,
                        1.0
                ),
                new CanvasDocument.CanvasEdgeData(
                        "Edge " + id,
                        "Test edge",
                        Map.of("index", id)
                ),
                new CanvasDocument.CanvasEdgeState(
                        CanvasDocument.CanvasEdgeState.EdgeStatus.NORMAL,
                        true,
                        false
                ),
                Instant.now(),
                Instant.now()
        );
    }
}
