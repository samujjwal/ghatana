package com.ghatana.aep.engine.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ghatana.aep.engine.registry.AepCentralRegistryService;
import com.ghatana.aep.engine.registry.AgentExecutionService;
import com.ghatana.aep.engine.registry.AgentInfo;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for the authorization check in {@link AepAgentRegistryController#deregisterAgent}.
 *
 * @doc.type class
 * @doc.purpose Verifies that deregisterAgent requires the ADMIN role
 * @doc.layer product
 * @doc.pattern TestCase
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepAgentRegistryController — deregisterAgent authorization [GH-90000]")
class AepAgentRegistryControllerDeregisterTest extends EventloopTestBase {

    @Mock
    private AepCentralRegistryService registryService;

    @Mock
    private AgentExecutionService executionService;

    @Mock
    private HttpHandlerUtils httpUtils;

    private AepAgentRegistryController controller;

    @BeforeEach
    void setUp() { // GH-90000
        controller = new AepAgentRegistryController(registryService, executionService, httpUtils); // GH-90000

        // Default error and success responses
        lenient() // GH-90000
                .when(httpUtils.errorResponse(eq(403), eq("Forbidden: admin role required to deregister agents [GH-90000]")))
                .thenReturn(HttpResponse.ofCode(403).build()); // GH-90000
        lenient() // GH-90000
                .when(httpUtils.errorResponse(eq(400), eq("agentId path parameter required [GH-90000]")))
                .thenReturn(HttpResponse.ofCode(400).build()); // GH-90000
        lenient() // GH-90000
                .when(httpUtils.errorResponse(eq(404), any())) // GH-90000
                .thenReturn(HttpResponse.ofCode(404).build()); // GH-90000
    }

    @Test
    @DisplayName("deregisterAgent: missing role header → 403 Forbidden [GH-90000]")
    void deregister_missingRoleHeader_returns403() { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/agent-1") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(403); // GH-90000
    }

    @Test
    @DisplayName("deregisterAgent: USER role → 403 Forbidden [GH-90000]")
    void deregister_userRole_returns403() { // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/agent-1") // GH-90000
                .withHeader(HttpHeaders.of("X-Agent-Role [GH-90000]"), "USER")
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(403); // GH-90000
    }

    @Test
    @DisplayName("deregisterAgent: ADMIN role + agent exists → 204 No Content [GH-90000]")
    void deregister_adminRole_agentExists_returns204() { // GH-90000
        AgentInfo agent = AgentInfo.builder() // GH-90000
                .id("agent-1 [GH-90000]")
                .name("Test Agent [GH-90000]")
                .type("DETERMINISTIC [GH-90000]")
                .product("test [GH-90000]")
                .capabilities(Set.of()) // GH-90000
                .status("ACTIVE [GH-90000]")
                .build(); // GH-90000
        when(registryService.resolve("agent-1 [GH-90000]")).thenReturn(Promise.of(Optional.of(agent)));
        when(registryService.deregister("agent-1 [GH-90000]")).thenReturn(Promise.of(null));

        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/agent-1") // GH-90000
                .withHeader(HttpHeaders.of("X-Agent-Role [GH-90000]"), "ADMIN")
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(204); // GH-90000
    }

    @Test
    @DisplayName("deregisterAgent: ADMIN role but agent not found → 404 [GH-90000]")
    void deregister_adminRole_agentNotFound_returns404() { // GH-90000
        when(registryService.resolve("ghost [GH-90000]")).thenReturn(Promise.of(Optional.empty()));

        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/ghost") // GH-90000
                .withHeader(HttpHeaders.of("X-Agent-Role [GH-90000]"), "ADMIN")
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(404); // GH-90000
    }

    @Test
    @DisplayName("deregisterAgent: case-insensitive ADMIN check (admin → allowed) [GH-90000]")
    void deregister_lowercaseAdmin_allowed() { // GH-90000
        AgentInfo agent = AgentInfo.builder() // GH-90000
                .id("agent-2 [GH-90000]")
                .name("Test [GH-90000]")
                .type("DETERMINISTIC [GH-90000]")
                .product("test [GH-90000]")
                .capabilities(Set.of()) // GH-90000
                .status("ACTIVE [GH-90000]")
                .build(); // GH-90000
        when(registryService.resolve("agent-2 [GH-90000]")).thenReturn(Promise.of(Optional.of(agent)));
        when(registryService.deregister("agent-2 [GH-90000]")).thenReturn(Promise.of(null));

        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/agents/agent-2") // GH-90000
                .withHeader(HttpHeaders.of("X-Agent-Role [GH-90000]"), "admin")
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.deregisterAgent(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(204); // GH-90000
    }
}
