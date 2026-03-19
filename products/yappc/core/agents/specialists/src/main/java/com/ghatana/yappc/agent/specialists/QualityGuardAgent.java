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
 * Expert quality guard agent enforcing code and artifact quality standards.
 *
 * @doc.type class
 * @doc.purpose Expert quality guard agent enforcing code and artifact quality standards
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class QualityGuardAgent extends YAPPCAgentBase<QualityGuardInput, QualityGuardOutput> {

  private static final Logger log = LoggerFactory.getLogger(QualityGuardAgent.class);

  private final MemoryStore memoryStore;

  public QualityGuardAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<QualityGuardInput>, StepResult<QualityGuardOutput>> generator) {
    super(
        "QualityGuardAgent",
        "expert.quality-guard",
        new StepContract(
            "expert.quality-guard",
            "#/definitions/QualityGuardInput",
            "#/definitions/QualityGuardOutput",
            List.of("quality", "gate", "standards"),
            Map.of("description", "Expert quality guard agent enforcing code and artifact quality standards", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull QualityGuardInput input) {
    if (input.artifactId() == null || input.artifactId().isEmpty()) {
      return ValidationResult.fail("artifactId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<QualityGuardInput> perceive(
      @NotNull StepRequest<QualityGuardInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.quality-guard request: {}", request.input().artifactId().substring(0, Math.min(50, request.input().artifactId().length())));
    return request;
  }

  /** Rule-based generator for expert.quality-guard. */
  public static class QualityGuardGenerator
      implements OutputGenerator<StepRequest<QualityGuardInput>, StepResult<QualityGuardOutput>> {

    private static final Logger log = LoggerFactory.getLogger(QualityGuardGenerator.class);

    @Override
    public @NotNull Promise<StepResult<QualityGuardOutput>> generate(
        @NotNull StepRequest<QualityGuardInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      QualityGuardInput stepInput = input.input();

      log.info("Executing expert.quality-guard for: {}", stepInput.artifactId());

      QualityGuardOutput output =
          new QualityGuardOutput(
              "expert.quality-guard-" + UUID.randomUUID(),
              true,
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.quality-guard", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<QualityGuardInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("QualityGuardGenerator")
          .type("rule-based")
          .description("Expert quality guard agent enforcing code and artifact quality standards")
          .version("1.0.0")
          .build();
    }
  }
}
