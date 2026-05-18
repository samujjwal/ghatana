package com.ghatana.yappc.domain.artifact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Contract compatibility tests verifying Java DTOs align with proto schema
 *              and TypeScript types for cross-language consistency.
 * @doc.layer test
 * @doc.pattern Contract Test
 */
@DisplayName("ArtifactGraph Contract Compatibility Tests")
class ArtifactGraphContractCompatibilityTest {

    @Test
    @DisplayName("ArtifactNodeDto has canonical field names matching proto ArtifactNode")
    void artifactNodeDtoHasCanonicalFieldNames() {
        Set<String> expectedFields = Set.of(
            "id", "type", "name", "filePath", "content", "properties", "tags",
            "tenantId", "projectId", "workspaceId", "snapshotRef", "sourceLocation",
            "confidence", "provenance", "extractorId", "extractorVersion",
            "residualFragmentIds", "securityFlags", "privacyFlags"
        );

        Set<String> actualFields = getRecordComponentNames(ArtifactNodeDto.class);

        assertThat(actualFields).containsAll(expectedFields);
        // Verify no legacy field names like 'kind' exist
        assertThat(actualFields).doesNotContain("kind");
    }

    @Test
    @DisplayName("ArtifactEdgeDto has canonical field names matching proto ArtifactEdge")
    void artifactEdgeDtoHasCanonicalFieldNames() {
        Set<String> expectedFields = Set.of(
            "edgeId", "sourceNodeId", "targetNodeId", "relationshipType", "properties",
            "confidence", "bidirectional", "tenantId", "projectId", "workspaceId"
        );

        Set<String> actualFields = getRecordComponentNames(ArtifactEdgeDto.class);

        assertThat(actualFields).containsAll(expectedFields);
        // Verify no legacy field names like 'kind', 'source', 'target' exist
        assertThat(actualFields).doesNotContain("kind", "source", "target");
    }

    @Test
    @DisplayName("UnresolvedGraphEdgeDto has canonical field names matching proto UnresolvedGraphEdge")
    void unresolvedGraphEdgeDtoHasCanonicalFieldNames() {
        Set<String> expectedFields = Set.of(
            "edgeId", "sourceNodeId", "targetRef", "relationshipType", "targetKindHint",
            "confidence", "metadata", "tenantId", "projectId", "workspaceId"
        );

        Set<String> actualFields = getRecordComponentNames(UnresolvedGraphEdgeDto.class);

        assertThat(actualFields).containsAll(expectedFields);
        // Verify no legacy field names like 'relationship' exist
        assertThat(actualFields).doesNotContain("relationship");
    }

    @Test
    @DisplayName("ResidualIslandDto has all required fields matching proto ResidualIsland")
    void residualIslandDtoHasAllRequiredFields() {
        Set<String> expectedFields = Set.of(
            "id", "islandType", "summary", "originalSource", "sourceLocation",
            "sourceSpan", "checksum", "rawFragmentRef", "reason", "confidence",
            "reviewRequired", "riskScore", "metadata", "fileCount",
            "tenantId", "projectId", "workspaceId", "snapshotId"
        );

        Set<String> actualFields = getRecordComponentNames(ResidualIslandDto.class);

        assertThat(actualFields).containsAll(expectedFields);
    }

    @Test
    @DisplayName("EdgeResolutionRecordDto has canonical field names matching proto EdgeResolutionRecord")
    void edgeResolutionRecordDtoHasCanonicalFieldNames() {
        Set<String> expectedFields = Set.of(
            "recordId", "unresolvedEdgeId", "status", "resolvedTargetId", "candidateIds",
            "reviewRequired", "resolutionMethod", "tenantId", "projectId", "workspaceId"
        );

        Set<String> actualFields = getRecordComponentNames(EdgeResolutionRecordDto.class);

        assertThat(actualFields).containsAll(expectedFields);
    }

