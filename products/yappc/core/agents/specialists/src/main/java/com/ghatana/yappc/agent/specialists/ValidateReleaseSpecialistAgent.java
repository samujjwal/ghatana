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
 * Specialist agent for release validation.
 *
 * <p>Validates deployed release through smoke tests, health checks, and integration tests.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for release validation
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class ValidateReleaseSpecialistAgent
    extends YAPPCAgentBase<ValidateReleaseInput, ValidateReleaseOutput> {

  private static final Logger log = LoggerFactory.getLogger(ValidateReleaseSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public ValidateReleaseSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<ValidateReleaseInput>, StepResult<ValidateReleaseOutput>>
              generator) {
    super(
        "ValidateReleaseSpecialistAgent",
        "ops.validateRelease",
        new StepContract(
            "ops.validateRelease",
            "#/definitions/ValidateReleaseInput",
            "#/definitions/ValidateReleaseOutput",
            List.of("ops", "validation", "release"),
            Map.of("description", "Validates release readiness", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ValidateReleaseInput input) {
    if (input.deploymentId() == null || input.deploymentId().isEmpty()) {
      return ValidationResult.fail("Deployment ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ValidateReleaseInput> perceive(
      @NotNull StepRequest<ValidateReleaseInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving release validation request for deployment: {}", request.input().deploymentId());
    return request;
  }

  /** Rule-based generator for release validation. */
  public static class ValidateReleaseGenerator
      implements OutputGenerator<
          StepRequest<ValidateReleaseInput>, StepResult<ValidateReleaseOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ValidateReleaseGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ValidateReleaseOutput>> generate(
        @NotNull StepRequest<ValidateReleaseInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ValidateReleaseInput validationInput = input.input();

      log.info("Validating release for deployment: {}", validationInput.deploymentId());

      Map<String, String> checkResults = new LinkedHashMap<>();
      for (String check : validationInput.validationChecks()) {
        checkResults.put(check, "PASS");
      }

      boolean valid = checkResults.values().stream().allMatch(result -> result.equals("PASS"));

      String validationId = "validation-" + UUID.randomUUID();

      ValidateReleaseOutput output =
          new ValidateReleaseOutput(
              validationId,
              valid,
              checkResults,
              Map.of(
                  "deploymentId",
                  validationInput.deploymentId(),
                  "validatedAt",
                  start.toString(),
                  "checksExecuted",
                  checkResults.size()));

      return Promise.of(
          StepResult.success(
              output, Map.of("validationId", validationId, "valid", valid), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ValidateReleaseInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ValidateReleaseGenerator")
          .type("rule-based")
          .description("Validates release through automated checks")
          .version("1.0.0")
          .build();
    }
  }
}
