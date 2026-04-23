package com.ghatana.products.yappc.domain.agent;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Verify AgentOrchestrator agent registration, workflow execution, and error handling
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentOrchestrator")
class AgentOrchestratorTest extends EventloopTestBase {

    private MetricsCollector metrics;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() { // GH-90000
        metrics = mock(MetricsCollector.class); // GH-90000
        orchestrator = new AgentOrchestrator(metrics); // GH-90000
    }

    private AIAgentContext context() { // GH-90000
        return AIAgentContext.builder() // GH-90000
                .userId("test-user")
                .workspaceId("test-workspace")
                .requestId("test-request-" + System.nanoTime()) // GH-90000
                .tenantId("test-tenant")
                .organizationId("test-org")
                .permissions(Set.of()) // GH-90000
                .timeout(AIAgentContext.DEFAULT_TIMEOUT) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000
    }

    private AgentResult<Map<String, Object>> successResult() { // GH-90000
        return AgentResult.success( // GH-90000
                Map.of("output", "value"), // GH-90000
                AgentResult.AgentMetrics.builder() // GH-90000
                        .latencyMs(50L).tokensUsed(100).modelVersion("gpt-4").build(),
                AgentResult.AgentTrace.of("TestAgent", "req-1")); // GH-90000
    }

    private AgentResult<Map<String, Object>> failureResult() { // GH-90000
        return AgentResult.failure( // GH-90000
                AgentResult.AgentError.of("ERR", "something went wrong", "TestAgent"), // GH-90000
                AgentResult.AgentMetrics.builder() // GH-90000
                        .latencyMs(10L).tokensUsed(0).modelVersion("gpt-4").build(),
                AgentResult.AgentTrace.of("TestAgent", "req-fail")); // GH-90000
    }

    @SuppressWarnings("unchecked")
    private AIAgent<Map<String, Object>, Map<String, Object>> mockAgent(AgentName name, // GH-90000
                                                                        AgentResult<Map<String, Object>> result) {
        AIAgent<Map<String, Object>, Map<String, Object>> agent = mock(AIAgent.class); // GH-90000
        AgentMetadata metadata = AgentMetadata.builder() // GH-90000
                .name(name).version("1.0").description("test agent")
                .capabilities(List.of("test")).supportedModels(List.of("gpt-4"))
                .latencySLA(1000L).build(); // GH-90000
        when(agent.getMetadata()).thenReturn(metadata); // GH-90000
        when(agent.execute(any(), any())).thenReturn(Promise.of(result)); // GH-90000
        return agent;
    }

