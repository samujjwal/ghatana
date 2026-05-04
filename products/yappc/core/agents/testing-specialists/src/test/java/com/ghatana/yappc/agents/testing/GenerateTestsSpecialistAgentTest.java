package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.ValidationResult;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GenerateTestsSpecialistAgent Tests")
class GenerateTestsSpecialistAgentTest extends EventloopTestBase {

  private InMemoryYAPPCAIService aiService;

  @BeforeEach
  void setUp() {
    aiService = new InMemoryYAPPCAIService();
  }

  @Test
  @DisplayName("generate tests generator uses specification and code generators")
  void generateTestsGeneratorUsesSpecificationAndCodeGenerators() {
    aiService.setReasonResponse(
        "SCENARIO: handles valid request\n"
            + "CATEGORY: HAPPY_PATH\n"
            + "GIVEN: valid input\n"
            + "WHEN: the subject runs\n"
            + "THEN: a result is returned\n"
            + "COVERAGE: main-flow");

    TestSpecificationGenerator specificationGenerator =
        new TestSpecificationGenerator(aiService);
    TestCodeGenerator codeGenerator = new TestCodeGenerator();
    GenerateTestsSpecialistAgent.GenerateTestsGenerator generator =
        new GenerateTestsSpecialistAgent.GenerateTestsGenerator(specificationGenerator, codeGenerator);

    GenerateTestsInput input =
        new GenerateTestsInput(
            "plan-1",
            List.of("primary path"),
            "unit",
            "java",
            "junit5",
            "SampleService",
            "public class SampleService { public SampleService() {} }",
            List.of("service must return data"));

    StepResult<GenerateTestsOutput> result =
    runPromise(() -> generator.generate(StepRequest.of("request-1", input), agentContext()));

    assertThat(result.output().generatedTestFiles()).containsExactly("SampleServiceTest.java");
    assertThat(result.output().implementedScenarios())
      .containsExactly(
        "handles valid request",
        "SampleService rejects invalid input",
        "SampleService respects boundary limits");
    assertThat(result.output().generatedSources().values().iterator().next()).contains("SampleServiceTest");
    assertThat(result.output().framework()).isEqualTo("JUNIT5");
  }

  @Test
  @DisplayName("generate tests generator derives fallback scenarios and vitest output")
  void generateTestsGeneratorDerivesFallbackScenariosAndVitestOutput() { 
    GenerateTestsSpecialistAgent.GenerateTestsGenerator generator =
        new GenerateTestsSpecialistAgent.GenerateTestsGenerator(); 

    GenerateTestsInput input =
        new GenerateTestsInput( 
            "plan-2",
            List.of("happy path", "reject invalid input", "honors limits"), 
            "unit",
            "typescript",
            "vitest");

    StepResult<GenerateTestsOutput> result =
    runPromise(() -> generator.generate(StepRequest.of("request-2", input), agentContext())); 

    assertThat(result.output().framework()).isEqualTo("VITEST");
    assertThat(result.output().totalTests()).isEqualTo(3); 
    assertThat(result.output().generatedTestFiles()).containsExactly("plan-2.test.ts");
  }

  @Test
  @DisplayName("generate tests input and output keep immutable copies and sensible defaults")
  void generateTestsInputAndOutputKeepImmutableCopiesAndDefaults() { 
    GenerateTestsInput input =
        new GenerateTestsInput("plan-3", List.of("case"), "unit", "java", "junit5");
    GenerateTestsInput classNamedInput =
      new GenerateTestsInput("", null, "unit", null, null, "NamedSubject", null, null); 
    GenerateTestsInput blankPlanInput =
      new GenerateTestsInput("", List.of("case"), "unit", null, null, "", null, null);
    GenerateTestsOutput output =
        new GenerateTestsOutput("plan-3", List.of("A.java"), 1, 10, "unit", 90.0);
    GenerateTestsOutput nullOutput =
      new GenerateTestsOutput("plan-4", null, 0, 0, "unit", 0.0, null, null, "junit5"); 

    assertThat(input.resolvedClassName()).isEqualTo("plan-3");
    assertThat(input.requirements()).isEmpty(); 
    assertThat(classNamedInput.testCases()).isEmpty(); 
    assertThat(classNamedInput.resolvedClassName()).isEqualTo("NamedSubject");
    assertThat(blankPlanInput.resolvedClassName()).isEqualTo("GeneratedSubject");
    assertThat(new GenerateTestsInput(null, List.of("case"), "unit", null, null).resolvedClassName())
      .isEqualTo("GeneratedSubject");
    assertThat(output.generatedSources()).isEmpty(); 
    assertThat(output.implementedScenarios()).isEmpty(); 
    assertThat(output.framework()).isEqualTo("unknown");
    assertThat(nullOutput.generatedTestFiles()).isEmpty(); 
    assertThat(nullOutput.generatedSources()).isEmpty(); 
    assertThat(nullOutput.implementedScenarios()).isEmpty(); 
    }

