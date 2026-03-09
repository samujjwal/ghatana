package com.ghatana.virtualorg.framework.runtime;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.ToolDefinition;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.tools.AgentTool;
import com.ghatana.virtualorg.framework.tools.ToolContext;
import com.ghatana.virtualorg.framework.tools.ToolInput;
import com.ghatana.virtualorg.framework.tools.ToolRegistry;
import com.ghatana.virtualorg.framework.tools.ToolResult;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DefaultAgentRuntime.
 *
 * @doc.type class
 * @doc.purpose Unit tests for agent runtime engine
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DefaultAgentRuntime Tests")
class DefaultAgentRuntimeTest extends EventloopTestBase {

    private DefaultAgentRuntime runtime;
    private MockLLMGateway mockGateway;
    private ToolRegistry toolRegistry;
    private MockMetricsCollector mockMetrics;

    @BeforeEach
    void setUp() {
        mockGateway = new MockLLMGateway();
        mockMetrics = new MockMetricsCollector();
        toolRegistry = new ToolRegistry();

        // Register a test tool
        toolRegistry.register(new TestTool());

        runtime = new DefaultAgentRuntime(
                DefaultAgentRuntime.RuntimeConfig.defaults(),
                mockGateway,
                toolRegistry,
                mockMetrics
        );
    }

    @Test
    @DisplayName("Should start in INITIALIZING state")
    void shouldStartInInitializingState() {
        assertThat(runtime.getState()).isEqualTo(AgentState.INITIALIZING);
    }

    @Test
    @DisplayName("Should transition to IDLE after start")
    void shouldTransitionToIdleAfterStart() {
        // GIVEN
        AgentContext context = AgentContext.builder()
                .agentId("test-agent-001")
                .build();

        // WHEN
        runPromise(() -> runtime.start(context));

        // THEN
        assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);
        assertThat(runtime.getContext()).isEqualTo(context);
    }

    @Test
    @DisplayName("Should transition to STOPPED after stop")
    void shouldTransitionToStoppedAfterStop() {
        // GIVEN
        AgentContext context = AgentContext.builder()
                .agentId("test-agent-001")
                .build();
        runPromise(() -> runtime.start(context));

        // WHEN
        runPromise(() -> runtime.stop());

        // THEN
        assertThat(runtime.getState()).isEqualTo(AgentState.STOPPED);
    }

    @Test
    @DisplayName("Should pause and resume correctly")
    void shouldPauseAndResumeCorrectly() {
        // GIVEN
        AgentContext context = AgentContext.builder()
                .agentId("test-agent-001")
                .build();
        runPromise(() -> runtime.start(context));

        // WHEN paused
        runtime.pause();
        assertThat(runtime.getState()).isEqualTo(AgentState.PAUSED);

        // WHEN resumed
        runtime.resume();
        assertThat(runtime.getState()).isEqualTo(AgentState.IDLE);
    }

    @Test
    @DisplayName("Should notify listeners on state change")
    void shouldNotifyListenersOnStateChange() {
        // GIVEN
        AtomicInteger stateChangeCount = new AtomicInteger(0);
        runtime.addListener(new AgentRuntime.RuntimeListener() {
            @Override
            public void onStateChange(AgentState oldState, AgentState newState) {
                stateChangeCount.incrementAndGet();
            }
        });

        AgentContext context = AgentContext.builder()
                .agentId("test-agent-001")
                .build();

        // WHEN
        runPromise(() -> runtime.start(context));

        // THEN (transition from INITIALIZING to IDLE)
        assertThat(stateChangeCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should run cycle and return to IDLE")
    void shouldRunCycleAndReturnToIdle() {
        // GIVEN
        AgentContext context = AgentContext.builder()
                .agentId("test-agent-001")
                .availableTools(List.of("test.echo"))
                .build();
        runPromise(() -> runtime.start(context));

        // Set up mock LLM to return a simple response
        mockGateway.setResponse(CompletionResult.of("I understand. No action needed."));

        // WHEN
        AgentState finalState = runPromise(() -> runtime.runCycle());

        // THEN
        assertThat(finalState).isEqualTo(AgentState.IDLE);
    }

    // ========== Mock Classes ==========
    private static class MockLLMGateway implements LLMGateway {

        private CompletionResult response = CompletionResult.of("Mock response");

        public void setResponse(CompletionResult response) {
            this.response = response;
        }

        @Override
        public Promise<CompletionResult> complete(CompletionRequest request) {
            return Promise.of(response);
        }

        @Override
        public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) {
            return Promise.of(response);
        }

        @Override
        public Promise<CompletionResult> continueWithToolResults(
                CompletionRequest request,
                List<com.ghatana.ai.llm.ToolCallResult> toolResults) {
            return Promise.of(response);
        }

        @Override
        public Promise<com.ghatana.ai.llm.TokenStream> stream(CompletionRequest request) {
            return Promise.of(null);
        }

        @Override
        public Promise<com.ghatana.ai.embedding.EmbeddingResult> embed(String text) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<com.ghatana.ai.embedding.EmbeddingResult>> embedBatch(List<String> texts) {
            return Promise.of(List.of());
        }

        @Override
        public MetricsCollector getMetrics() {
            return new MockMetricsCollector();
        }

        @Override
        public String getDefaultProvider() {
            return "mock";
        }

        @Override
        public List<String> getAvailableProviders() {
            return List.of("mock");
        }

        @Override
        public boolean isProviderAvailable(String providerName) {
            return "mock".equals(providerName);
        }
    }

    private static class MockMetricsCollector implements MetricsCollector {

        @Override
        public void increment(String metricName, double amount, Map<String, String> tags) {
        }

        @Override
        public void recordError(String metricName, Exception e, Map<String, String> tags) {
        }

        @Override
        public void incrementCounter(String name, String... tags) {
        }

        @Override
        public MeterRegistry getMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static class TestTool implements AgentTool {

        @Override
        public String getName() {
            return "test.echo";
        }

        @Override
        public String getDescription() {
            return "Echoes back the input";
        }

        @Override
        public Map<String, Object> getSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "message", Map.of("type", "string")
                    ),
                    "required", List.of("message")
            );
        }

        @Override
        public Set<String> getRequiredPermissions() {
            return Set.of();
        }

        @Override
        public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
            String message = input.getString("message", "default");
            return Promise.of(ToolResult.success(Map.of("echo", message)));
        }
    }
}
