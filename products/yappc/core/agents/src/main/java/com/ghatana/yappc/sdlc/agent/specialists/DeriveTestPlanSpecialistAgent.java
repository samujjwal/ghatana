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
 * Specialist agent for deriving test plan.
 *
 * @doc.type class
 * @doc.purpose Derives comprehensive test plan from requirements
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class DeriveTestPlanSpecialistAgent
    extends YAPPCAgentBase<DeriveTestPlanInput, DeriveTestPlanOutput> {

  private static final Logger log = LoggerFactory.getLogger(DeriveTestPlanSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public DeriveTestPlanSpecialistAgent(
      MemoryStore memoryStore, Map<String, OutputGenerator<?, ?>> generators) {
    super(
        "DeriveTestPlanSpecialistAgent",
        "testing.deriveTestPlan",
        new StepContract(
            "testing.deriveTestPlan",
            "#/definitions/DeriveTestPlanInput",
            "#/definitions/DeriveTestPlanOutput",
            List.of("testing", "plan", "derive"),
            Map.of("description", "Derives comprehensive test plan", "version", "1.0.0")),
        (OutputGenerator<StepRequest<DeriveTestPlanInput>, StepResult<DeriveTestPlanOutput>>)
            generators.get("testing.deriveTestPlan"));
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DeriveTestPlanInput input) {
    if (input.requirementsId() == null || input.requirementsId().isEmpty()) {
      return ValidationResult.fail("Requirements ID cannot be empty");
    }
    if (input.functionalRequirements() == null || input.functionalRequirements().isEmpty()) {
      return ValidationResult.fail("At least one functional requirement required");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DeriveTestPlanInput> perceive(
      @NotNull StepRequest<DeriveTestPlanInput> request, @NotNull AgentContext context) {
    log.info("Perceiving test plan derivation for: {}", request.input().requirementsId());
    return request;
  }

  /** Rule-based generator for test plan derivation. */
  public static class DeriveTestPlanGenerator
      implements OutputGenerator<
          StepRequest<DeriveTestPlanInput>, StepResult<DeriveTestPlanOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DeriveTestPlanGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DeriveTestPlanOutput>> generate(
        @NotNull StepRequest<DeriveTestPlanInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DeriveTestPlanInput planInput = input.input();

      log.info("Deriving test plan for requirements: {}", planInput.requirementsId());

      String testPlanId = "testplan-" + UUID.randomUUID().toString().substring(0, 8);

      List<String> unitTestCases = new ArrayList<>();
      for (String req : planInput.functionalRequirements()) {
        unitTestCases.add("Unit test for: " + req);
      }

      List<String> integrationTestCases = new ArrayList<>();
      for (String contract : planInput.contracts()) {
        integrationTestCases.add("Integration test for: " + contract);
      }

      List<String> performanceTestScenarios = new ArrayList<>();
      List<String> securityTestScenarios = new ArrayList<>();
      for (String nfr : planInput.nonFunctionalRequirements()) {
        if (nfr.toLowerCase().contains("performance")) {
          performanceTestScenarios.add("Performance test for: " + nfr);
        }
        if (nfr.toLowerCase().contains("security")) {
          securityTestScenarios.add("Security test for: " + nfr);
        }
      }

      Map<String, String> testStrategy = new HashMap<>();
      testStrategy.put("unit", "JUnit 5 with Mockito");
      testStrategy.put("integration", "RestAssured with test containers");
      testStrategy.put("performance", "JMeter for load testing");
      testStrategy.put("security", "OWASP ZAP for security scanning");

      int estimatedTestCount =
          unitTestCases.size()
              + integrationTestCases.size()
              + performanceTestScenarios.size()
              + securityTestScenarios.size();

      DeriveTestPlanOutput output =
          new DeriveTestPlanOutput(
              planInput.requirementsId(),
              testPlanId,
              unitTestCases,
              integrationTestCases,
              performanceTestScenarios,
              securityTestScenarios,
              testStrategy,
              estimatedTestCount);

      return Promise.of(
          StepResult.success(
              output,
              Map.of(
                  "requirementsId",
                  planInput.requirementsId(),
                  "testPlanId",
                  testPlanId,
                  "testCount",
                  estimatedTestCount),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DeriveTestPlanInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DeriveTestPlanGenerator")
          .type("rule-based")
          .description("Derives comprehensive test plan from requirements")
          .version("1.0.0")
          .build();
    }
  }
}
