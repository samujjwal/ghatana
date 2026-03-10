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
 * Tests for the ReleaseOrchestratorAgent — L1 orchestrator
 * coordinating the entire release pipeline.
 *
 * @doc.type class
 * @doc.purpose Unit tests for ReleaseOrchestratorAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ReleaseOrchestratorAgent")
class ReleaseOrchestratorAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private ReleaseOrchestratorAgent agent;

  @BeforeEach
  void setUp() {
    memoryStore = new EventLogMemoryStore();
    agent = new ReleaseOrchestratorAgent(
        memoryStore,
        new ReleaseOrchestratorAgent.ReleaseOrchestratorGenerator());
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("should accept valid release request with artifacts")
    void validRequest() {
      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput(
          "rel-1", "2.0.0", "standard", List.of("app.jar", "app.war"), Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue();
    }

    @Test
    @DisplayName("should reject empty releaseId")
    void emptyReleaseId() {
      assertThatThrownBy(() ->
          new ReleaseOrchestratorInput("", "2.0.0", "standard",
              List.of("app.jar"), Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject empty artifacts list")
    void emptyArtifacts() {
      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput(
          "rel-1", "2.0.0", "standard", List.of(), Map.of());
      ValidationResult result = agent.validateInput(input);
      assertThat(result.isValid()).isFalse();
    }
  }

  @Nested
  @DisplayName("Release Pipeline Gates")
  class ReleasePipeline {

    @Test
    @DisplayName("should block standard release without governance approval")
    void blockedWithoutGovernance() {
      OutputGenerator<StepRequest<ReleaseOrchestratorInput>,
          StepResult<ReleaseOrchestratorOutput>> generator =
          new ReleaseOrchestratorAgent.ReleaseOrchestratorGenerator();

      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput(
          "rel-1", "2.0.0", "standard", List.of("app.jar"), Map.of());

      StepResult<ReleaseOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.release", input),
              AgentContext.empty()));

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.output().status())
          .isEqualTo(ReleaseOrchestratorOutput.STATUS_BLOCKED);
      assertThat(result.output().pendingGates())
          .contains("governance-approval");
    }

    @Test
    @DisplayName("should pass patch release without explicit governance/staging")
    void patchReleaseAutoApproved() {
      OutputGenerator<StepRequest<ReleaseOrchestratorInput>,
          StepResult<ReleaseOrchestratorOutput>> generator =
          new ReleaseOrchestratorAgent.ReleaseOrchestratorGenerator();

      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput(
          "rel-2", "2.0.1", "patch", List.of("app.jar"),
          Map.of("stagingPassed", true));

      StepResult<ReleaseOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.release", input),
              AgentContext.empty()));

      assertThat(result.output().status())
          .isEqualTo(ReleaseOrchestratorOutput.STATUS_READY);
      assertThat(result.output().pendingGates()).isEmpty();
      assertThat(result.output().sbomDigest()).startsWith("sha256:");
    }

    @Test
    @DisplayName("should pass fully approved standard release")
    void fullyApprovedRelease() {
      OutputGenerator<StepRequest<ReleaseOrchestratorInput>,
          StepResult<ReleaseOrchestratorOutput>> generator =
          new ReleaseOrchestratorAgent.ReleaseOrchestratorGenerator();

      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput(
          "rel-3", "3.0.0", "standard", List.of("app.jar"),
          Map.of("governanceApproved", true, "stagingPassed", true));

      StepResult<ReleaseOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.release", input),
              AgentContext.empty()));

      assertThat(result.output().status())
          .isEqualTo(ReleaseOrchestratorOutput.STATUS_READY);
      assertThat(result.output().completedGates()).hasSize(6);
    }
  }

  @Nested
  @DisplayName("StepContract")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name")
    void stepName() {
      assertThat(agent.getStepName()).isEqualTo("orchestrator.release");
    }

    @Test
    @DisplayName("should report to head-of-devops")
    void reportsTo() {
      assertThat(agent.getStepContract().metadata().get("reports_to"))
          .isEqualTo("head-of-devops");
    }
  }
}
