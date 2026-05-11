/**
 * Canvas Validation Service Tests
 * 
 * Production-grade tests for canvas validation service.
 * Tests semantic zoom and drill-down functionality.
 * 
 * @doc.type test
 * @doc.purpose Canvas validation tests
 * @doc.layer test
 * @doc.pattern Service Test
 */

package com.ghatana.yappc.services.canvas;

import com.ghatana.yappc.api.CanvasDocument;
import com.ghatana.yappc.services.canvas.CanvasValidationService.CanvasValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-grade tests for canvas validation service.
 */
@DisplayName("Canvas Validation Service Tests")
class CanvasValidationServiceTest {

    private CanvasValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new CanvasValidationServiceImpl();
    }

    @Test
    @DisplayName("Should validate a valid canvas document")
    void shouldValidateValidCanvasDocument() {
        CanvasDocument document = createValidCanvasDocument();

        CanvasValidationResult result = validationService.validate(document);

        assertTrue(result.isValid(), "Document should be valid");
        assertTrue(result.errors().isEmpty(), "Document should have no errors");
    }

    @Test
    @DisplayName("Should reject canvas document with missing required fields")
    void shouldRejectCanvasDocumentWithMissingRequiredFields() {
        CanvasDocument document = new CanvasDocument(
                "", // Missing ID
                "project-1",
                "workspace-1",
                "tenant-1",
                "1.0.0",
                createValidMetadata(),
                List.of(),
                List.of(),
                createValidViewport(),
                createValidDocumentState(),
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1",
                0L
        );

        CanvasValidationResult result = validationService.validate(document);

        assertFalse(result.isValid(), "Document should be invalid");
        assertFalse(result.errors().isEmpty(), "Document should have errors");
        assertTrue(result.errors().contains("Canvas document ID is required"));
    }

    @Test
    @DisplayName("Should reject canvas document with duplicate node IDs")
    void shouldRejectCanvasDocumentWithDuplicateNodeIds() {
        CanvasDocument.CanvasNode node1 = createValidNode("node-1");
        CanvasDocument.CanvasNode node2 = createValidNode("node-1"); // Duplicate ID

        CanvasDocument document = new CanvasDocument(
                "canvas-1",
                "project-1",
                "workspace-1",
                "tenant-1",
                "1.0.0",
                createValidMetadata(),
                List.of(node1, node2),
                List.of(),
                createValidViewport(),
                createValidDocumentState(),
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1",
                0L
        );

        CanvasValidationResult result = validationService.validate(document);

        assertFalse(result.isValid(), "Document should be invalid");
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("Duplicate node ID")));
    }

    @Test
    @DisplayName("Should reject canvas edge referencing non-existent node")
    void shouldRejectCanvasEdgeReferencingNonExistentNode() {
        CanvasDocument.CanvasNode node = createValidNode("node-1");
        CanvasDocument.CanvasEdge edge = createValidEdge("edge-1", "node-1", "node-2"); // node-2 doesn't exist

        CanvasDocument document = new CanvasDocument(
                "canvas-1",
                "project-1",
                "workspace-1",
                "tenant-1",
                "1.0.0",
                createValidMetadata(),
                List.of(node),
                List.of(edge),
                createValidViewport(),
                createValidDocumentState(),
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1",
                0L
        );

        CanvasValidationResult result = validationService.validate(document);

        assertFalse(result.isValid(), "Document should be invalid");
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("non-existent target node")));
    }

    @Test
    @DisplayName("Should reject canvas edge connecting node to itself")
    void shouldRejectCanvasEdgeConnectingNodeToItself() {
        CanvasDocument.CanvasEdge edge = createValidEdge("edge-1", "node-1", "node-1"); // Same node

        CanvasValidationResult result = validationService.validateEdge(edge);

        assertFalse(result.isValid(), "Edge should be invalid");
        assertTrue(result.errors().contains("Edge cannot connect a node to itself"));
    }

    @Test
    @DisplayName("Should reject canvas node with negative position")
    void shouldRejectCanvasNodeWithNegativePosition() {
        CanvasDocument.CanvasNode node = new CanvasDocument.CanvasNode(
                "node-1",
                "default",
                "artifact-1",
                new CanvasDocument.CanvasPoint(-10, 0), // Negative x
                createValidBounds(),
                createValidTransform(),
                createValidNodeData(),
                createValidNodeStyle(),
                createValidNodeState(),
                Set.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        CanvasValidationResult result = validationService.validateNode(node);

        assertFalse(result.isValid(), "Node should be invalid");
        assertTrue(result.errors().contains("Node position cannot be negative"));
    }

    @Test
    @DisplayName("Should reject canvas node with invalid bounds")
    void shouldRejectCanvasNodeWithInvalidBounds() {
        CanvasDocument.CanvasNode node = new CanvasDocument.CanvasNode(
                "node-1",
                "default",
                "artifact-1",
                createValidPoint(),
                new CanvasDocument.CanvasBounds(0, 0, -100, 50), // Negative width
                createValidTransform(),
                createValidNodeData(),
                createValidNodeStyle(),
                createValidNodeState(),
                Set.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        CanvasValidationResult result = validationService.validateNode(node);

        assertFalse(result.isValid(), "Node should be invalid");
        assertTrue(result.errors().contains("Node bounds must be positive"));
    }

    @Test
    @DisplayName("Should reject canvas node with invalid transform scale")
    void shouldRejectCanvasNodeWithInvalidTransformScale() {
        CanvasDocument.CanvasNode node = new CanvasDocument.CanvasNode(
                "node-1",
                "default",
                "artifact-1",
                createValidPoint(),
                createValidBounds(),
                new CanvasDocument.CanvasTransform(createValidPoint(), 0, 0), // Zero scale
                createValidNodeData(),
                createValidNodeStyle(),
                createValidNodeState(),
                Set.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        CanvasValidationResult result = validationService.validateNode(node);

        assertFalse(result.isValid(), "Node should be invalid");
        assertTrue(result.errors().contains("Node transform scale must be positive"));
    }

    // Helper methods to create valid test data

    private CanvasDocument createValidCanvasDocument() {
        return new CanvasDocument(
                "canvas-1",
                "project-1",
                "workspace-1",
                "tenant-1",
                "1.0.0",
                createValidMetadata(),
                List.of(createValidNode("node-1"), createValidNode("node-2")),
                List.of(createValidEdge("edge-1", "node-1", "node-2")),
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
                "Test Canvas",
                "Test Description",
                "intent",
                Set.of("tag1", "tag2"),
                Map.of("key1", "value1")
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
                createValidPoint(),
                createValidBounds(),
                createValidTransform(),
                createValidNodeData(),
                createValidNodeStyle(),
                createValidNodeState(),
                Set.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private CanvasDocument.CanvasPoint createValidPoint() {
        return new CanvasDocument.CanvasPoint(100, 100);
    }

    private CanvasDocument.CanvasBounds createValidBounds() {
        return new CanvasDocument.CanvasBounds(0, 0, 100, 50);
    }

    private CanvasDocument.CanvasTransform createValidTransform() {
        return new CanvasDocument.CanvasTransform(
                new CanvasDocument.CanvasPoint(0, 0),
                1.0,
                0.0
        );
    }

    private CanvasDocument.CanvasNodeData createValidNodeData() {
        return new CanvasDocument.CanvasNodeData(
                "Node Label",
                "Node Description",
                Map.of("key", "value")
        );
    }

    private CanvasDocument.CanvasNodeStyle createValidNodeStyle() {
        return new CanvasDocument.CanvasNodeStyle(
                "#ffffff",
                "#000000",
                1.0,
                "#000000",
                "Arial",
                12.0,
                4.0,
                1.0
        );
    }

    private CanvasDocument.CanvasNodeState createValidNodeState() {
        return new CanvasDocument.CanvasNodeState(
                CanvasDocument.CanvasNodeState.NodeStatus.NORMAL,
                true,
                false,
                false
        );
    }

    private CanvasDocument.CanvasEdge createValidEdge(String id, String sourceId, String targetId) {
        return new CanvasDocument.CanvasEdge(
                id,
                sourceId,
                targetId,
                "source-handle",
                "target-handle",
                List.of(createValidPoint(), createValidPoint()),
                createValidEdgeStyle(),
                createValidEdgeData(),
                createValidEdgeState(),
                Instant.now(),
                Instant.now()
        );
    }

    private CanvasDocument.CanvasEdgeStyle createValidEdgeStyle() {
        return new CanvasDocument.CanvasEdgeStyle(
                "#000000",
                1.0,
                "solid",
                "arrow",
                null,
                1.0
        );
    }

    private CanvasDocument.CanvasEdgeData createValidEdgeData() {
        return new CanvasDocument.CanvasEdgeData(
                "Edge Label",
                "Edge Description",
                Map.of("key", "value")
        );
    }

    private CanvasDocument.CanvasEdgeState createValidEdgeState() {
        return new CanvasDocument.CanvasEdgeState(
                CanvasDocument.CanvasEdgeState.EdgeStatus.NORMAL,
                true,
                false
        );
    }
}
