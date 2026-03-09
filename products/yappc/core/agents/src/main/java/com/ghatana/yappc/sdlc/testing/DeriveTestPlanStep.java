package com.ghatana.yappc.sdlc.testing;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Testing Phase - Step 1: Derive Test Plan.
 *
 * <p>Derives comprehensive test plan from requirements baseline and implementation artifacts.
 * Analyzes functional/non-functional requirements, maps to test types (unit, integration, E2E,
 * security, performance), and creates test scope with NFR targets.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Load requirements baseline and implementation baseline
 *   <li>Extract functional requirements (FR) → unit/integration tests
 *   <li>Extract non-functional requirements (NFR) → performance/security tests
 *   <li>Map components to test scopes
 *   <li>Define NFR targets (latency, throughput, availability)
 *   <li>Persist test plan to Data-Cloud
 *   <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Derives test plan from requirements - test strategy and scope
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DeriveTestPlanStep implements WorkflowStep {

  private static final String COLLECTION_TEST_PLANS = "test_plans";
  private static final String COLLECTION_REQ_PUBLISHED = "requirements_published";
  private static final String COLLECTION_IMPL_PUBLISHED = "implementation_published";
  private static final String EVENT_TOPIC = "testing.workflow";

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public DeriveTestPlanStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "testing.derive_test_plan";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String runId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadBaselines(context))
        .then(baselines -> deriveTestPlan(baselines, tenantId, runId))
        .then(testPlan -> persistTestPlan(testPlan, tenantId, runId))
        .then(testPlan -> publishEvents(testPlan, tenantId, runId))
        .then(testPlan -> buildOutputContext(context, testPlan, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, runId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("requirementsBaselineId")) {
      return Promise.ofException(
          new IllegalArgumentException("Missing required input: requirementsBaselineId"));
    }
    if (!context.containsKey("implementationBaselineId")) {
      return Promise.ofException(
          new IllegalArgumentException("Missing required input: implementationBaselineId"));
    }
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  private Promise<Map<String, Object>> loadBaselines(WorkflowContext context) {
    String reqBaselineId = (String) context.get("requirementsBaselineId");
    String implBaselineId = (String) context.get("implementationBaselineId");
    String tenantId = (String) context.get("tenantId");

    Map<String, Object> baselines = new LinkedHashMap<>();

    return dbClient
        .query(
            COLLECTION_REQ_PUBLISHED, Map.of("baselineId", reqBaselineId, "tenantId", tenantId), 1)
        .then(
            reqResults -> {
              if (reqResults.isEmpty()) {
                return Promise.ofException(
                    new IllegalStateException("Requirements baseline not found: " + reqBaselineId));
              }
              baselines.put("requirements", reqResults.get(0));
              return dbClient.query(
                  COLLECTION_IMPL_PUBLISHED,
                  Map.of("baselineId", implBaselineId, "tenantId", tenantId),
                  1);
            })
        .then(
            implResults -> {
              if (implResults.isEmpty()) {
                return Promise.ofException(
                    new IllegalStateException(
                        "Implementation baseline not found: " + implBaselineId));
              }
              baselines.put("implementation", implResults.get(0));
              return Promise.of(baselines);
            });
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> deriveTestPlan(
      Map<String, Object> baselines, String tenantId, String runId) {

    Map<String, Object> reqBaseline = (Map<String, Object>) baselines.get("requirements");
    Map<String, Object> implBaseline = (Map<String, Object>) baselines.get("implementation");

    String testPlanId = UUID.randomUUID().toString();
    Map<String, Object> testPlan = new LinkedHashMap<>();
    testPlan.put("testPlanId", testPlanId);
    testPlan.put("tenantId", tenantId);
    testPlan.put("runId", runId);
    testPlan.put("requirementsBaselineId", reqBaseline.get("baselineId"));
    testPlan.put("implementationBaselineId", implBaseline.get("baselineId"));

    // Extract requirements for test mapping
    Map<String, Object> reqContent = (Map<String, Object>) reqBaseline.get("content");
    List<Map<String, Object>> requirements =
        (List<Map<String, Object>>) reqContent.getOrDefault("requirements", List.of());

    // Categorize requirements
    List<Map<String, Object>> functionalReqs =
        requirements.stream()
            .filter(r -> "FUNCTIONAL".equals(r.get("category")))
            .collect(Collectors.toList());

    List<Map<String, Object>> nonFunctionalReqs =
        requirements.stream()
            .filter(r -> "NON_FUNCTIONAL".equals(r.get("category")))
            .collect(Collectors.toList());

    // Derive test scope
    Map<String, Object> scope = new LinkedHashMap<>();
    scope.put("totalRequirements", requirements.size());
    scope.put("functionalRequirements", functionalReqs.size());
    scope.put("nonFunctionalRequirements", nonFunctionalReqs.size());

    // Map to test types
    Map<String, Integer> testTypes = new LinkedHashMap<>();
    testTypes.put("UNIT", functionalReqs.size() * 3); // ~3 unit tests per FR
    testTypes.put("INTEGRATION", functionalReqs.size()); // 1 integration test per FR
    testTypes.put("E2E", (int) Math.ceil(functionalReqs.size() / 3.0)); // E2E for user flows
    testTypes.put("SECURITY", 5); // Standard security test suite
    testTypes.put("PERFORMANCE", nonFunctionalReqs.size()); // 1 per NFR
    scope.put("testTypes", testTypes);
    testPlan.put("scope", scope);

    // Extract NFR targets for performance testing
    Map<String, Object> nfrTargets = extractNfrTargets(nonFunctionalReqs);
    testPlan.put("nfrTargets", nfrTargets);

    // Test strategy
    Map<String, Object> strategy = new LinkedHashMap<>();
    strategy.put("unitTestFramework", "JUnit 5 + EventloopTestBase");
    strategy.put("integrationTestFramework", "JUnit 5 + TestContainers");
    strategy.put("e2eTestFramework", "Playwright/Selenium");
    strategy.put("securityTestFramework", "OWASP ZAP + Snyk");
    strategy.put("performanceTestFramework", "Gatling");
    testPlan.put("strategy", strategy);

    // Test coverage targets
    Map<String, Object> coverageTargets = new LinkedHashMap<>();
    coverageTargets.put("unit", 80.0);
    coverageTargets.put("integration", 70.0);
    coverageTargets.put("e2e", 50.0);
    testPlan.put("coverageTargets", coverageTargets);

    testPlan.put("status", "DRAFT");
    testPlan.put("createdAt", Instant.now().toString());
    testPlan.put("updatedAt", Instant.now().toString());

    return Promise.of(testPlan);
  }

  private Map<String, Object> extractNfrTargets(List<Map<String, Object>> nonFunctionalReqs) {
    Map<String, Object> targets = new LinkedHashMap<>();

    for (Map<String, Object> nfr : nonFunctionalReqs) {
      String content = (String) nfr.getOrDefault("content", "");
      String lower = content.toLowerCase();

      // Extract latency targets
      if (lower.contains("latency") || lower.contains("response time")) {
        targets.put(
            "latency",
            Map.of(
                "p95", "500ms",
                "p99", "1000ms"));
      }

      // Extract throughput targets
      if (lower.contains("throughput") || lower.contains("requests per second")) {
        targets.put(
            "throughput",
            Map.of(
                "target", "1000 rps",
                "peak", "2000 rps"));
      }

      // Extract availability targets
      if (lower.contains("availability") || lower.contains("uptime")) {
        targets.put(
            "availability",
            Map.of(
                "target", "99.9%",
                "monthlyDowntime", "43.8 minutes"));
      }
    }

    // Defaults if not specified
    if (!targets.containsKey("latency")) {
      targets.put("latency", Map.of("p95", "500ms", "p99", "1000ms"));
    }
    if (!targets.containsKey("throughput")) {
      targets.put("throughput", Map.of("target", "500 rps", "peak", "1000 rps"));
    }
    if (!targets.containsKey("availability")) {
      targets.put("availability", Map.of("target", "99.5%"));
    }

    return targets;
  }

  private Promise<Map<String, Object>> persistTestPlan(
      Map<String, Object> testPlan, String tenantId, String runId) {

    return dbClient.insert(COLLECTION_TEST_PLANS, testPlan).map($ -> testPlan);
  }

  private Promise<Map<String, Object>> publishEvents(
      Map<String, Object> testPlan, String tenantId, String runId) {

    Map<String, Object> eventPayload =
        Map.of(
            "runId", runId,
            "eventType", "TEST_PLAN_DERIVED",
            "testPlanId", testPlan.get("testPlanId"),
            "scope", testPlan.get("scope"),
            "nfrTargets", testPlan.get("nfrTargets"));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> testPlan);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, Map<String, Object> testPlan, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "DeriveTestPlan");
    output.put("testPlanId", testPlan.get("testPlanId"));
    output.put("scope", testPlan.get("scope"));
    output.put("nfrTargets", testPlan.get("nfrTargets"));
    output.put("testPlan", testPlan);
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
            "TEST_PLAN_DERIVATION_FAILED",
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
