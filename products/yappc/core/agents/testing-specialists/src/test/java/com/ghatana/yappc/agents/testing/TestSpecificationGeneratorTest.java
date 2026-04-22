package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @doc.type class
 * @doc.purpose Verifies scenario-first test specification generation uses AI context and fills missing categories
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TestSpecificationGenerator Tests [GH-90000]")
class TestSpecificationGeneratorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  private TestSpecificationGenerator generator;

  @BeforeEach
  void setUp() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
    generator = new TestSpecificationGenerator(aiService); // GH-90000
  }

  @Test
  @DisplayName("generateSpecifications parses structured AI scenarios and passes source context [GH-90000]")
  @SuppressWarnings("unchecked [GH-90000]")
  void generateSpecificationsParsesStructuredResponse() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of( // GH-90000
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
            + "COVERAGE: boundary,numeric"));

    List<TestScenario> scenarios = runPromise(() -> generator.generateSpecifications( // GH-90000
        new TestSpecificationRequest( // GH-90000
            "CalculatorService",
            "class CalculatorService { int add(int left, int right) { return left + right; } }", // GH-90000
            List.of("The calculator must add two numbers correctly [GH-90000]"))));

    assertThat(scenarios).hasSize(3); // GH-90000
    assertThat(scenarios).extracting(TestScenario::category) // GH-90000
        .containsExactly( // GH-90000
            TestScenario.ScenarioCategory.HAPPY_PATH,
            TestScenario.ScenarioCategory.EDGE_CASE,
            TestScenario.ScenarioCategory.BOUNDARY_VALUE);

    ArgumentCaptor<Map<String, Object>> contextCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class); // GH-90000
    verify(aiService).reason(anyString(), contextCaptor.capture()); // GH-90000
    assertThat(contextCaptor.getValue()) // GH-90000
        .containsEntry("className", "CalculatorService") // GH-90000
        .containsKey("requirements [GH-90000]")
        .containsKey("classSource [GH-90000]")
        .containsKey("expectedCategories [GH-90000]");
  }

  @Test
  @DisplayName("generateSpecifications backfills missing edge and boundary scenarios for simple classes [GH-90000]")
  void generateSpecificationsBackfillsMissingCoverageCategories() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of( // GH-90000
        "SCENARIO: creates a customer for valid input\n"
            + "CATEGORY: HAPPY_PATH\n"
            + "GIVEN: a valid customer payload\n"
            + "WHEN: the handler stores the customer\n"
            + "THEN: the new customer id is returned\n"
            + "COVERAGE: create-flow"));

    List<TestScenario> scenarios = runPromise(() -> generator.generateSpecifications( // GH-90000
        new TestSpecificationRequest( // GH-90000
            "CustomerHandler",
            "class CustomerHandler { String create(String name) { return name.trim(); } }", // GH-90000
            List.of("The handler must create a customer record [GH-90000]"))));

    assertThat(scenarios).hasSize(3); // GH-90000
    assertThat(scenarios).extracting(TestScenario::category) // GH-90000
        .containsExactly( // GH-90000
            TestScenario.ScenarioCategory.HAPPY_PATH,
            TestScenario.ScenarioCategory.EDGE_CASE,
            TestScenario.ScenarioCategory.BOUNDARY_VALUE);
    assertThat(scenarios.get(2).givenClause()).contains("string lengths [GH-90000]");
  }

  @Test
  @DisplayName("generateSpecifications chooses collection-oriented boundary scenarios for collection-heavy classes [GH-90000]")
  void generateSpecificationsChoosesCollectionBoundariesForComplexClasses() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" [GH-90000]"));

    List<TestScenario> scenarios = runPromise(() -> generator.generateSpecifications( // GH-90000
        new TestSpecificationRequest( // GH-90000
            "BatchProcessor",
            "class BatchProcessor { void run(List<String> items) { for (String item : items) {} } }", // GH-90000
            List.of("The processor must support large batches [GH-90000]"))));

    assertThat(scenarios).hasSize(3); // GH-90000
    assertThat(scenarios.get(2).category()).isEqualTo(TestScenario.ScenarioCategory.BOUNDARY_VALUE); // GH-90000
    assertThat(scenarios.get(2).givenClause()).contains("collection size [GH-90000]");
    assertThat(scenarios.get(0).coverageTargets()).contains("main-flow", "requirement"); // GH-90000
  }
}
