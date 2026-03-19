package com.ghatana.yappc.agent.specialists;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for IntakeSpecialistAgent — requirements intake with LLM/rule-based.
 *
 * @doc.type class
 * @doc.purpose Unit tests for IntakeSpecialistAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("IntakeSpecialistAgent Tests")
class IntakeSpecialistAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private IntakeSpecialistAgent agent;

  @BeforeEach
  void setUp() {
    memoryStore = new EventLogMemoryStore();
    agent = new IntakeSpecialistAgent(
        memoryStore, new IntakeSpecialistAgent.IntakeGenerator());
    YAPPCAgentBase.configureAepEventPublisher(
        (eventType, tenantId, payload) -> Promise.complete());
  }

  @Nested
  @DisplayName("Input Validation")
  class InputValidation {

    @Test
    @DisplayName("Should accept valid requirements")
    void shouldAcceptValidRequirements() {
      IntakeInput input = new IntakeInput(
          "The system must support user authentication and role-based access control", "manual");
      ValidationResult result = agent.validateInput(input);
      assertThat(result.ok()).isTrue();
    }

    @Test
    @DisplayName("Should reject empty requirements")
    void shouldRejectEmpty() {
      assertThatThrownBy(() -> new IntakeInput("", "manual"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject too-short requirements")
    void shouldRejectTooShort() {
      IntakeInput input = new IntakeInput("Too short", "manual");
      ValidationResult result = agent.validateInput(input);
      assertThat(result.ok()).isFalse();
    }
  }

  @Nested
  @DisplayName("Rule-Based Extraction")
  class RuleBasedExtraction {

    @Test
    @DisplayName("Should extract functional requirements from MUST keywords")
    void shouldExtractMustRequirements() {
      IntakeInput input = new IntakeInput(
          "The system must handle 1000 concurrent users and should support REST API",
          "stakeholder");
      StepContext ctx = createStepContext();

      StepResult<IntakeOutput> result = runPromise(() -> agent.execute(input, ctx));

      assertThat(result.success()).isTrue();
      assertThat(result.output().functionalRequirements()).isNotEmpty();
    }

    @Test
    @DisplayName("Should extract security NFRs")
    void shouldExtractSecurityNfrs() {
      IntakeInput input = new IntakeInput(
          "The system must be secure and support encryption for data at rest",
          "compliance");
      StepContext ctx = createStepContext();

      StepResult<IntakeOutput> result = runPromise(() -> agent.execute(input, ctx));

      assertThat(result.success()).isTrue();
      assertThat(result.output().nonFunctionalRequirements()).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Contract")
  class ContractTests {

    @Test
    @DisplayName("Should expose architecture.intake step name")
    void shouldExposeStepName() {
      assertThat(agent.stepName()).isEqualTo("architecture.intake");
    }

    @Test
    @DisplayName("Should expose intake contract capabilities")
    void shouldExposeContract() {
      StepContract contract = agent.contract();
      assertThat(contract.requiredCapabilities())
          .containsExactlyInAnyOrder("requirements", "validation");
    }
  }

  private StepContext createStepContext() {
    return new StepContext(
        "run-intake-1", "tenant-1", "architecture", "config-1",
        new StepBudget(10.0, 60_000));
  }
}
