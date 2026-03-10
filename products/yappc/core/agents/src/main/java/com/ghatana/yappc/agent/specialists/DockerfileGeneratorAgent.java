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
 * Worker agent that generates optimized Dockerfiles for services.
 *
 * @doc.type class
 * @doc.purpose Worker agent that generates optimized Dockerfiles for services
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class DockerfileGeneratorAgent extends YAPPCAgentBase<DockerfileGeneratorInput, DockerfileGeneratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(DockerfileGeneratorAgent.class);

  private final MemoryStore memoryStore;

  public DockerfileGeneratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DockerfileGeneratorInput>, StepResult<DockerfileGeneratorOutput>> generator) {
    super(
        "DockerfileGeneratorAgent",
        "worker.dockerfile-generator",
        new StepContract(
            "worker.dockerfile-generator",
            "#/definitions/DockerfileGeneratorInput",
            "#/definitions/DockerfileGeneratorOutput",
            List.of("devops", "docker", "containerization"),
            Map.of("description", "Worker agent that generates optimized Dockerfiles for services", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DockerfileGeneratorInput input) {
    if (input.serviceId() == null || input.serviceId().isEmpty()) {
      return ValidationResult.fail("serviceId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DockerfileGeneratorInput> perceive(
      @NotNull StepRequest<DockerfileGeneratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.dockerfile-generator request: {}", request.input().serviceId().substring(0, Math.min(50, request.input().serviceId().length())));
    return request;
  }

  /** Rule-based generator for worker.dockerfile-generator. */
  public static class DockerfileGeneratorGenerator
      implements OutputGenerator<StepRequest<DockerfileGeneratorInput>, StepResult<DockerfileGeneratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DockerfileGeneratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DockerfileGeneratorOutput>> generate(
        @NotNull StepRequest<DockerfileGeneratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DockerfileGeneratorInput stepInput = input.input();

      log.info("Executing worker.dockerfile-generator for: {}", stepInput.serviceId());

      DockerfileGeneratorOutput output =
          new DockerfileGeneratorOutput(
              "Generated dockerfileContent for " + stepInput.serviceId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.dockerfile-generator", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DockerfileGeneratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DockerfileGeneratorGenerator")
          .type("rule-based")
          .description("Worker agent that generates optimized Dockerfiles for services")
          .version("1.0.0")
          .build();
    }
  }
}
