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
 * Integration bridge agent for issue tracking systems.
 *
 * @doc.type class
 * @doc.purpose Integration bridge agent for issue tracking systems
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class IssueTrackerIntegrationAgent extends YAPPCAgentBase<IssueTrackerIntegrationInput, IssueTrackerIntegrationOutput> {

  private static final Logger log = LoggerFactory.getLogger(IssueTrackerIntegrationAgent.class);

  private final MemoryStore memoryStore;

  public IssueTrackerIntegrationAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<IssueTrackerIntegrationInput>, StepResult<IssueTrackerIntegrationOutput>> generator) {
    super(
        "IssueTrackerIntegrationAgent",
        "integration.issue-tracker",
        new StepContract(
            "integration.issue-tracker",
            "#/definitions/IssueTrackerIntegrationInput",
            "#/definitions/IssueTrackerIntegrationOutput",
            List.of("integration", "issue-tracker", "jira"),
            Map.of("description", "Integration bridge agent for issue tracking systems", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull IssueTrackerIntegrationInput input) {
    if (input.trackerId() == null || input.trackerId().isEmpty()) {
      return ValidationResult.fail("trackerId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<IssueTrackerIntegrationInput> perceive(
      @NotNull StepRequest<IssueTrackerIntegrationInput> request, @NotNull AgentContext context) {
    log.info("Perceiving integration.issue-tracker request: {}", input.input().trackerId().substring(0, Math.min(50, input.input().trackerId().length())));
    return request;
  }

  /** Rule-based generator for integration.issue-tracker. */
  public static class IssueTrackerIntegrationGenerator
      implements OutputGenerator<StepRequest<IssueTrackerIntegrationInput>, StepResult<IssueTrackerIntegrationOutput>> {

    private static final Logger log = LoggerFactory.getLogger(IssueTrackerIntegrationGenerator.class);

    @Override
    public @NotNull Promise<StepResult<IssueTrackerIntegrationOutput>> generate(
        @NotNull StepRequest<IssueTrackerIntegrationInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      IssueTrackerIntegrationInput stepInput = input.input();

      log.info("Executing integration.issue-tracker for: {}", stepInput.trackerId());

      IssueTrackerIntegrationOutput output =
          new IssueTrackerIntegrationOutput(
              "integration.issue-tracker-" + UUID.randomUUID(),
              "Generated result for " + stepInput.trackerId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "integration.issue-tracker", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<IssueTrackerIntegrationInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("IssueTrackerIntegrationGenerator")
          .type("rule-based")
          .description("Integration bridge agent for issue tracking systems")
          .version("1.0.0")
          .build();
    }
  }
}
