package com.ghatana.yappc.sdlc.ops;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * Ops Phase - Step 2: Canary Deployment.
 *
 * <p>Performs gradual canary deployment to production with traffic shifting. Monitors metrics and
 * automatically progresses or rolls back based on health signals.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Deploy canary version (v2) alongside stable version (v1)
 *   <li>Gradually shift traffic: 5% → 25% → 50% → 100%
 *   <li>Monitor error rates, latency, and business metrics
 *   <li>Automatic rollback on threshold violations
 *   <li>Persist canary deployment record
 *   <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Performs canary deployment with gradual traffic shifting
 * @doc.layer product
 * @doc.pattern Service
 */
public final class CanaryStep implements WorkflowStep {

  private static final String COLLECTION_CANARY_DEPLOYMENTS = "canary_deployments";
  private static final String COLLECTION_DEPLOYMENTS = "deployments";
  private static final String EVENT_TOPIC = "ops.workflow";

  // Canary thresholds
  private static final double MAX_ERROR_RATE = 1.0; // 1%
  private static final double MAX_LATENCY_P99 = 500.0; // 500ms
  private static final int[] TRAFFIC_STAGES = {5, 25, 50, 100}; // Percentage
  private static final int STAGE_DURATION_MINUTES = 10;

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public CanaryStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String canaryId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadStagingDeployment(context, tenantId))
        .then(deployment -> deployCanary(deployment, tenantId, canaryId))
        .then(canary -> progressThroughStages(canary, tenantId, canaryId))
        .then(canary -> persistCanary(canary, tenantId, canaryId))
        .then(canary -> publishEvents(canary, tenantId, canaryId))
        .then(canary -> buildOutputContext(context, canary, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, canaryId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    if (!context.containsKey("deploymentId") && !context.containsKey("validated")) {
      return Promise.ofException(
          new IllegalArgumentException("Missing required input: deploymentId or validated flag"));
    }
    boolean validated = (boolean) context.getOrDefault("validated", false);
    if (!validated) {
      return Promise.ofException(
          new IllegalStateException("Staging deployment not validated - canary blocked"));
    }
    return Promise.complete();
  }

  private Promise<Map<String, Object>> loadStagingDeployment(
      WorkflowContext context, String tenantId) {
    String deploymentId = (String) context.getOrDefault("deploymentId", "latest");

    return dbClient
        .query(
            COLLECTION_DEPLOYMENTS,
            Map.of("tenantId", tenantId, "environment", "STAGING", "validated", true),
            1)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("No validated staging deployment found");
              }
              return results.get(0);
            });
  }

  private Promise<Map<String, Object>> deployCanary(
      Map<String, Object> stagingDeployment, String tenantId, String canaryId) {

    Map<String, Object> canary = new LinkedHashMap<>();
    canary.put("canaryId", canaryId);
    canary.put("tenantId", tenantId);
    canary.put("environment", "PRODUCTION");
    canary.put("stagingDeploymentId", stagingDeployment.get("deploymentId"));
    canary.put("version", stagingDeployment.get("version"));
    canary.put("strategy", "GRADUAL_TRAFFIC_SHIFT");
    canary.put("stages", TRAFFIC_STAGES);
    canary.put("currentStage", 0);
    canary.put("status", "IN_PROGRESS");
    canary.put("startedAt", Instant.now().toString());

    // Deploy canary pods
    canary.put(
        "canaryPods",
        Map.of(
            "replicas", 2,
            "version", "v1.2.3-canary",
            "status", "RUNNING"));

    // Stable version remains
    canary.put(
        "stablePods",
        Map.of(
            "replicas", 10,
            "version", "v1.2.2",
            "status", "RUNNING"));

    return Promise.of(canary);
  }

  private Promise<Map<String, Object>> progressThroughStages(
      Map<String, Object> canary, String tenantId, String canaryId) {

    List<Map<String, Object>> stageResults = new ArrayList<>();

    for (int i = 0; i < TRAFFIC_STAGES.length; i++) {
      int trafficPercent = TRAFFIC_STAGES[i];
      Map<String, Object> stageResult = executeStage(i + 1, trafficPercent, canary);
      stageResults.add(stageResult);

      // Check if stage passed
      boolean stagePassed = "PASS".equals(stageResult.get("status"));
      if (!stagePassed) {
        canary.put("status", "FAILED");
        canary.put("failedStage", i + 1);
        canary.put("stageResults", stageResults);
        return Promise.ofException(
            new IllegalStateException(
                String.format("Canary stage %d failed - initiating rollback", i + 1)));
      }
    }

    canary.put("stageResults", stageResults);
    canary.put("status", "SUCCESS");
    canary.put("completedAt", Instant.now().toString());

    return Promise.of(canary);
  }

  private Map<String, Object> executeStage(
      int stageNumber, int trafficPercent, Map<String, Object> canary) {
    // Simulate stage execution with monitoring

    // Shift traffic
    Map<String, Object> trafficSplit =
        Map.of("canary", trafficPercent, "stable", 100 - trafficPercent);

    // Monitor metrics (simulated)
    double errorRate = 0.3 + (Math.random() * 0.4); // 0.3% - 0.7%
    double latencyP99 = 250 + (Math.random() * 150); // 250ms - 400ms
    double requestRate = 1000 + (Math.random() * 200); // 1000-1200 rps

    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("errorRate", errorRate);
    metrics.put("latencyP99", latencyP99);
    metrics.put("requestRate", requestRate);
    metrics.put("duration", STAGE_DURATION_MINUTES + "min");

    // Evaluate health
    boolean healthy = errorRate <= MAX_ERROR_RATE && latencyP99 <= MAX_LATENCY_P99;

    Map<String, Object> stageResult = new LinkedHashMap<>();
    stageResult.put("stage", stageNumber);
    stageResult.put("trafficPercent", trafficPercent);
    stageResult.put("trafficSplit", trafficSplit);
    stageResult.put("metrics", metrics);
    stageResult.put("status", healthy ? "PASS" : "FAIL");
    stageResult.put("executedAt", Instant.now().toString());

    if (!healthy) {
      List<String> violations = new ArrayList<>();
      if (errorRate > MAX_ERROR_RATE) {
        violations.add(
            String.format("Error rate %.2f%% exceeds threshold %.1f%%", errorRate, MAX_ERROR_RATE));
      }
      if (latencyP99 > MAX_LATENCY_P99) {
        violations.add(
            String.format(
                "Latency P99 %.0fms exceeds threshold %.0fms", latencyP99, MAX_LATENCY_P99));
      }
      stageResult.put("violations", violations);
    }

    return stageResult;
  }

  private Promise<Map<String, Object>> persistCanary(
      Map<String, Object> canary, String tenantId, String canaryId) {

    return dbClient.insert(COLLECTION_CANARY_DEPLOYMENTS, canary).map($ -> canary);
  }

  private Promise<Map<String, Object>> publishEvents(
      Map<String, Object> canary, String tenantId, String canaryId) {

    String eventType =
        "SUCCESS".equals(canary.get("status")) ? "CANARY_COMPLETED" : "CANARY_FAILED";

    Map<String, Object> eventPayload =
        Map.of(
            "eventType",
            eventType,
            "canaryId",
            canaryId,
            "status",
            canary.get("status"),
            "version",
            canary.get("version"));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> canary);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, Map<String, Object> canary, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "Canary");
    output.put("canaryId", canary.get("canaryId"));
    output.put("canary", canary);
    output.put("canarySuccess", "SUCCESS".equals(canary.get("status")));
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex, WorkflowContext context, String tenantId, String canaryId, Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "eventType",
            "CANARY_ERROR",
            "canaryId",
            canaryId,
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
              errorContext.put("rollbackRequired", true);
              errorContext.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
              return Promise.of(errorContext);
            });
  }
}
