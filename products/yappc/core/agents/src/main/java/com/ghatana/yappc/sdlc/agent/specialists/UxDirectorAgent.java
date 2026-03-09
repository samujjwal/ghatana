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
 * Strategic UX director ensuring coherent user experience.
 *
 * @doc.type class
 * @doc.purpose Strategic UX director ensuring coherent user experience
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class UxDirectorAgent extends YAPPCAgentBase<UxDirectorInput, UxDirectorOutput> {

  private static final Logger log = LoggerFactory.getLogger(UxDirectorAgent.class);

  private final MemoryStore memoryStore;

  public UxDirectorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<UxDirectorInput>, StepResult<UxDirectorOutput>> generator) {
    super(
        "UxDirectorAgent",
        "strategic.ux-director",
        new StepContract(
            "strategic.ux-director",
            "#/definitions/UxDirectorInput",
            "#/definitions/UxDirectorOutput",
            List.of("strategic", "ux-design"),
            Map.of("description", "Strategic UX director ensuring coherent user experience", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull UxDirectorInput input) {
    if (input.productId() == null || input.productId().isEmpty()) {
      return ValidationResult.fail("productId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<UxDirectorInput> perceive(
      @NotNull StepRequest<UxDirectorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving strategic.ux-director request: {}", input.input().productId().substring(0, Math.min(50, input.input().productId().length())));
    return request;
  }

  /** Rule-based generator for strategic.ux-director. */
  public static class UxDirectorGenerator
      implements OutputGenerator<StepRequest<UxDirectorInput>, StepResult<UxDirectorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(UxDirectorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<UxDirectorOutput>> generate(
        @NotNull StepRequest<UxDirectorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      UxDirectorInput stepInput = input.input();

      log.info("Executing strategic.ux-director for: {}", stepInput.productId());

      UxDirectorOutput output =
          new UxDirectorOutput(
              "strategic.ux-director-" + UUID.randomUUID(),
              "Generated uxStrategy for " + stepInput.productId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "strategic.ux-director", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<UxDirectorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("UxDirectorGenerator")
          .type("rule-based")
          .description("Strategic UX director ensuring coherent user experience")
          .version("1.0.0")
          .build();
    }
  }
}