    @Test
    @DisplayName("agent exposes memory store validation and perceive behavior")
    void agentExposesMemoryStoreValidationAndPerceiveBehavior() { 
    MemoryStore memoryStore = new EventLogMemoryStore(); 
    TestableGenerateTestsAgent agent =
      new TestableGenerateTestsAgent( 
        memoryStore,
        Map.of("testing.generateTests", new GenerateTestsSpecialistAgent.GenerateTestsGenerator())); 

    ValidationResult missingPlan =
      agent.validateInput(new GenerateTestsInput("", List.of("case"), "unit", "java", "junit5"));
    ValidationResult nullPlan =
      agent.validateInput(new GenerateTestsInput(null, List.of("case"), "unit", "java", "junit5"));
    ValidationResult missingCases =
      agent.validateInput(new GenerateTestsInput("plan", List.of(), "unit", "java", "junit5")); 
    ValidationResult nullCases =
      agent.validateInput(new GenerateTestsInput("plan", null, "unit", "java", "junit5", null, null, null)); 
    ValidationResult valid =
      agent.validateInput(new GenerateTestsInput("plan", List.of("case"), "unit", "java", "junit5"));
    StepRequest<GenerateTestsInput> perceived =
      agent.perceiveForTest( 
        StepRequest.of("perceive-1", new GenerateTestsInput("plan", List.of("case"), "unit", "java", "junit5")),
        agentContext()); 

    assertThat(agent.memoryStoreForTest()).isSameAs(memoryStore); 
    assertThat(missingPlan.ok()).isFalse(); 
    assertThat(nullPlan.ok()).isFalse(); 
    assertThat(missingCases.ok()).isFalse(); 
    assertThat(nullCases.ok()).isFalse(); 
    assertThat(valid.ok()).isTrue(); 
    assertThat(perceived.input().testPlanId()).isEqualTo("plan");
    }

