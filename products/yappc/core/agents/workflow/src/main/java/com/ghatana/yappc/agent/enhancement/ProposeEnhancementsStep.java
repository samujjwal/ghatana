package com.ghatana.yappc.agent.enhancement;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: ENHANCEMENT / ProposeEnhancements.
 *
 * <p>Proposes concrete enhancements based on analyzed feedback and insights. Creates detailed
 * enhancement proposals with effort estimates, ROI projections, and implementation plans.
 *
 * <h3>Core Responsibilities:</h3>
 *
 * <ul>
 *   <li>Convert insights into actionable enhancement proposals
 *   <li>Define clear objectives and success criteria
 *   <li>Estimate implementation effort and resources
 *   <li>Calculate ROI and business value
 *   <li>Create implementation roadmap
 * </ul>
 *
 * <h3>Proposal Structure:</h3>
 *
 * <ul>
 *   <li><b>Title & Description</b>: Clear, concise enhancement definition
 *   <li><b>Objectives</b>: Measurable goals (e.g., reduce errors by 50%)
 *   <li><b>Effort</b>: Story points, person-days, complexity
 *   <li><b>ROI</b>: Expected value vs. cost
 *   <li><b>Risk</b>: Implementation risks and mitigation
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Enhancement phase propose enhancements step - creates enhancement proposals
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ProposeEnhancementsStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public ProposeEnhancementsStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = dbClient;
    this.eventClient = eventClient;
  }

  @Override
  public String getStepId() {
    return "enhancement.proposeenhancements";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext ctx) {
    String workflowId = ctx.getWorkflowId();
    String runId = (String) ctx.get("runId");

    // Load analysis data
    return loadAnalysisData(ctx)
        .then(
            analysisData -> {
              // Get top insights
              List<Map<String, Object>> insights =
                  (List<Map<String, Object>>) analysisData.get("insights");

              // Create proposals for high-priority insights
              List<Map<String, Object>> proposals = createProposals(insights);

              // Calculate effort estimates
              proposals.forEach(this::estimateEffort);

              // Calculate ROI
              proposals.forEach(proposal -> calculateROI(proposal, analysisData));

              // Identify implementation risks
              proposals.forEach(this::identifyRisks);

              // Build proposals record
              Map<String, Object> proposalsRecord =
                  buildProposalsRecord(workflowId, runId, analysisData, proposals);

              return dbClient
                  .insert("enhancement_proposals", proposalsRecord)
                  .map(
                      proposalId -> {
                        // Publish proposals created event
                        Map<String, Object> event =
                            Map.of(
                                "eventType",
                                "ENHANCEMENTS_PROPOSED",
                                "workflowId",
                                workflowId,
                                "runId",
                                runId,
                                "proposalId",
                                proposalId,
                                "proposalCount",
                                proposals.size(),
                                "timestamp",
                                Instant.now().toString());
                        eventClient.publish("enhancement.workflow", event);

                        // Build output context
                        ctx.put("proposalId", proposalId);
                        ctx.put("analysisId", analysisData.get("_id"));
                        ctx.put("proposalCount", proposals.size());
                        ctx.put(
                            "highValueCount",
                            proposals.stream().filter(p -> "HIGH".equals(p.get("value"))).count());
                        ctx.put("status", "PROPOSALS_CREATED");
                        return ctx;
                      });
            })
        .whenException(
            e -> {
              Map<String, Object> errorEvent =
                  Map.of(
                      "eventType",
                      "PROPOSAL_CREATION_FAILED",
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

  /** Load analysis data from previous step */
  private Promise<Map<String, Object>> loadAnalysisData(WorkflowContext ctx) {
    String analysisId = (String) ctx.get("analysisId");
    if (analysisId == null) {
      return Promise.ofException(new IllegalStateException("analysisId required in context"));
    }

    return dbClient
        .query("feedback_analysis", Map.of("_id", analysisId), 100)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("Analysis data not found: " + analysisId);
              }
              return results.get(0);
            });
  }

  /** Create enhancement proposals from insights */
  private List<Map<String, Object>> createProposals(List<Map<String, Object>> insights) {
    List<Map<String, Object>> proposals = new ArrayList<>();

    // Create proposals for HIGH priority insights
    for (Map<String, Object> insight : insights) {
      String priority = (String) insight.get("priority");
      if (!"HIGH".equals(priority) && !"MEDIUM".equals(priority)) {
        continue; // Skip LOW priority
      }

      String feature = (String) insight.get("feature");
      String type = (String) insight.get("type");
      String recommendation = (String) insight.get("recommendation");
      Double impactScore = (Double) insight.get("impactScore");

      Map<String, Object> proposal = new HashMap<>();
      proposal.put("id", "enhancement-" + UUID.randomUUID().toString().substring(0, 8));
      proposal.put("feature", feature);
      proposal.put("type", mapTypeToCategory(type));
      proposal.put("title", generateTitle(feature, type));
      proposal.put("description", recommendation);
      proposal.put("objectives", generateObjectives(feature, type, insight));
      proposal.put("impactScore", impactScore);
      proposal.put("priority", priority);
      proposal.put("status", "PROPOSED");

      proposals.add(proposal);
    }

    return proposals;
  }

  /** Map feedback type to enhancement category */
  private String mapTypeToCategory(String type) {
    return switch (type) {
      case "BUG_REPORT" -> "BUG_FIX";
      case "FEATURE_REQUEST" -> "FEATURE_ENHANCEMENT";
      case "USER_SATISFACTION" -> "UX_IMPROVEMENT";
      default -> "GENERAL_IMPROVEMENT";
    };
  }

  /** Generate enhancement title */
  private String generateTitle(String feature, String type) {
    String featureName = feature.replace("-", " ").toUpperCase();
    return switch (type) {
      case "BUG_REPORT" -> String.format("Fix %s Critical Issues", featureName);
      case "FEATURE_REQUEST" -> String.format("Enhance %s Capabilities", featureName);
      case "USER_SATISFACTION" -> String.format("Improve %s User Experience", featureName);
      default -> String.format("Improve %s", featureName);
    };
  }

  /** Generate measurable objectives */
  private List<String> generateObjectives(
      String feature, String type, Map<String, Object> insight) {
    List<String> objectives = new ArrayList<>();

    if ("BUG_REPORT".equals(type)) {
      objectives.add("Reduce error rate by 80%");
      objectives.add("Eliminate critical incidents");
      objectives.add("Improve system stability (99.9% uptime)");
    } else if ("FEATURE_REQUEST".equals(type)) {
      objectives.add("Increase feature adoption by 50%");
      objectives.add("Improve user satisfaction score (NPS +10)");
      objectives.add("Reduce user friction by 30%");
    } else {
      objectives.add("Enhance user experience");
      objectives.add("Improve feature usability");
    }

    return objectives;
  }

  /** Estimate implementation effort */
  private void estimateEffort(Map<String, Object> proposal) {
    String category = (String) proposal.get("type");
    String priority = (String) proposal.get("priority");

    // Estimate story points based on category and priority
    int storyPoints =
        switch (category) {
          case "BUG_FIX" -> priority.equals("HIGH") ? 5 : 3;
          case "FEATURE_ENHANCEMENT" -> priority.equals("HIGH") ? 13 : 8;
          case "UX_IMPROVEMENT" -> priority.equals("HIGH") ? 8 : 5;
          default -> 5;
        };

    // Convert to person-days (1 SP = 1 day)
    int personDays = storyPoints;

    // Determine complexity
    String complexity = storyPoints >= 10 ? "HIGH" : storyPoints >= 5 ? "MEDIUM" : "LOW";

    proposal.put(
        "effort",
        Map.of(
            "storyPoints", storyPoints,
            "personDays", personDays,
            "complexity", complexity,
            "estimatedAt", Instant.now().toString()));
  }

  /** Calculate ROI (Return on Investment) */
  private void calculateROI(Map<String, Object> proposal, Map<String, Object> analysisData) {
    Map<String, Object> effort = (Map<String, Object>) proposal.get("effort");
    int personDays = (int) effort.get("personDays");

    // Cost = person-days × $500/day (avg developer cost)
    double cost = personDays * 500.0;

    // Value based on impactScore and affected users
    Double impactScore = (Double) proposal.get("impactScore");
    int affectedUsers = estimateAffectedUsers(impactScore);

    // Value = affected users × $10/user/month × 12 months
    double annualValue = affectedUsers * 10.0 * 12;

    // ROI = (Value - Cost) / Cost
    double roi = (annualValue - cost) / cost;

    // Business value classification
    String value = roi >= 5.0 ? "HIGH" : roi >= 2.0 ? "MEDIUM" : "LOW";

    proposal.put(
        "roi",
        Map.of(
            "cost", cost,
            "annualValue", annualValue,
            "roiMultiplier", roi,
            "value", value,
            "affectedUsers", affectedUsers,
            "calculatedAt", Instant.now().toString()));
  }

  /** Estimate affected users from impact score */
  private int estimateAffectedUsers(Double impactScore) {
    // Rule: impactScore 1.0 = 100 users
    return (int) (impactScore * 100);
  }

  /** Identify implementation risks */
  private void identifyRisks(Map<String, Object> proposal) {
    String category = (String) proposal.get("type");
    Map<String, Object> effort = (Map<String, Object>) proposal.get("effort");
    String complexity = (String) effort.get("complexity");

    List<Map<String, Object>> risks = new ArrayList<>();

    // Complexity-based risks
    if ("HIGH".equals(complexity)) {
      risks.add(
          Map.of(
              "type", "TECHNICAL",
              "description", "High complexity may lead to scope creep",
              "severity", "MEDIUM",
              "mitigation", "Break down into smaller increments, use feature flags"));
    }

    // Category-specific risks
    if ("FEATURE_ENHANCEMENT".equals(category)) {
      risks.add(
          Map.of(
              "type", "PRODUCT",
              "description", "Feature may not meet user expectations",
              "severity", "MEDIUM",
              "mitigation", "Conduct user testing, A/B test with small cohort"));
    }

    if ("BUG_FIX".equals(category)) {
      risks.add(
          Map.of(
              "type", "TECHNICAL",
              "description", "Fix may introduce regressions",
              "severity", "HIGH",
              "mitigation", "Comprehensive regression testing, canary deployment"));
    }

    proposal.put("risks", risks);
  }

  /** Build proposals record for storage */
  private Map<String, Object> buildProposalsRecord(
      String workflowId,
      String runId,
      Map<String, Object> analysisData,
      List<Map<String, Object>> proposals) {
    // Calculate summary stats
    double totalROI =
        proposals.stream()
            .mapToDouble(
                p -> {
                  Map<String, Object> roi = (Map<String, Object>) p.get("roi");
                  return (double) roi.get("roiMultiplier");
                })
            .sum();

    int totalEffort =
        proposals.stream()
            .mapToInt(
                p -> {
                  Map<String, Object> effort = (Map<String, Object>) p.get("effort");
                  return (int) effort.get("personDays");
                })
            .sum();

    return Map.of(
        "_id",
        "proposals-" + UUID.randomUUID().toString().substring(0, 8),
        "workflowId",
        workflowId,
        "runId",
        runId,
        "analysisId",
        analysisData.get("_id"),
        "proposals",
        proposals,
        "summary",
        Map.of(
            "totalProposals",
            proposals.size(),
            "totalEffort",
            totalEffort,
            "averageROI",
            totalROI / proposals.size(),
            "highValueCount",
            proposals.stream()
                .filter(
                    p -> {
                      Map<String, Object> roi = (Map<String, Object>) p.get("roi");
                      return "HIGH".equals(roi.get("value"));
                    })
                .count()),
        "proposedAt",
        Instant.now().toString(),
        "status",
        "PROPOSED");
  }
}
