package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.ghatana.yappc.agents.architecture.*;
import com.ghatana.yappc.agents.code.*;

/**
 * Tests for IntakeSpecialistAgent — requirements intake with LLM/rule-based.
 *
 * @doc.type class
 * @doc.purpose Unit tests for IntakeSpecialistAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("IntakeSpecialistAgent Tests [GH-90000]")
class IntakeSpecialistAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private IntakeSpecialistAgent agent;

  @BeforeEach
  void setUp() { // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    agent = new IntakeSpecialistAgent( // GH-90000
        memoryStore, new IntakeSpecialistAgent.IntakeGenerator()); // GH-90000
    YAPPCAgentBase.setGlobalAepEventPublisher( // GH-90000
        (eventType, tenantId, payload) -> Promise.complete()); // GH-90000
  }

  @Nested
  @DisplayName("Input Validation [GH-90000]")
  class InputValidation {

    @Test
    @DisplayName("Should accept valid requirements [GH-90000]")
    void shouldAcceptValidRequirements() { // GH-90000
      IntakeInput input = new IntakeInput( // GH-90000
          "The system must support user authentication and role-based access control", "manual");
      ValidationResult result = agent.validateInput(input); // GH-90000
      assertThat(result.ok()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should reject empty requirements [GH-90000]")
    void shouldRejectEmpty() { // GH-90000
      assertThatThrownBy(() -> new IntakeInput("", "manual")) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Should reject too-short requirements [GH-90000]")
    void shouldRejectTooShort() { // GH-90000
      IntakeInput input = new IntakeInput("Too short", "manual"); // GH-90000
      ValidationResult result = agent.validateInput(input); // GH-90000
      assertThat(result.ok()).isFalse(); // GH-90000
    }
  }

  @Nested
  @DisplayName("Rule-Based Extraction [GH-90000]")
  class RuleBasedExtraction {

    @Test
    @DisplayName("Should extract functional requirements from MUST keywords [GH-90000]")
    void shouldExtractMustRequirements() { // GH-90000
      IntakeInput input = new IntakeInput( // GH-90000
          "The system must handle 1000 concurrent users and should support REST API",
          "stakeholder");
      StepContext ctx = createStepContext(); // GH-90000

      StepResult<IntakeOutput> result = runPromise(() -> agent.execute(input, ctx)); // GH-90000

      assertThat(result.success()).isTrue(); // GH-90000
      assertThat(result.output().functionalRequirements()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should extract security NFRs [GH-90000]")
    void shouldExtractSecurityNfrs() { // GH-90000
      IntakeInput input = new IntakeInput( // GH-90000
          "The system must be secure and support encryption for data at rest",
          "compliance");
      StepContext ctx = createStepContext(); // GH-90000

      StepResult<IntakeOutput> result = runPromise(() -> agent.execute(input, ctx)); // GH-90000

      assertThat(result.success()).isTrue(); // GH-90000
      assertThat(result.output().nonFunctionalRequirements()).isNotEmpty(); // GH-90000
    }
  }

  @Nested
  @DisplayName("Contract [GH-90000]")
  class ContractTests {

    @Test
    @DisplayName("Should expose architecture.intake step name [GH-90000]")
    void shouldExposeStepName() { // GH-90000
      assertThat(agent.stepName()).isEqualTo("architecture.intake [GH-90000]");
    }

    @Test
    @DisplayName("Should expose intake contract capabilities [GH-90000]")
    void shouldExposeContract() { // GH-90000
      StepContract contract = agent.contract(); // GH-90000
      assertThat(contract.requiredCapabilities()) // GH-90000
          .containsExactlyInAnyOrder("requirements", "validation"); // GH-90000
    }
  }

  private StepContext createStepContext() { // GH-90000
    return new StepContext( // GH-90000
        "run-intake-1", "tenant-1", "architecture", "config-1",
        new StepBudget(10.0, 60_000)); // GH-90000
  }
}
