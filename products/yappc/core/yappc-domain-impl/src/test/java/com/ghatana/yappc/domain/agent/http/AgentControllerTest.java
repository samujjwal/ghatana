package com.ghatana.yappc.domain.agent.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.agent.*;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AgentController}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for Agent HTTP controller
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("AgentController Tests")
class AgentControllerTest extends EventloopTestBase {

    private AgentRegistry registry;
    private ObjectMapper objectMapper;
    private AgentController controller;

    @BeforeEach
    void setUp() { // GH-90000
        MetricsCollector metricsCollector = mock(MetricsCollector.class); // GH-90000
        registry = mock(AgentRegistry.class); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        controller = new AgentController(registry, objectMapper); // GH-90000
    }

    @Nested
    @DisplayName("Agent Discovery")
    class AgentDiscovery {

        @Test
        @DisplayName("should list all registered agents")
        void shouldListAllAgents() { // GH-90000
            // GIVEN
            List<AgentMetadata> metadata = List.of( // GH-90000
                    AgentMetadata.builder() // GH-90000
                            .name(AgentName.COPILOT_AGENT) // GH-90000
                            .version("1.0.0")
                            .description("AI Copilot")
                            .capabilities(List.of("chat", "assistance")) // GH-90000
                            .supportedModels(List.of("gpt-4"))
                            .latencySLA(1000) // GH-90000
                            .build(), // GH-90000
                    AgentMetadata.builder() // GH-90000
                            .name(AgentName.SEARCH_AGENT) // GH-90000
                            .version("1.0.0")
                            .description("Semantic Search")
                            .capabilities(List.of("search", "vector-search")) // GH-90000
                            .supportedModels(List.of("text-embedding-3-small"))
                            .latencySLA(200) // GH-90000
                            .build() // GH-90000
            );
            when(registry.getAllMetadata()).thenReturn(metadata); // GH-90000

            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/agents") // GH-90000
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.listAgents(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(registry).getAllMetadata(); // GH-90000
        }

        @Test
        @DisplayName("should get specific agent by name")
        void shouldGetAgentByName() { // GH-90000
            // GIVEN
            @SuppressWarnings("unchecked")
            AIAgent<Object, Object> mockAgent = mock(AIAgent.class); // GH-90000
            AgentMetadata metadata = AgentMetadata.builder() // GH-90000
                    .name(AgentName.COPILOT_AGENT) // GH-90000
                    .version("1.0.0")
                    .description("AI Copilot")
                    .capabilities(List.of("chat"))
                    .supportedModels(List.of("gpt-4"))
                    .latencySLA(1000) // GH-90000
                    .build(); // GH-90000
            when(mockAgent.getMetadata()).thenReturn(metadata); // GH-90000
            when(registry.get(AgentName.COPILOT_AGENT)).thenReturn(mockAgent); // GH-90000

            HttpRequest request = HttpRequest.builder( // GH-90000
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/COPILOT_AGENT"
                    )
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.getAgent(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(registry).get(AgentName.COPILOT_AGENT); // GH-90000
        }

        @Test
        @DisplayName("should return 404 for unknown agent")
        void shouldReturn404ForUnknownAgent() { // GH-90000
            // GIVEN - no agent registered for this name
            when(registry.get(any(AgentName.class))).thenReturn(null); // GH-90000

            HttpRequest request = HttpRequest.builder( // GH-90000
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/COPILOT_AGENT"
                    )
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.getAgent(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(404); // GH-90000
        }
    }

    @Nested
    @DisplayName("Agent Health")
    class AgentHealthTests {

        @Test
        @DisplayName("should get health for all agents")
        void shouldGetAllAgentsHealth() { // GH-90000
            // GIVEN
            Map<AgentName, AgentHealth> healthMap = Map.of( // GH-90000
                    AgentName.COPILOT_AGENT, AgentHealth.healthy(50), // GH-90000
                    AgentName.SEARCH_AGENT, AgentHealth.healthy(30) // GH-90000
            );
            when(registry.healthCheckAll()).thenReturn(Promise.of(healthMap)); // GH-90000

            HttpRequest request = HttpRequest.builder( // GH-90000
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/health"
                    )
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.getAllAgentsHealth(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(registry).healthCheckAll(); // GH-90000
        }

        @Test
        @DisplayName("should get health for specific agent")
        void shouldGetAgentHealth() { // GH-90000
            // GIVEN
            @SuppressWarnings("unchecked")
            AIAgent<Object, Object> mockAgent = mock(AIAgent.class); // GH-90000
            when(registry.get(AgentName.COPILOT_AGENT)).thenReturn(mockAgent); // GH-90000
            when(mockAgent.healthCheck()) // GH-90000
                    .thenReturn(Promise.of(AgentHealth.healthy(50))); // GH-90000

            HttpRequest request = HttpRequest.builder( // GH-90000
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/COPILOT_AGENT/health"
                    )
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.getAgentHealth(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }
    }

    @Nested
    @DisplayName("Capability Discovery")
    class CapabilityDiscovery {

        @Test
        @DisplayName("should list all capabilities")
        void shouldListAllCapabilities() { // GH-90000
            // GIVEN
            List<AgentMetadata> metadata = List.of( // GH-90000
                    AgentMetadata.builder() // GH-90000
                            .name(AgentName.COPILOT_AGENT) // GH-90000
                            .version("1.0.0")
                            .description("AI Copilot")
                            .capabilities(List.of("chat", "code-generation")) // GH-90000
                            .supportedModels(List.of("gpt-4"))
                            .latencySLA(1000) // GH-90000
                            .build(), // GH-90000
                    AgentMetadata.builder() // GH-90000
                            .name(AgentName.CODE_GENERATOR_AGENT) // GH-90000
                            .version("1.0.0")
                            .description("Code Generator")
                            .capabilities(List.of("code-generation", "scaffolding")) // GH-90000
                            .supportedModels(List.of("gpt-4"))
                            .latencySLA(5000) // GH-90000
                            .build() // GH-90000
            );
            when(registry.getAllMetadata()).thenReturn(metadata); // GH-90000

            HttpRequest request = HttpRequest.builder( // GH-90000
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/capabilities"
                    )
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.listCapabilities(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("should find agents by capability")
        void shouldFindAgentsByCapability() { // GH-90000
            // GIVEN
            AgentRegistry.AgentInfo agentInfo = new AgentRegistry.AgentInfo( // GH-90000
                    AgentName.SEARCH_AGENT,
                    "1.0.0",
                    "Search",
                    AgentRegistry.AgentState.READY,
                    Instant.now(), // GH-90000
                    List.of("semantic-search")
            );
            when(registry.findByCapability("semantic-search"))
                    .thenReturn(List.of(agentInfo)); // GH-90000
            when(registry.getMetadata(AgentName.SEARCH_AGENT)) // GH-90000
                    .thenReturn(java.util.Optional.of( // GH-90000
                            AgentMetadata.builder() // GH-90000
                                    .name(AgentName.SEARCH_AGENT) // GH-90000
                                    .version("1.0.0")
                                    .description("Search")
                                    .capabilities(List.of("semantic-search"))
                                    .supportedModels(List.of("text-embedding-3-small"))
                                    .latencySLA(200) // GH-90000
                                    .build() // GH-90000
                    ));

            HttpRequest request = HttpRequest.builder( // GH-90000
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/by-capability/semantic-search"
                    )
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.findByCapability(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            verify(registry).findByCapability("semantic-search");
        }
    }

    @Nested
    @DisplayName("Agent Execution")
    class AgentExecution {

        @Test
        @DisplayName("should execute copilot chat")
        @SuppressWarnings("unchecked")
        void shouldExecuteCopilotChat() { // GH-90000
            // GIVEN
            AIAgent<CopilotInput, Object> mockAgent = mock(AIAgent.class); // GH-90000
            AgentMetadata metadata = AgentMetadata.builder() // GH-90000
                    .name(AgentName.COPILOT_AGENT) // GH-90000
                    .version("1.0.0")
                    .description("AI Copilot")
                    .capabilities(List.of("chat"))
                    .supportedModels(List.of("gpt-4"))
                    .latencySLA(1000) // GH-90000
                    .build(); // GH-90000
            when(mockAgent.getMetadata()).thenReturn(metadata); // GH-90000

            AgentResult<Object> result = AgentResult.success( // GH-90000
                    Map.of("response", "Hello! How can I help?"), // GH-90000
                    AgentResult.AgentMetrics.builder() // GH-90000
                            .latencyMs(50) // GH-90000
                            .tokensUsed(100) // GH-90000
                            .modelVersion("gpt-4")
                            .confidence(0.95) // GH-90000
                            .build(), // GH-90000
                    AgentResult.AgentTrace.of("CopilotAgent", "req-123") // GH-90000
            );

            doReturn(mockAgent).when(registry).get(AgentName.COPILOT_AGENT); // GH-90000
            when(mockAgent.execute(any(), any())).thenReturn(Promise.of(result)); // GH-90000

            String requestBody = """
                    {
                        "sessionId": "session-1",
                        "message": "Hello",
                        "workspaceId": "ws-1"
                    }
                    """;

            HttpRequest request = HttpRequest.builder( // GH-90000
                            HttpMethod.POST,
                            "http://localhost/api/v1/agents/copilot/chat"
                    )
                    .withHeader(io.activej.http.HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                    .withHeader(io.activej.http.HttpHeaders.of("X-Organization-ID"), "org-1")
                    .withHeader(io.activej.http.HttpHeaders.of("X-Workspace-ID"), "ws-1")
                    .withBody(requestBody.getBytes()) // GH-90000
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.copilotChat(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("should return 404 when agent not registered")
        void shouldReturn404WhenAgentNotRegistered() { // GH-90000
            // GIVEN
            when(registry.get(any(AgentName.class))).thenReturn(null); // GH-90000

            String requestBody = """
                    {
                        "query": "test search"
                    }
                    """;

            HttpRequest request = HttpRequest.builder( // GH-90000
                            HttpMethod.POST,
                            "http://localhost/api/v1/agents/search"
                    )
                    .withHeader(io.activej.http.HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                    .withHeader(io.activej.http.HttpHeaders.of("X-Organization-ID"), "org-1")
                    .withHeader(io.activej.http.HttpHeaders.of("X-Workspace-ID"), "ws-1")
                    .withBody(requestBody.getBytes()) // GH-90000
                    .build(); // GH-90000

            // WHEN
            HttpResponse response = runPromise(() -> controller.search(request)); // GH-90000

            // THEN
            assertThat(response.getCode()).isEqualTo(404); // GH-90000
        }
    }
}
