package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies that the YAPPC ActiveJ server leaves public endpoints open and protects business routes with auth
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YappcHttpServer auth routing")
@ExtendWith(MockitoExtension.class) // GH-90000
class YappcHttpServerAuthTest extends EventloopTestBase {

    @Mock
    private IntentApiController intentController;

    @Mock
    private ShapeApiController shapeController;

    @Mock
    private ValidationApiController validationController;

    @Mock
    private GenerationApiController generationController;

    @Mock
    private RunApiController runController;

    @Mock
    private ObserveApiController observeController;

    @Mock
    private LearnApiController learnController;

    @Mock
    private EvolveApiController evolveController;

    @Mock
    private LifecycleApiController lifecycleController;

    @Mock
    private ArtifactGraphController artifactGraphController;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() { // GH-90000
        YappcApiAuthFilter authFilter = new YappcApiAuthFilter(key -> Optional.of(new Principal("api-user", List.of("admin"), "tenant-alpha")));
        Eventloop eventloop = eventloop(); // GH-90000
        servlet = new YappcHttpServer().servlet( // GH-90000
            eventloop,
            authFilter,
            intentController,
            shapeController,
            validationController,
            generationController,
            runController,
            observeController,
            learnController,
            evolveController,
            lifecycleController,
            artifactGraphController
        );
    }

    @Test
    @DisplayName("health stays public")
    void healthStaysPublic() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/health").build();

        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("protected routes return 401 without credentials")
    void protectedRoutesReturn401WithoutCredentials() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/intent/capture").build();

        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
        verify(intentController, never()).captureIntent(any()); // GH-90000
    }

    @Test
    @DisplayName("full lifecycle endpoint stays protected")
    void fullLifecycleEndpointRequiresCredentials() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute").build();

        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
        verify(lifecycleController, never()).executeFullLifecycle(any()); // GH-90000
    }

    @Test
    @DisplayName("protected route rejects unsupported API version")
    void protectedRouteRejectsUnsupportedApiVersion() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/intent/capture")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-key")
            .withHeader(HttpHeaders.of("X-API-Version"), "v2")
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(406); // GH-90000
        verify(intentController, never()).captureIntent(any()); // GH-90000
    }

    @Test
    @DisplayName("protected routes delegate when credentials are valid")
    void protectedRoutesDelegateWhenCredentialsAreValid() { // GH-90000
        when(intentController.captureIntent(any())).thenReturn(HttpResponse.ok200().toPromise()); // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/intent/capture")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-key")
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(response.getHeader(HttpHeaders.of("X-API-Version"))).isEqualTo("v1");
        verify(intentController).captureIntent(any()); // GH-90000
    }
}
