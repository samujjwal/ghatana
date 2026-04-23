package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.ValidationResult;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@DisplayName("GenerateTestsSpecialistAgent Tests")
class GenerateTestsSpecialistAgentTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  GenerateTestsSpecialistAgentTest() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
  }

  @Test
  @DisplayName("generate tests generator uses specification and code generators")
  void generateTestsGeneratorUsesSpecificationAndCodeGenerators() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of( // GH-90000
            "SCENARIO: handles valid request\n"
                + "CATEGORY: HAPPY_PATH\n"
                + "GIVEN: valid input\n"
                + "WHEN: the subject runs\n"
                + "THEN: a result is returned\n"
                + "COVERAGE: main-flow"));

    TestSpecificationGenerator specificationGenerator =
        new TestSpecificationGenerator(aiService); // GH-90000
    TestCodeGenerator codeGenerator = new TestCodeGenerator(); // GH-90000
    GenerateTestsSpecialistAgent.GenerateTestsGenerator generator =
        new GenerateTestsSpecialistAgent.GenerateTestsGenerator(specificationGenerator, codeGenerator); // GH-90000

    GenerateTestsInput input =
        new GenerateTestsInput( // GH-90000
            "plan-1",
            List.of("primary path"),
            "unit",
            "java",
            "junit5",
            "SampleService",
            "public class SampleService { public SampleService() {} }", // GH-90000
            List.of("service must return data"));

    StepResult<GenerateTestsOutput> result =
    runPromise(() -> generator.generate(StepRequest.of("request-1", input), agentContext())); // GH-90000

    assertThat(result.output().generatedTestFiles()).containsExactly("SampleServiceTest.java");
    assertThat(result.output().implementedScenarios()) // GH-90000
      .containsExactly( // GH-90000
        "handles valid request",
        "SampleService rejects invalid input",
        "SampleService respects boundary limits");
    assertThat(result.output().generatedSources().values().iterator().next()).contains("SampleServiceTest");
    assertThat(result.output().framework()).isEqualTo("JUNIT5");
  }

  @Test
  @DisplayName("generate tests generator derives fallback scenarios and vitest output")
  void generateTestsGeneratorDerivesFallbackScenariosAndVitestOutput() { // GH-90000
    GenerateTestsSpecialistAgent.GenerateTestsGenerator generator =
        new GenerateTestsSpecialistAgent.GenerateTestsGenerator(); // GH-90000

    GenerateTestsInput input =
        new GenerateTestsInput( // GH-90000
            "plan-2",
            List.of("happy path", "reject invalid input", "honors limits"), // GH-90000
            "unit",
            "typescript",
            "vitest");

    StepResult<GenerateTestsOutput> result =
    runPromise(() -> generator.generate(StepRequest.of("request-2", input), agentContext())); // GH-90000

    assertThat(result.output().framework()).isEqualTo("VITEST");
    assertThat(result.output().totalTests()).isEqualTo(3); // GH-90000
    assertThat(result.output().generatedTestFiles()).containsExactly("plan-2.test.ts");
  }

  @Test
  @DisplayName("generate tests input and output keep immutable copies and sensible defaults")
  void generateTestsInputAndOutputKeepImmutableCopiesAndDefaults() { // GH-90000
    GenerateTestsInput input =
        new GenerateTestsInput("plan-3", List.of("case"), "unit", "java", "junit5");
    GenerateTestsInput classNamedInput =
      new GenerateTestsInput("", null, "unit", null, null, "NamedSubject", null, null); // GH-90000
    GenerateTestsInput blankPlanInput =
      new GenerateTestsInput("", List.of("case"), "unit", null, null, "", null, null);
    GenerateTestsOutput output =
        new GenerateTestsOutput("plan-3", List.of("A.java"), 1, 10, "unit", 90.0);
    GenerateTestsOutput nullOutput =
      new GenerateTestsOutput("plan-4", null, 0, 0, "unit", 0.0, null, null, "junit5"); // GH-90000

    assertThat(input.resolvedClassName()).isEqualTo("plan-3");
    assertThat(input.requirements()).isEmpty(); // GH-90000
    assertThat(classNamedInput.testCases()).isEmpty(); // GH-90000
    assertThat(classNamedInput.resolvedClassName()).isEqualTo("NamedSubject");
    assertThat(blankPlanInput.resolvedClassName()).isEqualTo("GeneratedSubject");
    assertThat(new GenerateTestsInput(null, List.of("case"), "unit", null, null).resolvedClassName())
      .isEqualTo("GeneratedSubject");
    assertThat(output.generatedSources()).isEmpty(); // GH-90000
    assertThat(output.implementedScenarios()).isEmpty(); // GH-90000
    assertThat(output.framework()).isEqualTo("unknown");
    assertThat(nullOutput.generatedTestFiles()).isEmpty(); // GH-90000
    assertThat(nullOutput.generatedSources()).isEmpty(); // GH-90000
    assertThat(nullOutput.implementedScenarios()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("agent exposes memory store validation and perceive behavior")
    void agentExposesMemoryStoreValidationAndPerceiveBehavior() { // GH-90000
    MemoryStore memoryStore = new EventLogMemoryStore(); // GH-90000
    TestableGenerateTestsAgent agent =
      new TestableGenerateTestsAgent( // GH-90000
        memoryStore,
        Map.of("testing.generateTests", new GenerateTestsSpecialistAgent.GenerateTestsGenerator())); // GH-90000

    ValidationResult missingPlan =
      agent.validateInput(new GenerateTestsInput("", List.of("case"), "unit", "java", "junit5"));
    ValidationResult nullPlan =
      agent.validateInput(new GenerateTestsInput(null, List.of("case"), "unit", "java", "junit5"));
    ValidationResult missingCases =
      agent.validateInput(new GenerateTestsInput("plan", List.of(), "unit", "java", "junit5")); // GH-90000
    ValidationResult nullCases =
      agent.validateInput(new GenerateTestsInput("plan", null, "unit", "java", "junit5", null, null, null)); // GH-90000
    ValidationResult valid =
      agent.validateInput(new GenerateTestsInput("plan", List.of("case"), "unit", "java", "junit5"));
    StepRequest<GenerateTestsInput> perceived =
      agent.perceiveForTest( // GH-90000
        StepRequest.of("perceive-1", new GenerateTestsInput("plan", List.of("case"), "unit", "java", "junit5")),
        agentContext()); // GH-90000

    assertThat(agent.memoryStoreForTest()).isSameAs(memoryStore); // GH-90000
    assertThat(missingPlan.ok()).isFalse(); // GH-90000
    assertThat(nullPlan.ok()).isFalse(); // GH-90000
    assertThat(missingCases.ok()).isFalse(); // GH-90000
    assertThat(nullCases.ok()).isFalse(); // GH-90000
    assertThat(valid.ok()).isTrue(); // GH-90000
    assertThat(perceived.input().testPlanId()).isEqualTo("plan");
    }

    @Test
    @DisplayName("generator covers fallback scenarios metadata and alternate coverage branches")
    void generatorCoversFallbackScenariosMetadataAndAlternateCoverageBranches() { // GH-90000
      TestSpecificationGenerator specificationGenerator = mock(TestSpecificationGenerator.class); // GH-90000
    TestCodeGenerator codeGenerator = mock(TestCodeGenerator.class); // GH-90000
      when(specificationGenerator.generateSpecifications(any())) // GH-90000
        .thenReturn( // GH-90000
          Promise.of( // GH-90000
            List.of( // GH-90000
              new TestScenario( // GH-90000
                "generated from spec",
                TestScenario.ScenarioCategory.HAPPY_PATH,
                "source exists",
                "scenario generation runs",
                "spec-backed flow is used",
                List.of("spec")))));
    when(codeGenerator.generateTestCode(anyString(), any(), any(), any())) // GH-90000
      .thenReturn(Promise.of(new TestCodeGenerator.GeneratedTestArtifact("DerivedTest.java", "", TestCodeGenerator.TestFramework.JUNIT5))) // GH-90000
      .thenReturn(Promise.of(new TestCodeGenerator.GeneratedTestArtifact("RegexTest.java", "line1\nline2", TestCodeGenerator.TestFramework.JUNIT5))) // GH-90000
        .thenReturn(Promise.of(new TestCodeGenerator.GeneratedTestArtifact("PerfTest.java", "line1", TestCodeGenerator.TestFramework.JUNIT5))) // GH-90000
        .thenReturn(Promise.of(new TestCodeGenerator.GeneratedTestArtifact("SpecTest.ts", null, TestCodeGenerator.TestFramework.VITEST))) // GH-90000
        .thenReturn(Promise.of(new TestCodeGenerator.GeneratedTestArtifact("NoMatchTest.java", "line", TestCodeGenerator.TestFramework.JUNIT5))); // GH-90000

    GenerateTestsSpecialistAgent.GenerateTestsGenerator generator =
        new GenerateTestsSpecialistAgent.GenerateTestsGenerator(specificationGenerator, codeGenerator); // GH-90000

    StepResult<GenerateTestsOutput> defaultResult =
      runPromise( // GH-90000
        () -> // GH-90000
          generator.generate( // GH-90000
            StepRequest.of( // GH-90000
              "request-default",
              new GenerateTestsInput("fallback-plan", List.of(), "custom", null, null)), // GH-90000
            agentContext())); // GH-90000
    StepResult<GenerateTestsOutput> integrationResult =
      runPromise( // GH-90000
        () -> // GH-90000
          generator.generate( // GH-90000
            StepRequest.of( // GH-90000
              "request-integration",
              new GenerateTestsInput( // GH-90000
                "plan-integration",
                List.of("case"),
                "integration",
                "java",
                "junit5",
                null,
                "public class RegexNamed {}",
                List.of())), // GH-90000
            agentContext())); // GH-90000
    StepResult<GenerateTestsOutput> performanceResult =
      runPromise( // GH-90000
        () -> // GH-90000
          generator.generate( // GH-90000
            StepRequest.of( // GH-90000
              "request-performance",
              new GenerateTestsInput("plan-performance", List.of("case"), "performance", "java", "junit5")),
            agentContext())); // GH-90000
    StepResult<GenerateTestsOutput> specificationResult =
      runPromise( // GH-90000
        () -> // GH-90000
          generator.generate( // GH-90000
            StepRequest.of( // GH-90000
              "request-specification",
              new GenerateTestsInput( // GH-90000
                "plan-spec",
                List.of("ignored case"),
                "unit",
                "typescript",
                null,
                "",
                "   ",
                List.of("req"))),
            agentContext())); // GH-90000
    StepResult<GenerateTestsOutput> noMatchResult =
      runPromise( // GH-90000
        () -> // GH-90000
          generator.generate( // GH-90000
            StepRequest.of( // GH-90000
              "request-nomatch",
              new GenerateTestsInput( // GH-90000
                "plan-security",
                List.of("case"),
                "security",
                "java",
                "junit5",
                "",
                "package demo;",
                List.of())), // GH-90000
            agentContext())); // GH-90000
    StepResult<GenerateTestsOutput> javascriptResult =
      runPromise( // GH-90000
        () -> // GH-90000
          generator.generate( // GH-90000
            StepRequest.of( // GH-90000
              "request-javascript",
              new GenerateTestsInput("plan-js", List.of("case"), "unit", "javascript", null)),
            agentContext())); // GH-90000

    assertThat(defaultResult.output().generatedTestFiles()).containsExactly("DerivedTest.java");
    assertThat(defaultResult.output().totalTests()).isEqualTo(1); // GH-90000
    assertThat(defaultResult.output().linesOfTestCode()).isZero(); // GH-90000
    assertThat(defaultResult.output().estimatedCoverage()).isEqualTo(40.0); // GH-90000
    assertThat(defaultResult.output().implementedScenarios()) // GH-90000
      .contains("fallback-plan can be instantiated");
    assertThat(integrationResult.output().generatedTestFiles()).containsExactly("RegexTest.java");
    assertThat(integrationResult.output().implementedScenarios()).contains("generated from spec");
    assertThat(integrationResult.output().linesOfTestCode()).isEqualTo(2); // GH-90000
    assertThat(integrationResult.output().estimatedCoverage()).isEqualTo(48.0); // GH-90000
    assertThat(performanceResult.output().estimatedCoverage()).isEqualTo(5.0); // GH-90000
    assertThat(specificationResult.output().framework()).isEqualTo("VITEST");
    assertThat(specificationResult.output().linesOfTestCode()).isZero(); // GH-90000
    assertThat(specificationResult.output().implementedScenarios()).contains("ignored case");
    assertThat(noMatchResult.output().estimatedCoverage()).isEqualTo(5.0); // GH-90000
    assertThat(javascriptResult.output().framework()).isEqualTo("JUNIT5");
    assertThat(runPromise(() -> generator.estimateCost(StepRequest.of("cost", new GenerateTestsInput("plan", List.of("case"), "unit", "java", "junit5")), agentContext())))
      .isZero(); // GH-90000
    assertThat(generator.getMetadata().getName()).isEqualTo("GenerateTestsGenerator");
  }

  private AgentContext agentContext() { // GH-90000
    return AgentContext.builder() // GH-90000
        .agentId("GenerateTestsSpecialistAgent")
        .turnId("turn-1")
        .tenantId("tenant-1")
        .userId("system")
        .sessionId("testing")
        .memoryStore(new EventLogMemoryStore()) // GH-90000
        .config(Map.of()) // GH-90000
        .remainingBudget(10.0) // GH-90000
        .build(); // GH-90000
  }

  private static final class TestableGenerateTestsAgent extends GenerateTestsSpecialistAgent {
    private TestableGenerateTestsAgent( // GH-90000
        MemoryStore memoryStore,
        Map<String, com.ghatana.agent.framework.api.OutputGenerator<?, ?>> generators) {
      super(memoryStore, generators); // GH-90000
    }

    private MemoryStore memoryStoreForTest() { // GH-90000
      return getMemoryStore(); // GH-90000
    }

    private StepRequest<GenerateTestsInput> perceiveForTest( // GH-90000
        StepRequest<GenerateTestsInput> request, AgentContext context) {
      return perceive(request, context); // GH-90000
    }
  }
}
