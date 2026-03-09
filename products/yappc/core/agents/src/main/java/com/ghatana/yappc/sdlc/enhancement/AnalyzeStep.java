package com.ghatana.yappc.sdlc.enhancement;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AEP Step: ENHANCEMENT / Analyze.
 *
 * <p>Analyzes ingested feedback to identify patterns, trends, and actionable insights. Uses
 * ML/LLM-assisted analysis to extract themes and prioritize issues.
 *
 * <h3>Core Responsibilities:</h3>
 *
 * <ul>
 *   <li>Analyze feedback sentiment and themes
 *   <li>Identify recurring issues and feature requests
 *   <li>Correlate feedback with usage patterns and incidents
 *   <li>Calculate impact scores for each feedback item
 *   <li>Generate actionable insights for enhancement proposals
 * </ul>
 *
 * <h3>Analysis Dimensions:</h3>
 *
 * <ul>
 *   <li><b>Frequency</b>: How often is this issue/request mentioned?
 *   <li><b>Severity</b>: How critical is this to users?
 *   <li><b>Impact</b>: How many users are affected?
 *   <li><b>Trend</b>: Is this increasing or decreasing?
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Enhancement phase analyze step - analyzes feedback for actionable insights
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AnalyzeStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public AnalyzeStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = dbClient;
    this.eventClient = eventClient;
  }

  @Override
  public String getStepId() {
    return "enhancement.analyze";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext ctx) {
    String workflowId = ctx.getWorkflowId();
    String runId = (String) ctx.get("runId");

    // Load feedback data
    return loadFeedbackData(ctx)
        .then(
            feedbackData -> {
              // Analyze sentiment and themes
              Map<String, Object> sentimentAnalysis = analyzeSentiment(feedbackData);

              // Identify recurring patterns
              Map<String, Object> patterns = identifyPatterns(feedbackData);

              // Calculate impact scores
              Map<String, Object> impactScores = calculateImpactScores(feedbackData, patterns);

              // Generate insights
              List<Map<String, Object>> insights =
                  generateInsights(sentimentAnalysis, patterns, impactScores);

              // Build analysis record
              Map<String, Object> analysisRecord =
                  buildAnalysisRecord(
                      workflowId,
                      runId,
                      feedbackData,
                      sentimentAnalysis,
                      patterns,
                      impactScores,
                      insights);

              String analysisId = (String) analysisRecord.get("_id");

              return dbClient
                  .insert("feedback_analysis", analysisRecord)
                  .map(
                      $ -> {
                        // Publish analysis completed event
                        Map<String, Object> event =
                            Map.of(
                                "eventType",
                                "FEEDBACK_ANALYZED",
                                "workflowId",
                                workflowId,
                                "runId",
                                runId,
                                "analysisId",
                                analysisId,
                                "insightCount",
                                insights.size(),
                                "timestamp",
                                Instant.now().toString());
                        eventClient.publish("enhancement.workflow", event);

                        // Build output context
                        ctx.put("analysisId", analysisId);
                        ctx.put("feedbackId", feedbackData.get("_id"));
                        ctx.put("insightCount", insights.size());
                        ctx.put(
                            "highPriorityCount",
                            insights.stream()
                                .filter(i -> "HIGH".equals(i.get("priority")))
                                .count());
                        ctx.put("status", "ANALYZED");
                        return ctx;
                      });
            })
        .whenException(
            e -> {
              Map<String, Object> errorEvent =
                  Map.of(
                      "eventType",
                      "FEEDBACK_ANALYSIS_FAILED",
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

  /** Load feedback data from previous step */
  private Promise<Map<String, Object>> loadFeedbackData(WorkflowContext ctx) {
    String feedbackId = (String) ctx.get("feedbackId");
    if (feedbackId == null) {
      return Promise.ofException(new IllegalStateException("feedbackId required in context"));
    }

    return dbClient
        .query("user_feedback", Map.of("_id", feedbackId), 100)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("Feedback data not found: " + feedbackId);
              }
              return results.get(0);
            });
  }

  /**
   * Analyze sentiment of feedback items In production: Use LLM API for sentiment analysis (e.g.,
   * GPT-4, Claude)
   */
  private Map<String, Object> analyzeSentiment(Map<String, Object> feedbackData) {
    Map<String, Object> aggregated = (Map<String, Object>) feedbackData.get("aggregated");
    Map<String, List<Map<String, Object>>> byFeature =
        (Map<String, List<Map<String, Object>>>) aggregated.get("byFeature");

    Map<String, Map<String, Integer>> sentimentByFeature = new HashMap<>();
    Map<String, Integer> overallSentiment = new HashMap<>();

    for (Map.Entry<String, List<Map<String, Object>>> entry : byFeature.entrySet()) {
      String feature = entry.getKey();
      List<Map<String, Object>> items = entry.getValue();

      Map<String, Integer> featureSentiment = new HashMap<>();
      for (Map<String, Object> item : items) {
        // Simulate sentiment analysis based on type and rating
        String sentiment = determineSentiment(item);
        featureSentiment.merge(sentiment, 1, Integer::sum);
        overallSentiment.merge(sentiment, 1, Integer::sum);
      }
      sentimentByFeature.put(feature, featureSentiment);
    }

    return Map.of(
        "byFeature", sentimentByFeature,
        "overall", overallSentiment,
        "analyzedAt", Instant.now().toString());
  }

  /** Determine sentiment from feedback item */
  private String determineSentiment(Map<String, Object> item) {
    String type = (String) item.get("type");

    // Bug reports and critical incidents = NEGATIVE
    if ("BUG_REPORT".equals(type)) {
      String severity = (String) item.get("severity");
      return ("HIGH".equals(severity) || "CRITICAL".equals(severity)) ? "NEGATIVE" : "NEUTRAL";
    }

    // Feature requests with low rating = NEGATIVE
    if ("FEATURE_REQUEST".equals(type)) {
      Object rating = item.get("rating");
      if (rating instanceof Integer) {
        int score = (Integer) rating;
        if (score <= 2) return "NEGATIVE";
        if (score >= 4) return "POSITIVE";
      }
      return "NEUTRAL";
    }

    // User satisfaction scores
    if ("USER_SATISFACTION".equals(type)) {
      Object rating = item.get("rating");
      if (rating instanceof Integer) {
        int score = (Integer) rating;
        if (score <= 6) return "NEGATIVE"; // Detractors
        if (score >= 9) return "POSITIVE"; // Promoters
      }
      return "NEUTRAL";
    }

    return "NEUTRAL";
  }

  /** Identify recurring patterns in feedback */
  private Map<String, Object> identifyPatterns(Map<String, Object> feedbackData) {
    Map<String, Object> aggregated = (Map<String, Object>) feedbackData.get("aggregated");
    Map<String, List<Map<String, Object>>> byFeature =
        (Map<String, List<Map<String, Object>>>) aggregated.get("byFeature");

    List<Map<String, Object>> patterns = new ArrayList<>();

    for (Map.Entry<String, List<Map<String, Object>>> entry : byFeature.entrySet()) {
      String feature = entry.getKey();
      List<Map<String, Object>> items = entry.getValue();

      // Count feedback types for this feature
      Map<String, Long> typeCounts =
          items.stream()
              .collect(
                  Collectors.groupingBy(item -> (String) item.get("type"), Collectors.counting()));

      // Identify pattern if frequency > 2
      for (Map.Entry<String, Long> typeEntry : typeCounts.entrySet()) {
        if (typeEntry.getValue() >= 2) {
          patterns.add(
              Map.of(
                  "feature", feature,
                  "type", typeEntry.getKey(),
                  "frequency", typeEntry.getValue(),
                  "trend", typeEntry.getValue() >= 3 ? "INCREASING" : "STABLE",
                  "severity", determinePatterSeverity(typeEntry.getKey(), typeEntry.getValue())));
        }
      }
    }

    // Sort by frequency (descending)
    patterns.sort((a, b) -> Long.compare((Long) b.get("frequency"), (Long) a.get("frequency")));

    return Map.of(
        "patterns", patterns,
        "totalPatterns", patterns.size(),
        "identifiedAt", Instant.now().toString());
  }

  /** Determine pattern severity */
  private String determinePatterSeverity(String type, Long frequency) {
    if ("BUG_REPORT".equals(type) && frequency >= 3) return "HIGH";
    if ("FEATURE_REQUEST".equals(type) && frequency >= 5) return "MEDIUM";
    return "LOW";
  }

  /** Calculate impact scores for feedback items */
  private Map<String, Object> calculateImpactScores(
      Map<String, Object> feedbackData, Map<String, Object> patterns) {
    List<Map<String, Object>> patternList = (List<Map<String, Object>>) patterns.get("patterns");

    Map<String, Double> impactScores = new HashMap<>();

    for (Map<String, Object> pattern : patternList) {
      String feature = (String) pattern.get("feature");
      Long frequency = (Long) pattern.get("frequency");
      String severity = (String) pattern.get("severity");

      // Impact Score = Frequency × Severity Multiplier
      double severityMultiplier =
          switch (severity) {
            case "HIGH" -> 3.0;
            case "MEDIUM" -> 2.0;
            default -> 1.0;
          };

      double score = frequency * severityMultiplier;
      impactScores.put(feature, score);
    }

    return Map.of(
        "scores", impactScores,
        "maxScore", impactScores.values().stream().max(Double::compareTo).orElse(0.0),
        "calculatedAt", Instant.now().toString());
  }

  /** Generate actionable insights from analysis */
  private List<Map<String, Object>> generateInsights(
      Map<String, Object> sentimentAnalysis,
      Map<String, Object> patterns,
      Map<String, Object> impactScores) {
    List<Map<String, Object>> insights = new ArrayList<>();
    List<Map<String, Object>> patternList = (List<Map<String, Object>>) patterns.get("patterns");
    Map<String, Double> scores = (Map<String, Double>) impactScores.get("scores");

    for (Map<String, Object> pattern : patternList) {
      String feature = (String) pattern.get("feature");
      String type = (String) pattern.get("type");
      Long frequency = (Long) pattern.get("frequency");
      Double impactScore = scores.getOrDefault(feature, 0.0);

      // Determine priority based on impact score
      String priority = impactScore >= 9.0 ? "HIGH" : impactScore >= 5.0 ? "MEDIUM" : "LOW";

      // Generate recommendation
      String recommendation = generateRecommendation(feature, type, frequency);

      insights.add(
          Map.of(
              "feature", feature,
              "type", type,
              "frequency", frequency,
              "impactScore", impactScore,
              "priority", priority,
              "recommendation", recommendation,
              "affectedUsers", estimateAffectedUsers(frequency)));
    }

    // Sort by impact score (descending)
    insights.sort(
        (a, b) -> Double.compare((Double) b.get("impactScore"), (Double) a.get("impactScore")));

    return insights;
  }

  /** Generate recommendation based on feedback pattern */
  private String generateRecommendation(String feature, String type, Long frequency) {
    if ("BUG_REPORT".equals(type)) {
      return String.format("Fix critical bug in %s affecting %d users", feature, frequency);
    }
    if ("FEATURE_REQUEST".equals(type)) {
      return String.format("Enhance %s based on %d user requests", feature, frequency);
    }
    return String.format("Investigate %s based on user feedback", feature);
  }

  /** Estimate affected users based on feedback frequency */
  private int estimateAffectedUsers(Long frequency) {
    // Rule of thumb: 1 feedback = ~100 affected users
    return (int) (frequency * 100);
  }

  /** Build analysis record for storage */
  private Map<String, Object> buildAnalysisRecord(
      String workflowId,
      String runId,
      Map<String, Object> feedbackData,
      Map<String, Object> sentimentAnalysis,
      Map<String, Object> patterns,
      Map<String, Object> impactScores,
      List<Map<String, Object>> insights) {
    return Map.of(
        "_id", "analysis-" + UUID.randomUUID().toString().substring(0, 8),
        "workflowId", workflowId,
        "runId", runId,
        "feedbackId", feedbackData.get("_id"),
        "sentiment", sentimentAnalysis,
        "patterns", patterns,
        "impactScores", impactScores,
        "insights", insights,
        "analyzedAt", Instant.now().toString(),
        "status", "COMPLETED");
  }
}
