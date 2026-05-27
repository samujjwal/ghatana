package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.security.rbac.RolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.pageartifact.InMemoryPageArtifactRepository;
import com.ghatana.yappc.domain.pageartifact.PageArtifactResourceScopeAuthorizer;
import com.ghatana.yappc.domain.pageartifact.http.PageArtifactController;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @doc.type class
 * @doc.purpose Verifies that the YAPPC ActiveJ server leaves public endpoints open and protects business routes with auth
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YappcHttpServer auth routing")
class YappcHttpServerAuthTest extends EventloopTestBase {

    private InMemoryIntentApiController intentController;
    private InMemoryLifecycleApiController lifecycleController;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        intentController = new InMemoryIntentApiController();
        lifecycleController = new InMemoryLifecycleApiController();
        
        // Create authorization service and registry
        YappcAuthorizationService authorizationService = new YappcAuthorizationService(
            new SyncAuthorizationService(new RolePermissionRegistry() {
                @Override
                public Set<String> getPermissions(String role) {
                    return Set.of(Permission.WORKSPACE_READ, Permission.PROJECT_READ, Permission.PROJECT_UPDATE);
                }

                @Override
                public void registerRole(String role, Set<String> permissions) {
                }
            })
        );
        RouteAuthorizationRegistry routeRegistry = new RouteAuthorizationRegistry(authorizationService);
        
        // Create authentication filter with route registry for public route bypass
        YappcAuthenticationFilter authenticationFilter = new YappcAuthenticationFilter(
            key -> Optional.of(new Principal("api-user", List.of("admin"), "tenant-alpha")),
            null,
            routeRegistry
        );
        
