package com.ghatana.yappc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * L1 orchestrator that coordinates governance policies, approvals, and compliance across all operations.
 *
 * <p>Enforces organizational policies before any release, deployment, or infrastructure change
 * can proceed. Aggregates results from policy-enforcement-agent, approval-workflow-agent,
 * and audit-coordinator, then issues a governance verdict with veto power.
 *
 * @doc.type class
 * @doc.purpose Coordinates governance policies, approvals, and compliance across all operations
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class GovernanceOrchestratorAgent
    extends YAPPCAgentBase<GovernanceOrchestratorInput, GovernanceOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(GovernanceOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  /**
   * Constructs the governance orchestrator.
   *
   * @param memoryStore memory store for governance audit events
   * @param generator   output generator (rule-based or LLM-powered)
   */
  public GovernanceOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> generator) {
    super(
        "GovernanceOrchestratorAgent",
        "orchestrator.governance",
        new StepContract(
            "orchestrator.governance",
            "#/definitions/GovernanceOrchestratorInput",
            "#/definitions/GovernanceOrchestratorOutput",
            List.of("governance-orchestration", "policy-enforcement", "approval-coordination"),
            Map.of(
                "description", "Coordinates governance policies, approvals, and compliance",
                "version", "1.0.0",
                "level", "L1",
                "veto_power", "true",
                "audit_trail", "comprehensive")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull GovernanceOrchestratorInput input) {
    if (input.requestId() == null || input.requestId().isEmpty()) {
      return ValidationResult.fail("requestId cannot be empty");
    }
    if (input.requestType() == null || input.requestType().isEmpty()) {
      return ValidationResult.fail("requestType cannot be empty");
    }
    String type = input.requestType().toLowerCase(Locale.ROOT);
    if (!Set.of("governance_request", "policy_check", "approval_needed").contains(type)) {
      return ValidationResult.fail(
          "requestType must be one of: governance_request, policy_check, approval_needed");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<GovernanceOrchestratorInput> perceive(
      @NotNull StepRequest<GovernanceOrchestratorInput> request,
      @NotNull AgentContext context) {
    GovernanceOrchestratorInput input = request.input();
    log.info("Perceiving governance request [{}] type={} for entity={}",
        input.requestId(), input.requestType(), input.targetEntity());
    return request;
  }

  /**
   * Rule-based governance orchestration generator.
   *
   * <p>Evaluates policies, checks for violations, and produces a governance verdict.
   */
  public static class GovernanceOrchestratorGenerator
      implements OutputGenerator<StepRequest<GovernanceOrchestratorInput>,
          StepResult<GovernanceOrchestratorOutput>> {

    private static final Logger log =
        LoggerFactory.getLogger(GovernanceOrchestratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<GovernanceOrchestratorOutput>> generate(
        @NotNull StepRequest<GovernanceOrchestratorInput> input,
        @NotNull AgentContext context) {

      Instant start = Instant.now();
      GovernanceOrchestratorInput govInput = input.input();

      log.info("Evaluating governance for request [{}]", govInput.requestId());

      // Evaluate each requested policy
      List<String> violations = new ArrayList<>();
      List<String> approvals = new ArrayList<>();

      for (String policy : govInput.policiesRequired()) {
        if (evaluatePolicy(policy, govInput)) {
          approvals.add(policy);
        } else {
          violations.add(policy);
        }
      }

      // If no policies were specified, auto-approve with advisory
      if (govInput.policiesRequired().isEmpty()) {
        approvals.add("default-governance-review");
      }

      String verdict = violations.isEmpty()
          ? GovernanceOrchestratorOutput.VERDICT_APPROVED
          : GovernanceOrchestratorOutput.VERDICT_REJECTED;

      String decisionId = "gov-" + govInput.requestId() + "-" + UUID.randomUUID();
      String auditTrailId = "audit-" + decisionId;

      GovernanceOrchestratorOutput output = new GovernanceOrchestratorOutput(
          decisionId,
          verdict,
          violations,
          approvals,
          auditTrailId,
          Map.of(
              "generatedAt", start.toString(),
              "requestType", govInput.requestType(),
              "targetEntity", govInput.targetEntity(),
              "totalPoliciesEvaluated", govInput.policiesRequired().size(),
              "violationCount", violations.size()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of(
                  "stepId", "orchestrator.governance",
                  "verdict", verdict,
                  "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    /**
     * Evaluates a single policy against the governance request.
     * Rule-based: delegates to downstream agents in production.
     */
    private boolean evaluatePolicy(String policy, GovernanceOrchestratorInput input) {
      // Rule-based policy evaluation — always passes for known policies
      // In production, delegates to policy-enforcement-agent
      return switch (policy.toLowerCase(Locale.ROOT)) {
        case "security-review" -> input.context().containsKey("securityScanPassed");
        case "budget-approval" -> input.context().containsKey("budgetApproved");
        case "compliance-check" -> input.context().containsKey("complianceVerified");
        default -> true; // Unknown policies pass by default (advisory only)
      };
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<GovernanceOrchestratorInput> input,
        @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("GovernanceOrchestratorGenerator")
          .type("rule-based")
          .description("Evaluates governance policies and produces approval/rejection verdicts")
          .version("1.0.0")
          .build();
    }
  }
}
