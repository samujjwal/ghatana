package com.ghatana.yappc.domain.agent;

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
    void setUp() { 
        metrics = mock(MetricsCollector.class); 
        orchestrator = new AgentOrchestrator(metrics); 
    }

    private AIAgentContext context() { 
        return AIAgentContext.builder() 
                .userId("test-user")
                .workspaceId("test-workspace")
                .requestId("test-request-" + System.nanoTime()) 
                .tenantId("test-tenant")
                .organizationId("test-org")
                .permissions(Set.of()) 
                .timeout(AIAgentContext.DEFAULT_TIMEOUT) 
                .metadata(Map.of()) 
                .build(); 
    }

    private AgentResult<Map<String, Object>> successResult() { 
        return AgentResult.success( 
                Map.of("output", "value"), 
                AgentResult.AgentMetrics.builder() 
                        .latencyMs(50L).tokensUsed(100).modelVersion("gpt-4").build(),
                AgentResult.AgentTrace.of("TestAgent", "req-1")); 
    }

    private AgentResult<Map<String, Object>> failureResult() { 
        return AgentResult.failure( 
                AgentResult.AgentError.of("ERR", "something went wrong", "TestAgent"), 
                AgentResult.AgentMetrics.builder() 
                        .latencyMs(10L).tokensUsed(0).modelVersion("gpt-4").build(),
                AgentResult.AgentTrace.of("TestAgent", "req-fail")); 
    }

    @SuppressWarnings("unchecked")
    private AIAgent<Map<String, Object>, Map<String, Object>> mockAgent(AgentName name, 
                                                                        AgentResult<Map<String, Object>> result) {
        AIAgent<Map<String, Object>, Map<String, Object>> agent = mock(AIAgent.class); 
        AgentMetadata metadata = AgentMetadata.builder() 
                .name(name).version("1.0").description("test agent")
                .capabilities(List.of("test")).supportedModels(List.of("gpt-4"))
                .latencySLA(1000L).build(); 
        when(agent.getMetadata()).thenReturn(metadata); 
        when(agent.execute(any(), any())).thenReturn(Promise.of(result)); 
        return agent;
    }

    private AgentOrchestrator.WorkflowStep step(String id, AgentName agent, List<String> deps) { 
        return new AgentOrchestrator.WorkflowStep(id, agent, Map.of(), deps); 
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Agent registration
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Agent registration")
    class RegistrationTests {

        @Test
        @DisplayName("getAgent returns registered agent")
        void shouldReturnRegisteredAgent() { 
            AIAgent<?, ?> agent = mockAgent(AgentName.SENTIMENT_AGENT, successResult()); 

            orchestrator.registerAgent(agent); 

            assertThat(orchestrator.getAgent(AgentName.SENTIMENT_AGENT)).isSameAs(agent); 
        }

        @Test
        @DisplayName("getAgent returns null for unregistered agent")
        void shouldReturnNullForUnregisteredAgent() { 
            assertThat(orchestrator.getAgent(AgentName.COPILOT_AGENT)).isNull(); 
        }

        @Test
        @DisplayName("unregisterAgent removes the agent")
        void shouldUnregisterAgent() { 
            AIAgent<?, ?> agent = mockAgent(AgentName.SEARCH_AGENT, successResult()); 
            orchestrator.registerAgent(agent); 

            orchestrator.unregisterAgent(AgentName.SEARCH_AGENT); 

            assertThat(orchestrator.getAgent(AgentName.SEARCH_AGENT)).isNull(); 
        }

        @Test
        @DisplayName("registering the same agent name replaces the previous registration")
        void shouldReplaceAgentOnReRegister() { 
            AIAgent<?, ?> first = mockAgent(AgentName.RECOMMENDATION_AGENT, successResult()); 
            AIAgent<?, ?> second = mockAgent(AgentName.RECOMMENDATION_AGENT, successResult()); 
            orchestrator.registerAgent(first); 
            orchestrator.registerAgent(second); 

            assertThat(orchestrator.getAgent(AgentName.RECOMMENDATION_AGENT)).isSameAs(second); 
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
        void shouldExecuteSingleStepWorkflow() { 
            AIAgent<Map<String, Object>, Map<String, Object>> agent =
                    mockAgent(AgentName.SENTIMENT_AGENT, successResult()); 
            orchestrator.registerAgent(agent); 

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( 
                    "sentiment-wf", "Sentiment workflow",
                    List.of(step("step-1", AgentName.SENTIMENT_AGENT, List.of())), 
                    true);

            AgentOrchestrator.WorkflowResult result = runPromise( 
                    () -> orchestrator.executeWorkflow(workflow, context())); 

            assertThat(result.success()).isTrue(); 
            assertThat(result.results()).hasSize(1); 
            assertThat(result.durationMs()).isGreaterThanOrEqualTo(0L); 
        }

        @Test
        @DisplayName("increments started and success counters for successful workflow")
        void shouldIncrementMetricsCountersOnSuccess() { 
            AIAgent<Map<String, Object>, Map<String, Object>> agent =
                    mockAgent(AgentName.QUERY_PARSER_AGENT, successResult()); 
            orchestrator.registerAgent(agent); 

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( 
                    "qp-wf", "QueryParser workflow",
                    List.of(step("step-1", AgentName.QUERY_PARSER_AGENT, List.of())), 
                    false);

            runPromise(() -> orchestrator.executeWorkflow(workflow, context())); 

            verify(metrics).incrementCounter(eq("orchestrator.workflows.started"), eq("workflow"), eq("qp-wf"));
            verify(metrics).incrementCounter(eq("orchestrator.workflows.success"), eq("workflow"), eq("qp-wf"));
            verify(metrics).recordTimer(eq("orchestrator.workflows.duration"), anyLong(), eq("workflow"), eq("qp-wf"));
        }

        @Test
        @DisplayName("two independent steps run in same stage, both succeed")
        void shouldExecuteParallelSteps() { 
            AIAgent<Map<String, Object>, Map<String, Object>> a1 =
                    mockAgent(AgentName.SENTIMENT_AGENT, successResult()); 
            AIAgent<Map<String, Object>, Map<String, Object>> a2 =
                    mockAgent(AgentName.SEARCH_AGENT, successResult()); 
            orchestrator.registerAgent(a1); 
            orchestrator.registerAgent(a2); 

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( 
                    "parallel-wf", "Two independent steps",
                    List.of( 
                            step("step-1", AgentName.SENTIMENT_AGENT, List.of()), 
                            step("step-2", AgentName.SEARCH_AGENT, List.of())), 
                    false);

            AgentOrchestrator.WorkflowResult result = runPromise( 
                    () -> orchestrator.executeWorkflow(workflow, context())); 

            assertThat(result.success()).isTrue(); 
            assertThat(result.results()).hasSize(2); 
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
        void shouldHandleMissingAgent() { 
            // no agent registered

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( 
                    "missing-agent-wf", "Uses unregistered agent",
                    List.of(step("step-1", AgentName.COPILOT_AGENT, List.of())), 
                    true);

            assertThatThrownBy(() -> runPromise(() -> orchestrator.executeWorkflow(workflow, context()))) 
                    .isInstanceOf(Exception.class); 

            verify(metrics).incrementCounter(eq("orchestrator.workflows.error"), eq("workflow"), eq("missing-agent-wf"));
        }

        @Test
        @DisplayName("stopOnError=true aborts workflow and increments failed counter")
        void shouldAbortOnFailureWhenStopOnErrorTrue() { 
            AIAgent<Map<String, Object>, Map<String, Object>> agent =
                    mockAgent(AgentName.ANOMALY_DETECTOR_AGENT, failureResult()); 
            orchestrator.registerAgent(agent); 

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( 
                    "fail-wf", "Failing workflow",
                    List.of(step("step-1", AgentName.ANOMALY_DETECTOR_AGENT, List.of())), 
                    true);

            assertThatThrownBy(() -> runPromise(() -> orchestrator.executeWorkflow(workflow, context()))) 
                    .isInstanceOf(Exception.class); 

            verify(metrics).incrementCounter(eq("orchestrator.workflows.error"), eq("workflow"), eq("fail-wf"));
        }

        @Test
        @DisplayName("stopOnError=false allows workflow to complete with mixed results")
        void shouldContinueOnFailureWhenStopOnErrorFalse() { 
            AIAgent<Map<String, Object>, Map<String, Object>> agent =
                    mockAgent(AgentName.ANOMALY_DETECTOR_AGENT, failureResult()); 
            orchestrator.registerAgent(agent); 

            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( 
                    "resilient-wf", "Continues despite failure",
                    List.of(step("step-1", AgentName.ANOMALY_DETECTOR_AGENT, List.of())), 
                    false);

            AgentOrchestrator.WorkflowResult result = runPromise( 
                    () -> orchestrator.executeWorkflow(workflow, context())); 

            assertThat(result.success()).isFalse(); 
            assertThat(result.results()).hasSize(1); 
            assertThat(result.results().getFirst().success()).isFalse(); 
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
        void shouldDetectCircularDependency() { 
            AgentOrchestrator.AgentWorkflow workflow = new AgentOrchestrator.AgentWorkflow( 
                    "circular-wf", "Circular deps",
                    List.of( 
                            step("A", AgentName.SENTIMENT_AGENT, List.of("B")),
                            step("B", AgentName.SEARCH_AGENT, List.of("A"))),
                    true);

            assertThatThrownBy(() -> runPromise(() -> orchestrator.executeWorkflow(workflow, context()))) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("Circular dependency");
        }
    }
}
