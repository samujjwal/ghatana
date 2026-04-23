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
@DisplayName("AgentDispatcherAgent")
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
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("should accept valid task request")
    void validRequest() { // GH-90000
      AgentDispatcherInput input = new AgentDispatcherInput( // GH-90000
          "task-1", "Implement user login", List.of("implementation"), "NORMAL", Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should reject empty taskDescription")
    void emptyDescription() { // GH-90000
      assertThatThrownBy(() -> // GH-90000
          new AgentDispatcherInput("task-1", "", List.of(), "NORMAL", Map.of())) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
  }

  @Nested
  @DisplayName("Capability Routing")
  class CapabilityRouting {

    @Test
    @DisplayName("should route implementation capability to implement agent")
    void routeImplementation() { // GH-90000
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator(); // GH-90000

      AgentDispatcherInput input = new AgentDispatcherInput( // GH-90000
          "task-1", "Build feature X", List.of("implementation"), "NORMAL", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("expert.agent-dispatcher", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.isSuccess()).isTrue(); // GH-90000
      assertThat(result.output().assignedAgentId()) // GH-90000
          .isEqualTo("implementation.implement");
      assertThat(result.output().routingReason()).isEqualTo("capability-match");
      assertThat(result.output().confidenceScore()).isGreaterThan(0.0); // GH-90000
    }

    @Test
    @DisplayName("should route security capability to security-tests agent")
    void routeSecurity() { // GH-90000
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator(); // GH-90000

      AgentDispatcherInput input = new AgentDispatcherInput( // GH-90000
          "task-2", "Run security scan", List.of("security"), "HIGH", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("expert.agent-dispatcher", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().assignedAgentId()) // GH-90000
          .isEqualTo("specialist.security-tests");
    }

    @Test
    @DisplayName("should fallback to lifecycle orchestrator for unknown capability")
    void fallbackForUnknown() { // GH-90000
      OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator =
          new AgentDispatcherAgent.AgentDispatcherGenerator(); // GH-90000

      AgentDispatcherInput input = new AgentDispatcherInput( // GH-90000
          "task-3", "Unknown task", List.of("quantum-computing"), "NORMAL", Map.of());

      StepResult<AgentDispatcherOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("expert.agent-dispatcher", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().assignedAgentId()) // GH-90000
          .isEqualTo("strategic.full-lifecycle");
      assertThat(result.output().routingReason()) // GH-90000
          .isEqualTo("no-capability-match-escalating");
      assertThat(result.output().confidenceScore()).isEqualTo(0.3); // GH-90000
    }

    @Test
    @DisplayName("should track alternative agents for multi-capability request")
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
  @DisplayName("StepContract")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name")
    void stepName() { // GH-90000
      assertThat(agent.getStepName()).isEqualTo("expert.agent-dispatcher");
    }

    @Test
    @DisplayName("should advertise routing capabilities")
    void capabilities() { // GH-90000
      assertThat(agent.getStepContract().capabilities()) // GH-90000
          .contains("task-routing", "agent-selection", "load-balancing"); // GH-90000
    }
  }
}
