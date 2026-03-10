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
 * Stack expert agent for Fastify/Node.js API patterns and best practices.
 *
 * @doc.type class
 * @doc.purpose Stack expert agent for Fastify/Node.js API patterns and best practices
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class FastifyExpertAgent extends YAPPCAgentBase<FastifyExpertInput, FastifyExpertOutput> {

  private static final Logger log = LoggerFactory.getLogger(FastifyExpertAgent.class);

  private final MemoryStore memoryStore;

  public FastifyExpertAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<FastifyExpertInput>, StepResult<FastifyExpertOutput>> generator) {
    super(
        "FastifyExpertAgent",
        "expert.fastify",
        new StepContract(
            "expert.fastify",
            "#/definitions/FastifyExpertInput",
            "#/definitions/FastifyExpertOutput",
            List.of("fastify", "nodejs", "api"),
            Map.of("description", "Stack expert agent for Fastify/Node.js API patterns and best practices", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull FastifyExpertInput input) {
    if (input.codeContext() == null || input.codeContext().isEmpty()) {
      return ValidationResult.fail("codeContext cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<FastifyExpertInput> perceive(
      @NotNull StepRequest<FastifyExpertInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.fastify request: {}", request.input().codeContext().substring(0, Math.min(50, request.input().codeContext().length())));
    return request;
  }

  /** Rule-based generator for expert.fastify. */
  public static class FastifyExpertGenerator
      implements OutputGenerator<StepRequest<FastifyExpertInput>, StepResult<FastifyExpertOutput>> {

    private static final Logger log = LoggerFactory.getLogger(FastifyExpertGenerator.class);

    @Override
    public @NotNull Promise<StepResult<FastifyExpertOutput>> generate(
        @NotNull StepRequest<FastifyExpertInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      FastifyExpertInput stepInput = input.input();

      log.info("Executing expert.fastify for: {}", stepInput.codeContext());

      FastifyExpertOutput output =
          new FastifyExpertOutput(
              "expert.fastify-" + UUID.randomUUID(),
              "Generated recommendation for " + stepInput.codeContext(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.fastify", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<FastifyExpertInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("FastifyExpertGenerator")
          .type("rule-based")
          .description("Stack expert agent for Fastify/Node.js API patterns and best practices")
          .version("1.0.0")
          .build();
    }
  }
}
