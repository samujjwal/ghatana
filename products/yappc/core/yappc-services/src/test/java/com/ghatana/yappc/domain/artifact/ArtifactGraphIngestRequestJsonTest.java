package com.ghatana.yappc.domain.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose JSON serialization test for ArtifactGraphIngestRequest
 * @doc.layer test
 * @doc.pattern Test
 * 
 * P0-4: Tests JSON round-trip for extended request fields including
 * snapshot metadata, unresolved edges, resolution records, and residual islands.
 */
class ArtifactGraphIngestRequestJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testJsonSerializationWithP0_4Extensions() throws Exception {
        // Create request with P0-4 extended fields
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node1",
            "CODE_MODULE",
            "TestComponent",
            "src/test.tsx",
            "content",
            Map.of("name", "TestComponent"),
            List.of("tag1"),
            "tenant-123",
            "project-456",
            Map.of("filePath", "src/test.tsx", "startLine", 0, "startColumn", 0, "endLine", 10, "endColumn", 0),
            "typescript-extractor",
            "1.0.0",
            0.95,
            "exact",
            List.of("PII"),
            List.of("residual-1"),
            "source-ref-123",
            "symbol-ref-456"
        );

        ArtifactEdgeDto edge = new ArtifactEdgeDto(
            "edge1",
            "node1",
            "node2",
            "USES",
            Map.of("relationship", "dependency"),
            0.9,
            false,
            Map.of("metadata", "test"),
            "snapshot-123",
            "version-456"
        );

        UnresolvedGraphEdgeDto unresolvedEdge = new UnresolvedGraphEdgeDto(
            "edge-1",
            "node1",
            "label://unknown-symbol",
            "REFERENCES",
            "SYMBOL",
            new UnresolvedGraphEdgeDto.SourceLocation("src/test.ts", 1, 0, 1, 10),
            0.5,
            Map.of("confidence", 0.5),
            "tenant-123",
            "product-456",
            "workspace-1"
        );

        EdgeResolutionRecordDto resolutionRecord = new EdgeResolutionRecordDto(
            "record-1",
            "edge-1",
            "RESOLVED",
            "node-2",
            List.of("node-2", "node-3"),
            false,
            "SYMBOL_LOOKUP",
            Map.of(),
            "tenant-123",
            "product-456",
            "workspace-1"
        );

        ResidualIslandDto residualIsland = new ResidualIslandDto(
            "residual-1",
            "unsupported_language",
            "Unknown artifact type",
            "raw source",
            "src/test.unknown:1:0-1:10",
            null,
            null,
            "unsupported",
            0.0,
            true,
            0.0,
            Map.of(),
            1,
            "tenant-123",
            "product-456",
            "workspace-1",
            "commit-abc123"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            "product-456",
            "tenant-123",
            List.of(node),
            List.of(edge),
            "snapshot-123", // snapshotRef
            "commit-abc123", // snapshotId
            "version-456",
            "sha256:abc123", // contentChecksum
            List.of(unresolvedEdge),
            List.of(resolutionRecord),
            List.of(residualIsland)
        );

        // Serialize
        String json = objectMapper.writeValueAsString(request);

        // Deserialize
        ArtifactGraphIngestRequest deserialized = objectMapper.readValue(json, ArtifactGraphIngestRequest.class);

        // Verify
        assertEquals(request.projectId(), deserialized.projectId());
        assertEquals(request.tenantId(), deserialized.tenantId());
        assertEquals(request.snapshotRef(), deserialized.snapshotRef());
        assertEquals(request.snapshotId(), deserialized.snapshotId());
        assertEquals(request.versionId(), deserialized.versionId());
        assertEquals(request.contentChecksum(), deserialized.contentChecksum());
        assertEquals(request.unresolvedEdges(), deserialized.unresolvedEdges());
        assertEquals(request.edgeResolutionRecords(), deserialized.edgeResolutionRecords());
        assertEquals(request.residualIslands(), deserialized.residualIslands());
    }
}
