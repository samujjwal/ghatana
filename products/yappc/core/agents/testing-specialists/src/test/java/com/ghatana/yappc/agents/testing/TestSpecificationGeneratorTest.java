package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIInterface;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @doc.type class
 * @doc.purpose Verifies scenario-first test specification generation uses AI context and fills missing categories
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TestSpecificationGenerator Tests")
class TestSpecificationGeneratorTest extends EventloopTestBase {

  private InMemoryYAPPCAIService aiService;
  private TestSpecificationGenerator generator;

  @BeforeEach
  void setUp() {
    aiService = new InMemoryYAPPCAIService();
    generator = new TestSpecificationGenerator(aiService);
  }

  @Test
  @DisplayName("generateSpecifications parses structured AI scenarios and passes source context")
  void generateSpecificationsParsesStructuredResponse() {
    aiService.setReasonResponse(
        "SCENARIO: returns result for valid request\n"
            + "CATEGORY: HAPPY_PATH\n"
            + "GIVEN: a valid request\n"
            + "WHEN: the service executes\n"
            + "THEN: the result is returned\n"
            + "COVERAGE: main-flow,success\n"
            + "---\n"
            + "SCENARIO: rejects invalid payload\n"
            + "CATEGORY: EDGE_CASE\n"
            + "GIVEN: invalid input\n"
            + "WHEN: validation runs\n"
            + "THEN: an explicit error is returned\n"
            + "COVERAGE: validation,error-handling\n"
            + "---\n"
            + "SCENARIO: supports numeric limits\n"
            + "CATEGORY: BOUNDARY_VALUE\n"
            + "GIVEN: max integer input\n"
            + "WHEN: the calculator executes\n"
            + "THEN: the value is handled correctly\n"
            + "COVERAGE: boundary,numeric");

    List<TestScenario> scenarios = runPromise(() -> generator.generateSpecifications(
        new TestSpecificationRequest(
            "CalculatorService",
            "class CalculatorService { int add(int left, int right) { return left + right; } }",
            List.of("The calculator must add two numbers correctly"))));

    assertThat(scenarios).hasSize(3);
    assertThat(scenarios).extracting(TestScenario::category)
        .containsExactly(
            TestScenario.ScenarioCategory.HAPPY_PATH,
            TestScenario.ScenarioCategory.EDGE_CASE,
            TestScenario.ScenarioCategory.BOUNDARY_VALUE);

    assertThat(aiService.getLastReasonContext())
        .containsEntry("className", "CalculatorService")
        .containsKey("requirements")
        .containsKey("classSource")
        .containsKey("expectedCategories");
  }

  @Test
  @DisplayName("generateSpecifications backfills missing edge and boundary scenarios for simple classes")
  void generateSpecificationsBackfillsMissingCoverageCategories() {
    aiService.setReasonResponse(
        "SCENARIO: creates a customer for valid input\n"
            + "CATEGORY: HAPPY_PATH\n"
            + "GIVEN: a valid customer payload\n"
            + "WHEN: the handler stores the customer\n"
            + "THEN: the new customer id is returned\n"
            + "COVERAGE: create-flow");

    List<TestScenario> scenarios = runPromise(() -> generator.generateSpecifications(
        new TestSpecificationRequest(
            "CustomerHandler",
            "class CustomerHandler { String create(String name) { return name.trim(); } }",
            List.of("The handler must create a customer record"))));

    assertThat(scenarios).hasSize(3);
    assertThat(scenarios).extracting(TestScenario::category)
        .containsExactly(
            TestScenario.ScenarioCategory.HAPPY_PATH,
            TestScenario.ScenarioCategory.EDGE_CASE,
            TestScenario.ScenarioCategory.BOUNDARY_VALUE);
    assertThat(scenarios.get(2).givenClause()).contains("string lengths");
  }

  @Test
  @DisplayName("generateSpecifications chooses collection-oriented boundary scenarios for collection-heavy classes")
  void generateSpecificationsChoosesCollectionBoundariesForComplexClasses() {
    aiService.setReasonResponse("");

    List<TestScenario> scenarios = runPromise(() -> generator.generateSpecifications(
        new TestSpecificationRequest(
            "BatchProcessor",
            "class BatchProcessor { void run(List<String> items) { for (String item : items) {} } }",
            List.of("The processor must support large batches"))));

    assertThat(scenarios).hasSize(3);
    assertThat(scenarios.get(2).category()).isEqualTo(TestScenario.ScenarioCategory.BOUNDARY_VALUE);
    assertThat(scenarios.get(2).givenClause()).contains("collection size");
    assertThat(scenarios.get(0).coverageTargets()).contains("main-flow", "requirement");
  }

  private static final class InMemoryYAPPCAIService implements YAPPCAIInterface {
    private String reasonResponse = null;
    private Map<String, Object> lastReasonContext = null;

    void setReasonResponse(String response) {
      this.reasonResponse = response;
    }

    Map<String, Object> getLastReasonContext() {
      return lastReasonContext;
    }

    @Override
    public Promise<String> reason(String prompt, Map<String, Object> context) {
      this.lastReasonContext = context;
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
}
