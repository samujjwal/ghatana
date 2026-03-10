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
 * Orchestrates cloud security audits across providers and services.
 *
 * @doc.type class
 * @doc.purpose Orchestrates cloud security audits across providers and services
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class CloudSecurityAuditOrchestratorAgent extends YAPPCAgentBase<CloudSecurityAuditOrchestratorInput, CloudSecurityAuditOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(CloudSecurityAuditOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  public CloudSecurityAuditOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<CloudSecurityAuditOrchestratorInput>, StepResult<CloudSecurityAuditOrchestratorOutput>> generator) {
    super(
        "CloudSecurityAuditOrchestratorAgent",
        "expert.cloud-security-audit",
        new StepContract(
            "expert.cloud-security-audit",
            "#/definitions/CloudSecurityAuditOrchestratorInput",
            "#/definitions/CloudSecurityAuditOrchestratorOutput",
            List.of("cloud-security", "audit"),
            Map.of("description", "Orchestrates cloud security audits across providers and services", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull CloudSecurityAuditOrchestratorInput input) {
    if (input.cloudAccountId() == null || input.cloudAccountId().isEmpty()) {
      return ValidationResult.fail("cloudAccountId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<CloudSecurityAuditOrchestratorInput> perceive(
      @NotNull StepRequest<CloudSecurityAuditOrchestratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.cloud-security-audit request: {}", request.input().cloudAccountId().substring(0, Math.min(50, request.input().cloudAccountId().length())));
    return request;
  }

  /** Rule-based generator for expert.cloud-security-audit. */
  public static class CloudSecurityAuditOrchestratorGenerator
      implements OutputGenerator<StepRequest<CloudSecurityAuditOrchestratorInput>, StepResult<CloudSecurityAuditOrchestratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(CloudSecurityAuditOrchestratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<CloudSecurityAuditOrchestratorOutput>> generate(
        @NotNull StepRequest<CloudSecurityAuditOrchestratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      CloudSecurityAuditOrchestratorInput stepInput = input.input();

      log.info("Executing expert.cloud-security-audit for: {}", stepInput.cloudAccountId());

      CloudSecurityAuditOrchestratorOutput output =
          new CloudSecurityAuditOrchestratorOutput(
              "expert.cloud-security-audit-" + UUID.randomUUID(),
              List.of(),
              "Generated complianceScore for " + stepInput.cloudAccountId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.cloud-security-audit", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<CloudSecurityAuditOrchestratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("CloudSecurityAuditOrchestratorGenerator")
          .type("rule-based")
          .description("Orchestrates cloud security audits across providers and services")
          .version("1.0.0")
          .build();
    }
  }
}
