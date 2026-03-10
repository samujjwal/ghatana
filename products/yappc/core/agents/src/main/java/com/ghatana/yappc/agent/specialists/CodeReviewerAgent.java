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
 * Expert code reviewer for automated code quality and best practice enforcement.
 *
 * @doc.type class
 * @doc.purpose Expert code reviewer for automated code quality and best practice enforcement
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class CodeReviewerAgent extends YAPPCAgentBase<CodeReviewerInput, CodeReviewerOutput> {

  private static final Logger log = LoggerFactory.getLogger(CodeReviewerAgent.class);

  private final MemoryStore memoryStore;

  public CodeReviewerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<CodeReviewerInput>, StepResult<CodeReviewerOutput>> generator) {
    super(
        "CodeReviewerAgent",
        "expert.code-reviewer",
        new StepContract(
            "expert.code-reviewer",
            "#/definitions/CodeReviewerInput",
            "#/definitions/CodeReviewerOutput",
            List.of("code-review", "quality"),
            Map.of("description", "Expert code reviewer for automated code quality and best practice enforcement", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull CodeReviewerInput input) {
    if (input.pullRequestId() == null || input.pullRequestId().isEmpty()) {
      return ValidationResult.fail("pullRequestId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<CodeReviewerInput> perceive(
      @NotNull StepRequest<CodeReviewerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.code-reviewer request: {}", request.input().pullRequestId().substring(0, Math.min(50, request.input().pullRequestId().length())));
    return request;
  }

  /** Rule-based generator for expert.code-reviewer. */
  public static class CodeReviewerGenerator
      implements OutputGenerator<StepRequest<CodeReviewerInput>, StepResult<CodeReviewerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<CodeReviewerOutput>> generate(
        @NotNull StepRequest<CodeReviewerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      CodeReviewerInput stepInput = input.input();

      log.info("Executing expert.code-reviewer for: {}", stepInput.pullRequestId());

      CodeReviewerOutput output =
          new CodeReviewerOutput(
              "expert.code-reviewer-" + UUID.randomUUID(),
              List.of(),
              "Generated verdict for " + stepInput.pullRequestId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.code-reviewer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<CodeReviewerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("CodeReviewerGenerator")
          .type("rule-based")
          .description("Expert code reviewer for automated code quality and best practice enforcement")
          .version("1.0.0")
          .build();
    }
  }
}
