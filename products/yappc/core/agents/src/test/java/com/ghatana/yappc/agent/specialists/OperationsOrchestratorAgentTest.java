package com.ghatana.yappc.agent.specialists;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.ValidationResult;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the OperationsOrchestratorAgent — L1 orchestrator
 * for runtime operations (monitoring, incidents, SLOs).
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
  void setUp() {
    memoryStore = new EventLogMemoryStore();
    agent = new OperationsOrchestratorAgent(
        memoryStore,
        new OperationsOrchestratorAgent.OperationsOrchestratorGenerator());
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("should accept valid monitoring request")
    void validMonitoring() {
      OperationsOrchestratorInput input = new OperationsOrchestratorInput(
          "ops-1", "monitoring", "INFO", List.of("api-service"), Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue();
    }

    @Test
    @DisplayName("should accept incident type")
    void validIncident() {
      OperationsOrchestratorInput input = new OperationsOrchestratorInput(
          "ops-2", "incident", "CRITICAL", List.of("auth-service"), Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue();
    }

    @Test
    @DisplayName("should reject invalid operation type")
    void invalidType() {
      OperationsOrchestratorInput input = new OperationsOrchestratorInput(
          "ops-3", "invalid_type", "INFO", List.of(), Map.of());
      ValidationResult result = agent.validateInput(input);
      assertThat(result.isValid()).isFalse();
    }
  }

  @Nested
  @DisplayName("Incident Handling")
  class IncidentHandling {

    @Test
    @DisplayName("should create incident ID and page oncall")
    void incidentCreation() {
      OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> generator =
          new OperationsOrchestratorAgent.OperationsOrchestratorGenerator();

      OperationsOrchestratorInput input = new OperationsOrchestratorInput(
          "ops-inc-1", "incident", "CRITICAL",
          List.of("auth-service", "api-gateway"), Map.of());

      StepResult<OperationsOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.operations", input),
              AgentContext.empty()));

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.output().status())
          .isEqualTo(OperationsOrchestratorOutput.STATUS_INCIDENT);
      assertThat(result.output().incidentId()).startsWith("INC-");
      assertThat(result.output().notifications())
          .anyMatch(n -> n.contains("oncall-paged"));
    }

    @Test
    @DisplayName("should escalate CRITICAL incidents to management")
    void criticalEscalation() {
      OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> generator =
          new OperationsOrchestratorAgent.OperationsOrchestratorGenerator();

      OperationsOrchestratorInput input = new OperationsOrchestratorInput(
          "ops-crit", "incident", "CRITICAL", List.of("payments"), Map.of());

      StepResult<OperationsOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.operations", input),
              AgentContext.empty()));

      assertThat(result.output().notifications())
          .anyMatch(n -> n.contains("management-notified"));
      assertThat(result.output().actionsExecuted())
          .anyMatch(a -> a.contains("escalated"));
    }
  }

  @Nested
  @DisplayName("SLO Checks")
  class SloChecks {

    @Test
    @DisplayName("should report healthy when no SLO breach")
    void healthyWhenNoBreech() {
      OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> generator =
          new OperationsOrchestratorAgent.OperationsOrchestratorGenerator();

      OperationsOrchestratorInput input = new OperationsOrchestratorInput(
          "ops-slo-1", "slo_check", "INFO", List.of("api-service"), Map.of());

      StepResult<OperationsOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.operations", input),
              AgentContext.empty()));

      assertThat(result.output().status())
          .isEqualTo(OperationsOrchestratorOutput.STATUS_HEALTHY);
    }

    @Test
    @DisplayName("should report degraded when SLO breached")
    void degradedOnBreech() {
      OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> generator =
          new OperationsOrchestratorAgent.OperationsOrchestratorGenerator();

      OperationsOrchestratorInput input = new OperationsOrchestratorInput(
          "ops-slo-2", "slo_check", "WARNING",
          List.of("api-service"),
          Map.of("sloBreached", true));

      StepResult<OperationsOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.operations", input),
              AgentContext.empty()));

      assertThat(result.output().status())
          .isEqualTo(OperationsOrchestratorOutput.STATUS_DEGRADED);
    }
  }

  @Nested
  @DisplayName("StepContract")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name")
    void stepName() {
      assertThat(agent.getStepName()).isEqualTo("orchestrator.operations");
    }

    @Test
    @DisplayName("should advertise monitoring and incident capabilities")
    void capabilities() {
      assertThat(agent.getStepContract().capabilities())
          .contains("monitoring", "incident-response");
    }
  }
}
