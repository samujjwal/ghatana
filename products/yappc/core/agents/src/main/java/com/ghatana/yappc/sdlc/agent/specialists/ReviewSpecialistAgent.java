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
 * Specialist agent for code review.
 *
 * <p>Reviews implemented code for quality, standards, and best practices.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for code review
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class ReviewSpecialistAgent extends YAPPCAgentBase<ReviewInput, ReviewOutput> {

  private static final Logger log = LoggerFactory.getLogger(ReviewSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public ReviewSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ReviewInput>, StepResult<ReviewOutput>> generator) {
    super(
        "ReviewSpecialistAgent",
        "implementation.review",
        new StepContract(
            "implementation.review",
            "#/definitions/ReviewInput",
            "#/definitions/ReviewOutput",
            List.of("implementation", "review", "quality"),
            Map.of("description", "Reviews implemented code", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ReviewInput input) {
    if (input.implementationId() == null || input.implementationId().isEmpty()) {
      return ValidationResult.fail("Implementation ID cannot be empty");
    }
    if (input.unitName() == null || input.unitName().isEmpty()) {
      return ValidationResult.fail("Unit name cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ReviewInput> perceive(
      @NotNull StepRequest<ReviewInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving review request for implementation: {}, unit: {}",
        request.input().implementationId(),
        request.input().unitName());
    return request;
  }

  /** Rule-based generator for code review. */
  public static class ReviewGenerator
      implements OutputGenerator<StepRequest<ReviewInput>, StepResult<ReviewOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ReviewGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ReviewOutput>> generate(
        @NotNull StepRequest<ReviewInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ReviewInput reviewInput = input.input();

      log.info(
          "Reviewing implementation: {}, unit: {}",
          reviewInput.implementationId(),
          reviewInput.unitName());

      // Perform automated checks
      List<String> findings = new ArrayList<>();

      // Simulate review findings
      findings.add("Consider adding more inline documentation");
      findings.add("Extract magic numbers into constants");
      findings.add("Add error handling for edge cases");

      // Calculate quality metrics
      Map<String, Integer> qualityMetrics =
          Map.of(
              "codeSmells", 2,
              "duplications", 1,
              "coverage", 85,
              "complexity", 12,
              "maintainability", 78);

      boolean approved =
          qualityMetrics.get("coverage") >= 80
              && qualityMetrics.get("complexity") <= 15
              && findings.size() <= 5;

      String reviewId = "review-" + UUID.randomUUID();

      ReviewOutput output =
          new ReviewOutput(
              reviewId,
              approved,
              findings,
              qualityMetrics,
              Map.of(
                  "implementationId",
                  reviewInput.implementationId(),
                  "unitName",
                  reviewInput.unitName(),
                  "reviewedAt",
                  start.toString(),
                  "findingsCount",
                  findings.size()));

      return Promise.of(
          StepResult.success(
              output, Map.of("reviewId", reviewId, "approved", approved), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ReviewInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ReviewGenerator")
          .type("rule-based")
          .description("Reviews code for quality and best practices")
          .version("1.0.0")
          .build();
    }
  }
}
