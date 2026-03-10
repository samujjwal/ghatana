package com.ghatana.yappc.agent.enhancement;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: ENHANCEMENT / Publish.
 *
 * <p>Publishes immutable enhancement baseline with complete trace through the SDLC cycle. Creates
 * auditable record of all enhancement activities, experiments, and outcomes.
 *
 * <h3>Core Responsibilities:</h3>
 *
 * <ul>
 *   <li>Aggregate all enhancement phase data (feedback, analysis, proposals, experiments)
 *   <li>Create complete trace links through SDLC (requirements → ops → enhancement)
 *   <li>Calculate enhancement summary metrics and ROI
 *   <li>Generate SHA-256 hash for immutability
 *   <li>Publish baseline for audit and compliance
 * </ul>
 *
 * <h3>Enhancement Baseline Contents:</h3>
 *
 * <ul>
 *   <li><b>Feedback</b>: User feedback, bug reports, usage patterns
 *   <li><b>Analysis</b>: Insights, patterns, impact scores
 *   <li><b>Proposals</b>: Enhancement proposals with ROI
 *   <li><b>Prioritization</b>: RICE scores, roadmap
 *   <li><b>Approvals</b>: Approval decisions and criteria
 *   <li><b>Experiments</b>: A/B tests, results, outcomes
 *   <li><b>Metrics</b>: Success rates, business impact
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Enhancement phase publish step - creates immutable enhancement baseline
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PublishStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public PublishStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = dbClient;
    this.eventClient = eventClient;
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext ctx) {
    String workflowId = ctx.getWorkflowId();
    String runId = (String) ctx.get("runId");

    // Load all enhancement data
    return loadAllEnhancementData(ctx)
        .then(
            enhancementData -> {
              // Build comprehensive baseline
              Map<String, Object> baseline =
                  buildEnhancementBaseline(workflowId, runId, enhancementData);

              // Compute SHA-256 hash for immutability
              String hash = computeHash(baseline);
              baseline.put("hash", hash);

              // Store baseline
              return dbClient
                  .insert("enhancement_baselines", baseline)
                  .map(
                      baselineId -> {
                        // Publish enhancement phase completed event
                        Map<String, Object> event =
                            Map.of(
                                "eventType", "ENHANCEMENT_PHASE_COMPLETED",
                                "workflowId", workflowId,
                                "runId", runId,
                                "baselineId", baselineId,
                                "hash", hash,
                                "timestamp", Instant.now().toString());
                        eventClient.publish("enhancement.workflow", event);

                        // Build output context
                        ctx.put("enhancementBaselineId", baselineId);
                        ctx.put("experimentId", enhancementData.get("experimentId"));
                        ctx.put("hash", hash);
                        ctx.put("successfulExperiments", enhancementData.get("successfulCount"));
                        ctx.put("sdlcComplete", true); // Full SDLC cycle complete!
                        ctx.put("status", "ENHANCEMENT_PHASE_COMPLETED");
                        return ctx;
                      });
            })
        .whenException(
            e -> {
              Map<String, Object> errorEvent =
                  Map.of(
                      "eventType",
                      "ENHANCEMENT_PUBLISH_FAILED",
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

  /** Load all enhancement phase data */
  private Promise<Map<String, Object>> loadAllEnhancementData(WorkflowContext ctx) {
    String experimentId = (String) ctx.get("experimentId");
    if (experimentId == null) {
      return Promise.ofException(new IllegalStateException("experimentId required in context"));
    }

    // Load experiments data
    return dbClient
        .query("enhancement_experiments", Map.of("_id", experimentId), 100)
        .then(
            experimentResults -> {
              if (experimentResults.isEmpty()) {
                return Promise.ofException(
                    new IllegalStateException("Experiments not found: " + experimentId));
              }
              Map<String, Object> experimentsData = experimentResults.get(0);
              String approvalId = (String) experimentsData.get("approvalId");

              // Load approval data
              return dbClient
                  .query("enhancement_approvals", Map.of("_id", approvalId), 100)
                  .then(
                      approvalResults -> {
                        Map<String, Object> approvalData = approvalResults.get(0);
                        String prioritizationId = (String) approvalData.get("prioritizationId");

                        // Load prioritization data
                        return dbClient
                            .query(
                                "enhancement_prioritization", Map.of("_id", prioritizationId), 100)
                            .then(
                                prioritizationResults -> {
                                  Map<String, Object> prioritizationData =
                                      prioritizationResults.get(0);
                                  String proposalId = (String) prioritizationData.get("proposalId");

                                  // Load proposals data
                                  return dbClient
                                      .query(
                                          "enhancement_proposals", Map.of("_id", proposalId), 100)
                                      .then(
                                          proposalResults -> {
                                            Map<String, Object> proposalsData =
                                                proposalResults.get(0);
                                            String analysisId =
                                                (String) proposalsData.get("analysisId");

                                            // Load analysis data
                                            return dbClient
                                                .query(
                                                    "feedback_analysis",
                                                    Map.of("_id", analysisId),
                                                    100)
                                                .then(
                                                    analysisResults -> {
                                                      Map<String, Object> analysisData =
                                                          analysisResults.get(0);
                                                      String feedbackId =
                                                          (String) analysisData.get("feedbackId");

                                                      // Load feedback data
                                                      return dbClient
                                                          .query(
                                                              "user_feedback",
                                                              Map.of("_id", feedbackId),
                                                              100)
                                                          .map(
                                                              feedbackResults -> {
                                                                Map<String, Object> feedbackData =
                                                                    feedbackResults.get(0);

                                                                // Aggregate all data
                                                                return Map.of(
                                                                    "feedbackData", feedbackData,
                                                                    "analysisData", analysisData,
                                                                    "proposalsData", proposalsData,
                                                                    "prioritizationData",
                                                                        prioritizationData,
                                                                    "approvalData", approvalData,
                                                                    "experimentsData",
                                                                        experimentsData,
                                                                    "experimentId", experimentId,
                                                                    "successfulCount",
                                                                        experimentsData.get(
                                                                                    "summary")
                                                                                != null
                                                                            ? ((Map<?, ?>)
                                                                                    experimentsData
                                                                                        .get(
                                                                                            "summary"))
                                                                                .get(
                                                                                    "successfulCount")
                                                                            : 0);
                                                              });
                                                    });
                                          });
                                });
                      });
            });
  }

  /** Build comprehensive enhancement baseline */
  private Map<String, Object> buildEnhancementBaseline(
      String workflowId, String runId, Map<String, Object> enhancementData) {
    Map<String, Object> feedbackData = (Map<String, Object>) enhancementData.get("feedbackData");
    Map<String, Object> analysisData = (Map<String, Object>) enhancementData.get("analysisData");
    Map<String, Object> proposalsData = (Map<String, Object>) enhancementData.get("proposalsData");
    Map<String, Object> prioritizationData =
        (Map<String, Object>) enhancementData.get("prioritizationData");
    Map<String, Object> approvalData = (Map<String, Object>) enhancementData.get("approvalData");
    Map<String, Object> experimentsData =
        (Map<String, Object>) enhancementData.get("experimentsData");

    // Build trace links through entire SDLC
    Map<String, Object> traceLinks = buildTraceLinks(feedbackData);

    // Calculate enhancement summary metrics
    Map<String, Object> summary =
        calculateSummaryMetrics(
            feedbackData,
            analysisData,
            proposalsData,
            prioritizationData,
            approvalData,
            experimentsData);

    Map<String, Object> baseline = new HashMap<>();
    baseline.put("_id", "enhancement-baseline-" + UUID.randomUUID().toString().substring(0, 8));
    baseline.put("workflowId", workflowId);
    baseline.put("runId", runId);
    baseline.put(
        "feedback",
        Map.of(
            "id", feedbackData.get("_id"),
            "totalItems", ((Map<?, ?>) feedbackData.get("aggregated")).get("totalFeedbackItems"),
            "sources", ((Map<?, ?>) feedbackData.get("aggregated")).get("feedbackSources")));
    baseline.put(
        "analysis",
        Map.of(
            "id", analysisData.get("_id"),
            "insightCount", ((List<?>) analysisData.get("insights")).size(),
            "patterns", ((Map<?, ?>) analysisData.get("patterns")).get("totalPatterns")));
    baseline.put(
        "proposals",
        Map.of(
            "id", proposalsData.get("_id"),
            "totalProposals", ((List<?>) proposalsData.get("proposals")).size(),
            "averageROI", ((Map<?, ?>) proposalsData.get("summary")).get("averageROI")));
    baseline.put(
        "prioritization",
        Map.of(
            "id", prioritizationData.get("_id"),
            "rankedCount", ((List<?>) prioritizationData.get("ranked")).size(),
            "quickWins", ((List<?>) prioritizationData.get("quickWins")).size()));
    baseline.put(
        "approval",
        Map.of(
            "id", approvalData.get("_id"),
            "approvedCount", ((Map<?, ?>) approvalData.get("summary")).get("approvedCount"),
            "approvalRate", ((Map<?, ?>) approvalData.get("summary")).get("approvalRate")));
    baseline.put(
        "experiments",
        Map.of(
            "id", experimentsData.get("_id"),
            "totalExperiments",
                ((Map<?, ?>) experimentsData.get("summary")).get("totalExperiments"),
            "successfulCount", ((Map<?, ?>) experimentsData.get("summary")).get("successfulCount"),
            "successRate", ((Map<?, ?>) experimentsData.get("summary")).get("successRate")));
    baseline.put("traceLinks", traceLinks);
    baseline.put("summary", summary);
    baseline.put("publishedAt", Instant.now().toString());
    baseline.put("sdlcComplete", true);

    return baseline;
  }

  /** Build trace links through entire SDLC cycle */
  private Map<String, Object> buildTraceLinks(Map<String, Object> feedbackData) {
    // Get ops baseline ID from feedback
    String opsBaselineId = (String) feedbackData.get("opsBaselineId");

    return Map.of(
        "opsBaselineId",
        opsBaselineId,
        "note",
        "Enhancement phase completes the full SDLC cycle",
        "cycle",
        List.of("requirements → architecture → implementation → testing → ops → enhancement"),
        "traceability",
        "Complete end-to-end trace from requirements to production improvements");
  }

  /** Calculate summary metrics for enhancement baseline */
  private Map<String, Object> calculateSummaryMetrics(
      Map<String, Object> feedbackData,
      Map<String, Object> analysisData,
      Map<String, Object> proposalsData,
      Map<String, Object> prioritizationData,
      Map<String, Object> approvalData,
      Map<String, Object> experimentsData) {
    // Extract key metrics
    int totalFeedback =
        (int) ((Map<?, ?>) feedbackData.get("aggregated")).get("totalFeedbackItems");
    int insightCount = ((List<?>) analysisData.get("insights")).size();
    int proposalCount = ((List<?>) proposalsData.get("proposals")).size();
    int approvedCount = (int) ((Map<?, ?>) approvalData.get("summary")).get("approvedCount");
    int successfulExperiments =
        (int) ((Map<?, ?>) experimentsData.get("summary")).get("successfulCount");
    double successRate = (double) ((Map<?, ?>) experimentsData.get("summary")).get("successRate");

    // Calculate conversion funnel
    double feedbackToInsights = (insightCount * 100.0) / totalFeedback;
    double insightsToProposals = (proposalCount * 100.0) / insightCount;
    double proposalsToApproved = (approvedCount * 100.0) / proposalCount;
    double approvedToSuccessful = (successfulExperiments * 100.0) / approvedCount;

    return Map.of(
        "totalFeedbackProcessed", totalFeedback,
        "totalInsights", insightCount,
        "totalProposals", proposalCount,
        "totalApproved", approvedCount,
        "totalExperiments", ((Map<?, ?>) experimentsData.get("summary")).get("totalExperiments"),
        "successfulExperiments", successfulExperiments,
        "experimentSuccessRate", successRate,
        "conversionFunnel",
            Map.of(
                "feedbackToInsights", feedbackToInsights,
                "insightsToProposals", insightsToProposals,
                "proposalsToApproved", proposalsToApproved,
                "approvedToSuccessful", approvedToSuccessful,
                "overallConversion", (successfulExperiments * 100.0) / totalFeedback),
        "businessImpact",
            Map.of(
                "averageROI", ((Map<?, ?>) proposalsData.get("summary")).get("averageROI"),
                "quickWinsDelivered", ((List<?>) prioritizationData.get("quickWins")).size(),
                "enhancementsDeployed", successfulExperiments));
  }

  /** Compute SHA-256 hash for baseline immutability */
  private String computeHash(Map<String, Object> baseline) {
    try {
      // Remove mutable fields before hashing
      Map<String, Object> immutableData = new HashMap<>(baseline);
      immutableData.remove("hash"); // Don't include hash in hash computation
      immutableData.remove("_id"); // Don't include ID in hash computation

      String dataString = immutableData.toString();
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(dataString.getBytes(StandardCharsets.UTF_8));

      StringBuilder hexString = new StringBuilder();
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to compute hash", e);
    }
  }
}
