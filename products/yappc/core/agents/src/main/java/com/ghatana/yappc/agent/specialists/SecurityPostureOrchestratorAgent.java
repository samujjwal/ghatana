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
 * Orchestrates security posture assessment across the organization.
 *
 * @doc.type class
 * @doc.purpose Orchestrates security posture assessment across the organization
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class SecurityPostureOrchestratorAgent extends YAPPCAgentBase<SecurityPostureOrchestratorInput, SecurityPostureOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(SecurityPostureOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  public SecurityPostureOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<SecurityPostureOrchestratorInput>, StepResult<SecurityPostureOrchestratorOutput>> generator) {
    super(
        "SecurityPostureOrchestratorAgent",
        "expert.security-posture",
        new StepContract(
            "expert.security-posture",
            "#/definitions/SecurityPostureOrchestratorInput",
            "#/definitions/SecurityPostureOrchestratorOutput",
            List.of("security", "posture", "orchestration"),
            Map.of("description", "Orchestrates security posture assessment across the organization", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull SecurityPostureOrchestratorInput input) {
    if (input.organizationId() == null || input.organizationId().isEmpty()) {
      return ValidationResult.fail("organizationId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<SecurityPostureOrchestratorInput> perceive(
      @NotNull StepRequest<SecurityPostureOrchestratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.security-posture request: {}", request.input().organizationId().substring(0, Math.min(50, request.input().organizationId().length())));
    return request;
  }

  /** Rule-based generator for expert.security-posture. */
  public static class SecurityPostureOrchestratorGenerator
      implements OutputGenerator<StepRequest<SecurityPostureOrchestratorInput>, StepResult<SecurityPostureOrchestratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(SecurityPostureOrchestratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<SecurityPostureOrchestratorOutput>> generate(
        @NotNull StepRequest<SecurityPostureOrchestratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      SecurityPostureOrchestratorInput stepInput = input.input();

      log.info("Executing expert.security-posture for: {}", stepInput.organizationId());

      SecurityPostureOrchestratorOutput output =
          new SecurityPostureOrchestratorOutput(
              "expert.security-posture-" + UUID.randomUUID(),
              "Generated postureScore for " + stepInput.organizationId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.security-posture", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<SecurityPostureOrchestratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("SecurityPostureOrchestratorGenerator")
          .type("rule-based")
          .description("Orchestrates security posture assessment across the organization")
          .version("1.0.0")
          .build();
    }
  }
}
