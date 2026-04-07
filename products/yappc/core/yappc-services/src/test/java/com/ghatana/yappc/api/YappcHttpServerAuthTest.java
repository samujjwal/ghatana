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
@ExtendWith(MockitoExtension.class)
class YappcHttpServerAuthTest extends EventloopTestBase {

    @Mock
    private IntentApiController intentController;

    @Mock
    private ShapeApiController shapeController;

    @Mock
    private ValidationApiController validationController;

    @Mock
    private GenerationApiController generationController;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        YappcApiAuthFilter authFilter = new YappcApiAuthFilter(key -> Optional.of(new Principal("api-user", List.of("admin"), "tenant-alpha")));
        Eventloop eventloop = eventloop();
        servlet = new YappcHttpServer().servlet(
            eventloop,
            authFilter,
            intentController,
            shapeController,
            validationController,
            generationController
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
        verify(intentController, never()).captureIntent(any());
    }

    @Test
    @DisplayName("protected routes delegate when credentials are valid")
    void protectedRoutesDelegateWhenCredentialsAreValid() {
        when(intentController.captureIntent(any())).thenReturn(HttpResponse.ok200().toPromise());
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/intent/capture")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-key")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(intentController).captureIntent(any());
    }
}