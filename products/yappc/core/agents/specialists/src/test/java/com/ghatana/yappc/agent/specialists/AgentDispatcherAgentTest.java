package com.ghatana.yappc.agent.specialists;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.yappc.agent.YAPPCAgentRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the AgentDispatcherAgent — L2 domain-expert
 * that routes tasks to appropriate agents based on capabilities.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AgentDispatcherAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentDispatcherAgent")
class AgentDispatcherAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private AgentDispatcherAgent agent;

  @BeforeEach
  void setUp() {
    memoryStore = new EventLogMemoryStore();
    agent = new AgentDispatcherAgent(
        memoryStore,
        new AgentDispatcherAgent.AgentDispatcherGenerator(),
        new YAPPCAgentRegistry());
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("should accept valid task request")
    void validRequest() {
      AgentDispatcherInput input = new AgentDispatcherInput(
          "task-1", "Implement user login", List.of("implementation"), "NORMAL", Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue();
    }

    @Test
    @DisplayName("should reject empty taskDescription")
    void emptyDescription() {
      assertThatThrownBy(() ->
          new AgentDispatcherInput("task-1", "", List.of(), "NORMAL", Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("Capability Routing")
  class CapabilityRouting {

    @Test
    @DisplayName("should route implementation capability to implement agent")
    void routeImplementation() {
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator();

      AgentDispatcherInput input = new AgentDispatcherInput(
          "task-1", "Build feature X", List.of("implementation"), "NORMAL", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("expert.agent-dispatcher", input),
              AgentContext.empty()));

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.output().assignedAgentId())
          .isEqualTo("implementation.implement");
      assertThat(result.output().routingReason()).isEqualTo("capability-match");
      assertThat(result.output().confidenceScore()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("should route security capability to security-tests agent")
    void routeSecurity() {
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator();

      AgentDispatcherInput input = new AgentDispatcherInput(
          "task-2", "Run security scan", List.of("security"), "HIGH", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("expert.agent-dispatcher", input),
              AgentContext.empty()));

      assertThat(result.output().assignedAgentId())
          .isEqualTo("specialist.security-tests");
    }

    @Test
    @DisplayName("should fallback to lifecycle orchestrator for unknown capability")
    void fallbackForUnknown() {
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator();

      AgentDispatcherInput input = new AgentDispatcherInput(
          "task-3", "Unknown task", List.of("quantum-computing"), "NORMAL", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("expert.agent-dispatcher", input),
              AgentContext.empty()));

      assertThat(result.output().assignedAgentId())
          .isEqualTo("strategic.full-lifecycle");
      assertThat(result.output().routingReason())
          .isEqualTo("no-capability-match-escalating");
      assertThat(result.output().confidenceScore()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("should track alternative agents for multi-capability request")
    void alternativeAgents() {
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator();

      AgentDispatcherInput input = new AgentDispatcherInput(
          "task-4", "Build and test", List.of("implementation", "testing"),
          "NORMAL", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("expert.agent-dispatcher", input),
              AgentContext.empty()));

      assertThat(result.isSuccess()).isTrue();
      // One agent should be primary, the other in alternatives
      assertThat(result.output().assignedAgentId()).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("StepContract")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name")
    void stepName() {
      assertThat(agent.getStepName()).isEqualTo("expert.agent-dispatcher");
    }

    @Test
    @DisplayName("should advertise routing capabilities")
    void capabilities() {
      assertThat(agent.getStepContract().capabilities())
          .contains("task-routing", "agent-selection", "load-balancing");
    }
  }
}
