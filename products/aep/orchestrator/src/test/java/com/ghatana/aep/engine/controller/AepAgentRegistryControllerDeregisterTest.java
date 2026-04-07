package com.ghatana.aep.engine.controller;

import com.ghatana.aep.engine.registry.AepCentralRegistryService;
import com.ghatana.aep.engine.registry.AgentExecutionService;
import com.ghatana.aep.engine.registry.AgentInfo;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpMethod;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests for the authorization check in {@link AepAgentRegistryController#deregisterAgent}.
 *
 * @doc.type class
 * @doc.purpose Verifies that deregisterAgent requires the ADMIN role
 * @doc.layer product
 * @doc.pattern TestCase
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepAgentRegistryController — deregisterAgent authorization")
class AepAgentRegistryControllerDeregisterTest extends EventloopTestBase {

    @Mock
    private AepCentralRegistryService registryService;

    @Mock
    private AgentExecutionService executionService;

    @Mock
    private HttpHandlerUtils httpUtils;

    private AepAgentRegistryController controller;

    @BeforeEach
    void setUp() {
        controller = new AepAgentRegistryController(registryService, executionService, httpUtils);

        // Default error and success responses
        lenient().when(httpUtils.errorResponse(eq(403), eq("Forbidden: admin role required to deregister agents")))
                .thenReturn(HttpResponse.ofCode(403).build());
        lenient().when(httpUtils.errorResponse(eq(400), eq("agentId path parameter required")))
                .thenReturn(HttpResponse.ofCode(400).build());
        lenient().when(httpUtils.errorResponse(eq(404), any()))
                .thenReturn(HttpResponse.ofCode(404).build());
    }

    @Test
    @DisplayName("deregisterAgent: missing role header → 403 Forbidden")
    void deregister_missingRoleHeader_returns403() {
        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/agent-1").build();

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("deregisterAgent: USER role → 403 Forbidden")
    void deregister_userRole_returns403() {
        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/agent-1")
                .withHeader(HttpHeaders.of("X-Agent-Role"), "USER")
                .build();

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("deregisterAgent: ADMIN role + agent exists → 204 No Content")
    void deregister_adminRole_agentExists_returns204() {
        AgentInfo agent = AgentInfo.builder()
                .id("agent-1").name("Test Agent").type("DETERMINISTIC")
                .product("test").capabilities(Set.of()).status("ACTIVE")
                .build();
        when(registryService.resolve("agent-1")).thenReturn(Promise.of(Optional.of(agent)));
        when(registryService.deregister("agent-1")).thenReturn(Promise.of(null));

        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/agent-1")
                .withHeader(HttpHeaders.of("X-Agent-Role"), "ADMIN")
                .build();

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request));

        assertThat(response.getCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("deregisterAgent: ADMIN role but agent not found → 404")
    void deregister_adminRole_agentNotFound_returns404() {
        when(registryService.resolve("ghost")).thenReturn(Promise.of(Optional.empty()));

        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/ghost")
                .withHeader(HttpHeaders.of("X-Agent-Role"), "ADMIN")
                .build();

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("deregisterAgent: case-insensitive ADMIN check (admin → allowed)")
    void deregister_lowercaseAdmin_allowed() {
        AgentInfo agent = AgentInfo.builder()
                .id("agent-2").name("Test").type("DETERMINISTIC")
                .product("test").capabilities(Set.of()).status("ACTIVE")
                .build();
        when(registryService.resolve("agent-2")).thenReturn(Promise.of(Optional.of(agent)));
        when(registryService.deregister("agent-2")).thenReturn(Promise.of(null));

        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/agent-2")
                .withHeader(HttpHeaders.of("X-Agent-Role"), "admin")
                .build();

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request));

        assertThat(response.getCode()).isEqualTo(204);
    }
}

