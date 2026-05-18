package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.EdgeResolutionRecordDto;
import com.ghatana.yappc.domain.artifact.ResidualIslandDto;
import com.ghatana.yappc.domain.artifact.UnresolvedGraphEdgeDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Unit tests for ArtifactGraphValidator covering validation rules,
 *              scope consistency, residual fidelity, and edge integrity.
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("ArtifactGraphValidator Tests")
class ArtifactGraphValidatorTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String WORKSPACE_ID = "test-workspace";
    private static final String PROJECT_ID = "test-project";

    private static ArtifactRequestScope testScope() {
        return new ArtifactRequestScope(PROJECT_ID, TENANT_ID, WORKSPACE_ID);
    }

    @Test
    @DisplayName("Should validate valid ingest request successfully")
    void shouldValidateValidIngestRequest() {
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1", "component", "Button", "/src/Button.tsx", null,
            Map.of(), List.of(), "test-tenant", "test-project",
            Map.of("filePath", "/src/Button.tsx", "startLine", 1, "endLine", 10),
            "extractor-1", "1.0", 0.9, "exact", List.of(), List.of(), "src:/src/Button.tsx", "src/Button.tsx#component:Button"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            "test-project", "test-tenant", List.of(node), List.of(),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of()
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("Should reject duplicate node IDs")
    void shouldRejectDuplicateNodeIds() {
        ArtifactNodeDto node1 = new ArtifactNodeDto(
            "node-1", "component", "Button1", "/src/Button1.tsx", null,
            Map.of(), List.of(), TENANT_ID, PROJECT_ID, null,
            "extractor-1", "1.0", 0.9, "exact", List.of(), List.of(), "src:/src/Button1.tsx", "src/Button1.tsx#component:Button1"
        );
        ArtifactNodeDto node2 = new ArtifactNodeDto(
            "node-1", "component", "Button2", "/src/Button2.tsx", null,
            Map.of(), List.of(), TENANT_ID, PROJECT_ID, null,
            "extractor-1", "1.0", 0.9, "exact", List.of(), List.of(), "src:/src/Button2.tsx", "src/Button2.tsx#component:Button2"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            PROJECT_ID, TENANT_ID, List.of(node1, node2), List.of(),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of()
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.code().equals("DUPLICATE_NODE_ID"));
    }

    @Test
    @DisplayName("Should reject edge with non-existent source node")
    void shouldRejectEdgeWithNonExistentSource() {
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1", "component", "Button", "/src/Button.tsx", null,
            Map.of(), List.of(), "test-tenant", "test-project",
            Map.of("filePath", "/src/Button.tsx", "startLine", 1, "endLine", 10),
            "extractor-1", "1.0", 0.9, "exact", List.of(), List.of(), "src:/src/Button.tsx", "src/Button.tsx#component:Button"
        );
        ArtifactEdgeDto edge = new ArtifactEdgeDto(
            "edge-1", "non-existent-node", "node-1", "imports",
            Map.of(), 0.9, false, Map.of(), "snapshot-1", "version-1"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            PROJECT_ID, TENANT_ID, List.of(node), List.of(edge),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of()
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.code().equals("EDGE_SOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Should reject edge with non-existent target node")
    void shouldRejectEdgeWithNonExistentTarget() {
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1", "component", "Button", "/src/Button.tsx", null,
            Map.of(), List.of(), "test-tenant", "test-project",
            Map.of("filePath", "/src/Button.tsx", "startLine", 1, "endLine", 10),
            "extractor-1", "1.0", 0.9, "exact", List.of(), List.of(), "src:/src/Button.tsx", "src/Button.tsx#component:Button"
        );
        ArtifactEdgeDto edge = new ArtifactEdgeDto(
            "edge-1", "node-1", "non-existent-node", "imports",
            Map.of(), 0.9, false, Map.of(), "snapshot-1", "version-1"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            PROJECT_ID, TENANT_ID, List.of(node), List.of(edge),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of()
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.code().equals("EDGE_TARGET_NOT_FOUND"));
    }

    @Test
    @DisplayName("Should reject missing relationshipType in edge")
    void shouldRejectMissingRelationshipType() {
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1", "component", "Button", "/src/Button.tsx", null,
            Map.of(), List.of(), "test-tenant", "test-project",
            Map.of("filePath", "/src/Button.tsx", "startLine", 1, "endLine", 10),
            "extractor-1", "1.0", 0.9, "exact", List.of(), List.of(), "src:/src/Button.tsx", "src/Button.tsx#component:Button"
        );
        ArtifactEdgeDto edge = new ArtifactEdgeDto(
            "edge-1", "node-1", "node-1", "",
            Map.of(), 0.9, false, Map.of(), "snapshot-1", "version-1"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            PROJECT_ID, TENANT_ID, List.of(node), List.of(edge),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of()
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.code().equals("MISSING_RELATIONSHIP_TYPE"));
    }

    @Test
    @DisplayName("Should detect scope mismatch in request")
    void shouldDetectScopeMismatch() {
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1", "component", "Button", "/src/Button.tsx", null,
            Map.of(), List.of(), "wrong-tenant", "test-project",
            Map.of("filePath", "/src/Button.tsx", "startLine", 1, "endLine", 10),
            "extractor-1", "1.0", 0.9, "exact", List.of(), List.of(), "src:/src/Button.tsx", "src/Button.tsx#component:Button"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            "test-project", "wrong-tenant", List.of(node), List.of(),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of()
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.code().equals("SCOPE_MISMATCH_TENANT"));
    }

    @Test
    @DisplayName("Should reject residual island without source span")
    void shouldRejectResidualWithoutSourceSpan() {
        ResidualIslandDto residual = new ResidualIslandDto(
            "residual-1", "unknown", "Summary", "raw-source", null, "checksum",
            "ref", "reason", 0.5, true, 0.5, Map.of(), 1,
            TENANT_ID, PROJECT_ID, WORKSPACE_ID, "snapshot-1"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            PROJECT_ID, TENANT_ID, List.of(), List.of(),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of(residual)
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.code().equals("MISSING_SOURCE_SPAN"));
    }

    @Test
    @DisplayName("Should warn about missing checksum in residual")
    void shouldWarnAboutMissingChecksum() {
        ResidualIslandDto residual = new ResidualIslandDto(
            "residual-1", "unknown", "Summary", "raw-source", "file:1:1-10:10", null,
            "ref", "reason", 0.5, true, 0.5, Map.of(), 1,
            TENANT_ID, PROJECT_ID, WORKSPACE_ID, "snapshot-1"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            PROJECT_ID, TENANT_ID, List.of(), List.of(),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of(residual)
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.errors()).anyMatch(e -> e.code().equals("MISSING_RESIDUAL_CHECKSUM"));
    }

    @Test
    @DisplayName("Should detect orphaned residual references in nodes")
    void shouldDetectOrphanedResidualReferences() {
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1", "component", "Button", "/src/Button.tsx", null,
            Map.of(), List.of(), "test-tenant", "test-project",
            Map.of("filePath", "/src/Button.tsx", "startLine", 1, "endLine", 10),
            "extractor-1", "1.0", 0.9, "exact", List.of(),
            List.of("orphaned-residual-id"), "src:/src/Button.tsx", "src/Button.tsx#component:Button"
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateResidualReferences(List.of(node), List.of());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.code().equals("ORPHANED_RESIDUAL_REF"));
    }

    @Test
    @DisplayName("Should reject invalid confidence range")
    void shouldRejectInvalidConfidenceRange() {
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1", "component", "Button", "/src/Button.tsx", null,
            Map.of(), List.of(), "test-tenant", "test-project",
            Map.of("filePath", "/src/Button.tsx", "startLine", 1, "endLine", 10),
            "extractor-1", "1.0", 1.5, "exact", List.of(), List.of(), "src:/src/Button.tsx", "src/Button.tsx#component:Button"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            "test-project", "test-tenant", List.of(node), List.of(),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of()
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.code().equals("INVALID_CONFIDENCE_RANGE"));
    }

    @Test
    @DisplayName("Should warn about low confidence node")
    void shouldWarnAboutLowConfidence() {
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1", "component", "Button", "/src/Button.tsx", null,
            Map.of(), List.of(), "test-tenant", "test-project",
            Map.of("filePath", "/src/Button.tsx", "startLine", 1, "endLine", 10),
            "extractor-1", "1.0", 0.3, "exact", List.of(), List.of(), "src:/src/Button.tsx", "src/Button.tsx#component:Button"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            "test-project", "test-tenant", List.of(node), List.of(),
            "snapshot-1", "snapshot-1", "version-1", "checksum-1",
            List.of(), List.of(), List.of()
        );

        ArtifactGraphValidator.ValidationResult result =
            ArtifactGraphValidator.validateIngestRequest(request, testScope());

        assertThat(result.warnings()).anyMatch(w -> w.code().equals("LOW_CONFIDENCE"));
    }
}
