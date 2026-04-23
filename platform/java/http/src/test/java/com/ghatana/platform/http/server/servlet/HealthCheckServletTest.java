package com.ghatana.platform.http.server.servlet;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for HealthCheckServlet standardised /health and /readiness endpoints
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("HealthCheckServlet — standardised health and readiness endpoints")
class HealthCheckServletTest extends EventloopTestBase {

    private static final String SERVICE = "test-service";
    private static final String VERSION = "2.0.0";

    // ── /health endpoint ──────────────────────────────────────────────────────

    @Test
    @DisplayName("/health returns 200 OK")
    void healthEndpointReturns200() { // GH-90000
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) // GH-90000
                .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("/health response body contains status UP")
    void healthBodyContainsStatusUp() { // GH-90000
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) // GH-90000
                .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"status\":\"UP\""); // GH-90000
    }

    @Test
    @DisplayName("/health response body contains the service name")
    void healthBodyContainsServiceName() { // GH-90000
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) // GH-90000
                .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains(SERVICE); // GH-90000
    }

    @Test
    @DisplayName("/health response body contains the version")
    void healthBodyContainsVersion() { // GH-90000
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) // GH-90000
                .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains(VERSION); // GH-90000
    }

    @Test
    @DisplayName("/health response has application/json content type")
    void healthResponseIsJson() { // GH-90000
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) // GH-90000
                .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        String contentType = response.getHeader(io.activej.http.HttpHeaders.CONTENT_TYPE); // GH-90000
        assertThat(contentType).contains("application/json");
    }

    // ── /readiness endpoint ───────────────────────────────────────────────────

    @Test
    @DisplayName("/readiness returns 200 OK")
    void readinessEndpointReturns200() { // GH-90000
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) // GH-90000
                .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/readiness").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("/readiness response body contains status READY")
    void readinessBodyContainsStatusReady() { // GH-90000
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) // GH-90000
                .build(); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/readiness").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); // GH-90000

        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"status\":\"READY\""); // GH-90000
    }

    // ── Builder chaining ──────────────────────────────────────────────────────

    @Test
    @DisplayName("addHealthEndpoints returns builder for further route registration")
    void addHealthEndpointsReturnsSameBuilder() { // GH-90000
        // This verifies that routes can be added after health endpoints (fluent chaining) // GH-90000
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) // GH-90000
                .with(io.activej.http.HttpMethod.GET, "/ping", // GH-90000
                        req -> io.activej.http.HttpResponse.ok200().toPromise()) // GH-90000
                .build(); // GH-90000

        HttpRequest pingRequest = HttpRequest.get("http://localhost/ping").build();
        HttpResponse pingResponse = runPromise(() -> servlet.serve(pingRequest)); // GH-90000
        assertThat(pingResponse.getCode()).isEqualTo(200); // GH-90000

        HttpRequest healthRequest = HttpRequest.get("http://localhost/health").build();
        HttpResponse healthResponse = runPromise(() -> servlet.serve(healthRequest)); // GH-90000
        assertThat(healthResponse.getCode()).isEqualTo(200); // GH-90000
    }

    // ── Null guard ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null serviceName throws NullPointerException")
    void nullServiceNameThrows() { // GH-90000
        assertThatThrownBy(() -> HealthCheckServlet.addHealthEndpoints( // GH-90000
                RoutingServlet.builder(eventloop()), null, VERSION)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("null version throws NullPointerException")
    void nullVersionThrows() { // GH-90000
        assertThatThrownBy(() -> HealthCheckServlet.addHealthEndpoints( // GH-90000
                RoutingServlet.builder(eventloop()), SERVICE, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
