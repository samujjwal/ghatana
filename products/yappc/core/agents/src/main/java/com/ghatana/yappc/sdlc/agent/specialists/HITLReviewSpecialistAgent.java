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
 * Specialist agent for human-in-the-loop review.
 *
 * @doc.type class
 * @doc.purpose Coordinates human review of architecture
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class HITLReviewSpecialistAgent extends YAPPCAgentBase<HITLReviewInput, HITLReviewOutput> {

  private static final Logger log = LoggerFactory.getLogger(HITLReviewSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public HITLReviewSpecialistAgent(
      MemoryStore memoryStore, Map<String, OutputGenerator<?, ?>> generators) {
    super(
        "HITLReviewSpecialistAgent",
        "architecture.hitlReview",
        new StepContract(
            "architecture.hitlReview",
            "#/definitions/HITLReviewInput",
            "#/definitions/HITLReviewOutput",
            List.of("architecture", "review", "human"),
            Map.of("description", "Coordinates human review", "version", "1.0.0")),
        (OutputGenerator<StepRequest<HITLReviewInput>, StepResult<HITLReviewOutput>>)
            generators.get("architecture.hitlReview"));
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull HITLReviewInput input) {
    if (input.architectureId() == null || input.architectureId().isEmpty()) {
      return ValidationResult.fail("Architecture ID cannot be empty");
    }
    if (input.reviewers() == null || input.reviewers().isEmpty()) {
      return ValidationResult.fail("At least one reviewer required");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<HITLReviewInput> perceive(
      @NotNull StepRequest<HITLReviewInput> request, @NotNull AgentContext context) {
    log.info("Perceiving HITL review request for: {}", request.input().architectureId());
    return request;
  }

  /** Rule-based generator for HITL review. */
  public static class HITLReviewGenerator
      implements OutputGenerator<StepRequest<HITLReviewInput>, StepResult<HITLReviewOutput>> {

    private static final Logger log = LoggerFactory.getLogger(HITLReviewGenerator.class);

    @Override
    public @NotNull Promise<StepResult<HITLReviewOutput>> generate(
        @NotNull StepRequest<HITLReviewInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      HITLReviewInput reviewInput = input.input();

      log.info(
          "Creating HITL review for {} with {} reviewers",
          reviewInput.architectureId(),
          reviewInput.reviewers().size());

      String reviewId = "review-" + UUID.randomUUID().toString().substring(0, 8);
      String status = "PENDING";

      List<String> comments = new ArrayList<>();
      comments.add("Review request created for " + reviewInput.reviewers().size() + " reviewers");
      comments.add("Design document: " + reviewInput.designDocument());
      comments.add("Contracts: " + reviewInput.contracts().size());
      comments.add("Data models: " + reviewInput.dataModels().size());

      String nextAction = "Waiting for reviewer feedback";

      HITLReviewOutput output =
          new HITLReviewOutput(
              reviewInput.architectureId(),
              reviewId,
              status,
              comments,
              List.of(),
              Instant.now(),
              nextAction);

      return Promise.of(
          StepResult.success(
              output,
              Map.of(
                  "architectureId",
                  reviewInput.architectureId(),
                  "reviewId",
                  reviewId,
                  "status",
                  status),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<HITLReviewInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("HITLReviewGenerator")
          .type("rule-based")
          .description("Coordinates human review of architecture")
          .version("1.0.0")
          .build();
    }
  }
}
