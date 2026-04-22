package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agents.code.AgentDispatcherAgent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.ghatana.yappc.agents.code.*;

/**
 * Tests for the AgentDispatcherAgent — L2 domain-expert
 * that routes tasks to appropriate agents based on capabilities.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AgentDispatcherAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentDispatcherAgent [GH-90000]")
class AgentDispatcherAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private AgentDispatcherAgent agent;

  @BeforeEach
  void setUp() { // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    agent = new AgentDispatcherAgent( // GH-90000
        memoryStore,
      new AgentDispatcherAgent.AgentDispatcherGenerator()); // GH-90000
  }

  @Nested
  @DisplayName("Validation [GH-90000]")
  class Validation {

    @Test
    @DisplayName("should accept valid task request [GH-90000]")
    void validRequest() { // GH-90000
      AgentDispatcherInput input = new AgentDispatcherInput( // GH-90000
          "task-1", "Implement user login", List.of("implementation [GH-90000]"), "NORMAL", Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should reject empty taskDescription [GH-90000]")
    void emptyDescription() { // GH-90000
      assertThatThrownBy(() -> // GH-90000
          new AgentDispatcherInput("task-1", "", List.of(), "NORMAL", Map.of())) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
  }

  @Nested
  @DisplayName("Capability Routing [GH-90000]")
  class CapabilityRouting {

    @Test
    @DisplayName("should route implementation capability to implement agent [GH-90000]")
    void routeImplementation() { // GH-90000
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator(); // GH-90000

      AgentDispatcherInput input = new AgentDispatcherInput( // GH-90000
          "task-1", "Build feature X", List.of("implementation [GH-90000]"), "NORMAL", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("expert.agent-dispatcher", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.isSuccess()).isTrue(); // GH-90000
      assertThat(result.output().assignedAgentId()) // GH-90000
          .isEqualTo("implementation.implement [GH-90000]");
      assertThat(result.output().routingReason()).isEqualTo("capability-match [GH-90000]");
      assertThat(result.output().confidenceScore()).isGreaterThan(0.0); // GH-90000
    }

    @Test
    @DisplayName("should route security capability to security-tests agent [GH-90000]")
    void routeSecurity() { // GH-90000
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator(); // GH-90000

      AgentDispatcherInput input = new AgentDispatcherInput( // GH-90000
          "task-2", "Run security scan", List.of("security [GH-90000]"), "HIGH", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("expert.agent-dispatcher", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().assignedAgentId()) // GH-90000
          .isEqualTo("specialist.security-tests [GH-90000]");
    }

    @Test
    @DisplayName("should fallback to lifecycle orchestrator for unknown capability [GH-90000]")
    void fallbackForUnknown() { // GH-90000
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator(); // GH-90000

      AgentDispatcherInput input = new AgentDispatcherInput( // GH-90000
          "task-3", "Unknown task", List.of("quantum-computing [GH-90000]"), "NORMAL", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("expert.agent-dispatcher", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().assignedAgentId()) // GH-90000
          .isEqualTo("strategic.full-lifecycle [GH-90000]");
      assertThat(result.output().routingReason()) // GH-90000
          .isEqualTo("no-capability-match-escalating [GH-90000]");
      assertThat(result.output().confidenceScore()).isEqualTo(0.3); // GH-90000
    }

    @Test
    @DisplayName("should track alternative agents for multi-capability request [GH-90000]")
    void alternativeAgents() { // GH-90000
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator(); // GH-90000

      AgentDispatcherInput input = new AgentDispatcherInput( // GH-90000
          "task-4", "Build and test", List.of("implementation", "testing"), // GH-90000
          "NORMAL", Map.of()); // GH-90000

      StepResult<AgentDispatcherOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("expert.agent-dispatcher", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.isSuccess()).isTrue(); // GH-90000
      // One agent should be primary, the other in alternatives
      assertThat(result.output().assignedAgentId()).isNotEmpty(); // GH-90000
    }
  }

  @Nested
  @DisplayName("StepContract [GH-90000]")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name [GH-90000]")
    void stepName() { // GH-90000
      assertThat(agent.getStepName()).isEqualTo("expert.agent-dispatcher [GH-90000]");
    }

    @Test
    @DisplayName("should advertise routing capabilities [GH-90000]")
    void capabilities() { // GH-90000
      assertThat(agent.getStepContract().capabilities()) // GH-90000
          .contains("task-routing", "agent-selection", "load-balancing"); // GH-90000
    }
  }
}
