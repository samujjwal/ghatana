package com.ghatana.yappc.domain.pageartifact.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.security.rbac.InMemoryRolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.pageartifact.InMemoryPageArtifactRepository;
import com.ghatana.yappc.domain.pageartifact.PageArtifactAuditRepository;
import com.ghatana.yappc.domain.pageartifact.PageArtifactDocument;
import com.ghatana.yappc.domain.pageartifact.PageArtifactPermission;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
        private RecordingAuditRepository auditRepository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPageArtifactRepository();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        auditRepository = new RecordingAuditRepository();

        InMemoryRolePermissionRegistry registry = new InMemoryRolePermissionRegistry();
        registry.registerRole("EDITOR", Set.of(PageArtifactPermission.READ, PageArtifactPermission.EDIT));
        registry.registerRole("VIEWER", Set.of(PageArtifactPermission.READ));

        SyncAuthorizationService authorizationService = new SyncAuthorizationService(registry);
                controller = new PageArtifactController(repository, auditRepository, objectMapper, authorizationService, MetricsCollector.create());
    }

    @Test
    @DisplayName("save persists a valid document and returns an etag")
    void saveDocument_persistsValidDocument() throws Exception {
        PageArtifactDocument document = sampleDocument("artifact-1", "doc-1", "manual", List.of());

        HttpRequest request = HttpRequest.put("http://localhost/api/v1/page-artifacts/artifact-1/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Workspace-IDs"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Project-IDs"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-User-ID"), USER_ID)
                .withHeader(HttpHeaders.of("X-User-Role"), "EDITOR")
                .withHeader(HttpHeaders.of("If-Match"), "doc-1")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(document)))
                .build();

        HttpResponse response = runPromise(() -> controller.saveDocument(attachPrincipal(request, "EDITOR")));

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
                .withHeader(HttpHeaders.of("X-Authorized-Workspace-IDs"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Project-IDs"), PROJECT_ID)
                .withHeader(HttpHeaders.of("If-Match"), "doc-2")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(document)))
                .build();

        HttpResponse response = runPromise(() -> controller.saveDocument(attachPrincipal(request, "EDITOR")));

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
                .build();

        HttpResponse response = runPromise(() -> controller.loadDocument(attachPrincipal(request, "UNKNOWN")));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("save enforces resource-scoped workspace authorization when allowed scopes are provided")
    void saveDocument_enforcesWorkspaceScope() throws Exception {
        PageArtifactDocument document = sampleDocument("artifact-5", "doc-5", "manual", List.of());

        HttpRequest request = HttpRequest.put("http://localhost/api/v1/page-artifacts/artifact-5/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Workspace-IDs"), "ws-2,ws-3")
                .withHeader(HttpHeaders.of("X-Authorized-Project-IDs"), PROJECT_ID)
                .withHeader(HttpHeaders.of("If-Match"), "doc-5")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(document)))
                .build();

        HttpResponse response = runPromise(() -> controller.saveDocument(attachPrincipal(request, "EDITOR")));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("save enforces resource-scoped authorization through injected authorizer")
    void saveDocument_enforcesInjectedResourceScopeAuthorizer() throws Exception {
        controller = new PageArtifactController(
                repository,
                auditRepository,
                objectMapper,
                new SyncAuthorizationService(new InMemoryRolePermissionRegistry() {{
                    registerRole("EDITOR", Set.of(PageArtifactPermission.READ, PageArtifactPermission.EDIT));
                }}),
                MetricsCollector.create(),
                (userId, tenantId, workspaceId, projectId, artifactId, permission) ->
                        Promise.ofException(new com.ghatana.platform.security.rbac.AccessDeniedException(
                                "User is not authorized for requested project: " + projectId
                        ))
        );
        PageArtifactDocument document = sampleDocument("artifact-7", "doc-7", "manual", List.of());

        HttpRequest request = HttpRequest.put("http://localhost/api/v1/page-artifacts/artifact-7/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Workspace-IDs"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Project-IDs"), PROJECT_ID)
                .withHeader(HttpHeaders.of("If-Match"), "doc-7")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(document)))
                .build();

        HttpResponse response = runPromise(() -> controller.saveDocument(attachPrincipal(request, "EDITOR")));

        assertThat(response.getCode()).isEqualTo(403);
        JsonNode body = objectMapper.readTree(runPromise(response::loadBody).asString(StandardCharsets.UTF_8));
        assertThat(body.get("message").asText()).contains("requested project");
    }

    @Test
    @DisplayName("save fails closed when audit persistence fails")
    void saveDocument_failsClosedWhenAuditPersistenceFails() throws Exception {
        controller = new PageArtifactController(
                repository,
                new FailingAuditRepository(),
                objectMapper,
                new SyncAuthorizationService(new InMemoryRolePermissionRegistry() {{
                    registerRole("EDITOR", Set.of(PageArtifactPermission.READ, PageArtifactPermission.EDIT));
                }}),
                MetricsCollector.create()
        );
        PageArtifactDocument document = sampleDocument("artifact-6", "doc-6", "manual", List.of());

        HttpRequest request = HttpRequest.put("http://localhost/api/v1/page-artifacts/artifact-6/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Workspace-IDs"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Project-IDs"), PROJECT_ID)
                .withHeader(HttpHeaders.of("If-Match"), "doc-6")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(document)))
                .build();

        HttpResponse response = runPromise(() -> controller.saveDocument(attachPrincipal(request, "EDITOR")));

        assertThat(response.getCode()).isEqualTo(500);
        PageArtifactDocument persisted = runPromise(() -> repository.load(
                TENANT_ID,
                WORKSPACE_ID,
                PROJECT_ID,
                "artifact-6"
        ));
        assertThat(persisted).isNull();
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
                .withHeader(HttpHeaders.of("X-Authorized-Workspace-IDs"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Project-IDs"), PROJECT_ID)
                .withHeader(HttpHeaders.of("If-Match"), "doc-3")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(created)))
                .build();

        HttpResponse response = runPromise(() -> controller.saveDocument(attachPrincipal(request, "EDITOR")));

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
                .withHeader(HttpHeaders.of("X-Authorized-Workspace-IDs"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Project-IDs"), PROJECT_ID)
                .build();

        HttpResponse response = runPromise(() -> controller.loadDocument(attachPrincipal(request, "VIEWER")));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("ETag"))).isEqualTo("doc-4");
        JsonNode body = objectMapper.readTree(runPromise(response::loadBody).asString(StandardCharsets.UTF_8));
        assertThat(body.get("artifactId").asText()).isEqualTo("artifact-4");
        assertThat(body.get("documentId").asText()).isEqualTo("doc-4");
    }

    @Test
    @DisplayName("load passes resource details into the injected resource authorizer")
    void loadDocument_passesResourceDetailsToAuthorizer() throws Exception {
        PageArtifactDocument document = sampleDocument("artifact-8", "doc-8", "manual", List.of());
        runPromise(() -> repository.save(TENANT_ID, WORKSPACE_ID, PROJECT_ID, document));

        List<String> observed = new ArrayList<>();
        controller = new PageArtifactController(
                repository,
                auditRepository,
                objectMapper,
                new SyncAuthorizationService(new InMemoryRolePermissionRegistry() {{
                    registerRole("VIEWER", Set.of(PageArtifactPermission.READ));
                }}),
                MetricsCollector.create(),
                (userId, tenantId, workspaceId, projectId, artifactId, permission) -> {
                    observed.add(String.join(":", userId, tenantId, workspaceId, projectId, artifactId, permission));
                    return Promise.complete();
                }
        );

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/page-artifacts/artifact-8/document")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID)
                .withHeader(HttpHeaders.of("X-Workspace-ID"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-ID"), PROJECT_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Workspace-IDs"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Authorized-Project-IDs"), PROJECT_ID)
                .build();

        HttpResponse response = runPromise(() -> controller.loadDocument(attachPrincipal(request, "VIEWER")));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(observed).containsExactly(
                USER_ID + ":" + TENANT_ID + ":" + WORKSPACE_ID + ":" + PROJECT_ID + ":artifact-8:" + PageArtifactPermission.READ
        );
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

        private static HttpRequest attachPrincipal(HttpRequest request, String role) {
                request.attach(Principal.class, new Principal(USER_ID, List.of(role), TENANT_ID));
                return request;
        }

        private static final class RecordingAuditRepository implements PageArtifactAuditRepository {
                private final List<String> events = new ArrayList<>();

                @Override
                public Promise<Void> record(String action, String tenantId, String workspaceId, String projectId, String artifactId, String actor, String summary) {
                        events.add(action + ":" + artifactId + ":" + actor);
                        return Promise.complete();
                }
        }

        private static final class FailingAuditRepository implements PageArtifactAuditRepository {
                @Override
                public Promise<Void> record(String action, String tenantId, String workspaceId, String projectId, String artifactId, String actor, String summary) {
                        return Promise.ofException(new RuntimeException("audit write failed"));
                }
        }
}
