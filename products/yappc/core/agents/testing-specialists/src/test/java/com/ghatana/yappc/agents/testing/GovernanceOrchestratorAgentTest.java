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
 * Tests for the GovernanceOrchestratorAgent — L1 orchestrator
 * coordinating governance policies, approvals, and compliance.
 *
 * @doc.type class
 * @doc.purpose Unit tests for GovernanceOrchestratorAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GovernanceOrchestratorAgent [GH-90000]")
class GovernanceOrchestratorAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private GovernanceOrchestratorAgent agent;

  @BeforeEach
  void setUp() { // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    agent = new GovernanceOrchestratorAgent( // GH-90000
        memoryStore,
        new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator()); // GH-90000
  }

  @Nested
  @DisplayName("Validation [GH-90000]")
  class Validation {

    @Test
    @DisplayName("should accept valid governance_request [GH-90000]")
    void validRequest() { // GH-90000
      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput( // GH-90000
          "req-1", "governance_request", "release-v2.0",
          List.of("security-review [GH-90000]"), Map.of());
      ValidationResult result = agent.validateInput(input); // GH-90000
      assertThat(result.isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should reject empty requestId [GH-90000]")
    void emptyRequestId() { // GH-90000
      assertThatThrownBy(() -> // GH-90000
          new GovernanceOrchestratorInput("", "governance_request", "entity", // GH-90000
              List.of(), Map.of())) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject unknown requestType [GH-90000]")
    void unknownRequestType() { // GH-90000
      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput( // GH-90000
          "req-1", "unknown_type", "entity", List.of(), Map.of()); // GH-90000
      ValidationResult result = agent.validateInput(input); // GH-90000
      assertThat(result.isValid()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("should accept policy_check type [GH-90000]")
    void policyCheckType() { // GH-90000
      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput( // GH-90000
          "req-1", "policy_check", "entity", List.of(), Map.of()); // GH-90000
      assertThat(agent.validateInput(input).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should accept approval_needed type [GH-90000]")
    void approvalNeededType() { // GH-90000
      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput( // GH-90000
          "req-1", "approval_needed", "entity", List.of(), Map.of()); // GH-90000
      assertThat(agent.validateInput(input).isValid()).isTrue(); // GH-90000
    }
  }

  @Nested
  @DisplayName("Generation [GH-90000]")
  class Generation {

    @Test
    @DisplayName("should approve when no violations found [GH-90000]")
    void approvedWhenNoViolations() { // GH-90000
      OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> generator =
          new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator(); // GH-90000

      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput( // GH-90000
          "req-1", "governance_request", "release-v2.0",
          List.of(), Map.of()); // GH-90000

      StepResult<GovernanceOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.governance", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.isSuccess()).isTrue(); // GH-90000
      assertThat(result.output().verdict()) // GH-90000
          .isEqualTo(GovernanceOrchestratorOutput.VERDICT_APPROVED); // GH-90000
      assertThat(result.output().violations()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("should reject when security policy fails [GH-90000]")
    void rejectedOnSecurityViolation() { // GH-90000
      OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> generator =
          new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator(); // GH-90000

      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput( // GH-90000
          "req-2", "policy_check", "deploy-to-prod",
          List.of("security-review [GH-90000]"), Map.of()); // Missing securityScanPassed

      StepResult<GovernanceOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.governance", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.isSuccess()).isTrue(); // GH-90000
      assertThat(result.output().verdict()) // GH-90000
          .isEqualTo(GovernanceOrchestratorOutput.VERDICT_REJECTED); // GH-90000
      assertThat(result.output().violations()).contains("security-review [GH-90000]");
    }

    @Test
    @DisplayName("should pass security policy when securityScanPassed in context [GH-90000]")
    void securityPolicyPassesWithContext() { // GH-90000
      OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> generator =
          new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator(); // GH-90000

      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput( // GH-90000
          "req-3", "governance_request", "deploy",
          List.of("security-review [GH-90000]"), Map.of("securityScanPassed", true));

      StepResult<GovernanceOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.governance", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().verdict()) // GH-90000
          .isEqualTo(GovernanceOrchestratorOutput.VERDICT_APPROVED); // GH-90000
      assertThat(result.output().approvals()).contains("security-review [GH-90000]");
    }

    @Test
    @DisplayName("should generate audit trail ID [GH-90000]")
    void generatesAuditTrailId() { // GH-90000
      OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> generator =
          new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator(); // GH-90000

      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput( // GH-90000
          "req-4", "governance_request", "entity", List.of(), Map.of()); // GH-90000

      StepResult<GovernanceOrchestratorOutput> result = runPromise(() -> // GH-90000
          generator.generate( // GH-90000
              StepRequest.of("orchestrator.governance", input), // GH-90000
              AgentContext.empty())); // GH-90000

      assertThat(result.output().auditTrailId()).startsWith("audit-gov-req-4- [GH-90000]");
    }
  }

  @Nested
  @DisplayName("StepContract [GH-90000]")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name [GH-90000]")
    void stepName() { // GH-90000
      assertThat(agent.getStepName()).isEqualTo("orchestrator.governance [GH-90000]");
    }

    @Test
    @DisplayName("should advertise governance capabilities [GH-90000]")
    void capabilities() { // GH-90000
      assertThat(agent.getStepContract().capabilities()) // GH-90000
          .contains("governance-orchestration", "policy-enforcement", "approval-coordination"); // GH-90000
    }
  }
}
