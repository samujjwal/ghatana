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
 * Worker agent that captures and institutionalizes learned patterns and practices.
 *
 * @doc.type class
 * @doc.purpose Worker agent that captures and institutionalizes learned patterns and practices
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reflect
 */
public class InstitutionalizeAgent extends YAPPCAgentBase<InstitutionalizeInput, InstitutionalizeOutput> {

  private static final Logger log = LoggerFactory.getLogger(InstitutionalizeAgent.class);

  private final MemoryStore memoryStore;

  public InstitutionalizeAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<InstitutionalizeInput>, StepResult<InstitutionalizeOutput>> generator) {
    super(
        "InstitutionalizeAgent",
        "worker.institutionalize",
        new StepContract(
            "worker.institutionalize",
            "#/definitions/InstitutionalizeInput",
            "#/definitions/InstitutionalizeOutput",
            List.of("learning", "institutionalization", "knowledge"),
            Map.of("description", "Worker agent that captures and institutionalizes learned patterns and practices", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull InstitutionalizeInput input) {
    if (input.patternId() == null || input.patternId().isEmpty()) {
      return ValidationResult.fail("patternId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<InstitutionalizeInput> perceive(
      @NotNull StepRequest<InstitutionalizeInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.institutionalize request: {}", request.input().patternId().substring(0, Math.min(50, request.input().patternId().length())));
    return request;
  }

  /** Rule-based generator for worker.institutionalize. */
  public static class InstitutionalizeGenerator
      implements OutputGenerator<StepRequest<InstitutionalizeInput>, StepResult<InstitutionalizeOutput>> {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalizeGenerator.class);

    @Override
    public @NotNull Promise<StepResult<InstitutionalizeOutput>> generate(
        @NotNull StepRequest<InstitutionalizeInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      InstitutionalizeInput stepInput = input.input();

      log.info("Executing worker.institutionalize for: {}", stepInput.patternId());

      InstitutionalizeOutput output =
          new InstitutionalizeOutput(
              "worker.institutionalize-" + UUID.randomUUID(),
              "Generated policyContent for " + stepInput.patternId(),
              0.0,
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.institutionalize", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<InstitutionalizeInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("InstitutionalizeGenerator")
          .type("rule-based")
          .description("Worker agent that captures and institutionalizes learned patterns and practices")
          .version("1.0.0")
          .build();
    }
  }
}
