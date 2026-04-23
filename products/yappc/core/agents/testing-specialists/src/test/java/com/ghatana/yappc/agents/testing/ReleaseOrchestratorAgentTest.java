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
import com.ghatana.yappc.agents.code.*;

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
  void setUp() { // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    agent = new ReleaseOrchestratorAgent( // GH-90000
        memoryStore,
        new ReleaseOrchestratorAgent.ReleaseOrchestratorGenerator()); // GH-90000
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("should accept valid release request with artifacts")
    void validRequest() { // GH-90000
      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput( // GH-90000
          "rel-1", "2.0.0", "standard", List.of("app.jar", "app.war"), Map.of()); // GH-90000
      assertThat(agent.validateInput(input).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should reject empty releaseId")
    void emptyReleaseId() { // GH-90000
      assertThatThrownBy(() -> // GH-90000
          new ReleaseOrchestratorInput("", "2.0.0", "standard", // GH-90000
              List.of("app.jar"), Map.of()))
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject empty artifacts list")
    void emptyArtifacts() { // GH-90000
      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput( // GH-90000
          "rel-1", "2.0.0", "standard", List.of(), Map.of()); // GH-90000
      ValidationResult result = agent.validateInput(input); // GH-90000
      assertThat(result.isValid()).isFalse(); // GH-90000
    }
  }

  @Nested
  @DisplayName("Release Pipeline Gates")
  class ReleasePipeline {

    @Test
    @DisplayName("should block standard release without governance approval")
    void blockedWithoutGovernance() { // GH-90000
      OutputGenerator<StepRequest<ReleaseOrchestratorInput>,
          StepResult<ReleaseOrchestratorOutput>> generator =
          new ReleaseOrchestratorAgent.ReleaseOrchestratorGenerator(); // GH-90000

      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput( // GH-90000
          "rel-1", "2.0.0", "standard", List.of("app.jar"), Map.of());

      StepResult<ReleaseOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.release", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.isSuccess()).isTrue(); // GH-90000
      assertThat(result.output().status()) // GH-90000
          .isEqualTo(ReleaseOrchestratorOutput.STATUS_BLOCKED); // GH-90000
      assertThat(result.output().pendingGates()) // GH-90000
          .contains("governance-approval");
    }

    @Test
    @DisplayName("should pass patch release without explicit governance/staging")
    void patchReleaseAutoApproved() { // GH-90000
      OutputGenerator<StepRequest<ReleaseOrchestratorInput>,
          StepResult<ReleaseOrchestratorOutput>> generator =
          new ReleaseOrchestratorAgent.ReleaseOrchestratorGenerator(); // GH-90000

      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput( // GH-90000
          "rel-2", "2.0.1", "patch", List.of("app.jar"),
          Map.of("stagingPassed", true)); // GH-90000

      StepResult<ReleaseOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.release", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().status()) // GH-90000
          .isEqualTo(ReleaseOrchestratorOutput.STATUS_READY); // GH-90000
      assertThat(result.output().pendingGates()).isEmpty(); // GH-90000
      assertThat(result.output().sbomDigest()).startsWith("sha256:");
    }

    @Test
    @DisplayName("should pass fully approved standard release")
    void fullyApprovedRelease() { // GH-90000
      OutputGenerator<StepRequest<ReleaseOrchestratorInput>,
          StepResult<ReleaseOrchestratorOutput>> generator =
          new ReleaseOrchestratorAgent.ReleaseOrchestratorGenerator(); // GH-90000

      ReleaseOrchestratorInput input = new ReleaseOrchestratorInput( // GH-90000
          "rel-3", "3.0.0", "standard", List.of("app.jar"),
          Map.of("governanceApproved", true, "stagingPassed", true)); // GH-90000

      StepResult<ReleaseOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.release", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().status()) // GH-90000
          .isEqualTo(ReleaseOrchestratorOutput.STATUS_READY); // GH-90000
      assertThat(result.output().completedGates()).hasSize(6); // GH-90000
    }
  }

  @Nested
  @DisplayName("StepContract")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name")
    void stepName() { // GH-90000
      assertThat(agent.getStepName()).isEqualTo("orchestrator.release");
    }

    @Test
    @DisplayName("should report to head-of-devops")
    void reportsTo() { // GH-90000
      assertThat(agent.getStepContract().metadata().get("reports_to"))
          .isEqualTo("head-of-devops");
    }
  }
}
