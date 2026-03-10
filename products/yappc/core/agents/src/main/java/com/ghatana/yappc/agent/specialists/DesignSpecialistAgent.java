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
 * Specialist agent for architecture design.
 *
 * <p>Synthesizes system architecture from requirements.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for architecture design
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class DesignSpecialistAgent extends YAPPCAgentBase<DesignInput, DesignOutput> {

  private static final Logger log = LoggerFactory.getLogger(DesignSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public DesignSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DesignInput>, StepResult<DesignOutput>> generator) {
    super(
        "DesignSpecialistAgent",
        "architecture.design",
        new StepContract(
            "architecture.design",
            "#/definitions/DesignInput",
            "#/definitions/DesignOutput",
            List.of("architecture", "design", "synthesis"),
            Map.of("description", "Synthesizes system architecture", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DesignInput input) {
    if (input.requirementsId() == null || input.requirementsId().isEmpty()) {
      return ValidationResult.fail("Requirements ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DesignInput> perceive(
      @NotNull StepRequest<DesignInput> request, @NotNull AgentContext context) {
    log.info("Perceiving design request for requirements: {}", request.input().requirementsId());
    return request;
  }

  /** Rule-based generator for design. */
  public static class DesignGenerator
      implements OutputGenerator<StepRequest<DesignInput>, StepResult<DesignOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DesignGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DesignOutput>> generate(
        @NotNull StepRequest<DesignInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DesignInput designInput = input.input();

      log.info("Generating architecture design for requirements: {}", designInput.requirementsId());

      // Generate architecture components (simplified)
      List<String> components =
          List.of(
              "API Gateway",
              "Service Layer",
              "Data Layer",
              "Cache Layer",
              "Message Queue",
              "Event Store");

      Map<String, String> patterns =
          Map.of(
              "style", "Microservices",
              "communication", "Event-Driven",
              "data", "CQRS",
              "deployment", "Containerized");

      String architectureId = "arch-" + UUID.randomUUID();

      DesignOutput output =
          new DesignOutput(
              architectureId,
              components,
              patterns,
              Map.of(
                  "requirementsId",
                  designInput.requirementsId(),
                  "generatedAt",
                  start.toString(),
                  "componentCount",
                  components.size()));

      return Promise.of(
          StepResult.success(
              output, Map.of("architectureId", architectureId), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DesignInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DesignGenerator")
          .type("rule-based")
          .description("Synthesizes system architecture from requirements")
          .version("1.0.0")
          .build();
    }
  }
}
