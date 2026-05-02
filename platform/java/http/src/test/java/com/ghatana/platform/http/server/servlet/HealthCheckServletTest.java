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
    void healthEndpointReturns200() { 
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) 
                .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
    }

    @Test
    @DisplayName("/health response body contains status UP")
    void healthBodyContainsStatusUp() { 
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) 
                .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains("\"status\":\"UP\""); 
    }

    @Test
    @DisplayName("/health response body contains the service name")
    void healthBodyContainsServiceName() { 
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) 
                .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains(SERVICE); 
    }

    @Test
    @DisplayName("/health response body contains the version")
    void healthBodyContainsVersion() { 
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) 
                .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains(VERSION); 
    }

    @Test
    @DisplayName("/health response has application/json content type")
    void healthResponseIsJson() { 
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) 
                .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/health").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        String contentType = response.getHeader(io.activej.http.HttpHeaders.CONTENT_TYPE); 
        assertThat(contentType).contains("application/json");
    }

    // ── /readiness endpoint ───────────────────────────────────────────────────

    @Test
    @DisplayName("/readiness returns 200 OK")
    void readinessEndpointReturns200() { 
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) 
                .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/readiness").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        assertThat(response.getCode()).isEqualTo(200); 
    }

    @Test
    @DisplayName("/readiness response body contains status READY")
    void readinessBodyContainsStatusReady() { 
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) 
                .build(); 

        HttpRequest request = HttpRequest.get("http://localhost/readiness").build();
        HttpResponse response = runPromise(() -> servlet.serve(request)); 

        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8); 
        assertThat(body).contains("\"status\":\"READY\""); 
    }

    // ── Builder chaining ──────────────────────────────────────────────────────

    @Test
    @DisplayName("addHealthEndpoints returns builder for further route registration")
    void addHealthEndpointsReturnsSameBuilder() { 
        // This verifies that routes can be added after health endpoints (fluent chaining) 
        RoutingServlet servlet = HealthCheckServlet
                .addHealthEndpoints(RoutingServlet.builder(eventloop()), SERVICE, VERSION) 
                .with(io.activej.http.HttpMethod.GET, "/ping", 
                        req -> io.activej.http.HttpResponse.ok200().toPromise()) 
                .build(); 

        HttpRequest pingRequest = HttpRequest.get("http://localhost/ping").build();
        HttpResponse pingResponse = runPromise(() -> servlet.serve(pingRequest)); 
        assertThat(pingResponse.getCode()).isEqualTo(200); 

        HttpRequest healthRequest = HttpRequest.get("http://localhost/health").build();
        HttpResponse healthResponse = runPromise(() -> servlet.serve(healthRequest)); 
        assertThat(healthResponse.getCode()).isEqualTo(200); 
    }

    // ── Null guard ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null serviceName throws NullPointerException")
    void nullServiceNameThrows() { 
        assertThatThrownBy(() -> HealthCheckServlet.addHealthEndpoints( 
                RoutingServlet.builder(eventloop()), null, VERSION)) 
                .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("null version throws NullPointerException")
    void nullVersionThrows() { 
        assertThatThrownBy(() -> HealthCheckServlet.addHealthEndpoints( 
                RoutingServlet.builder(eventloop()), SERVICE, null)) 
                .isInstanceOf(NullPointerException.class); 
    }
}
