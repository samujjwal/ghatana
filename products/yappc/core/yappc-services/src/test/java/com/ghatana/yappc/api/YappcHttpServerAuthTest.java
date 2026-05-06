package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.observability.NoopMetricsCollector;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
        YappcApiAuthFilter authFilter = new YappcApiAuthFilter(key -> Optional.of(new Principal("api-user", List.of("admin"), "tenant-alpha")));
        Eventloop eventloop = eventloop();
        servlet = new YappcHttpServer().servlet(
            eventloop,
            authFilter,
            intentController,
            new InMemoryShapeApiController(),
            new InMemoryValidationApiController(),
            new InMemoryGenerationApiController(),
            new InMemoryRunApiController(),
            new InMemoryObserveApiController(),
            new InMemoryLearnApiController(),
            new InMemoryEvolveApiController(),
            lifecycleController,
            new InMemoryArtifactGraphController(),
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
            new PreviewSessionApiController(new com.fasterxml.jackson.databind.ObjectMapper(), "test-preview-secret")
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
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-API-Version"))).isEqualTo("v1");
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
            super(null, null, null, null, null, null, null, null, null, null);
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
            super(null, null);
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
