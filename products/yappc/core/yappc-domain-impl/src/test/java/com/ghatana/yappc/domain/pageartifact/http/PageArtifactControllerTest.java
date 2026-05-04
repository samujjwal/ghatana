package com.ghatana.yappc.domain.pageartifact.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.security.rbac.InMemoryRolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.pageartifact.InMemoryPageArtifactRepository;
import com.ghatana.yappc.domain.pageartifact.PageArtifactDocument;
import com.ghatana.yappc.domain.pageartifact.PageArtifactPermission;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageArtifactController Tests")
class PageArtifactControllerTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-a";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String USER_ID = "user-1";

    private InMemoryPageArtifactRepository repository;
    private PageArtifactController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPageArtifactRepository();
        objectMapper = new ObjectMapper().findAndRegisterModules();

        InMemoryRolePermissionRegistry registry = new InMemoryRolePermissionRegistry();
        registry.registerRole("EDITOR", Set.of(PageArtifactPermission.READ, PageArtifactPermission.EDIT));
        registry.registerRole("VIEWER", Set.of(PageArtifactPermission.READ));

        SyncAuthorizationService authorizationService = new SyncAuthorizationService(registry);
        controller = new PageArtifactController(repository, objectMapper, authorizationService, MetricsCollector.create());
    }

    @Test
    @DisplayName("save persists a valid document and returns an etag")
    void saveDocument_persistsValidDocument() throws Exception {
        PageArtifactDocument document = sampleDocument("artifact-1", "doc-1", "manual", List.of());

        HttpRequest request = HttpRequest.put("http://localhost/api/v1/page-artifacts/artifact-1/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-User-ID"), USER_ID)
                .withHeader(HttpHeaders.of("X-User-Role"), "EDITOR")
                .withHeader(HttpHeaders.of("If-Match"), "doc-1")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(document)))
                .build();

        HttpResponse response = runPromise(() -> controller.saveDocument(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("ETag"))).isEqualTo("doc-1");
        JsonNode body = objectMapper.readTree(runPromise(response::loadBody).asString(StandardCharsets.UTF_8));
        assertThat(body.get("artifactId").asText()).isEqualTo("artifact-1");
        assertThat(body.get("documentId").asText()).isEqualTo("doc-1");
    }

    @Test
    @DisplayName("save rejects generated documents without governance records")
    void saveDocument_rejectsMissingGovernanceForGeneratedSource() throws Exception {
        PageArtifactDocument document = sampleDocument("artifact-2", "doc-2", "generated", List.of());

        HttpRequest request = HttpRequest.put("http://localhost/api/v1/page-artifacts/artifact-2/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-User-ID"), USER_ID)
                .withHeader(HttpHeaders.of("X-User-Role"), "EDITOR")
                .withHeader(HttpHeaders.of("If-Match"), "doc-2")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(document)))
                .build();

        HttpResponse response = runPromise(() -> controller.saveDocument(request));

        assertThat(response.getCode()).isEqualTo(422);
        JsonNode body = objectMapper.readTree(runPromise(response::loadBody).asString(StandardCharsets.UTF_8));
        assertThat(body.get("message").asText()).contains("Governance records are required");
    }

    @Test
    @DisplayName("load requires a role with read permission")
    void loadDocument_enforcesAuthorization() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/page-artifacts/artifact-1/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-User-ID"), USER_ID)
                .withHeader(HttpHeaders.of("X-User-Role"), "UNKNOWN")
                .build();

        HttpResponse response = runPromise(() -> controller.loadDocument(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("save returns conflict and current version when request uses a stale etag")
    void saveDocument_returnsConflictForStaleVersion() throws Exception {
        PageArtifactDocument created = sampleDocument("artifact-3", "doc-3", "manual", List.of());
        runPromise(() -> repository.save(TENANT_ID, WORKSPACE_ID, PROJECT_ID, created));
        PageArtifactDocument updated = runPromise(() -> repository.save(TENANT_ID, WORKSPACE_ID, PROJECT_ID, created));

        HttpRequest request = HttpRequest.put("http://localhost/api/v1/page-artifacts/artifact-3/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-User-ID"), USER_ID)
                .withHeader(HttpHeaders.of("X-User-Role"), "EDITOR")
                .withHeader(HttpHeaders.of("If-Match"), "doc-3")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(created)))
                .build();

        HttpResponse response = runPromise(() -> controller.saveDocument(request));

        assertThat(response.getCode()).isEqualTo(409);
        assertThat(response.getHeader(HttpHeaders.of("X-Current-Version"))).isEqualTo(updated.documentId());
    }

    @Test
    @DisplayName("load returns the persisted document and etag")
    void loadDocument_returnsPersistedDocument() throws Exception {
        PageArtifactDocument document = sampleDocument("artifact-4", "doc-4", "manual", List.of());
        runPromise(() -> repository.save(TENANT_ID, WORKSPACE_ID, PROJECT_ID, document));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/page-artifacts/artifact-4/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-User-ID"), USER_ID)
                .withHeader(HttpHeaders.of("X-User-Role"), "VIEWER")
                .build();

        HttpResponse response = runPromise(() -> controller.loadDocument(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("ETag"))).isEqualTo("doc-4");
        JsonNode body = objectMapper.readTree(runPromise(response::loadBody).asString(StandardCharsets.UTF_8));
        assertThat(body.get("artifactId").asText()).isEqualTo("artifact-4");
        assertThat(body.get("documentId").asText()).isEqualTo("doc-4");
    }

    private static PageArtifactDocument sampleDocument(
            String artifactId,
            String documentId,
            String source,
            List<PageArtifactDocument.GovernanceRecord> governanceRecords
    ) {
        return new PageArtifactDocument(
                artifactId,
                documentId,
                "Landing Page",
                USER_ID,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                "SYNCED",
                "TRUSTED",
                "INTERNAL",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "id", "root",
                                        "contractName", "Box",
                                        "props", Map.of(),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                new PageArtifactDocument.ValidationSummary(true, 0, 0),
                governanceRecords,
                source,
                0,
                0.92
        );
    }
}
