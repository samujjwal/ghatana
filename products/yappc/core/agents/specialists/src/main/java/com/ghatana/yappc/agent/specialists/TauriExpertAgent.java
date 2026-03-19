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
 * Stack expert agent for Tauri desktop application patterns.
 *
 * @doc.type class
 * @doc.purpose Stack expert agent for Tauri desktop application patterns
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class TauriExpertAgent extends YAPPCAgentBase<TauriExpertInput, TauriExpertOutput> {

  private static final Logger log = LoggerFactory.getLogger(TauriExpertAgent.class);

  private final MemoryStore memoryStore;

  public TauriExpertAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<TauriExpertInput>, StepResult<TauriExpertOutput>> generator) {
    super(
        "TauriExpertAgent",
        "expert.tauri",
        new StepContract(
            "expert.tauri",
            "#/definitions/TauriExpertInput",
            "#/definitions/TauriExpertOutput",
            List.of("tauri", "desktop", "rust-bridge"),
            Map.of("description", "Stack expert agent for Tauri desktop application patterns", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull TauriExpertInput input) {
    if (input.codeContext() == null || input.codeContext().isEmpty()) {
      return ValidationResult.fail("codeContext cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<TauriExpertInput> perceive(
      @NotNull StepRequest<TauriExpertInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.tauri request: {}", request.input().codeContext().substring(0, Math.min(50, request.input().codeContext().length())));
    return request;
  }

  /** Rule-based generator for expert.tauri. */
  public static class TauriExpertGenerator
      implements OutputGenerator<StepRequest<TauriExpertInput>, StepResult<TauriExpertOutput>> {

    private static final Logger log = LoggerFactory.getLogger(TauriExpertGenerator.class);

    @Override
    public @NotNull Promise<StepResult<TauriExpertOutput>> generate(
        @NotNull StepRequest<TauriExpertInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      TauriExpertInput stepInput = input.input();

      log.info("Executing expert.tauri for: {}", stepInput.codeContext());

      TauriExpertOutput output =
          new TauriExpertOutput(
              "expert.tauri-" + UUID.randomUUID(),
              "Generated recommendation for " + stepInput.codeContext(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.tauri", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<TauriExpertInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("TauriExpertGenerator")
          .type("rule-based")
          .description("Stack expert agent for Tauri desktop application patterns")
          .version("1.0.0")
          .build();
    }
  }
}