        RouteAuthorizationFilter routeAuthorizationFilter = new RouteAuthorizationFilter(routeRegistry);
        Eventloop eventloop = eventloop();
        PhasePacketController phasePacketController = new PhasePacketController(
            new ObjectMapper(),
            (phase, projectId, workspaceId, principal, correlationId) -> Promise.of(
                new PhasePacket(
                    phase,
                    projectId,
                    "Project-" + projectId,
                    principal.getTenantId(),
                    workspaceId,
                    "Workspace-" + workspaceId,
                    new PhasePacket.ActorContext(principal.getName(), principal.getName(), "user", false, false),
                    phase,
                    PhasePacket.TenantTier.FREE,
                    Set.of(),
                    new PhasePacket.CapabilityModel(true, true, true, false, false, false, false),
                    List.of(),
                    new PhasePacket.PhaseReadiness(true, null, List.of(), 1.0, false),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    List.of(),
                    null,
                    List.of(),
                    null,
                    null,
                    Instant.now().toEpochMilli(),
                    correlationId
                )
            )
        );
        servlet = new YappcHttpServer().servlet(
            eventloop,
            authenticationFilter,
            routeAuthorizationFilter,
            intentController,
            new InMemoryShapeApiController(),
            new InMemoryValidationApiController(),
            new InMemoryGenerationApiController(),
            new InMemoryRunApiController(),
            new InMemoryObserveApiController(),
            new InMemoryLearnApiController(),
            new InMemoryEvolveApiController(),
            lifecycleController,
            mock(ImportController.class),
            new InMemoryArtifactGraphController(),
            mock(ArtifactPatchController.class),
            new PageArtifactController(
                new InMemoryPageArtifactRepository(),
                (action, tenantId, workspaceId, projectId, artifactId, actor, summary) -> Promise.complete(),
                new ObjectMapper(),
                new SyncAuthorizationService(new RolePermissionRegistry() {
                    @Override public Set<String> getPermissions(String role) { return Set.of(); }
                    @Override public void registerRole(String role, Set<String> permissions) {}
                }),
                new NoopMetricsCollector(),
                PageArtifactResourceScopeAuthorizer.allowAll()
            ),
            new PreviewSessionApiController(new com.fasterxml.jackson.databind.ObjectMapper(), "test-preview-secret"),
            phasePacketController,
            new CapabilityController(authorizationService),
            new DashboardActionController(new ObjectMapper(), mock(com.ghatana.yappc.services.dashboard.DashboardActionService.class)),
            new AdminObservabilityController(new ObjectMapper()),
            new AdminFeatureFlagController(mock(com.ghatana.datacloud.DataCloudClient.class), new ObjectMapper()),
            new AdminAbTestingController(
                mock(com.ghatana.datacloud.DataCloudClient.class),
                new ObjectMapper(),
                new com.ghatana.yappc.ai.abtesting.ABTestingEvaluationService(),
                new com.ghatana.yappc.ai.PromptLifecycleService(
                    new com.ghatana.yappc.ai.PromptTemplateRegistry(),
                    event -> Promise.complete()
                )
            ),
            new AdminPromptVersionController(
                mock(com.ghatana.datacloud.DataCloudClient.class),
                new ObjectMapper(),
                new com.ghatana.yappc.ai.PromptLifecycleService(
                    new com.ghatana.yappc.ai.PromptTemplateRegistry(),
                    event -> Promise.complete()
                )
            ),
            new ProductFamilyControlPlaneController(
                mock(com.ghatana.datacloud.DataCloudClient.class),
                new ObjectMapper()
            )
        );
    }

    @Test
    @DisplayName("health stays public")
    void healthStaysPublic() { 
        HttpRequest request = HttpRequest.get("http://localhost/health").build();

        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
    }

    @Test
    @DisplayName("protected routes return 401 without credentials")
    void protectedRoutesReturn401WithoutCredentials() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/intent/capture").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(intentController.getCaptureIntentCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("full lifecycle endpoint stays protected")
    void fullLifecycleEndpointRequiresCredentials() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(lifecycleController.getExecuteFullLifecycleCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("protected route rejects unsupported API version")
    void protectedRouteRejectsUnsupportedApiVersion() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/intent/capture")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-key")
            .withHeader(HttpHeaders.of("X-API-Version"), "v2")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(406);
        assertThat(intentController.getCaptureIntentCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("protected routes delegate when credentials are valid")
    void protectedRoutesDelegateWhenCredentialsAreValid() {
        intentController.setCaptureIntentResponse(HttpResponse.ok200().toPromise());
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/intent/capture")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-key")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), "workspace-1")
            .withHeader(HttpHeaders.of("X-Project-Id"), "project-1")
            .withHeader(HttpHeaders.of("X-API-Version"), "v1")
            .withBody(io.activej.bytebuf.ByteBuf.wrapForReading("{\"rawText\":\"Build an order service\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(intentController.getCaptureIntentCallCount()).isEqualTo(1);
    }

    private static final class InMemoryIntentApiController extends IntentApiController {
        private Promise<HttpResponse> captureIntentResponse = null;
        private int captureIntentCallCount = 0;

        InMemoryIntentApiController() {
            super(null, null);
        }

        void setCaptureIntentResponse(Promise<HttpResponse> response) {
            this.captureIntentResponse = response;
        }

        int getCaptureIntentCallCount() {
            return captureIntentCallCount;
        }

        @Override
        public Promise<HttpResponse> captureIntent(HttpRequest request) {
            captureIntentCallCount++;
            return captureIntentResponse != null ? captureIntentResponse : super.captureIntent(request);
        }
    }

    private static final class InMemoryLifecycleApiController extends LifecycleApiController {
        private int executeFullLifecycleCallCount = 0;

        InMemoryLifecycleApiController() {
            super(null, null, null, null, null, null, null, null, null, null, null);
        }

        int getExecuteFullLifecycleCallCount() {
            return executeFullLifecycleCallCount;
        }

        @Override
        public Promise<HttpResponse> executeFullLifecycle(HttpRequest request) {
            executeFullLifecycleCallCount++;
            return super.executeFullLifecycle(request);
        }
    }

    private static final class InMemoryShapeApiController extends ShapeApiController {
        InMemoryShapeApiController() {
            super(null, null);
        }
    }

    private static final class InMemoryValidationApiController extends ValidationApiController {
        InMemoryValidationApiController() {
            super(null);
        }
    }

    private static final class InMemoryGenerationApiController extends GenerationApiController {
        InMemoryGenerationApiController() {
            super(null, null, null);
        }
    }

    private static final class InMemoryRunApiController extends RunApiController {
        InMemoryRunApiController() {
            super(null);
        }
    }

    private static final class InMemoryObserveApiController extends ObserveApiController {
        InMemoryObserveApiController() {
            super(null);
        }
    }

    private static final class InMemoryLearnApiController extends LearnApiController {
        InMemoryLearnApiController() {
            super(null);
        }
    }

    private static final class InMemoryEvolveApiController extends EvolveApiController {
        InMemoryEvolveApiController() {
            super(null);
        }
    }

    private static final class InMemoryArtifactGraphController extends ArtifactGraphController {
        InMemoryArtifactGraphController() {
            super(null);
        }
    }
}