    @Test
    @DisplayName("ArtifactGraphIngestRequest uses projectId not legacy product_id")
    void artifactGraphIngestRequestUsesProjectId() {
        Set<String> actualFields = getRecordComponentNames(ArtifactGraphIngestRequest.class);

        assertThat(actualFields).contains("projectId");
        // Verify no legacy field name 'productId' exists
        assertThat(actualFields).doesNotContain("productId");
    }

    @Test
    @DisplayName("Proto ArtifactNode uses canonical type field not legacy kind")
    void protoArtifactNodeUsesCanonicalTypeField() throws IOException {
        String proto = readProto("artifact_compiler.proto");

        assertThat(proto).contains("message ArtifactNode");
        assertThat(proto).contains("string type = 2");
        // Verify legacy 'kind' field is not present
        assertThat(proto).doesNotContain("string kind");
    }

    @Test
    @DisplayName("Proto ArtifactEdge uses canonical relationshipType field not legacy kind")
    void protoArtifactEdgeUsesCanonicalRelationshipTypeField() throws IOException {
        String proto = readProto("artifact_compiler.proto");

        assertThat(proto).contains("message ArtifactEdge");
        assertThat(proto).contains("string relationship_type = 4");
        // Verify legacy 'kind' field is not present
        assertThat(proto).doesNotContain("string kind");
    }

    @Test
    @DisplayName("Proto UnresolvedGraphEdge uses canonical relationshipType field not legacy relationship")
    void protoUnresolvedGraphEdgeUsesCanonicalRelationshipTypeField() throws IOException {
        String proto = readProto("artifact_compiler.proto");

        assertThat(proto).contains("message UnresolvedGraphEdge");
        assertThat(proto).contains("string relationship_type = 4");
        // Verify legacy 'relationship' field is not present
        assertThat(proto).doesNotContain("string relationship");
    }

    @Test
    @DisplayName("Proto IngestArtifactGraphRequest uses projectId not legacy product_id")
    void protoIngestArtifactGraphRequestUsesProjectId() throws IOException {
        String proto = readProto("artifact_compiler.proto");

        assertThat(proto).contains("message IngestArtifactGraphRequest");
        assertThat(proto).contains("string project_id = 1");
        // Verify legacy 'product_id' field is not present
        assertThat(proto).doesNotContain("string product_id");
    }

    @Test
    @DisplayName("Proto SemanticModel has all governance fields as first-class columns")
    void protoSemanticModelHasAllGovernanceFields() throws IOException {
        String proto = readProto("artifact_compiler.proto");

        assertThat(proto).contains("message SemanticModel");
        
        // Verify governance fields are present
        assertThat(proto).contains("double confidence");
        assertThat(proto).contains("bool review_required");
        assertThat(proto).contains("string review_reason");
        assertThat(proto).contains("repeated string security_flags");
        assertThat(proto).contains("repeated string privacy_flags");
        assertThat(proto).contains("repeated string graph_node_ids");
        assertThat(proto).contains("repeated string residual_island_ids");
        assertThat(proto).contains("string source_ref");
        assertThat(proto).contains("string symbol_ref");
        assertThat(proto).contains("string extractor_id");
        assertThat(proto).contains("string extractor_version");
        assertThat(proto).contains("string model_version_id");
        assertThat(proto).contains("string synthetic_reason");
    }

    @Test
    @DisplayName("Proto ResidualIsland has originalSource and sourceLocation for round-trip fidelity")
    void protoResidualIslandHasOriginalSourceAndSourceLocation() throws IOException {
        String proto = readProto("artifact_compiler.proto");

        assertThat(proto).contains("message ResidualIsland");
        assertThat(proto).contains("string original_source = 4");
        assertThat(proto).contains("SourceLocation source_location = 5");
    }

    private Set<String> getRecordComponentNames(Class<?> recordClass) {
        Set<String> names = new HashSet<>();
        for (RecordComponent component : recordClass.getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }

    private String readProto(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
