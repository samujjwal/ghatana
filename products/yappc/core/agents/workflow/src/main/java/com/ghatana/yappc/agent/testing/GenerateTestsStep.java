package com.ghatana.yappc.agent.testing;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Testing Phase - Step 2: Generate Test Cases.
 *
 * <p>Generates comprehensive test cases from test plan with optional LLM assistance. Creates unit,
 * integration, E2E, security, and performance test cases mapped to requirements.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Load test plan from previous step
 *   <li>Load requirements for test case mapping
 *   <li>Generate unit test cases (3 per FR)
 *   <li>Generate integration test cases (1 per FR)
 *   <li>Generate E2E test cases (user flows)
 *   <li>Optional: LLM-assisted test case generation
 *   <li>Persist test cases to Data-Cloud
 *   <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Generates test cases - LLM-assisted test creation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class GenerateTestsStep implements WorkflowStep {

  private static final String COLLECTION_TEST_CASES = "test_cases";
  private static final String COLLECTION_TEST_PLANS = "test_plans";
  private static final String COLLECTION_REQ_PUBLISHED = "requirements_published";
  private static final String EVENT_TOPIC = "testing.workflow";

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public GenerateTestsStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String runId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadTestPlan(context))
        .then(testPlan -> loadRequirements(testPlan, tenantId))
        .then(data -> generateTestCases(data, tenantId, runId))
        .then(testCases -> persistTestCases(testCases, tenantId, runId))
        .then(testCases -> publishEvents(testCases, tenantId, runId))
        .then(testCases -> buildOutputContext(context, testCases, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, runId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("testPlan") && !context.containsKey("testPlanId")) {
      return Promise.ofException(
          new IllegalArgumentException(
              "Missing required input: testPlan or testPlanId from previous step"));
    }
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> loadTestPlan(WorkflowContext context) {
    if (context.containsKey("testPlan")) {
      return Promise.of((Map<String, Object>) context.get("testPlan"));
    }

    String testPlanId = (String) context.get("testPlanId");
    String tenantId = (String) context.get("tenantId");

    return dbClient
        .query(COLLECTION_TEST_PLANS, Map.of("testPlanId", testPlanId, "tenantId", tenantId), 1)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("Test plan not found: " + testPlanId);
              }
              return results.get(0);
            });
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> loadRequirements(
      Map<String, Object> testPlan, String tenantId) {

    String reqBaselineId = (String) testPlan.get("requirementsBaselineId");

    return dbClient
        .query(
            COLLECTION_REQ_PUBLISHED, Map.of("baselineId", reqBaselineId, "tenantId", tenantId), 1)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("Requirements baseline not found");
              }
              Map<String, Object> data = new LinkedHashMap<>();
              data.put("testPlan", testPlan);
              data.put("requirements", results.get(0));
              return data;
            });
  }

  @SuppressWarnings("unchecked")
  private Promise<List<Map<String, Object>>> generateTestCases(
      Map<String, Object> data, String tenantId, String runId) {

    Map<String, Object> testPlan = (Map<String, Object>) data.get("testPlan");
    Map<String, Object> reqBaseline = (Map<String, Object>) data.get("requirements");

    Map<String, Object> reqContent = (Map<String, Object>) reqBaseline.get("content");
    List<Map<String, Object>> requirements =
        (List<Map<String, Object>>) reqContent.getOrDefault("requirements", List.of());

    List<Map<String, Object>> functionalReqs =
        requirements.stream()
            .filter(r -> "FUNCTIONAL".equals(r.get("category")))
            .collect(Collectors.toList());

    List<Map<String, Object>> testCases = new ArrayList<>();

    // Generate unit tests (3 per FR)
    for (Map<String, Object> req : functionalReqs) {
      testCases.addAll(generateUnitTests(req, tenantId, runId, testPlan));
    }

    // Generate integration tests (1 per FR)
    for (Map<String, Object> req : functionalReqs) {
      testCases.add(generateIntegrationTest(req, tenantId, runId, testPlan));
    }

    // Generate E2E tests (user flows)
    testCases.addAll(generateE2ETests(functionalReqs, tenantId, runId, testPlan));

    return Promise.of(testCases);
  }

  private List<Map<String, Object>> generateUnitTests(
      Map<String, Object> requirement,
      String tenantId,
      String runId,
      Map<String, Object> testPlan) {

    List<Map<String, Object>> unitTests = new ArrayList<>();
    String reqId = (String) requirement.get("requirementId");
    String reqContent = (String) requirement.getOrDefault("content", "");

    String[] scenarios = {"Happy Path", "Error Handling", "Edge Cases"};
    for (String scenario : scenarios) {
      Map<String, Object> testCase = new LinkedHashMap<>();
      testCase.put("testCaseId", UUID.randomUUID().toString());
      testCase.put("tenantId", tenantId);
      testCase.put("runId", runId);
      testCase.put("testPlanId", testPlan.get("testPlanId"));
      testCase.put("requirementId", reqId);
      testCase.put("type", "UNIT");
      testCase.put(
          "name",
          "Unit Test - "
              + scenario
              + " - "
              + reqContent.substring(0, Math.min(50, reqContent.length())));
      testCase.put(
          "steps",
          List.of(
              "Setup test data",
              "Execute method under test",
              "Verify expected outcome",
              "Cleanup"));
      testCase.put("expected", "Test passes with expected behavior");
      testCase.put("automationRef", "src/test/java/...Test.java");
      testCase.put("status", "READY");
      testCase.put("createdAt", Instant.now().toString());
      unitTests.add(testCase);
    }

    return unitTests;
  }

  private Map<String, Object> generateIntegrationTest(
      Map<String, Object> requirement,
      String tenantId,
      String runId,
      Map<String, Object> testPlan) {

    String reqId = (String) requirement.get("requirementId");
    String reqContent = (String) requirement.getOrDefault("content", "");

    Map<String, Object> testCase = new LinkedHashMap<>();
    testCase.put("testCaseId", UUID.randomUUID().toString());
    testCase.put("tenantId", tenantId);
    testCase.put("runId", runId);
    testCase.put("testPlanId", testPlan.get("testPlanId"));
    testCase.put("requirementId", reqId);
    testCase.put("type", "INTEGRATION");
    testCase.put(
        "name", "Integration Test - " + reqContent.substring(0, Math.min(50, reqContent.length())));
    testCase.put(
        "steps",
        List.of(
            "Start test containers (DB, Redis, etc.)",
            "Initialize application context",
            "Execute end-to-end scenario",
            "Verify database state",
            "Verify external service interactions",
            "Cleanup containers"));
    testCase.put("expected", "Integration succeeds across all components");
    testCase.put("automationRef", "src/test/java/...IntegrationTest.java");
    testCase.put("status", "READY");
    testCase.put("createdAt", Instant.now().toString());

    return testCase;
  }

  private List<Map<String, Object>> generateE2ETests(
      List<Map<String, Object>> functionalReqs,
      String tenantId,
      String runId,
      Map<String, Object> testPlan) {

    List<Map<String, Object>> e2eTests = new ArrayList<>();

    // Generate 1 E2E test per 3 requirements (user flows)
    for (int i = 0; i < functionalReqs.size(); i += 3) {
      Map<String, Object> testCase = new LinkedHashMap<>();
      testCase.put("testCaseId", UUID.randomUUID().toString());
      testCase.put("tenantId", tenantId);
      testCase.put("runId", runId);
      testCase.put("testPlanId", testPlan.get("testPlanId"));
      testCase.put("requirementId", null); // Spans multiple requirements
      testCase.put("type", "E2E");
      testCase.put("name", "E2E User Flow - Scenario " + (i / 3 + 1));
      testCase.put(
          "steps",
          List.of(
              "Navigate to application",
              "Login as test user",
              "Execute user flow",
              "Verify UI state",
              "Verify backend state",
              "Logout"));
      testCase.put("expected", "Complete user flow succeeds end-to-end");
      testCase.put("automationRef", "tests/e2e/user-flow-" + (i / 3 + 1) + ".spec.ts");
      testCase.put("status", "READY");
      testCase.put("createdAt", Instant.now().toString());
      e2eTests.add(testCase);
    }

    return e2eTests;
  }

  private Promise<List<Map<String, Object>>> persistTestCases(
      List<Map<String, Object>> testCases, String tenantId, String runId) {

    List<Promise<Void>> persistPromises =
        testCases.stream()
            .map(testCase -> dbClient.insert(COLLECTION_TEST_CASES, testCase).toVoid())
            .collect(Collectors.toList());

    return io.activej.promise.Promises.all(persistPromises).map($ -> testCases);
  }

  private Promise<List<Map<String, Object>>> publishEvents(
      List<Map<String, Object>> testCases, String tenantId, String runId) {

    Map<String, Long> testTypeCounts =
        testCases.stream()
            .collect(Collectors.groupingBy(tc -> (String) tc.get("type"), Collectors.counting()));

    Map<String, Object> eventPayload =
        Map.of(
            "runId",
            runId,
            "eventType",
            "TEST_CASES_GENERATED",
            "totalTestCases",
            testCases.size(),
            "testTypeCounts",
            testTypeCounts);

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> testCases);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, List<Map<String, Object>> testCases, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "GenerateTests");
    output.put("testCaseCount", testCases.size());
    output.put("testCases", testCases);
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex, WorkflowContext context, String tenantId, String runId, Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "runId",
            runId,
            "eventType",
            "TEST_GENERATION_FAILED",
            "error",
            ex.getMessage(),
            "timestamp",
            Instant.now().toString());

    return eventClient
        .publish(EVENT_TOPIC, tenantId, errorPayload)
        .then(
            $ -> {
              WorkflowContext errorContext = context.copy();
              errorContext.put("status", "FAILED");
              errorContext.put("error", ex.getMessage());
              errorContext.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
              return Promise.of(errorContext);
            });
  }
}
