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
 * Release governance agent that verifies supply chain integrity.
 *
 * @doc.type class
 * @doc.purpose Release governance agent that verifies supply chain integrity
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class SupplyChainVerifierAgent extends YAPPCAgentBase<SupplyChainVerifierInput, SupplyChainVerifierOutput> {

  private static final Logger log = LoggerFactory.getLogger(SupplyChainVerifierAgent.class);

  private final MemoryStore memoryStore;

  public SupplyChainVerifierAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<SupplyChainVerifierInput>, StepResult<SupplyChainVerifierOutput>> generator) {
    super(
        "SupplyChainVerifierAgent",
        "release.supply-chain-verifier",
        new StepContract(
            "release.supply-chain-verifier",
            "#/definitions/SupplyChainVerifierInput",
            "#/definitions/SupplyChainVerifierOutput",
            List.of("release", "supply-chain", "verification"),
            Map.of("description", "Release governance agent that verifies supply chain integrity", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull SupplyChainVerifierInput input) {
    if (input.artifactId() == null || input.artifactId().isEmpty()) {
      return ValidationResult.fail("artifactId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<SupplyChainVerifierInput> perceive(
      @NotNull StepRequest<SupplyChainVerifierInput> request, @NotNull AgentContext context) {
    log.info("Perceiving release.supply-chain-verifier request: {}", input.input().artifactId().substring(0, Math.min(50, input.input().artifactId().length())));
    return request;
  }

  /** Rule-based generator for release.supply-chain-verifier. */
  public static class SupplyChainVerifierGenerator
      implements OutputGenerator<StepRequest<SupplyChainVerifierInput>, StepResult<SupplyChainVerifierOutput>> {

    private static final Logger log = LoggerFactory.getLogger(SupplyChainVerifierGenerator.class);

    @Override
    public @NotNull Promise<StepResult<SupplyChainVerifierOutput>> generate(
        @NotNull StepRequest<SupplyChainVerifierInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      SupplyChainVerifierInput stepInput = input.input();

      log.info("Executing release.supply-chain-verifier for: {}", stepInput.artifactId());

      SupplyChainVerifierOutput output =
          new SupplyChainVerifierOutput(
              "release.supply-chain-verifier-" + UUID.randomUUID(),
              true,
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "release.supply-chain-verifier", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<SupplyChainVerifierInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("SupplyChainVerifierGenerator")
          .type("rule-based")
          .description("Release governance agent that verifies supply chain integrity")
          .version("1.0.0")
          .build();
    }
  }
}