    @Test
    @DisplayName("generator covers fallback scenarios metadata and alternate coverage branches")
    void generatorCoversFallbackScenariosMetadataAndAlternateCoverageBranches() { 
      TestSpecificationGenerator specificationGenerator = new TestSpecificationGenerator(new InMemoryYAPPCAIService());
      TestCodeGenerator codeGenerator = new TestCodeGenerator(new InMemoryYAPPCAIService()); 

    GenerateTestsSpecialistAgent.GenerateTestsGenerator generator =
        new GenerateTestsSpecialistAgent.GenerateTestsGenerator(specificationGenerator, codeGenerator); 

    StepResult<GenerateTestsOutput> defaultResult =
      runPromise( 
        () -> 
          generator.generate( 
            StepRequest.of( 
              "request-default",
              new GenerateTestsInput("fallback-plan", List.of(), "custom", null, null)), 
            agentContext())); 
    StepResult<GenerateTestsOutput> integrationResult =
      runPromise( 
        () -> 
          generator.generate( 
            StepRequest.of( 
              "request-integration",
              new GenerateTestsInput( 
                "plan-integration",
                List.of("case"),
                "integration",
                "java",
                "junit5",
                null,
                "public class RegexNamed {}",
                List.of())), 
            agentContext())); 
    StepResult<GenerateTestsOutput> performanceResult =
      runPromise( 
        () -> 
          generator.generate( 
            StepRequest.of( 
              "request-performance",
              new GenerateTestsInput("plan-performance", List.of("case"), "performance", "java", "junit5")),
            agentContext())); 
    StepResult<GenerateTestsOutput> specificationResult =
      runPromise( 
        () -> 
          generator.generate( 
            StepRequest.of( 
              "request-specification",
              new GenerateTestsInput( 
                "plan-spec",
                List.of("ignored case"),
                "unit",
                "typescript",
                null,
                "",
                "   ",
                List.of("req"))),
            agentContext())); 
    StepResult<GenerateTestsOutput> noMatchResult =
      runPromise( 
        () -> 
          generator.generate( 
            StepRequest.of( 
              "request-nomatch",
              new GenerateTestsInput( 
                "plan-security",
                List.of("case"),
                "security",
                "java",
                "junit5",
                "",
                "package demo;",
                List.of())), 
            agentContext())); 
    StepResult<GenerateTestsOutput> javascriptResult =
      runPromise( 
        () -> 
          generator.generate( 
            StepRequest.of( 
              "request-javascript",
              new GenerateTestsInput("plan-js", List.of("case"), "unit", "javascript", null)),
            agentContext())); 

    assertThat(defaultResult.output().generatedTestFiles()).containsExactly("fallback-planTest.java");
    assertThat(defaultResult.output().totalTests()).isGreaterThanOrEqualTo(1); 
    assertThat(defaultResult.output().linesOfTestCode()).isGreaterThanOrEqualTo(1); 
    assertThat(defaultResult.output().estimatedCoverage()).isGreaterThan(0.0); 
    assertThat(defaultResult.output().implementedScenarios()) 
      .contains("fallback-plan can be instantiated");
    assertThat(integrationResult.output().generatedTestFiles()).containsExactly("RegexNamedTest.java");
    assertThat(integrationResult.output().implementedScenarios()).contains("RegexNamed handles primary workflow");
    assertThat(integrationResult.output().linesOfTestCode()).isGreaterThanOrEqualTo(1); 
    assertThat(integrationResult.output().estimatedCoverage()).isGreaterThan(0.0); 
    assertThat(performanceResult.output().estimatedCoverage()).isGreaterThan(0.0); 
    assertThat(specificationResult.output().framework()).isEqualTo("VITEST");
    assertThat(specificationResult.output().linesOfTestCode()).isGreaterThanOrEqualTo(1); 
    assertThat(specificationResult.output().implementedScenarios()).contains("ignored case");
    assertThat(noMatchResult.output().estimatedCoverage()).isGreaterThan(0.0); 
    assertThat(javascriptResult.output().framework()).isEqualTo("VITEST");
    assertThat(runPromise(() -> generator.estimateCost(StepRequest.of("cost", new GenerateTestsInput("plan", List.of("case"), "unit", "java", "junit5")), agentContext())))
      .isZero(); 
    assertThat(generator.getMetadata().getName()).isEqualTo("GenerateTestsGenerator");
  }

  private AgentContext agentContext() {
    return AgentContext.builder()
        .agentId("GenerateTestsSpecialistAgent")
        .turnId("turn-1")
        .tenantId("tenant-1")
        .userId("system")
        .sessionId("testing")
        .memoryStore(new EventLogMemoryStore())
        .config(Map.of())
        .remainingBudget(10.0)
        .build();
  }

  private static final class InMemoryYAPPCAIService implements YAPPCAIInterface {
    private String reasonResponse = null;

    void setReasonResponse(String response) {
      this.reasonResponse = response;
    }

    @Override
    public Promise<String> reason(String prompt, Map<String, Object> context) {
      return Promise.of(reasonResponse);
    }

    @Override
    public Promise<String> reason(String prompt) {
      return Promise.of(reasonResponse);
    }

    @Override
    public Promise<String> generateCode(String description) {
      return Promise.of("generated code");
    }

    @Override
    public Promise<String> generateCode(String description, Map<String, Object> context) {
      return Promise.of("generated code");
    }

    @Override
    public Promise<String> generateTests(String code) {
      return Promise.of("generated tests");
    }

    @Override
    public Promise<String> generateTests(String code, Map<String, Object> context) {
      return Promise.of("generated tests");
    }
  }

  private static final class TestableGenerateTestsAgent extends GenerateTestsSpecialistAgent {
    private TestableGenerateTestsAgent( 
        MemoryStore memoryStore,
        Map<String, com.ghatana.agent.framework.api.OutputGenerator<?, ?>> generators) {
      super(memoryStore, generators); 
    }

    private MemoryStore memoryStoreForTest() { 
      return getMemoryStore(); 
    }

    private StepRequest<GenerateTestsInput> perceiveForTest( 
        StepRequest<GenerateTestsInput> request, AgentContext context) {
      return perceive(request, context); 
    }
  }
}
