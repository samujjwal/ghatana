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
 * Stack expert agent for Prisma ORM patterns and database modeling.
 *
 * @doc.type class
 * @doc.purpose Stack expert agent for Prisma ORM patterns and database modeling
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class PrismaExpertAgent extends YAPPCAgentBase<PrismaExpertInput, PrismaExpertOutput> {

  private static final Logger log = LoggerFactory.getLogger(PrismaExpertAgent.class);

  private final MemoryStore memoryStore;

  public PrismaExpertAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<PrismaExpertInput>, StepResult<PrismaExpertOutput>> generator) {
    super(
        "PrismaExpertAgent",
        "expert.prisma",
        new StepContract(
            "expert.prisma",
            "#/definitions/PrismaExpertInput",
            "#/definitions/PrismaExpertOutput",
            List.of("prisma", "orm", "database"),
            Map.of("description", "Stack expert agent for Prisma ORM patterns and database modeling", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull PrismaExpertInput input) {
    if (input.codeContext() == null || input.codeContext().isEmpty()) {
      return ValidationResult.fail("codeContext cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<PrismaExpertInput> perceive(
      @NotNull StepRequest<PrismaExpertInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.prisma request: {}", request.input().codeContext().substring(0, Math.min(50, request.input().codeContext().length())));
    return request;
  }

  /** Rule-based generator for expert.prisma. */
  public static class PrismaExpertGenerator
      implements OutputGenerator<StepRequest<PrismaExpertInput>, StepResult<PrismaExpertOutput>> {

    private static final Logger log = LoggerFactory.getLogger(PrismaExpertGenerator.class);

    @Override
    public @NotNull Promise<StepResult<PrismaExpertOutput>> generate(
        @NotNull StepRequest<PrismaExpertInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      PrismaExpertInput stepInput = input.input();

      log.info("Executing expert.prisma for: {}", stepInput.codeContext());

      PrismaExpertOutput output =
          new PrismaExpertOutput(
              "expert.prisma-" + UUID.randomUUID(),
              "Generated recommendation for " + stepInput.codeContext(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.prisma", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<PrismaExpertInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("PrismaExpertGenerator")
          .type("rule-based")
          .description("Stack expert agent for Prisma ORM patterns and database modeling")
          .version("1.0.0")
          .build();
    }
  }
}