    private AgentOrchestrator.WorkflowStep step(String id, AgentName agent, List<String> deps) { // GH-90000
        return new AgentOrchestrator.WorkflowStep(id, agent, Map.of(), deps); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Agent registration
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Agent registration")
    class RegistrationTests {

        @Test
        @DisplayName("getAgent returns registered agent")
        void shouldReturnRegisteredAgent() { // GH-90000
            AIAgent<?, ?> agent = mockAgent(AgentName.SENTIMENT_AGENT, successResult()); // GH-90000

            orchestrator.registerAgent(agent); // GH-90000

            assertThat(orchestrator.getAgent(AgentName.SENTIMENT_AGENT)).isSameAs(agent); // GH-90000
        }

        @Test
        @DisplayName("getAgent returns null for unregistered agent")
        void shouldReturnNullForUnregisteredAgent() { // GH-90000
            assertThat(orchestrator.getAgent(AgentName.COPILOT_AGENT)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("unregisterAgent removes the agent")
        void shouldUnregisterAgent() { // GH-90000
            AIAgent<?, ?> agent = mockAgent(AgentName.SEARCH_AGENT, successResult()); // GH-90000
            orchestrator.registerAgent(agent); // GH-90000

            orchestrator.unregisterAgent(AgentName.SEARCH_AGENT); // GH-90000

            assertThat(orchestrator.getAgent(AgentName.SEARCH_AGENT)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("registering the same agent name replaces the previous registration")
        void shouldReplaceAgentOnReRegister() { // GH-90000
            AIAgent<?, ?> first = mockAgent(AgentName.RECOMMENDATION_AGENT, successResult()); // GH-90000
            AIAgent<?, ?> second = mockAgent(AgentName.RECOMMENDATION_AGENT, successResult()); // GH-90000
            orchestrator.registerAgent(first); // GH-90000
            orchestrator.registerAgent(second); // GH-90000

            assertThat(orchestrator.getAgent(AgentName.RECOMMENDATION_AGENT)).isSameAs(second); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Workflow execution — happy path
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeWorkflow() — success")
    class SuccessWorkflowTests {

        @Test
        @DisplayName("single-step workflow with registered agent returns success result")
        void shouldExecuteSingleStepWorkflow() { // GH-90000
            AIAgent<Map<String, Object>, Map<String, Object>> agent =
                    mockAgent(AgentName.SENTIMENT_AGENT, successResult()); // GH-90000
            orchestrator.registerAgent(agent); // GH-90000

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( // GH-90000
                    "sentiment-wf", "Sentiment workflow",
                    List.of(step("step-1", AgentName.SENTIMENT_AGENT, List.of())), // GH-90000
                    true);

            AgentOrchestrator.WorkflowResult result = runPromise( // GH-90000
                    () -> orchestrator.executeWorkflow(workflow, context())); // GH-90000

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.results()).hasSize(1); // GH-90000
            assertThat(result.durationMs()).isGreaterThanOrEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("increments started and success counters for successful workflow")
        void shouldIncrementMetricsCountersOnSuccess() { // GH-90000
            AIAgent<Map<String, Object>, Map<String, Object>> agent =
                    mockAgent(AgentName.QUERY_PARSER_AGENT, successResult()); // GH-90000
            orchestrator.registerAgent(agent); // GH-90000

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( // GH-90000
                    "qp-wf", "QueryParser workflow",
                    List.of(step("step-1", AgentName.QUERY_PARSER_AGENT, List.of())), // GH-90000
                    false);

            runPromise(() -> orchestrator.executeWorkflow(workflow, context())); // GH-90000

            verify(metrics).incrementCounter(eq("orchestrator.workflows.started"), eq("workflow"), eq("qp-wf"));
            verify(metrics).incrementCounter(eq("orchestrator.workflows.success"), eq("workflow"), eq("qp-wf"));
            verify(metrics).recordTimer(eq("orchestrator.workflows.duration"), anyLong(), eq("workflow"), eq("qp-wf"));
        }

        @Test
        @DisplayName("two independent steps run in same stage, both succeed")
        void shouldExecuteParallelSteps() { // GH-90000
            AIAgent<Map<String, Object>, Map<String, Object>> a1 =
                    mockAgent(AgentName.SENTIMENT_AGENT, successResult()); // GH-90000
            AIAgent<Map<String, Object>, Map<String, Object>> a2 =
                    mockAgent(AgentName.SEARCH_AGENT, successResult()); // GH-90000
            orchestrator.registerAgent(a1); // GH-90000
            orchestrator.registerAgent(a2); // GH-90000

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( // GH-90000
                    "parallel-wf", "Two independent steps",
                    List.of( // GH-90000
                            step("step-1", AgentName.SENTIMENT_AGENT, List.of()), // GH-90000
                            step("step-2", AgentName.SEARCH_AGENT, List.of())), // GH-90000
                    false);

            AgentOrchestrator.WorkflowResult result = runPromise( // GH-90000
                    () -> orchestrator.executeWorkflow(workflow, context())); // GH-90000

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.results()).hasSize(2); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Workflow execution — failure paths
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeWorkflow() — failures")
    class FailureWorkflowTests {

        @Test
        @DisplayName("step for unregistered agent causes error counter to be incremented")
        void shouldHandleMissingAgent() { // GH-90000
            // no agent registered

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( // GH-90000
                    "missing-agent-wf", "Uses unregistered agent",
                    List.of(step("step-1", AgentName.COPILOT_AGENT, List.of())), // GH-90000
                    true);

            assertThatThrownBy(() -> runPromise(() -> orchestrator.executeWorkflow(workflow, context()))) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000

            verify(metrics).incrementCounter(eq("orchestrator.workflows.error"), eq("workflow"), eq("missing-agent-wf"));
        }

        @Test
        @DisplayName("stopOnError=true aborts workflow and increments failed counter")
        void shouldAbortOnFailureWhenStopOnErrorTrue() { // GH-90000
            AIAgent<Map<String, Object>, Map<String, Object>> agent =
                    mockAgent(AgentName.ANOMALY_DETECTOR_AGENT, failureResult()); // GH-90000
            orchestrator.registerAgent(agent); // GH-90000

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( // GH-90000
                    "fail-wf", "Failing workflow",
                    List.of(step("step-1", AgentName.ANOMALY_DETECTOR_AGENT, List.of())), // GH-90000
                    true);

            assertThatThrownBy(() -> runPromise(() -> orchestrator.executeWorkflow(workflow, context()))) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000

            verify(metrics).incrementCounter(eq("orchestrator.workflows.error"), eq("workflow"), eq("fail-wf"));
        }

        @Test
        @DisplayName("stopOnError=false allows workflow to complete with mixed results")
        void shouldContinueOnFailureWhenStopOnErrorFalse() { // GH-90000
            AIAgent<Map<String, Object>, Map<String, Object>> agent =
                    mockAgent(AgentName.ANOMALY_DETECTOR_AGENT, failureResult()); // GH-90000
            orchestrator.registerAgent(agent); // GH-90000

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( // GH-90000
                    "resilient-wf", "Continues despite failure",
                    List.of(step("step-1", AgentName.ANOMALY_DETECTOR_AGENT, List.of())), // GH-90000
                    false);

            AgentOrchestrator.WorkflowResult result = runPromise( // GH-90000
                    () -> orchestrator.executeWorkflow(workflow, context())); // GH-90000

            assertThat(result.success()).isFalse(); // GH-90000
            assertThat(result.results()).hasSize(1); // GH-90000
            assertThat(result.results().getFirst().success()).isFalse(); // GH-90000
            verify(metrics).incrementCounter(eq("orchestrator.workflows.failed"), eq("workflow"), eq("resilient-wf"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Dependency resolution
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Dependency resolution")
    class DependencyTests {

        @Test
        @DisplayName("circular dependency throws IllegalArgumentException")
        void shouldDetectCircularDependency() { // GH-90000
            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( // GH-90000
                    "circular-wf", "Circular deps",
                    List.of( // GH-90000
                            step("A", AgentName.SENTIMENT_AGENT, List.of("B")),
                            step("B", AgentName.SEARCH_AGENT, List.of("A"))),
                    true);

            assertThatThrownBy(() -> runPromise(() -> orchestrator.executeWorkflow(workflow, context()))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Circular dependency");
        }
    }
}
