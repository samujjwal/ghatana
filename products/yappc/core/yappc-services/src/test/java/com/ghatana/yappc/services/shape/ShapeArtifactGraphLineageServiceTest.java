package com.ghatana.yappc.services.shape;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.artifact.ArtifactGraphIngestRequest;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import com.ghatana.yappc.domain.shape.ArchitecturePattern;
import com.ghatana.yappc.domain.shape.BoundedContextSpec;
import com.ghatana.yappc.domain.shape.DomainModel;
import com.ghatana.yappc.domain.shape.EntitySpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.services.artifact.ArtifactGraphService;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies Shape lineage graph publication through the canonical artifact graph service
 * @doc.layer test
 * @doc.pattern Test
 */
class ShapeArtifactGraphLineageServiceTest extends EventloopTestBase {

    @Test
    void recordsIntentArchitectureSurfaceModuleAndGeneratedArtifactLineage() {
        ArtifactGraphService artifactGraphService = mock(ArtifactGraphService.class);
        when(artifactGraphService.ingestGraph(any(), any()))
                .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingest", Map.of(), "ok")));
        ShapeArtifactGraphLineageService service = new ShapeArtifactGraphLineageService(artifactGraphService);

        ShapeSpec shape = ShapeSpec.builder()
                .id("shape-123")
                .intentRef("intent-123")
                .tenantId("tenant-123")
                .metadata(Map.of(
                        "workspaceId", "workspace-123",
                        "projectId", "project-123",
                        "surface", "web"))
                .architecture(ArchitecturePattern.builder()
                        .name("microservices")
                        .description("Services split by bounded context")
                        .components(List.of())
                        .properties(Map.of())
                        .build())
                .domainModel(DomainModel.builder()
                        .entities(List.of(EntitySpec.builder()
                                .name("Task")
                                .description("Task entity")
                                .fields(List.of())
                                .behaviors(List.of())
                                .build()))
                        .relationships(List.of())
                        .boundedContexts(List.of(BoundedContextSpec.builder()
                                .name("WorkManagement")
                                .description("Work management module")
                                .entities(List.of("Task"))
                                .build()))
                        .build())
                .build();

        runPromise(() -> service.recordShapeLineage(shape));

        ArgumentCaptor<ArtifactRequestScope> scopeCaptor = ArgumentCaptor.forClass(ArtifactRequestScope.class);
        ArgumentCaptor<ArtifactGraphIngestRequest> requestCaptor = ArgumentCaptor.forClass(ArtifactGraphIngestRequest.class);
        verify(artifactGraphService).ingestGraph(scopeCaptor.capture(), requestCaptor.capture());

        ArtifactRequestScope scope = scopeCaptor.getValue();
        assertEquals("tenant-123", scope.tenantId());
        assertEquals("workspace-123", scope.workspaceId());
        assertEquals("project-123", scope.projectId());

        ArtifactGraphIngestRequest request = requestCaptor.getValue();
        assertTrue(request.nodes().stream().anyMatch(node -> node.type().equals("intent")));
        assertTrue(request.nodes().stream().anyMatch(node -> node.type().equals("architecture")));
        assertTrue(request.nodes().stream().anyMatch(node -> node.type().equals("surface")));
        assertTrue(request.nodes().stream().anyMatch(node -> node.type().equals("module")));
        assertTrue(request.nodes().stream().anyMatch(node -> node.type().equals("generated_artifact")));
        assertTrue(request.edges().stream().anyMatch(edge -> edge.relationshipType().equals("INFORMS")));
        assertTrue(request.edges().stream().anyMatch(edge -> edge.relationshipType().equals("DESCRIBES_ARCHITECTURE")));
        assertTrue(request.edges().stream().anyMatch(edge -> edge.relationshipType().equals("TARGETS_SURFACE")));
        assertTrue(request.edges().stream().anyMatch(edge -> edge.relationshipType().equals("DEFINES_MODULE")));
        assertTrue(request.edges().stream().anyMatch(edge -> edge.relationshipType().equals("GENERATES_ARTIFACT")));
    }
}
