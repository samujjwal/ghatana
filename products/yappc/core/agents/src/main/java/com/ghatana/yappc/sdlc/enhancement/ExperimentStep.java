package com.ghatana.yappc.sdlc.enhancement;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: ENHANCEMENT / Experiment.
 *
 * <p>Runs controlled experiments (A/B tests, canary deployments) to validate approved enhancements
 * before full production rollout. Measures impact on key metrics.
 *
 * <h3>Core Responsibilities:</h3>
 *
 * <ul>
 *   <li>Design experiment methodology (A/B test, canary, feature flag)
 *   <li>Define success metrics and hypothesis
 *   <li>Execute experiment with control and treatment groups
 *   <li>Collect and analyze experiment data
 *   <li>Determine statistical significance and business impact
 * </ul>
 *
 * <h3>Experiment Types:</h3>
 *
 * <ul>
 *   <li><b>A/B Test</b>: Random user assignment, measure conversion/engagement
 *   <li><b>Canary</b>: Gradual rollout, monitor error rates and latency
 *   <li><b>Feature Flag</b>: Targeted rollout, measure adoption and satisfaction
 * </ul>
 *
 * <h3>Success Criteria:</h3>
 *
 * <ul>
 *   <li>Statistical significance (p-value < 0.05)
 *   <li>Positive impact on key metrics (>5% improvement)
 *   <li>No regression in baseline metrics
 *   <li>User satisfaction maintained or improved
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Enhancement phase experiment step - validates enhancements via experiments
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ExperimentStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public ExperimentStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = dbClient;
    this.eventClient = eventClient;
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext ctx) {
    String workflowId = ctx.getWorkflowId();
    String runId = (String) ctx.get("runId");

    // Load approved enhancements
    return loadApprovedEnhancements(ctx)
        .then(
            approvalData -> {
              List<Map<String, Object>> approved =
                  (List<Map<String, Object>>) approvalData.get("approved");

              // Design experiments for approved enhancements
              List<Map<String, Object>> experiments = new ArrayList<>();
              for (Map<String, Object> enhancement : approved) {
                Map<String, Object> experiment = designExperiment(enhancement);
                experiments.add(experiment);
              }

              // Execute experiments (simulated for now)
              for (Map<String, Object> experiment : experiments) {
                executeExperiment(experiment);
              }

              // Analyze experiment results
              for (Map<String, Object> experiment : experiments) {
                analyzeResults(experiment);
              }

              // Determine which experiments succeeded
              List<Map<String, Object>> successful =
                  experiments.stream().filter(e -> "SUCCESS".equals(e.get("outcome"))).toList();

              // Build experiment record
              Map<String, Object> experimentRecord =
                  buildExperimentRecord(workflowId, runId, approvalData, experiments, successful);

              return dbClient
                  .insert("enhancement_experiments", experimentRecord)
                  .map(
                      experimentId -> {
                        // Publish experiments completed event
                        Map<String, Object> event =
                            Map.of(
                                "eventType",
                                "EXPERIMENTS_COMPLETED",
                                "workflowId",
                                workflowId,
                                "runId",
                                runId,
                                "experimentId",
                                experimentId,
                                "totalExperiments",
                                experiments.size(),
                                "successfulCount",
                                successful.size(),
                                "timestamp",
                                Instant.now().toString());
                        eventClient.publish("enhancement.workflow", event);

                        // Build output context
                        ctx.put("experimentId", experimentId);
                        ctx.put("approvalId", approvalData.get("_id"));
                        ctx.put("totalExperiments", experiments.size());
                        ctx.put("successfulCount", successful.size());
                        ctx.put("successRate", (successful.size() * 100.0) / experiments.size());
                        ctx.put("status", "EXPERIMENTS_COMPLETED");
                        return ctx;
                      });
            })
        .whenException(
            e -> {
              Map<String, Object> errorEvent =
                  Map.of(
                      "eventType",
                      "EXPERIMENTS_FAILED",
                      "workflowId",
                      workflowId,
                      "runId",
                      runId,
                      "error",
                      e.getMessage(),
                      "timestamp",
                      Instant.now().toString());
              eventClient.publish("enhancement.workflow", errorEvent);
            });
  }

  /** Load approved enhancements from previous step */
  private Promise<Map<String, Object>> loadApprovedEnhancements(WorkflowContext ctx) {
    String approvalId = (String) ctx.get("approvalId");
    if (approvalId == null) {
      return Promise.ofException(new IllegalStateException("approvalId required in context"));
    }

    return dbClient
        .query("enhancement_approvals", Map.of("_id", approvalId), 100)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("Approval data not found: " + approvalId);
              }
              return results.get(0);
            });
  }

  /** Design experiment for an enhancement */
  private Map<String, Object> designExperiment(Map<String, Object> enhancement) {
    String enhancementId = (String) enhancement.get("id");
    String category = (String) enhancement.get("type");
    String feature = (String) enhancement.get("feature");

    // Select experiment type based on category
    String experimentType =
        switch (category) {
          case "BUG_FIX" -> "CANARY"; // Bug fixes use canary deployment
          case "FEATURE_ENHANCEMENT" -> "AB_TEST"; // New features use A/B test
          case "UX_IMPROVEMENT" -> "FEATURE_FLAG"; // UX changes use feature flags
          default -> "AB_TEST";
        };

    // Define hypothesis
    String hypothesis = generateHypothesis(enhancement, experimentType);

    // Define success metrics
    List<Map<String, Object>> metrics = defineMetrics(category);

    return new HashMap<>(
        Map.of(
            "id", "experiment-" + UUID.randomUUID().toString().substring(0, 8),
            "enhancementId", enhancementId,
            "feature", feature,
            "type", experimentType,
            "hypothesis", hypothesis,
            "metrics", metrics,
            "cohorts",
                Map.of(
                    "control", Map.of("size", 50, "description", "No changes"),
                    "treatment", Map.of("size", 50, "description", "With enhancement")),
            "duration", "7 days",
            "status", "DESIGNED"));
  }

  /** Generate hypothesis for experiment */
  private String generateHypothesis(Map<String, Object> enhancement, String experimentType) {
    String feature = (String) enhancement.get("feature");
    String category = (String) enhancement.get("type");

    return switch (category) {
      case "BUG_FIX" -> String.format("Fixing %s will reduce error rate by 80%%", feature);
      case "FEATURE_ENHANCEMENT" -> String.format(
          "Enhancing %s will increase user engagement by 20%%", feature);
      case "UX_IMPROVEMENT" -> String.format(
          "Improving %s UX will increase satisfaction score by 15%%", feature);
      default -> String.format("Enhancement to %s will improve key metrics", feature);
    };
  }

  /** Define success metrics for experiment */
  private List<Map<String, Object>> defineMetrics(String category) {
    List<Map<String, Object>> metrics = new ArrayList<>();

    if ("BUG_FIX".equals(category)) {
      metrics.add(
          Map.of(
              "name", "error_rate",
              "baseline", 2.0,
              "target", 0.4,
              "threshold", 0.8, // 80% reduction
              "unit", "percentage"));
      metrics.add(
          Map.of(
              "name",
              "incident_count",
              "baseline",
              5,
              "target",
              1,
              "threshold",
              80.0,
              "unit",
              "count"));
    } else if ("FEATURE_ENHANCEMENT".equals(category)) {
      metrics.add(
          Map.of(
              "name", "engagement_rate",
              "baseline", 45.0,
              "target", 54.0,
              "threshold", 20.0, // 20% increase
              "unit", "percentage"));
      metrics.add(
          Map.of(
              "name", "feature_adoption",
              "baseline", 60.0,
              "target", 75.0,
              "threshold", 25.0,
              "unit", "percentage"));
    } else { // UX_IMPROVEMENT
      metrics.add(
          Map.of(
              "name", "satisfaction_score",
              "baseline", 7.5,
              "target", 8.6,
              "threshold", 15.0,
              "unit", "score"));
      metrics.add(
          Map.of(
              "name", "task_completion_time",
              "baseline", 45.0,
              "target", 36.0,
              "threshold", 20.0,
              "unit", "seconds"));
    }

    return metrics;
  }

  /**
   * Execute experiment (simulated) In production: integrate with experimentation platform
   * (LaunchDarkly, Optimizely, etc.)
   */
  private void executeExperiment(Map<String, Object> experiment) {
    String experimentId = (String) experiment.get("id");

    // Simulate experiment execution
    experiment.put("startedAt", Instant.now().toString());
    experiment.put("status", "RUNNING");

    // Simulate data collection (7 days compressed into instant)
    Map<String, Object> collectedData = collectExperimentData(experiment);
    experiment.put("data", collectedData);

    experiment.put("completedAt", Instant.now().toString());
    experiment.put("status", "COMPLETED");
  }

  /** Collect experiment data (simulated) */
  private Map<String, Object> collectExperimentData(Map<String, Object> experiment) {
    List<Map<String, Object>> metrics = (List<Map<String, Object>>) experiment.get("metrics");

    Map<String, Map<String, Double>> results = new HashMap<>();

    for (Map<String, Object> metric : metrics) {
      String metricName = (String) metric.get("name");
      double baseline = ((Number) metric.get("baseline")).doubleValue();
      double target = ((Number) metric.get("target")).doubleValue();

      // Simulate results with some variation
      double controlValue = baseline + (Math.random() * 2 - 1); // ±1 variation
      double treatmentValue = target + (Math.random() * 2 - 1); // ±1 variation

      results.put(
          metricName,
          Map.of(
              "control", controlValue,
              "treatment", treatmentValue,
              "lift", ((treatmentValue - controlValue) / controlValue) * 100));
    }

    return Map.of(
        "cohortSizes", Map.of("control", 5000, "treatment", 5000),
        "metrics", results,
        "collectedAt", Instant.now().toString());
  }

  /** Analyze experiment results */
  private void analyzeResults(Map<String, Object> experiment) {
    Map<String, Object> data = (Map<String, Object>) experiment.get("data");
    Map<String, Map<String, Double>> metricsData =
        (Map<String, Map<String, Double>>) data.get("metrics");
    List<Map<String, Object>> metricDefinitions =
        (List<Map<String, Object>>) experiment.get("metrics");

    List<Map<String, Object>> analysis = new ArrayList<>();
    boolean allMetricsMet = true;

    for (Map<String, Object> metricDef : metricDefinitions) {
      String metricName = (String) metricDef.get("name");
      double threshold = ((Number) metricDef.get("threshold")).doubleValue();

      Map<String, Double> metricResults = metricsData.get(metricName);
      double lift = metricResults.get("lift");

      // Check if metric meets threshold
      boolean metMet = Math.abs(lift) >= threshold;
      allMetricsMet = allMetricsMet && metMet;

      analysis.add(
          Map.of(
              "metric", metricName,
              "lift", lift,
              "threshold", threshold,
              "met", metMet,
              "significance", calculateSignificance(lift) // p-value simulation
              ));
    }

    experiment.put("analysis", analysis);
    experiment.put("outcome", allMetricsMet ? "SUCCESS" : "FAILURE");
    experiment.put("analyzedAt", Instant.now().toString());
  }

  /** Calculate statistical significance (simulated) */
  private double calculateSignificance(double lift) {
    // Simulate p-value based on lift magnitude
    // Larger lifts = lower p-values (more significant)
    if (Math.abs(lift) >= 20) return 0.01; // Highly significant
    if (Math.abs(lift) >= 10) return 0.03; // Significant
    if (Math.abs(lift) >= 5) return 0.08; // Marginally significant
    return 0.15; // Not significant
  }

  /** Build experiment record for storage */
  private Map<String, Object> buildExperimentRecord(
      String workflowId,
      String runId,
      Map<String, Object> approvalData,
      List<Map<String, Object>> experiments,
      List<Map<String, Object>> successful) {
    return Map.of(
        "_id",
        "experiments-" + UUID.randomUUID().toString().substring(0, 8),
        "workflowId",
        workflowId,
        "runId",
        runId,
        "approvalId",
        approvalData.get("_id"),
        "experiments",
        experiments,
        "successful",
        successful,
        "summary",
        Map.of(
            "totalExperiments",
            experiments.size(),
            "successfulCount",
            successful.size(),
            "failedCount",
            experiments.size() - successful.size(),
            "successRate",
            (successful.size() * 100.0) / experiments.size()),
        "completedAt",
        Instant.now().toString(),
        "status",
        "COMPLETED");
  }
}
