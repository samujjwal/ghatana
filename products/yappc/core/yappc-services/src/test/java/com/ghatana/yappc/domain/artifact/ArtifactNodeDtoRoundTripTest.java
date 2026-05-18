package com.ghatana.yappc.domain.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose JSON round-trip test for ArtifactNodeDto with P0-5 extended fields
 * @doc.layer test
 * @doc.pattern Test
 * 
 * P0-5: Tests JSON serialization/deserialization for extended node fields including
 * source location, extractor metadata, confidence, provenance, privacy flags,
 * residual fragments, and symbol references.
 */
class ArtifactNodeDtoRoundTripTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testJsonRoundTripWithP0_5Extensions() throws Exception {
        // Create node with all P0-5 extended fields
        ArtifactNodeDto original = new ArtifactNodeDto(
            "node-123",
            "CODE_MODULE",
            "TestComponent",
            "src/test/TestComponent.tsx",
            "export const TestComponent = () => { return <div>Test</div>; }",
            Map.of("name", "TestComponent", "exported", true),
            List.of("component", "test"),
            "tenant-123",
            "project-456",
            Map.of(
                "filePath", "src/test/TestComponent.tsx",
                "startLine", 0,
                "startColumn", 0,
                "endLine", 1,
                "endColumn", 50
            ),
            "typescript-extractor",
            "1.2.0",
            0.98,
            "exact",
            List.of("PII", "sensitive"),
            List.of("residual-fragment-1", "residual-fragment-2"),
            "urn:source:typescript:src/test/TestComponent.tsx",
            "TestComponent#component"
        );

        // Serialize
        String json = objectMapper.writeValueAsString(original);

        // Deserialize
        ArtifactNodeDto deserialized = objectMapper.readValue(json, ArtifactNodeDto.class);

        // Verify all fields
        assertEquals(original.id(), deserialized.id());
        assertEquals(original.type(), deserialized.type());
        assertEquals(original.name(), deserialized.name());
        assertEquals(original.filePath(), deserialized.filePath());
        assertEquals(original.content(), deserialized.content());
        assertEquals(original.properties(), deserialized.properties());
        assertEquals(original.tags(), deserialized.tags());
        assertEquals(original.tenantId(), deserialized.tenantId());
        assertEquals(original.projectId(), deserialized.projectId());
        assertEquals(original.sourceLocation(), deserialized.sourceLocation());
        assertEquals(original.extractorId(), deserialized.extractorId());
        assertEquals(original.extractorVersion(), deserialized.extractorVersion());
        assertEquals(original.confidence(), deserialized.confidence());
        assertEquals(original.provenance(), deserialized.provenance());
        assertEquals(original.privacySecurityFlags(), deserialized.privacySecurityFlags());
        assertEquals(original.residualFragmentIds(), deserialized.residualFragmentIds());
        assertEquals(original.sourceRef(), deserialized.sourceRef());
        assertEquals(original.symbolRef(), deserialized.symbolRef());
    }

    @Test
    void testJsonRoundTripWithNullExtendedFields() throws Exception {
        // Create node with null extended fields
        ArtifactNodeDto original = new ArtifactNodeDto(
            "node-456",
            "CODE_MODULE",
            "SimpleComponent",
            "src/SimpleComponent.ts",
            null,
            Map.of(),
            List.of(),
            "tenant-123",
            "project-456",
            Map.of("filePath", "src/SimpleComponent.ts", "startLine", 1, "endLine", 10),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        // Serialize
        String json = objectMapper.writeValueAsString(original);

        // Deserialize
        ArtifactNodeDto deserialized = objectMapper.readValue(json, ArtifactNodeDto.class);

        // Verify
        assertEquals(original.id(), deserialized.id());
        assertEquals(original.type(), deserialized.type());
        assertEquals(original.name(), deserialized.name());
        assertEquals(original.sourceLocation(), deserialized.sourceLocation());
        assertEquals(original.extractorId(), deserialized.extractorId());
        assertEquals(original.confidence(), deserialized.confidence());
    }
}
