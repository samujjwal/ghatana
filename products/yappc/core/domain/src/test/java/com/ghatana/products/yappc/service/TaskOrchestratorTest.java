package com.ghatana.products.yappc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.yappc.domain.agent.*;
import com.ghatana.products.yappc.domain.task.*;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for TaskOrchestrator — core orchestration logic with 4 execution patterns.
 *
 * @doc.type class
 * @doc.purpose Unit tests for TaskOrchestrator orchestration patterns
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TaskOrchestrator Tests")
@ExtendWith(MockitoExtension.class)
class TaskOrchestratorTest extends EventloopTestBase {

  private AgentRegistry agentRegistry;
  private CapabilityMatcher capabilityMatcher;
  private TaskOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    agentRegistry = new AgentRegistry(new NoopMetricsCollector());
    capabilityMatcher = new CapabilityMatcher();
    orchestrator = new TaskOrchestrator(agentRegistry, capabilityMatcher);
  }

  // ===== Sequential Execution =====

  @Nested
  @DisplayName("Sequential Execution")
  class SequentialExecution {

    @Test
    @DisplayName("Should execute task sequentially with capable agent")
    void shouldExecuteSequentially() {
      // Register a mock agent with matching capability
      AIAgent<Map<String, Object>, String> mockAgent = createMockAgent(
          AgentName.CODE_GENERATOR_AGENT,
          List.of("code-generation"),
          Promise.of(AgentResult.success("generated code", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request"))));
      agentRegistry.register(mockAgent);

      TaskDefinition task = TaskDefinition.builder()
          .id("task-1")
          .name("Generate Code")
          .description("Generate Java code")
          .domain("implementation")
          .phase(SDLCPhase.IMPLEMENTATION)
          .requiredCapabilities(List.of("code-generation"))
          .build();

      TaskExecutionContext ctx = TaskExecutionContext.builder()
          .userId("user-1")
          .tenantId("tenant-1")
          .build();

      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx));

      assertThat(result).isEqualTo("generated code");
    }

    @Test
    @DisplayName("Should fail when no capable agent found")
    void shouldFailWhenNoCapableAgent() {
      TaskDefinition task = TaskDefinition.builder()
          .id("task-2")
          .name("Unknown Task")
          .description("No agent has this capability")
          .domain("unknown")
          .phase(SDLCPhase.DISCOVERY)
          .requiredCapabilities(List.of("non-existent-capability"))
          .build();

      TaskExecutionContext ctx = TaskExecutionContext.builder()
          .userId("user-1")
          .tenantId("tenant-1")
          .build();

      assertThatThrownBy(() -> runPromise(() -> orchestrator.execute(task, Map.of(), ctx)))
          .isInstanceOf(TaskOrchestrator.NoCapableAgentException.class)
          .hasMessageContaining("non-existent-capability");
    }
  }

  // ===== Parallel Execution =====

  @Nested
  @DisplayName("Parallel Execution")
  class ParallelExecution {

    @Test
    @DisplayName("Should execute in parallel and return first success")
    void shouldReturnFirstSuccess() {
      AIAgent<Map<String, Object>, String> agent1 = createMockAgent(
          AgentName.COPILOT_AGENT, List.of("analysis"),
          Promise.of(AgentResult.success("result-from-copilot", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request"))));
      AIAgent<Map<String, Object>, String> agent2 = createMockAgent(
          AgentName.PREDICTION_AGENT, List.of("analysis"),
          Promise.of(AgentResult.success("result-from-prediction", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request"))));
      agentRegistry.register(agent1);
      agentRegistry.register(agent2);

      TaskDefinition task = TaskDefinition.builder()
          .id("task-parallel")
          .name("Parallel Analysis")
          .description("Run analysis in parallel")
          .domain("analysis")
          .phase(SDLCPhase.TESTING)
          .requiredCapabilities(List.of("analysis"))
          .build();

      TaskExecutionContext ctx = TaskExecutionContext.builder()
          .userId("user-1")
          .tenantId("tenant-1")
          .metadata(Map.of("orchestrationPattern", "PARALLEL"))
          .build();

      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx));

      // At least one result should be returned
      assertThat(result).isIn("result-from-copilot", "result-from-prediction");
    }
  }

  // ===== Conditional Execution =====

  @Nested
  @DisplayName("Conditional Execution")
  class ConditionalExecution {

    @Test
    @DisplayName("Should fall back to sequential when no condition found")
    void shouldFallbackToSequential() {
      AIAgent<Map<String, Object>, String> agent = createMockAgent(
          AgentName.ANOMALY_DETECTOR_AGENT, List.of("anomaly-detection"),
          Promise.of(AgentResult.success("anomaly result", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request"))));
      agentRegistry.register(agent);

      TaskDefinition task = TaskDefinition.builder()
          .id("task-conditional")
          .name("Conditional Task")
          .description("Conditional execution")
          .domain("operations")
          .phase(SDLCPhase.OPERATIONS)
          .requiredCapabilities(List.of("anomaly-detection"))
          .build();

      TaskExecutionContext ctx = TaskExecutionContext.builder()
          .userId("user-1")
          .tenantId("tenant-1")
          .metadata(Map.of("orchestrationPattern", "CONDITIONAL"))
          .build();

      // Input without conditionCapability - should fall back to sequential
      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx));
      assertThat(result).isEqualTo("anomaly result");
    }
  }

  // ===== Orchestration Pattern Resolution =====

  @Nested
  @DisplayName("Pattern Resolution")
  class PatternResolution {

    @Test
    @DisplayName("Should default to SEQUENTIAL when no pattern specified")
    void shouldDefaultToSequential() {
      AIAgent<Map<String, Object>, String> agent = createMockAgent(
          AgentName.SENTIMENT_AGENT, List.of("sentiment"),
          Promise.of(AgentResult.success("sentiment ok", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request"))));
      agentRegistry.register(agent);

      TaskDefinition task = TaskDefinition.builder()
          .id("task-default")
          .name("Default Pattern")
          .description("No pattern specified")
          .domain("analysis")
          .phase(SDLCPhase.TESTING)
          .requiredCapabilities(List.of("sentiment"))
          .build();

      TaskExecutionContext ctx = TaskExecutionContext.builder()
          .userId("user-1")
          .tenantId("tenant-1")
          .build();

      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx));
      assertThat(result).isEqualTo("sentiment ok");
    }

    @Test
    @DisplayName("Should handle invalid pattern gracefully")
    void shouldHandleInvalidPattern() {
      AIAgent<Map<String, Object>, String> agent = createMockAgent(
          AgentName.RECOMMENDATION_AGENT, List.of("recommendations"),
          Promise.of(AgentResult.success("recommendation", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request"))));
      agentRegistry.register(agent);

      TaskDefinition task = TaskDefinition.builder()
          .id("task-invalid-pattern")
          .name("Invalid Pattern")
          .description("Invalid pattern string")
          .domain("analysis")
          .phase(SDLCPhase.DISCOVERY)
          .requiredCapabilities(List.of("recommendations"))
          .build();

      TaskExecutionContext ctx = TaskExecutionContext.builder()
          .userId("user-1")
          .tenantId("tenant-1")
          .metadata(Map.of("orchestrationPattern", "NONEXISTENT"))
          .build();

      // Should fall back to SEQUENTIAL
      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx));
      assertThat(result).isEqualTo("recommendation");
    }
  }

  // ===== Helper Methods =====

  @SuppressWarnings("unchecked")
  private <TIn, TOut> AIAgent<TIn, TOut> createMockAgent(
      AgentName name,
      List<String> capabilities,
      Promise<AgentResult<TOut>> result) {

    AIAgent<TIn, TOut> agent = mock(AIAgent.class);

    AgentMetadata metadata = AgentMetadata.builder()
        .name(name)
        .version("1.0.0")
        .description("Mock agent: " + name.getDisplayName())
        .capabilities(capabilities)
        .supportedModels(List.of("gpt-4"))
        .latencySLA(5000)
        .build();

    when(agent.getMetadata()).thenReturn(metadata);
    when(agent.getId()).thenReturn(name.name().toLowerCase().replace("_", "-"));
    doReturn(result).when(agent).process(any(), any());
    lenient().when(agent.healthCheck()).thenReturn(Promise.of(AgentHealth.healthy(0L)));
    lenient().when(agent.execute(any(), any())).thenReturn(result);

    return agent;
  }
}
