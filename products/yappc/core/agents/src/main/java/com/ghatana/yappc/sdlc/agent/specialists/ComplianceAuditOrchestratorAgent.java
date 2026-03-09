package com.ghatana.yappc.sdlc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates compliance audits against regulatory frameworks.
 *
 * @doc.type class
 * @doc.purpose Orchestrates compliance audits against regulatory frameworks
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ComplianceAuditOrchestratorAgent extends YAPPCAgentBase<ComplianceAuditOrchestratorInput, ComplianceAuditOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(ComplianceAuditOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  public ComplianceAuditOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ComplianceAuditOrchestratorInput>, StepResult<ComplianceAuditOrchestratorOutput>> generator) {
    super(
        "ComplianceAuditOrchestratorAgent",
        "expert.compliance-audit",
        new StepContract(
            "expert.compliance-audit",
            "#/definitions/ComplianceAuditOrchestratorInput",
            "#/definitions/ComplianceAuditOrchestratorOutput",
            List.of("compliance", "audit", "regulatory"),
            Map.of("description", "Orchestrates compliance audits against regulatory frameworks", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ComplianceAuditOrchestratorInput input) {
    if (input.frameworkId() == null || input.frameworkId().isEmpty()) {
      return ValidationResult.fail("frameworkId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ComplianceAuditOrchestratorInput> perceive(
      @NotNull StepRequest<ComplianceAuditOrchestratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.compliance-audit request: {}", input.input().frameworkId().substring(0, Math.min(50, input.input().frameworkId().length())));
    return request;
  }

  /** Rule-based generator for expert.compliance-audit. */
  public static class ComplianceAuditOrchestratorGenerator
      implements OutputGenerator<StepRequest<ComplianceAuditOrchestratorInput>, StepResult<ComplianceAuditOrchestratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ComplianceAuditOrchestratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ComplianceAuditOrchestratorOutput>> generate(
        @NotNull StepRequest<ComplianceAuditOrchestratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ComplianceAuditOrchestratorInput stepInput = input.input();

      log.info("Executing expert.compliance-audit for: {}", stepInput.frameworkId());

      ComplianceAuditOrchestratorOutput output =
          new ComplianceAuditOrchestratorOutput(
              "expert.compliance-audit-" + UUID.randomUUID(),
              List.of(),
              "Generated complianceStatus for " + stepInput.frameworkId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.compliance-audit", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ComplianceAuditOrchestratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ComplianceAuditOrchestratorGenerator")
          .type("rule-based")
          .description("Orchestrates compliance audits against regulatory frameworks")
          .version("1.0.0")
          .build();
    }
  }
}
