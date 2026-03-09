package com.ghatana.products.yappc.domain.agent.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.yappc.domain.agent.*;
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
import static org.mockito.ArgumentMatchers.eq;
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
    void setUp() {
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        registry = mock(AgentRegistry.class);
        objectMapper = new ObjectMapper();
        controller = new AgentController(registry, objectMapper);
    }

    @Nested
    @DisplayName("Agent Discovery")
    class AgentDiscovery {

        @Test
        @DisplayName("should list all registered agents")
        void shouldListAllAgents() {
            // GIVEN
            List<AgentMetadata> metadata = List.of(
                    AgentMetadata.builder()
                            .name(AgentName.COPILOT_AGENT)
                            .version("1.0.0")
                            .description("AI Copilot")
                            .capabilities(List.of("chat", "assistance"))
                            .supportedModels(List.of("gpt-4"))
                            .latencySLA(1000)
                            .build(),
                    AgentMetadata.builder()
                            .name(AgentName.SEARCH_AGENT)
                            .version("1.0.0")
                            .description("Semantic Search")
                            .capabilities(List.of("search", "vector-search"))
                            .supportedModels(List.of("text-embedding-3-small"))
                            .latencySLA(200)
                            .build()
            );
            when(registry.getAllMetadata()).thenReturn(metadata);

            HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/agents")
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> controller.listAgents(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(200);
            verify(registry).getAllMetadata();
        }

        @Test
        @DisplayName("should get specific agent by name")
        void shouldGetAgentByName() {
            // GIVEN
            @SuppressWarnings("unchecked")
            AIAgent<Object, Object> mockAgent = mock(AIAgent.class);
            AgentMetadata metadata = AgentMetadata.builder()
                    .name(AgentName.COPILOT_AGENT)
                    .version("1.0.0")
                    .description("AI Copilot")
                    .capabilities(List.of("chat"))
                    .supportedModels(List.of("gpt-4"))
                    .latencySLA(1000)
                    .build();
            when(mockAgent.getMetadata()).thenReturn(metadata);
            when(registry.get(AgentName.COPILOT_AGENT)).thenReturn(mockAgent);

            HttpRequest request = HttpRequest.builder(
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/COPILOT_AGENT"
                    )
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> controller.getAgent(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(200);
            verify(registry).get(AgentName.COPILOT_AGENT);
        }

        @Test
        @DisplayName("should return 404 for unknown agent")
        void shouldReturn404ForUnknownAgent() {
            // GIVEN - no agent registered for this name
            when(registry.get(any(AgentName.class))).thenReturn(null);

            HttpRequest request = HttpRequest.builder(
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/COPILOT_AGENT"
                    )
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> controller.getAgent(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("Agent Health")
    class AgentHealthTests {

        @Test
        @DisplayName("should get health for all agents")
        void shouldGetAllAgentsHealth() {
            // GIVEN
            Map<AgentName, AgentHealth> healthMap = Map.of(
                    AgentName.COPILOT_AGENT, AgentHealth.healthy(50),
                    AgentName.SEARCH_AGENT, AgentHealth.healthy(30)
            );
            when(registry.healthCheckAll()).thenReturn(Promise.of(healthMap));

            HttpRequest request = HttpRequest.builder(
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/health"
                    )
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> controller.getAllAgentsHealth(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(200);
            verify(registry).healthCheckAll();
        }

        @Test
        @DisplayName("should get health for specific agent")
        void shouldGetAgentHealth() {
            // GIVEN
            @SuppressWarnings("unchecked")
            AIAgent<Object, Object> mockAgent = mock(AIAgent.class);
            when(registry.get(AgentName.COPILOT_AGENT)).thenReturn(mockAgent);
            when(mockAgent.healthCheck())
                    .thenReturn(Promise.of(AgentHealth.healthy(50)));

            HttpRequest request = HttpRequest.builder(
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/COPILOT_AGENT/health"
                    )
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> controller.getAgentHealth(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Capability Discovery")
    class CapabilityDiscovery {

        @Test
        @DisplayName("should list all capabilities")
        void shouldListAllCapabilities() {
            // GIVEN
            List<AgentMetadata> metadata = List.of(
                    AgentMetadata.builder()
                            .name(AgentName.COPILOT_AGENT)
                            .version("1.0.0")
                            .description("AI Copilot")
                            .capabilities(List.of("chat", "code-generation"))
                            .supportedModels(List.of("gpt-4"))
                            .latencySLA(1000)
                            .build(),
                    AgentMetadata.builder()
                            .name(AgentName.CODE_GENERATOR_AGENT)
                            .version("1.0.0")
                            .description("Code Generator")
                            .capabilities(List.of("code-generation", "scaffolding"))
                            .supportedModels(List.of("gpt-4"))
                            .latencySLA(5000)
                            .build()
            );
            when(registry.getAllMetadata()).thenReturn(metadata);

            HttpRequest request = HttpRequest.builder(
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/capabilities"
                    )
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> controller.listCapabilities(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("should find agents by capability")
        void shouldFindAgentsByCapability() {
            // GIVEN
            AgentRegistry.AgentInfo agentInfo = new AgentRegistry.AgentInfo(
                    AgentName.SEARCH_AGENT,
                    "1.0.0",
                    "Search",
                    AgentRegistry.AgentState.READY,
                    Instant.now(),
                    List.of("semantic-search")
            );
            when(registry.findByCapability("semantic-search"))
                    .thenReturn(List.of(agentInfo));
            when(registry.getMetadata(AgentName.SEARCH_AGENT))
                    .thenReturn(java.util.Optional.of(
                            AgentMetadata.builder()
                                    .name(AgentName.SEARCH_AGENT)
                                    .version("1.0.0")
                                    .description("Search")
                                    .capabilities(List.of("semantic-search"))
                                    .supportedModels(List.of("text-embedding-3-small"))
                                    .latencySLA(200)
                                    .build()
                    ));

            HttpRequest request = HttpRequest.builder(
                            HttpMethod.GET,
                            "http://localhost/api/v1/agents/by-capability/semantic-search"
                    )
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> controller.findByCapability(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(200);
            verify(registry).findByCapability("semantic-search");
        }
    }

    @Nested
    @DisplayName("Agent Execution")
    class AgentExecution {

        @Test
        @DisplayName("should execute copilot chat")
        @SuppressWarnings("unchecked")
        void shouldExecuteCopilotChat() {
            // GIVEN
            AIAgent<CopilotInput, Object> mockAgent = mock(AIAgent.class);
            AgentMetadata metadata = AgentMetadata.builder()
                    .name(AgentName.COPILOT_AGENT)
                    .version("1.0.0")
                    .description("AI Copilot")
                    .capabilities(List.of("chat"))
                    .supportedModels(List.of("gpt-4"))
                    .latencySLA(1000)
                    .build();
            when(mockAgent.getMetadata()).thenReturn(metadata);

            AgentResult<Object> result = AgentResult.success(
                    Map.of("response", "Hello! How can I help?"),
                    AgentResult.AgentMetrics.builder()
                            .latencyMs(50)
                            .tokensUsed(100)
                            .modelVersion("gpt-4")
                            .confidence(0.95)
                            .build(),
                    AgentResult.AgentTrace.of("CopilotAgent", "req-123")
            );

            doReturn(mockAgent).when(registry).get(AgentName.COPILOT_AGENT);
            when(mockAgent.execute(any(), any())).thenReturn(Promise.of(result));

            String requestBody = """
                    {
                        "sessionId": "session-1",
                        "message": "Hello",
                        "workspaceId": "ws-1"
                    }
                    """;

            HttpRequest request = HttpRequest.builder(
                            HttpMethod.POST,
                            "http://localhost/api/v1/agents/copilot/chat"
                    )
                    .withBody(requestBody.getBytes())
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> controller.copilotChat(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 404 when agent not registered")
        void shouldReturn404WhenAgentNotRegistered() {
            // GIVEN
            when(registry.get(any(AgentName.class))).thenReturn(null);

            String requestBody = """
                    {
                        "query": "test search"
                    }
                    """;

            HttpRequest request = HttpRequest.builder(
                            HttpMethod.POST,
                            "http://localhost/api/v1/agents/search"
                    )
                    .withBody(requestBody.getBytes())
                    .build();

            // WHEN
            HttpResponse response = runPromise(() -> controller.search(request));

            // THEN
            assertThat(response.getCode()).isEqualTo(404);
        }
    }
}
