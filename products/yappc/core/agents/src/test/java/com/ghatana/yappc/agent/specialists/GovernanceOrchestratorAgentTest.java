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
 * Tests for the GovernanceOrchestratorAgent — L1 orchestrator
 * coordinating governance policies, approvals, and compliance.
 *
 * @doc.type class
 * @doc.purpose Unit tests for GovernanceOrchestratorAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GovernanceOrchestratorAgent")
class GovernanceOrchestratorAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private GovernanceOrchestratorAgent agent;

  @BeforeEach
  void setUp() {
    memoryStore = new EventLogMemoryStore();
    agent = new GovernanceOrchestratorAgent(
        memoryStore,
        new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator());
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("should accept valid governance_request")
    void validRequest() {
      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput(
          "req-1", "governance_request", "release-v2.0",
          List.of("security-review"), Map.of());
      ValidationResult result = agent.validateInput(input);
      assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should reject empty requestId")
    void emptyRequestId() {
      assertThatThrownBy(() ->
          new GovernanceOrchestratorInput("", "governance_request", "entity",
              List.of(), Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject unknown requestType")
    void unknownRequestType() {
      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput(
          "req-1", "unknown_type", "entity", List.of(), Map.of());
      ValidationResult result = agent.validateInput(input);
      assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("should accept policy_check type")
    void policyCheckType() {
      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput(
          "req-1", "policy_check", "entity", List.of(), Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue();
    }

    @Test
    @DisplayName("should accept approval_needed type")
    void approvalNeededType() {
      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput(
          "req-1", "approval_needed", "entity", List.of(), Map.of());
      assertThat(agent.validateInput(input).isValid()).isTrue();
    }
  }

  @Nested
  @DisplayName("Generation")
  class Generation {

    @Test
    @DisplayName("should approve when no violations found")
    void approvedWhenNoViolations() {
      OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> generator =
          new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator();

      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput(
          "req-1", "governance_request", "release-v2.0",
          List.of(), Map.of());

      StepResult<GovernanceOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.governance", input),
              AgentContext.empty()));

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.output().verdict())
          .isEqualTo(GovernanceOrchestratorOutput.VERDICT_APPROVED);
      assertThat(result.output().violations()).isEmpty();
    }

    @Test
    @DisplayName("should reject when security policy fails")
    void rejectedOnSecurityViolation() {
      OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> generator =
          new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator();

      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput(
          "req-2", "policy_check", "deploy-to-prod",
          List.of("security-review"), Map.of()); // Missing securityScanPassed

      StepResult<GovernanceOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.governance", input),
              AgentContext.empty()));

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.output().verdict())
          .isEqualTo(GovernanceOrchestratorOutput.VERDICT_REJECTED);
      assertThat(result.output().violations()).contains("security-review");
    }

    @Test
    @DisplayName("should pass security policy when securityScanPassed in context")
    void securityPolicyPassesWithContext() {
      OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> generator =
          new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator();

      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput(
          "req-3", "governance_request", "deploy",
          List.of("security-review"), Map.of("securityScanPassed", true));

      StepResult<GovernanceOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.governance", input),
              AgentContext.empty()));

      assertThat(result.output().verdict())
          .isEqualTo(GovernanceOrchestratorOutput.VERDICT_APPROVED);
      assertThat(result.output().approvals()).contains("security-review");
    }

    @Test
    @DisplayName("should generate audit trail ID")
    void generatesAuditTrailId() {
      OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> generator =
          new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator();

      GovernanceOrchestratorInput input = new GovernanceOrchestratorInput(
          "req-4", "governance_request", "entity", List.of(), Map.of());

      StepResult<GovernanceOrchestratorOutput> result = runPromise(() ->
          generator.generate(
              StepRequest.of("orchestrator.governance", input),
              AgentContext.empty()));

      assertThat(result.output().auditTrailId()).startsWith("audit-gov-req-4-");
    }
  }

  @Nested
  @DisplayName("StepContract")
  class StepContractTests {

    @Test
    @DisplayName("should have correct step name")
    void stepName() {
      assertThat(agent.getStepName()).isEqualTo("orchestrator.governance");
    }

    @Test
    @DisplayName("should advertise governance capabilities")
    void capabilities() {
      assertThat(agent.getStepContract().capabilities())
          .contains("governance-orchestration", "policy-enforcement", "approval-coordination");
    }
  }
}
