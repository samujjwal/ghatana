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
 * Worker agent that generates documentation from code and specifications.
 *
 * @doc.type class
 * @doc.purpose Worker agent that generates documentation from code and specifications
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class DocumentationWriterAgent extends YAPPCAgentBase<DocumentationWriterInput, DocumentationWriterOutput> {

  private static final Logger log = LoggerFactory.getLogger(DocumentationWriterAgent.class);

  private final MemoryStore memoryStore;

  public DocumentationWriterAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DocumentationWriterInput>, StepResult<DocumentationWriterOutput>> generator) {
    super(
        "DocumentationWriterAgent",
        "worker.documentation-writer",
        new StepContract(
            "worker.documentation-writer",
            "#/definitions/DocumentationWriterInput",
            "#/definitions/DocumentationWriterOutput",
            List.of("documentation", "generation"),
            Map.of("description", "Worker agent that generates documentation from code and specifications", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DocumentationWriterInput input) {
    if (input.sourceId() == null || input.sourceId().isEmpty()) {
      return ValidationResult.fail("sourceId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DocumentationWriterInput> perceive(
      @NotNull StepRequest<DocumentationWriterInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.documentation-writer request: {}", request.input().sourceId().substring(0, Math.min(50, request.input().sourceId().length())));
    return request;
  }

  /** Rule-based generator for worker.documentation-writer. */
  public static class DocumentationWriterGenerator
      implements OutputGenerator<StepRequest<DocumentationWriterInput>, StepResult<DocumentationWriterOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DocumentationWriterGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DocumentationWriterOutput>> generate(
        @NotNull StepRequest<DocumentationWriterInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DocumentationWriterInput stepInput = input.input();

      log.info("Executing worker.documentation-writer for: {}", stepInput.sourceId());

      DocumentationWriterOutput output =
          new DocumentationWriterOutput(
              "worker.documentation-writer-" + UUID.randomUUID(),
              "Generated content for " + stepInput.sourceId(),
              "Generated format for " + stepInput.sourceId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.documentation-writer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DocumentationWriterInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DocumentationWriterGenerator")
          .type("rule-based")
          .description("Worker agent that generates documentation from code and specifications")
          .version("1.0.0")
          .build();
    }
  }
}
