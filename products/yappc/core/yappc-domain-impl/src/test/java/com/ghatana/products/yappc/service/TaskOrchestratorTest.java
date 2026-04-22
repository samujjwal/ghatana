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
@DisplayName("TaskOrchestrator Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class TaskOrchestratorTest extends EventloopTestBase {

  private AgentRegistry agentRegistry;
  private CapabilityMatcher capabilityMatcher;
  private TaskOrchestrator orchestrator;

  @BeforeEach
  void setUp() { // GH-90000
    agentRegistry = new AgentRegistry(new NoopMetricsCollector()); // GH-90000
    capabilityMatcher = new CapabilityMatcher(); // GH-90000
    orchestrator = new TaskOrchestrator(agentRegistry, capabilityMatcher); // GH-90000
  }

  // ===== Sequential Execution =====

  @Nested
  @DisplayName("Sequential Execution [GH-90000]")
  class SequentialExecution {

    @Test
    @DisplayName("Should execute task sequentially with capable agent [GH-90000]")
    void shouldExecuteSequentially() { // GH-90000
      // Register a mock agent with matching capability
      AIAgent<Map<String, Object>, String> mockAgent = createMockAgent( // GH-90000
          AgentName.CODE_GENERATOR_AGENT,
          List.of("code-generation [GH-90000]"),
          Promise.of(AgentResult.success("generated code", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request")))); // GH-90000
      agentRegistry.register(mockAgent); // GH-90000

      TaskDefinition task = TaskDefinition.builder() // GH-90000
          .id("task-1 [GH-90000]")
          .name("Generate Code [GH-90000]")
          .description("Generate Java code [GH-90000]")
          .domain("implementation [GH-90000]")
          .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
          .requiredCapabilities(List.of("code-generation [GH-90000]"))
          .build(); // GH-90000

      TaskExecutionContext ctx = TaskExecutionContext.builder() // GH-90000
          .userId("user-1 [GH-90000]")
          .tenantId("tenant-1 [GH-90000]")
          .build(); // GH-90000

      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx)); // GH-90000

      assertThat(result).isEqualTo("generated code [GH-90000]");
    }

    @Test
    @DisplayName("Should fail when no capable agent found [GH-90000]")
    void shouldFailWhenNoCapableAgent() { // GH-90000
      TaskDefinition task = TaskDefinition.builder() // GH-90000
          .id("task-2 [GH-90000]")
          .name("Unknown Task [GH-90000]")
          .description("No agent has this capability [GH-90000]")
          .domain("unknown [GH-90000]")
          .phase(SDLCPhase.DISCOVERY) // GH-90000
          .requiredCapabilities(List.of("non-existent-capability [GH-90000]"))
          .build(); // GH-90000

      TaskExecutionContext ctx = TaskExecutionContext.builder() // GH-90000
          .userId("user-1 [GH-90000]")
          .tenantId("tenant-1 [GH-90000]")
          .build(); // GH-90000

      assertThatThrownBy(() -> runPromise(() -> orchestrator.execute(task, Map.of(), ctx))) // GH-90000
          .isInstanceOf(TaskOrchestrator.NoCapableAgentException.class) // GH-90000
          .hasMessageContaining("non-existent-capability [GH-90000]");
    }
  }

  // ===== Parallel Execution =====

  @Nested
  @DisplayName("Parallel Execution [GH-90000]")
  class ParallelExecution {

    @Test
    @DisplayName("Should execute in parallel and return first success [GH-90000]")
    void shouldReturnFirstSuccess() { // GH-90000
      AIAgent<Map<String, Object>, String> agent1 = createMockAgent( // GH-90000
          AgentName.COPILOT_AGENT, List.of("analysis [GH-90000]"),
          Promise.of(AgentResult.success("result-from-copilot", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request")))); // GH-90000
      AIAgent<Map<String, Object>, String> agent2 = createMockAgent( // GH-90000
          AgentName.PREDICTION_AGENT, List.of("analysis [GH-90000]"),
          Promise.of(AgentResult.success("result-from-prediction", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request")))); // GH-90000
      agentRegistry.register(agent1); // GH-90000
      agentRegistry.register(agent2); // GH-90000

      TaskDefinition task = TaskDefinition.builder() // GH-90000
          .id("task-parallel [GH-90000]")
          .name("Parallel Analysis [GH-90000]")
          .description("Run analysis in parallel [GH-90000]")
          .domain("analysis [GH-90000]")
          .phase(SDLCPhase.TESTING) // GH-90000
          .requiredCapabilities(List.of("analysis [GH-90000]"))
          .build(); // GH-90000

      TaskExecutionContext ctx = TaskExecutionContext.builder() // GH-90000
          .userId("user-1 [GH-90000]")
          .tenantId("tenant-1 [GH-90000]")
          .metadata(Map.of("orchestrationPattern", "PARALLEL")) // GH-90000
          .build(); // GH-90000

      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx)); // GH-90000

      // At least one result should be returned
      assertThat(result).isIn("result-from-copilot", "result-from-prediction"); // GH-90000
    }
  }

  // ===== Conditional Execution =====

  @Nested
  @DisplayName("Conditional Execution [GH-90000]")
  class ConditionalExecution {

    @Test
    @DisplayName("Should fall back to sequential when no condition found [GH-90000]")
    void shouldFallbackToSequential() { // GH-90000
      AIAgent<Map<String, Object>, String> agent = createMockAgent( // GH-90000
          AgentName.ANOMALY_DETECTOR_AGENT, List.of("anomaly-detection [GH-90000]"),
          Promise.of(AgentResult.success("anomaly result", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request")))); // GH-90000
      agentRegistry.register(agent); // GH-90000

      TaskDefinition task = TaskDefinition.builder() // GH-90000
          .id("task-conditional [GH-90000]")
          .name("Conditional Task [GH-90000]")
          .description("Conditional execution [GH-90000]")
          .domain("operations [GH-90000]")
          .phase(SDLCPhase.OPERATIONS) // GH-90000
          .requiredCapabilities(List.of("anomaly-detection [GH-90000]"))
          .build(); // GH-90000

      TaskExecutionContext ctx = TaskExecutionContext.builder() // GH-90000
          .userId("user-1 [GH-90000]")
          .tenantId("tenant-1 [GH-90000]")
          .metadata(Map.of("orchestrationPattern", "CONDITIONAL")) // GH-90000
          .build(); // GH-90000

      // Input without conditionCapability - should fall back to sequential
      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx)); // GH-90000
      assertThat(result).isEqualTo("anomaly result [GH-90000]");
    }
  }

  // ===== Orchestration Pattern Resolution =====

  @Nested
  @DisplayName("Pattern Resolution [GH-90000]")
  class PatternResolution {

    @Test
    @DisplayName("Should default to SEQUENTIAL when no pattern specified [GH-90000]")
    void shouldDefaultToSequential() { // GH-90000
      AIAgent<Map<String, Object>, String> agent = createMockAgent( // GH-90000
          AgentName.SENTIMENT_AGENT, List.of("sentiment [GH-90000]"),
          Promise.of(AgentResult.success("sentiment ok", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request")))); // GH-90000
      agentRegistry.register(agent); // GH-90000

      TaskDefinition task = TaskDefinition.builder() // GH-90000
          .id("task-default [GH-90000]")
          .name("Default Pattern [GH-90000]")
          .description("No pattern specified [GH-90000]")
          .domain("analysis [GH-90000]")
          .phase(SDLCPhase.TESTING) // GH-90000
          .requiredCapabilities(List.of("sentiment [GH-90000]"))
          .build(); // GH-90000

      TaskExecutionContext ctx = TaskExecutionContext.builder() // GH-90000
          .userId("user-1 [GH-90000]")
          .tenantId("tenant-1 [GH-90000]")
          .build(); // GH-90000

      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx)); // GH-90000
      assertThat(result).isEqualTo("sentiment ok [GH-90000]");
    }

    @Test
    @DisplayName("Should handle invalid pattern gracefully [GH-90000]")
    void shouldHandleInvalidPattern() { // GH-90000
      AIAgent<Map<String, Object>, String> agent = createMockAgent( // GH-90000
          AgentName.RECOMMENDATION_AGENT, List.of("recommendations [GH-90000]"),
          Promise.of(AgentResult.success("recommendation", AgentResult.AgentMetrics.builder().build(), AgentResult.AgentTrace.of("test-agent", "test-request")))); // GH-90000
      agentRegistry.register(agent); // GH-90000

      TaskDefinition task = TaskDefinition.builder() // GH-90000
          .id("task-invalid-pattern [GH-90000]")
          .name("Invalid Pattern [GH-90000]")
          .description("Invalid pattern string [GH-90000]")
          .domain("analysis [GH-90000]")
          .phase(SDLCPhase.DISCOVERY) // GH-90000
          .requiredCapabilities(List.of("recommendations [GH-90000]"))
          .build(); // GH-90000

      TaskExecutionContext ctx = TaskExecutionContext.builder() // GH-90000
          .userId("user-1 [GH-90000]")
          .tenantId("tenant-1 [GH-90000]")
          .metadata(Map.of("orchestrationPattern", "NONEXISTENT")) // GH-90000
          .build(); // GH-90000

      // Should fall back to SEQUENTIAL
      String result = runPromise(() -> orchestrator.execute(task, Map.of(), ctx)); // GH-90000
      assertThat(result).isEqualTo("recommendation [GH-90000]");
    }
  }

  // ===== Helper Methods =====

  @SuppressWarnings("unchecked [GH-90000]")
  private <TIn, TOut> AIAgent<TIn, TOut> createMockAgent( // GH-90000
      AgentName name,
      List<String> capabilities,
      Promise<AgentResult<TOut>> result) {

    AIAgent<TIn, TOut> agent = mock(AIAgent.class); // GH-90000

    AgentMetadata metadata = AgentMetadata.builder() // GH-90000
        .name(name) // GH-90000
        .version("1.0.0 [GH-90000]")
        .description("Mock agent: " + name.getDisplayName()) // GH-90000
        .capabilities(capabilities) // GH-90000
        .supportedModels(List.of("gpt-4 [GH-90000]"))
        .latencySLA(5000) // GH-90000
        .build(); // GH-90000

    lenient().when(agent.getMetadata()).thenReturn(metadata); // GH-90000
    lenient().when(agent.getId()).thenReturn(name.name().toLowerCase().replace("_", "-")); // GH-90000
    lenient().when(agent.healthCheck()).thenReturn(Promise.of(AgentHealth.healthy(0L))); // GH-90000
    lenient().when(agent.execute(any(), any())).thenReturn(result); // GH-90000

    return agent;
  }
}
