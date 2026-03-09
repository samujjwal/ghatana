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
 * Worker agent that audits project dependencies for security and licensing.
 *
 * @doc.type class
 * @doc.purpose Worker agent that audits project dependencies for security and licensing
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class DependencyAuditorAgent extends YAPPCAgentBase<DependencyAuditorInput, DependencyAuditorOutput> {

  private static final Logger log = LoggerFactory.getLogger(DependencyAuditorAgent.class);

  private final MemoryStore memoryStore;

  public DependencyAuditorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DependencyAuditorInput>, StepResult<DependencyAuditorOutput>> generator) {
    super(
        "DependencyAuditorAgent",
        "worker.dependency-auditor",
        new StepContract(
            "worker.dependency-auditor",
            "#/definitions/DependencyAuditorInput",
            "#/definitions/DependencyAuditorOutput",
            List.of("dependency", "audit", "security", "licensing"),
            Map.of("description", "Worker agent that audits project dependencies for security and licensing", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DependencyAuditorInput input) {
    if (input.projectId() == null || input.projectId().isEmpty()) {
      return ValidationResult.fail("projectId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DependencyAuditorInput> perceive(
      @NotNull StepRequest<DependencyAuditorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.dependency-auditor request: {}", input.input().projectId().substring(0, Math.min(50, input.input().projectId().length())));
    return request;
  }

  /** Rule-based generator for worker.dependency-auditor. */
  public static class DependencyAuditorGenerator
      implements OutputGenerator<StepRequest<DependencyAuditorInput>, StepResult<DependencyAuditorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DependencyAuditorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DependencyAuditorOutput>> generate(
        @NotNull StepRequest<DependencyAuditorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DependencyAuditorInput stepInput = input.input();

      log.info("Executing worker.dependency-auditor for: {}", stepInput.projectId());

      DependencyAuditorOutput output =
          new DependencyAuditorOutput(
              "worker.dependency-auditor-" + UUID.randomUUID(),
              List.of(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.dependency-auditor", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DependencyAuditorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DependencyAuditorGenerator")
          .type("rule-based")
          .description("Worker agent that audits project dependencies for security and licensing")
          .version("1.0.0")
          .build();
    }
  }
}
