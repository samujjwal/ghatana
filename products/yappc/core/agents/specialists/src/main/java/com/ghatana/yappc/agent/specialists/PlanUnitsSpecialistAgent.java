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
 * Specialist agent for implementation unit planning.
 *
 * <p>Breaks down scaffold into implementation units with dependencies and effort estimates.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for unit planning
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class PlanUnitsSpecialistAgent extends YAPPCAgentBase<PlanUnitsInput, PlanUnitsOutput> {

  private static final Logger log = LoggerFactory.getLogger(PlanUnitsSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public PlanUnitsSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<PlanUnitsInput>, StepResult<PlanUnitsOutput>> generator) {
    super(
        "PlanUnitsSpecialistAgent",
        "implementation.planUnits",
        new StepContract(
            "implementation.planUnits",
            "#/definitions/PlanUnitsInput",
            "#/definitions/PlanUnitsOutput",
            List.of("implementation", "planning", "units"),
            Map.of(
                "description", "Plans implementation units with dependencies", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull PlanUnitsInput input) {
    if (input.scaffoldId() == null || input.scaffoldId().isEmpty()) {
      return ValidationResult.fail("Scaffold ID cannot be empty");
    }
    if (input.architectureId() == null || input.architectureId().isEmpty()) {
      return ValidationResult.fail("Architecture ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<PlanUnitsInput> perceive(
      @NotNull StepRequest<PlanUnitsInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving unit planning request for scaffold: {}, architecture: {}",
        request.input().scaffoldId(),
        request.input().architectureId());
    return request;
  }

  /** Rule-based generator for unit planning. */
  public static class PlanUnitsGenerator
      implements OutputGenerator<StepRequest<PlanUnitsInput>, StepResult<PlanUnitsOutput>> {

    private static final Logger log = LoggerFactory.getLogger(PlanUnitsGenerator.class);

    @Override
    public @NotNull Promise<StepResult<PlanUnitsOutput>> generate(
        @NotNull StepRequest<PlanUnitsInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      PlanUnitsInput planInput = input.input();

      log.info(
          "Generating implementation plan for scaffold: {}, architecture: {}",
          planInput.scaffoldId(),
          planInput.architectureId());

      // Generate implementation units
      List<String> implementationUnits =
          List.of(
              "domain-models",
              "repositories",
              "services",
              "api-controllers",
              "event-handlers",
              "validators",
              "migrations",
              "integration-tests");

      // Generate dependencies (topological order)
      Map<String, List<String>> dependencies =
          Map.of(
              "domain-models", List.of(),
              "repositories", List.of("domain-models", "migrations"),
              "services", List.of("domain-models", "repositories"),
              "api-controllers", List.of("services", "validators"),
              "event-handlers", List.of("services"),
              "validators", List.of("domain-models"),
              "migrations", List.of("domain-models"),
              "integration-tests", List.of("api-controllers", "event-handlers"));

      // Estimate effort (story points)
      Map<String, Integer> estimatedEffort =
          Map.of(
              "domain-models", 5,
              "repositories", 8,
              "services", 13,
              "api-controllers", 8,
              "event-handlers", 5,
              "validators", 3,
              "migrations", 5,
              "integration-tests", 13);

      String planId = "plan-" + UUID.randomUUID();

      PlanUnitsOutput output =
          new PlanUnitsOutput(
              planId,
              implementationUnits,
              dependencies,
              estimatedEffort,
              Map.of(
                  "scaffoldId",
                  planInput.scaffoldId(),
                  "architectureId",
                  planInput.architectureId(),
                  "totalUnits",
                  implementationUnits.size(),
                  "totalEffort",
                  estimatedEffort.values().stream().mapToInt(Integer::intValue).sum()));

      return Promise.of(StepResult.success(output, Map.of("planId", planId), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<PlanUnitsInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("PlanUnitsGenerator")
          .type("rule-based")
          .description("Plans implementation units with dependency analysis")
          .version("1.0.0")
          .build();
    }
  }
}
