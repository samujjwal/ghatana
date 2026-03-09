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
 * Expert business analyst for requirements elicitation and domain modeling.
 *
 * @doc.type class
 * @doc.purpose Expert business analyst for requirements elicitation and domain modeling
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class BusinessAnalystAgent extends YAPPCAgentBase<BusinessAnalystInput, BusinessAnalystOutput> {

  private static final Logger log = LoggerFactory.getLogger(BusinessAnalystAgent.class);

  private final MemoryStore memoryStore;

  public BusinessAnalystAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<BusinessAnalystInput>, StepResult<BusinessAnalystOutput>> generator) {
    super(
        "BusinessAnalystAgent",
        "expert.business-analyst",
        new StepContract(
            "expert.business-analyst",
            "#/definitions/BusinessAnalystInput",
            "#/definitions/BusinessAnalystOutput",
            List.of("requirements", "domain-modeling"),
            Map.of("description", "Expert business analyst for requirements elicitation and domain modeling", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull BusinessAnalystInput input) {
    if (input.projectId() == null || input.projectId().isEmpty()) {
      return ValidationResult.fail("projectId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<BusinessAnalystInput> perceive(
      @NotNull StepRequest<BusinessAnalystInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.business-analyst request: {}", input.input().projectId().substring(0, Math.min(50, input.input().projectId().length())));
    return request;
  }

  /** Rule-based generator for expert.business-analyst. */
  public static class BusinessAnalystGenerator
      implements OutputGenerator<StepRequest<BusinessAnalystInput>, StepResult<BusinessAnalystOutput>> {

    private static final Logger log = LoggerFactory.getLogger(BusinessAnalystGenerator.class);

    @Override
    public @NotNull Promise<StepResult<BusinessAnalystOutput>> generate(
        @NotNull StepRequest<BusinessAnalystInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      BusinessAnalystInput stepInput = input.input();

      log.info("Executing expert.business-analyst for: {}", stepInput.projectId());

      BusinessAnalystOutput output =
          new BusinessAnalystOutput(
              "expert.business-analyst-" + UUID.randomUUID(),
              List.of(),
              Map.of("generatedAt", start.toString()),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.business-analyst", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<BusinessAnalystInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("BusinessAnalystGenerator")
          .type("rule-based")
          .description("Expert business analyst for requirements elicitation and domain modeling")
          .version("1.0.0")
          .build();
    }
  }
}
