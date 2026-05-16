package com.ghatana.yappc.domain.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Validation test for ArtifactEdgeDto with P0-6 extended fields
 * @doc.layer test
 * @doc.pattern Test
 * 
 * P0-6: Tests edge validation including edgeId, confidence, bidirectional flag,
 * metadata, snapshot ID, and version ID. Enforces source and target as node IDs.
 */
class ArtifactEdgeDtoValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testEdgeWithP0_6Extensions() throws Exception {
        // Create edge with all P0-6 extended fields
        ArtifactEdgeDto edge = new ArtifactEdgeDto(
            "edge-123",
            "node-source",
            "node-target",
            "REFERENCES",
            Map.of("relationship", "dependency", "strength", "strong"),
            0.95,
            false,
            Map.of("metadata", "test", "source", "manual"),
            "snapshot-abc123",
            "version-def456"
        );

        // Serialize
        String json = objectMapper.writeValueAsString(edge);

        // Deserialize
        ArtifactEdgeDto deserialized = objectMapper.readValue(json, ArtifactEdgeDto.class);

        // Verify all fields
        assertEquals(edge.edgeId(), deserialized.edgeId());
        assertEquals(edge.sourceNodeId(), deserialized.sourceNodeId());
        assertEquals(edge.targetNodeId(), deserialized.targetNodeId());
        assertEquals(edge.relationshipType(), deserialized.relationshipType());
        assertEquals(edge.properties(), deserialized.properties());
        assertEquals(edge.confidence(), deserialized.confidence());
        assertEquals(edge.bidirectional(), deserialized.bidirectional());
        assertEquals(edge.metadata(), deserialized.metadata());
        assertEquals(edge.snapshotId(), deserialized.snapshotId());
        assertEquals(edge.versionId(), deserialized.versionId());
    }

    @Test
    void testEdgeEnforcesSourceTargetAsNodeIds() {
        // P0-6: Source and target must be node IDs, not label references
        ArtifactEdgeDto edge = new ArtifactEdgeDto(
            "edge-456",
            "node-1",
            "node-2",
            "USES",
            Map.of(),
            0.9,
            true,
            null,
            null,
            null
        );

        // Verify source and target are node IDs (not label:// references)
        assertTrue(edge.sourceNodeId().startsWith("node-"));
        assertTrue(edge.targetNodeId().startsWith("node-"));
        assertFalse(edge.sourceNodeId().startsWith("label://"));
        assertFalse(edge.targetNodeId().startsWith("label://"));
    }

    @Test
    void testEdgeWithNullExtendedFields() throws Exception {
        // Create edge with null extended fields
        ArtifactEdgeDto edge = new ArtifactEdgeDto(
            "edge-789",
            "node-a",
            "node-b",
            "IMPORTS",
            Map.of("module", "test"),
            null,
            null,
            null,
            null,
            null
        );

        // Serialize
        String json = objectMapper.writeValueAsString(edge);

        // Deserialize
        ArtifactEdgeDto deserialized = objectMapper.readValue(json, ArtifactEdgeDto.class);

        // Verify
        assertEquals(edge.edgeId(), deserialized.edgeId());
        assertEquals(edge.sourceNodeId(), deserialized.sourceNodeId());
        assertEquals(edge.targetNodeId(), deserialized.targetNodeId());
        assertEquals(edge.confidence(), deserialized.confidence());
        assertEquals(edge.bidirectional(), deserialized.bidirectional());
        assertEquals(edge.metadata(), deserialized.metadata());
        assertEquals(edge.snapshotId(), deserialized.snapshotId());
        assertEquals(edge.versionId(), deserialized.versionId());
    }
}
