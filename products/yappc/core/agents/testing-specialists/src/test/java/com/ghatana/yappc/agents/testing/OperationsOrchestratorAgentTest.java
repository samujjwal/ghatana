package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.ValidationResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.ghatana.yappc.agents.architecture.*;

/**
 * Tests for the OperationsOrchestratorAgent — L1 orchestrator
 * for runtime operations (monitoring, incidents, SLOs). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for OperationsOrchestratorAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("OperationsOrchestratorAgent")
class OperationsOrchestratorAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private OperationsOrchestratorAgent agent;

  @BeforeEach
  void setUp() { // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    agent = new OperationsOrchestratorAgent( // GH-90000
        memoryStore,
        new OperationsOrchestratorAgent.OperationsOrchestratorGenerator()); // GH-90000
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("should accept valid monitoring request")
    void validMonitoring() { // GH-90000
      OperationsOrchestratorInput input = new OperationsOrchestratorInput( // GH-90000
          "ops-1", "monitoring", "INFO", List.of("api-service"), Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should accept incident type")
    void validIncident() { // GH-90000
      OperationsOrchestratorInput input = new OperationsOrchestratorInput( // GH-90000
          "ops-2", "incident", "CRITICAL", List.of("auth-service"), Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should reject invalid operation type")
    void invalidType() { // GH-90000
      OperationsOrchestratorInput input = new OperationsOrchestratorInput( // GH-90000
          "ops-3", "invalid_type", "INFO", List.of(), Map.of()); // GH-90000
      ValidationResult result = agent.validateInput(input); // GH-90000
      assertThat(result.isValid()).isFalse(); // GH-90000
    }
  }

  @Nested
  @DisplayName("Incident Handling")
  class IncidentHandling {

    @Test
    @DisplayName("should create incident ID and page oncall")
    void incidentCreation() { // GH-90000
      OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> generator =
          new OperationsOrchestratorAgent.OperationsOrchestratorGenerator(); // GH-90000

      OperationsOrchestratorInput input = new OperationsOrchestratorInput( // GH-90000
          "ops-inc-1", "incident", "CRITICAL",
          List.of("auth-service", "api-gateway"), Map.of()); // GH-90000

      StepResult<OperationsOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.operations", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.isSuccess()).isTrue(); // GH-90000
      assertThat(result.output().status()) // GH-90000
          .isEqualTo(OperationsOrchestratorOutput.STATUS_INCIDENT); // GH-90000
      assertThat(result.output().incidentId()).startsWith("INC-");
      assertThat(result.output().notifications()) // GH-90000
          .anyMatch(n -> n.contains("oncall-paged"));
    }

    @Test
    @DisplayName("should escalate CRITICAL incidents to management")
    void criticalEscalation() { // GH-90000
      OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> generator =
          new OperationsOrchestratorAgent.OperationsOrchestratorGenerator(); // GH-90000

      OperationsOrchestratorInput input = new OperationsOrchestratorInput( // GH-90000
          "ops-crit", "incident", "CRITICAL", List.of("payments"), Map.of());

      StepResult<OperationsOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.operations", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().notifications()) // GH-90000
          .anyMatch(n -> n.contains("management-notified"));
      assertThat(result.output().actionsExecuted()) // GH-90000
          .anyMatch(a -> a.contains("escalated"));
    }
  }

  @Nested
  @DisplayName("SLO Checks")
  class SloChecks {

    @Test
    @DisplayName("should report healthy when no SLO breach")
    void healthyWhenNoBreech() { // GH-90000
      OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> generator =
          new OperationsOrchestratorAgent.OperationsOrchestratorGenerator(); // GH-90000

      OperationsOrchestratorInput input = new OperationsOrchestratorInput( // GH-90000
          "ops-slo-1", "slo_check", "INFO", List.of("api-service"), Map.of());

      StepResult<OperationsOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.operations", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().status()) // GH-90000
          .isEqualTo(OperationsOrchestratorOutput.STATUS_HEALTHY); // GH-90000
    }

    @Test
    @DisplayName("should report degraded when SLO breached")
    void degradedOnBreech() { // GH-90000
      OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> generator =
          new OperationsOrchestratorAgent.OperationsOrchestratorGenerator(); // GH-90000

      OperationsOrchestratorInput input = new OperationsOrchestratorInput( // GH-90000
          "ops-slo-2", "slo_check", "WARNING",
          List.of("api-service"),
          Map.of("sloBreached", true)); // GH-90000

      StepResult<OperationsOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.operations", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().status()) // GH-90000
          .isEqualTo(OperationsOrchestratorOutput.STATUS_DEGRADED); // GH-90000
    }
  }

  @Nested
  @DisplayName("StepContract")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name")
    void stepName() { // GH-90000
      assertThat(agent.getStepName()).isEqualTo("orchestrator.operations");
    }

    @Test
    @DisplayName("should advertise monitoring and incident capabilities")
    void capabilities() { // GH-90000
      assertThat(agent.getStepContract().capabilities()) // GH-90000
          .contains("monitoring", "incident-response"); // GH-90000
    }
  }
}
