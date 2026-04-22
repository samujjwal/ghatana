package com.ghatana.yappc.domain.agent.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.yappc.domain.agent.AgentRegistry;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tenant-isolation regression tests for {@link AgentController}.
 *
 * <p>Verifies that requests missing mandatory tenant-scoped headers
 * ({@code X-Tenant-ID}, {@code X-Organization-ID}, {@code X-Workspace-ID}) // GH-90000
 * are rejected with 400 Bad Request instead of falling back to defaults.
 *
 * @doc.type class
 * @doc.purpose Tenant isolation regression tests
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("AgentController — Tenant Isolation Regression [GH-90000]")
class AgentControllerTenantTest extends EventloopTestBase {

    private AgentController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() { // GH-90000
        AgentRegistry registry = mock(AgentRegistry.class); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        controller = new AgentController(registry, objectMapper); // GH-90000
    }

    @Nested
    @DisplayName("Missing Tenant Headers [GH-90000]")
    class MissingTenantHeaders {

        @Test
        @DisplayName("should reject request missing X-Tenant-ID with 400 [GH-90000]")
        void shouldRejectMissingTenantId() { // GH-90000
            // GIVEN — request with workspace+org but NO tenant header
            String body = "{\"prompt\": \"hello\", \"workspaceId\": \"ws-1\"}";
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, // GH-90000
                    "http://localhost/api/v1/agents/copilot/execute")
                    .withHeader(HttpHeaders.of("X-Workspace-ID [GH-90000]"), "ws-1")
                    .withHeader(HttpHeaders.of("X-Organization-ID [GH-90000]"), "org-1")
                    .withBody(body.getBytes()) // GH-90000
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.executeAgent(request)); // GH-90000

            // THEN — 400 instead of silently defaulting
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("should reject request missing X-Organization-ID with 400 [GH-90000]")
        void shouldRejectMissingOrganizationId() { // GH-90000
            // GIVEN — request with tenant+workspace but NO org header
            String body = "{\"prompt\": \"hello\", \"workspaceId\": \"ws-1\"}";
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, // GH-90000
                    "http://localhost/api/v1/agents/copilot/execute")
                    .withHeader(HttpHeaders.of("X-Workspace-ID [GH-90000]"), "ws-1")
                    .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                    .withBody(body.getBytes()) // GH-90000
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.executeAgent(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("should reject request missing X-Workspace-ID with 400 [GH-90000]")
        void shouldRejectMissingWorkspaceId() { // GH-90000
            // GIVEN — request with tenant+org but NO workspace header or body field
            String body = "{\"prompt\": \"hello\"}";
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, // GH-90000
                    "http://localhost/api/v1/agents/copilot/execute")
                    .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                    .withHeader(HttpHeaders.of("X-Organization-ID [GH-90000]"), "org-1")
                    .withBody(body.getBytes()) // GH-90000
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.executeAgent(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("should accept request with all required headers [GH-90000]")
        void shouldAcceptWithAllHeaders() { // GH-90000
            // GIVEN — request with all required headers
            String body = "{\"prompt\": \"hello\", \"workspaceId\": \"ws-1\"}";
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, // GH-90000
                    "http://localhost/api/v1/agents/copilot/execute")
                    .withHeader(HttpHeaders.of("X-Tenant-ID [GH-90000]"), "tenant-1")
                    .withHeader(HttpHeaders.of("X-Organization-ID [GH-90000]"), "org-1")
                    .withHeader(HttpHeaders.of("X-Workspace-ID [GH-90000]"), "ws-1")
                    .withBody(body.getBytes()) // GH-90000
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.executeAgent(request)); // GH-90000

            // THEN — should not be 400 (may be 404 for unknown agent, but not 400 for missing headers) // GH-90000
            assertThat(response.getCode()).isNotEqualTo(400); // GH-90000
        }
    }
}
