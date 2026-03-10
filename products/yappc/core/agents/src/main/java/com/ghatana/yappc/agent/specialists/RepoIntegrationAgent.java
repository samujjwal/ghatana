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
 * Integration bridge agent for source code repository operations.
 *
 * @doc.type class
 * @doc.purpose Integration bridge agent for source code repository operations
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class RepoIntegrationAgent extends YAPPCAgentBase<RepoIntegrationInput, RepoIntegrationOutput> {

  private static final Logger log = LoggerFactory.getLogger(RepoIntegrationAgent.class);

  private final MemoryStore memoryStore;

  public RepoIntegrationAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<RepoIntegrationInput>, StepResult<RepoIntegrationOutput>> generator) {
    super(
        "RepoIntegrationAgent",
        "integration.repo",
        new StepContract(
            "integration.repo",
            "#/definitions/RepoIntegrationInput",
            "#/definitions/RepoIntegrationOutput",
            List.of("integration", "git", "repository"),
            Map.of("description", "Integration bridge agent for source code repository operations", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull RepoIntegrationInput input) {
    if (input.repoUrl() == null || input.repoUrl().isEmpty()) {
      return ValidationResult.fail("repoUrl cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<RepoIntegrationInput> perceive(
      @NotNull StepRequest<RepoIntegrationInput> request, @NotNull AgentContext context) {
    log.info("Perceiving integration.repo request: {}", request.input().repoUrl().substring(0, Math.min(50, request.input().repoUrl().length())));
    return request;
  }

  /** Rule-based generator for integration.repo. */
  public static class RepoIntegrationGenerator
      implements OutputGenerator<StepRequest<RepoIntegrationInput>, StepResult<RepoIntegrationOutput>> {

    private static final Logger log = LoggerFactory.getLogger(RepoIntegrationGenerator.class);

    @Override
    public @NotNull Promise<StepResult<RepoIntegrationOutput>> generate(
        @NotNull StepRequest<RepoIntegrationInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      RepoIntegrationInput stepInput = input.input();

      log.info("Executing integration.repo for: {}", stepInput.repoUrl());

      RepoIntegrationOutput output =
          new RepoIntegrationOutput(
              "integration.repo-" + UUID.randomUUID(),
              "Generated result for " + stepInput.repoUrl(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "integration.repo", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<RepoIntegrationInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("RepoIntegrationGenerator")
          .type("rule-based")
          .description("Integration bridge agent for source code repository operations")
          .version("1.0.0")
          .build();
    }
  }
}
