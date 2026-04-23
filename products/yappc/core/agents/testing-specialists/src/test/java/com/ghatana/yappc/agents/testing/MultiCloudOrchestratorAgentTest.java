package com.ghatana.yappc.agents.testing;

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
import com.ghatana.yappc.agents.architecture.*;

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
  void setUp() { // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    agent = new MultiCloudOrchestratorAgent( // GH-90000
        memoryStore,
        new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator()); // GH-90000
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("should accept valid multi-cloud request")
    void validRequest() { // GH-90000
      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput( // GH-90000
          "cloud-1", "deployment", List.of("aws", "gcp"), // GH-90000
          Map.of("instanceType", "m5.large"), Map.of()); // GH-90000
      assertThat(agent.validateInput(input).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should reject unknown cloud provider")
    void unknownProvider() { // GH-90000
      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput( // GH-90000
          "cloud-2", "deployment", List.of("ibm-cloud"),
          Map.of(), Map.of()); // GH-90000
      assertThat(agent.validateInput(input).isValid()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("should accept all known providers")
    void allKnownProviders() { // GH-90000
      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput( // GH-90000
          "cloud-3", "audit", List.of("aws", "azure", "gcp", "kubernetes"), // GH-90000
          Map.of(), Map.of()); // GH-90000
      assertThat(agent.validateInput(input).isValid()).isTrue(); // GH-90000
    }
  }

  @Nested
  @DisplayName("Multi-Cloud Planning")
  class CloudPlanning {

    @Test
    @DisplayName("should create provider-specific actions")
    void providerSpecificActions() { // GH-90000
      OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> generator =
          new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator(); // GH-90000

      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput( // GH-90000
          "cloud-1", "deployment", List.of("aws", "gcp"), // GH-90000
          Map.of(), Map.of()); // GH-90000

      StepResult<MultiCloudOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.multi-cloud", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.isSuccess()).isTrue(); // GH-90000
      assertThat(result.output().status()) // GH-90000
          .isEqualTo(MultiCloudOrchestratorOutput.STATUS_PLANNED); // GH-90000
      assertThat(result.output().providerActions()) // GH-90000
          .hasSize(2) // GH-90000
          .anyMatch(a -> a.contains("aws"))
          .anyMatch(a -> a.contains("gcp"));
    }

    @Test
    @DisplayName("should track per-provider status")
    void perProviderStatus() { // GH-90000
      OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> generator =
          new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator(); // GH-90000

      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput( // GH-90000
          "cloud-2", "resource_allocation", List.of("aws", "azure", "gcp"), // GH-90000
          Map.of(), Map.of()); // GH-90000

      StepResult<MultiCloudOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.multi-cloud", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().providerStatus()) // GH-90000
          .containsKeys("aws", "azure", "gcp") // GH-90000
          .allSatisfy((k, v) -> assertThat(v).isEqualTo("PLANNED"));
    }

    @Test
    @DisplayName("should estimate cost based on providers")
    void costEstimation() { // GH-90000
      OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> generator =
          new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator(); // GH-90000

      MultiCloudOrchestratorInput input = new MultiCloudOrchestratorInput( // GH-90000
          "cloud-3", "deployment", List.of("aws"),
          Map.of(), Map.of()); // GH-90000

      StepResult<MultiCloudOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.multi-cloud", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().estimatedCost()).isGreaterThan(0.0); // GH-90000
    }

    @Test
    @DisplayName("should cost more with multiple providers")
    void moreCostWithMoreProviders() { // GH-90000
      OutputGenerator<StepRequest<MultiCloudOrchestratorInput>,
          StepResult<MultiCloudOrchestratorOutput>> generator =
          new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator(); // GH-90000

      MultiCloudOrchestratorInput singleInput = new MultiCloudOrchestratorInput( // GH-90000
          "cost-1", "deployment", List.of("aws"), Map.of(), Map.of());

      MultiCloudOrchestratorInput multiInput = new MultiCloudOrchestratorInput( // GH-90000
          "cost-2", "deployment", List.of("aws", "azure", "gcp"), Map.of(), Map.of()); // GH-90000

      StepResult<MultiCloudOrchestratorOutput> singleResult = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.multi-cloud", singleInput), // GH-90000
              AgentContext.empty())); // GH-90000

      StepResult<MultiCloudOrchestratorOutput> multiResult = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.multi-cloud", multiInput), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(multiResult.output().estimatedCost()) // GH-90000
          .isGreaterThan(singleResult.output().estimatedCost()); // GH-90000
    }
  }

  @Nested
  @DisplayName("StepContract")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name")
    void stepName() { // GH-90000
      assertThat(agent.getStepName()).isEqualTo("orchestrator.multi-cloud");
    }

    @Test
    @DisplayName("should advertise multi-cloud capabilities")
    void capabilities() { // GH-90000
      assertThat(agent.getStepContract().capabilities()) // GH-90000
          .contains("multi-cloud-orchestration", "cross-cloud-coordination"); // GH-90000
    }
  }
}
