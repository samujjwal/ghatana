package com.ghatana.yappc.agents.testing;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialist agent for generating test code.
 *
 * @doc.type class
 * @doc.purpose Generates executable test code from test plan
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class GenerateTestsSpecialistAgent
    extends YAPPCAgentBase<GenerateTestsInput, GenerateTestsOutput> {

  private static final Logger log = LoggerFactory.getLogger(GenerateTestsSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public GenerateTestsSpecialistAgent(
      MemoryStore memoryStore, Map<String, OutputGenerator<?, ?>> generators) {
    super(
        "GenerateTestsSpecialistAgent",
        "testing.generateTests",
        new StepContract(
            "testing.generateTests",
            "#/definitions/GenerateTestsInput",
            "#/definitions/GenerateTestsOutput",
            List.of("testing", "generate", "tests"),
            Map.of("description", "Generates executable test code", "version", "1.0.0")),
        (OutputGenerator<StepRequest<GenerateTestsInput>, StepResult<GenerateTestsOutput>>)
            generators.get("testing.generateTests"),
        defaultEventPublisher());
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull GenerateTestsInput input) {
    if (input.testPlanId() == null || input.testPlanId().isEmpty()) {
      return ValidationResult.fail("Test plan ID cannot be empty");
    }
    if (input.testCases().isEmpty()) {
      return ValidationResult.fail("At least one test case required");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<GenerateTestsInput> perceive(
      @NotNull StepRequest<GenerateTestsInput> request, @NotNull AgentContext context) {
    log.info("Perceiving test generation for plan: {}", request.input().testPlanId());
    return request;
  }

  /** Rule-based generator for test code generation. */
  public static class GenerateTestsGenerator
      implements OutputGenerator<StepRequest<GenerateTestsInput>, StepResult<GenerateTestsOutput>> {

    private static final Logger log = LoggerFactory.getLogger(GenerateTestsGenerator.class);
    private static final Pattern CLASS_NAME_PATTERN =
        Pattern.compile("(?:class|interface|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private final TestSpecificationGenerator specificationGenerator;
    private final TestCodeGenerator codeGenerator;

    public GenerateTestsGenerator() {
      this(null, new TestCodeGenerator());
    }

    GenerateTestsGenerator(
        TestSpecificationGenerator specificationGenerator, TestCodeGenerator codeGenerator) {
      this.specificationGenerator = specificationGenerator;
      this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator");
    }

    @Override
    public @NotNull Promise<StepResult<GenerateTestsOutput>> generate(
        @NotNull StepRequest<GenerateTestsInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      GenerateTestsInput testsInput = input.input();

      log.info("Generating {} tests for plan {}", testsInput.testType(), testsInput.testPlanId());
      return resolveScenarios(testsInput)
          .then(
              scenarios -> {
                TestCodeGenerator.TestFramework framework = resolveFramework(testsInput);
                String className = deriveClassName(testsInput);
                return codeGenerator
                    .generateTestCode(className, testsInput.classSource(), scenarios, framework)
                    .map(artifact -> toOutput(testsInput, scenarios, artifact, start));
              });
    }

    private Promise<List<TestScenario>> resolveScenarios(GenerateTestsInput input) {
      if (specificationGenerator != null
          && input.classSource() != null
          && !input.classSource().isBlank()) {
        return specificationGenerator.generateSpecifications(
            new TestSpecificationRequest(
                deriveClassName(input), input.classSource(), input.requirements()));
      }
      return Promise.of(deriveScenariosFromCases(input));
    }

    private List<TestScenario> deriveScenariosFromCases(GenerateTestsInput input) {
      List<String> cases = input.testCases();
      if (cases.isEmpty()) {
        return List.of(
            new TestScenario(
                input.resolvedClassName() + " can be instantiated",
                TestScenario.ScenarioCategory.HAPPY_PATH,
                "a valid subject setup exists",
                "the subject is constructed",
                "the construction succeeds",
                List.of("instantiation")));
      }

      List<TestScenario> scenarios = new ArrayList<>();
      for (int index = 0; index < cases.size(); index++) {
        String testCase = cases.get(index);
        TestScenario.ScenarioCategory category = switch (index) {
          case 0 -> TestScenario.ScenarioCategory.HAPPY_PATH;
          case 1 -> TestScenario.ScenarioCategory.EDGE_CASE;
          default -> TestScenario.ScenarioCategory.BOUNDARY_VALUE;
        };
        scenarios.add(
            new TestScenario(
                testCase,
                category,
                "the preconditions for '" + testCase + "' are satisfied",
                "the test exercises the target behaviour",
                "the expected outcome for '" + testCase + "' is observed",
                List.of("generated", category.name().toLowerCase(Locale.ROOT))));
      }
      return List.copyOf(scenarios);
    }

    private StepResult<GenerateTestsOutput> toOutput(
        GenerateTestsInput input,
        List<TestScenario> scenarios,
        TestCodeGenerator.GeneratedTestArtifact artifact,
        Instant start) {
      GenerateTestsOutput output =
          new GenerateTestsOutput(
              input.testPlanId(),
              List.of(artifact.fileName()),
              scenarios.size(),
              countLines(artifact.sourceCode()),
              input.testType(),
              estimateCoverage(input.testType(), scenarios.size()),
            Map.of(artifact.fileName(), artifact.sourceCode() == null ? "" : artifact.sourceCode()),
              scenarios.stream().map(TestScenario::title).toList(),
              artifact.framework().name());

      return StepResult.success(
          output,
          Map.of(
              "testPlanId", input.testPlanId(),
              "fileCount", output.generatedTestFiles().size(),
              "totalTests", output.totalTests(),
              "framework", output.framework()),
          start,
          Instant.now());
    }

    private int countLines(String sourceCode) {
      if (sourceCode == null || sourceCode.isBlank()) {
        return 0;
      }
      return sourceCode.split("\\R", -1).length;
    }

    private TestCodeGenerator.TestFramework resolveFramework(GenerateTestsInput input) {
      String framework = input.testFramework() == null ? "" : input.testFramework().toLowerCase(Locale.ROOT);
      String language = input.targetLanguage() == null ? "" : input.targetLanguage().toLowerCase(Locale.ROOT);
      if (framework.contains("vitest") || language.contains("typescript") || language.contains("javascript")) {
        return TestCodeGenerator.TestFramework.VITEST;
      }
      return TestCodeGenerator.TestFramework.JUNIT5;
    }

    private String deriveClassName(GenerateTestsInput input) {
      if (input.className() != null && !input.className().isBlank()) {
        return input.className();
      }
      if (input.classSource() != null && !input.classSource().isBlank()) {
        Matcher matcher = CLASS_NAME_PATTERN.matcher(input.classSource());
        if (matcher.find()) {
          return matcher.group(1);
        }
      }
      return input.resolvedClassName();
    }

    private double estimateCoverage(String testType, int totalTests) {
      return switch (testType.toLowerCase(Locale.ROOT)) {
        case "unit" -> Math.min(95.0, 55.0 + (totalTests * 10.0));
        case "integration" -> Math.min(80.0, 40.0 + (totalTests * 8.0));
        case "performance", "security" -> Math.min(35.0, totalTests * 5.0);
        default -> Math.min(75.0, 30.0 + (totalTests * 10.0));
      };
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<GenerateTestsInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("GenerateTestsGenerator")
          .type("rule-based")
          .description("Generates executable test code from test plan")
          .version("1.0.0")
          .build();
    }
  }
}
