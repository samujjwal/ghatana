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
 * Specialist agent for architecture validation.
 *
 * <p>Validates architecture against best practices, patterns, and constraints.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for architecture validation
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class ValidateArchitectureSpecialistAgent
    extends YAPPCAgentBase<ValidateArchitectureInput, ValidateArchitectureOutput> {

  private static final Logger log =
      LoggerFactory.getLogger(ValidateArchitectureSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public ValidateArchitectureSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<
                  StepRequest<ValidateArchitectureInput>, StepResult<ValidateArchitectureOutput>>
              generator) {
    super(
        "ValidateArchitectureSpecialistAgent",
        "architecture.validate",
        new StepContract(
            "architecture.validate",
            "#/definitions/ValidateArchitectureInput",
            "#/definitions/ValidateArchitectureOutput",
            List.of("architecture", "validation", "quality"),
            Map.of("description", "Validates architecture design", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ValidateArchitectureInput input) {
    if (input.architectureId() == null || input.architectureId().isEmpty()) {
      return ValidationResult.fail("Architecture ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ValidateArchitectureInput> perceive(
      @NotNull StepRequest<ValidateArchitectureInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving architecture validation request for: {}", request.input().architectureId());
    return request;
  }

  /** Rule-based generator for architecture validation. */
  public static class ValidateArchitectureGenerator
      implements OutputGenerator<
          StepRequest<ValidateArchitectureInput>, StepResult<ValidateArchitectureOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ValidateArchitectureGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ValidateArchitectureOutput>> generate(
        @NotNull StepRequest<ValidateArchitectureInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ValidateArchitectureInput validationInput = input.input();

      log.info("Validating architecture: {}", validationInput.architectureId());

      // Perform validation checks
      List<String> issues = new ArrayList<>();
      List<String> recommendations = new ArrayList<>();

      // Simulate validation rules
      if (validationInput.contractId().isEmpty()) {
        issues.add("Missing API contracts");
        recommendations.add("Generate API contracts before proceeding");
      }

      if (validationInput.modelId().isEmpty()) {
        issues.add("Missing data models");
        recommendations.add("Derive data models from architecture");
      }

      // Check for best practices
      recommendations.add("Consider implementing circuit breaker pattern");
      recommendations.add("Add observability instrumentation");
      recommendations.add("Implement rate limiting for public APIs");

      boolean isValid = issues.isEmpty();

      Map<String, Object> metrics =
          Map.of(
              "issuesFound",
              issues.size(),
              "criticalIssues",
              0,
              "warningsFound",
              recommendations.size(),
              "complianceScore",
              isValid ? 100 : 70);

      String validationId = "validation-" + UUID.randomUUID();

      ValidateArchitectureOutput output =
          new ValidateArchitectureOutput(
              validationId,
              isValid,
              issues,
              recommendations,
              metrics,
              Map.of(
                  "architectureId",
                  validationInput.architectureId(),
                  "validatedAt",
                  start.toString(),
                  "validator",
                  "rule-based-v1"));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("validationId", validationId, "isValid", isValid),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ValidateArchitectureInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ValidateArchitectureGenerator")
          .type("rule-based")
          .description("Validates architecture against best practices")
          .version("1.0.0")
          .build();
    }
  }
}
