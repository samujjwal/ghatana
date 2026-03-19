package com.ghatana.yappc.agent.specialists;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the MultiCloudOrchestratorAgent — L1 orchestrator
 * coordinating resources across multiple cloud providers.
 *
 * @doc.type class
 * @doc.purpose Unit tests for MultiCloudOrchestratorAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MultiCloudOrchestratorAgent")
class MultiCloudOrchestratorAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private MultiCloudOrchestratorAgent agent;

  @BeforeEach
  void setUp() {
    memoryStore = new EventLogMemoryStore();
    agent = new MultiCloudOrchestratorAgent(
        memoryStore,
        new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator());
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("should accept valid multi-cloud request")
    void validRequest() {
      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput(
          "cloud-1", "deployment", List.of("aws", "gcp"),
          Map.of("instanceType", "m5.large"), Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue();
    }

    @Test
    @DisplayName("should reject unknown cloud provider")
    void unknownProvider() {
      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput(
          "cloud-2", "deployment", List.of("ibm-cloud"),
          Map.of(), Map.of());
      assertThat(agent.validateInput(input).isValid()).isFalse();
    }

    @Test
    @DisplayName("should accept all known providers")
    void allKnownProviders() {
      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput(
          "cloud-3", "audit", List.of("aws", "azure", "gcp", "kubernetes"),
          Map.of(), Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue();
    }
  }

  @Nested
  @DisplayName("Multi-Cloud Planning")
  class CloudPlanning {

    @Test
    @DisplayName("should create provider-specific actions")
    void providerSpecificActions() {
      OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> generator =
          new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator();

      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput(
          "cloud-1", "deployment", List.of("aws", "gcp"),
          Map.of(), Map.of());

      StepResult<MultiCloudOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.multi-cloud", input),
              AgentContext.empty()));

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.output().status())
          .isEqualTo(MultiCloudOrchestratorOutput.STATUS_PLANNED);
      assertThat(result.output().providerActions())
          .hasSize(2)
          .anyMatch(a -> a.contains("aws"))
          .anyMatch(a -> a.contains("gcp"));
    }

    @Test
    @DisplayName("should track per-provider status")
    void perProviderStatus() {
      OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> generator =
          new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator();

      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput(
          "cloud-2", "resource_allocation", List.of("aws", "azure", "gcp"),
          Map.of(), Map.of());

      StepResult<MultiCloudOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.multi-cloud", input),
              AgentContext.empty()));

      assertThat(result.output().providerStatus())
          .containsKeys("aws", "azure", "gcp")
          .allSatisfy((k, v) -> assertThat(v).isEqualTo("PLANNED"));
    }

    @Test
    @DisplayName("should estimate cost based on providers")
    void costEstimation() {
      OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> generator =
          new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator();

      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput(
          "cloud-3", "deployment", List.of("aws"),
          Map.of(), Map.of());

      StepResult<MultiCloudOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.multi-cloud", input),
              AgentContext.empty()));

      assertThat(result.output().estimatedCost()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("should cost more with multiple providers")
    void moreCostWithMoreProviders() {
      OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> generator =
          new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator();

      MultiCloudOrchestratorInput singleInput = new MultiCloudOrchestratorInput(
          "cost-1", "deployment", List.of("aws"), Map.of(), Map.of());

      MultiCloudOrchestratorInput multiInput = new MultiCloudOrchestratorInput(
          "cost-2", "deployment", List.of("aws", "azure", "gcp"), Map.of(), Map.of());

      StepResult<MultiCloudOrchestratorOutput> singleResult = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.multi-cloud", singleInput),
              AgentContext.empty()));

      StepResult<MultiCloudOrchestratorOutput> multiResult = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.multi-cloud", multiInput),
              AgentContext.empty()));

      assertThat(multiResult.output().estimatedCost())
          .isGreaterThan(singleResult.output().estimatedCost());
    }
  }

  @Nested
  @DisplayName("StepContract")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name")
    void stepName() {
      assertThat(agent.getStepName()).isEqualTo("orchestrator.multi-cloud");
    }

    @Test
    @DisplayName("should advertise multi-cloud capabilities")
    void capabilities() {
      assertThat(agent.getStepContract().capabilities())
          .contains("multi-cloud-orchestration", "cross-cloud-coordination");
    }
  }
}
