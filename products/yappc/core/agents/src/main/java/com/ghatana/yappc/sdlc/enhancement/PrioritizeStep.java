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
 * AEP Step: ENHANCEMENT / Prioritize.
 *
 * <p>Prioritizes enhancement proposals using weighted scoring across multiple dimensions. Creates a
 * ranked backlog for implementation planning.
 *
 * <h3>Core Responsibilities:</h3>
 *
 * <ul>
 *   <li>Score proposals across multiple dimensions (ROI, effort, risk, urgency)
 *   <li>Apply weighted prioritization framework (e.g., RICE, WSJF)
 *   <li>Consider dependencies and sequencing
 *   <li>Create ranked backlog with clear rationale
 *   <li>Identify quick wins vs. strategic initiatives
 * </ul>
 *
 * <h3>Prioritization Framework (RICE):</h3>
 *
 * <ul>
 *   <li><b>Reach</b>: How many users will benefit?
 *   <li><b>Impact</b>: How much will it improve their experience?
 *   <li><b>Confidence</b>: How sure are we about estimates?
 *   <li><b>Effort</b>: How much time will it take?
 *   <li><b>Score</b>: (Reach × Impact × Confidence) / Effort
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Enhancement phase prioritize step - ranks proposals for implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PrioritizeStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public PrioritizeStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = dbClient;
    this.eventClient = eventClient;
  }

  @Override
  public String getStepId() {
    return "enhancement.prioritize";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext ctx) {
    String workflowId = ctx.getWorkflowId();
    String runId = (String) ctx.get("runId");

    // Load proposals
    return loadProposals(ctx)
        .then(
            proposalsData -> {
              List<Map<String, Object>> proposals =
                  (List<Map<String, Object>>) proposalsData.get("proposals");

              // Calculate RICE scores
              proposals.forEach(this::calculateRICEScore);

              // Rank proposals by RICE score
              List<Map<String, Object>> ranked = rankProposals(proposals);

              // Identify quick wins
              List<Map<String, Object>> quickWins = identifyQuickWins(ranked);

              // Create implementation roadmap
              Map<String, Object> roadmap = createRoadmap(ranked, quickWins);

              // Build prioritization record
              Map<String, Object> prioritizationRecord =
                  buildPrioritizationRecord(
                      workflowId, runId, proposalsData, ranked, quickWins, roadmap);

              return dbClient
                  .insert("enhancement_prioritization", prioritizationRecord)
                  .map(
                      prioritizationId -> {
                        // Publish prioritization completed event
                        Map<String, Object> event =
                            Map.of(
                                "eventType",
                                "ENHANCEMENTS_PRIORITIZED",
                                "workflowId",
                                workflowId,
                                "runId",
                                runId,
                                "prioritizationId",
                                prioritizationId,
                                "rankedCount",
                                ranked.size(),
                                "quickWinsCount",
                                quickWins.size(),
                                "timestamp",
                                Instant.now().toString());
                        eventClient.publish("enhancement.workflow", event);

                        // Build output context
                        ctx.put("prioritizationId", prioritizationId);
                        ctx.put("proposalId", proposalsData.get("_id"));
                        ctx.put("rankedCount", ranked.size());
                        ctx.put("quickWinsCount", quickWins.size());
                        ctx.put("totalEffort", roadmap.get("totalEffort"));
                        ctx.put("status", "PRIORITIZED");
                        return ctx;
                      });
            })
        .whenException(
            e -> {
              Map<String, Object> errorEvent =
                  Map.of(
                      "eventType",
                      "PRIORITIZATION_FAILED",
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

  /** Load proposals from previous step */
  private Promise<Map<String, Object>> loadProposals(WorkflowContext ctx) {
    String proposalId = (String) ctx.get("proposalId");
    if (proposalId == null) {
      return Promise.ofException(new IllegalStateException("proposalId required in context"));
    }

    return dbClient
        .query("enhancement_proposals", Map.of("_id", proposalId), 100)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("Proposals not found: " + proposalId);
              }
              return results.get(0);
            });
  }

  /** Calculate RICE score for each proposal RICE = (Reach × Impact × Confidence) / Effort */
  private void calculateRICEScore(Map<String, Object> proposal) {
    // Extract data
    Map<String, Object> roi = (Map<String, Object>) proposal.get("roi");
    int affectedUsers = (int) roi.get("affectedUsers");

    Map<String, Object> effort = (Map<String, Object>) proposal.get("effort");
    int personDays = (int) effort.get("personDays");

    Double impactScore = (Double) proposal.get("impactScore");

    // RICE components
    double reach = affectedUsers / 100.0; // Normalize to 0-100 scale
    double impact = normalizeImpact(impactScore); // 0.25, 0.5, 1.0, 2.0, 3.0
    double confidence = estimateConfidence(proposal); // 0.5, 0.8, 1.0
    int effortScore = personDays;

    // Calculate RICE score
    double riceScore = (reach * impact * confidence) / effortScore;

    proposal.put(
        "riceScore",
        Map.of(
            "reach", reach,
            "impact", impact,
            "confidence", confidence,
            "effort", effortScore,
            "score", riceScore,
            "calculatedAt", Instant.now().toString()));
  }

  /** Normalize impact to RICE scale (0.25, 0.5, 1.0, 2.0, 3.0) */
  private double normalizeImpact(Double impactScore) {
    if (impactScore >= 15.0) return 3.0; // Massive
    if (impactScore >= 10.0) return 2.0; // High
    if (impactScore >= 5.0) return 1.0; // Medium
    if (impactScore >= 2.0) return 0.5; // Low
    return 0.25; // Minimal
  }

  /** Estimate confidence in estimates (0.5, 0.8, 1.0) */
  private double estimateConfidence(Map<String, Object> proposal) {
    String category = (String) proposal.get("type");
    Map<String, Object> effort = (Map<String, Object>) proposal.get("effort");
    String complexity = (String) effort.get("complexity");

    // Bug fixes have higher confidence
    if ("BUG_FIX".equals(category)) return 1.0;

    // Low complexity = high confidence
    if ("LOW".equals(complexity)) return 1.0;

    // Medium complexity = medium confidence
    if ("MEDIUM".equals(complexity)) return 0.8;

    // High complexity = lower confidence
    return 0.5;
  }

  /** Rank proposals by RICE score (descending) */
  private List<Map<String, Object>> rankProposals(List<Map<String, Object>> proposals) {
    List<Map<String, Object>> ranked = new ArrayList<>(proposals);

    // Sort by RICE score (descending)
    ranked.sort(
        (a, b) -> {
          Map<String, Object> riceA = (Map<String, Object>) a.get("riceScore");
          Map<String, Object> riceB = (Map<String, Object>) b.get("riceScore");
          double scoreA = (double) riceA.get("score");
          double scoreB = (double) riceB.get("score");
          return Double.compare(scoreB, scoreA);
        });

    // Assign ranks
    for (int i = 0; i < ranked.size(); i++) {
      ranked.get(i).put("rank", i + 1);
    }

    return ranked;
  }

  /**
   * Identify quick wins (high impact, low effort) Quick Win: RICE score > 5.0 AND effort <= 5 days
   */
  private List<Map<String, Object>> identifyQuickWins(List<Map<String, Object>> ranked) {
    return ranked.stream()
        .filter(
            proposal -> {
              Map<String, Object> riceScore = (Map<String, Object>) proposal.get("riceScore");
              double score = (double) riceScore.get("score");
              int effort = (int) riceScore.get("effort");

              return score >= 5.0 && effort <= 5;
            })
        .collect(Collectors.toList());
  }

  /** Create implementation roadmap */
  private Map<String, Object> createRoadmap(
      List<Map<String, Object>> ranked, List<Map<String, Object>> quickWins) {
    // Phase 1: Quick Wins (0-2 weeks)
    List<Map<String, Object>> phase1 = new ArrayList<>(quickWins);
    int phase1Effort =
        phase1.stream()
            .mapToInt(
                p -> {
                  Map<String, Object> effort = (Map<String, Object>) p.get("effort");
                  return (int) effort.get("personDays");
                })
            .sum();

    // Phase 2: High Priority (2-8 weeks)
    List<Map<String, Object>> phase2 =
        ranked.stream()
            .filter(p -> !quickWins.contains(p))
            .filter(
                p -> {
                  Integer rank = (Integer) p.get("rank");
                  return rank <= 5; // Top 5 non-quick-win items
                })
            .collect(Collectors.toList());
    int phase2Effort =
        phase2.stream()
            .mapToInt(
                p -> {
                  Map<String, Object> effort = (Map<String, Object>) p.get("effort");
                  return (int) effort.get("personDays");
                })
            .sum();

    // Phase 3: Medium Priority (8-16 weeks)
    List<Map<String, Object>> phase3 =
        ranked.stream()
            .filter(p -> !quickWins.contains(p))
            .filter(
                p -> {
                  Integer rank = (Integer) p.get("rank");
                  return rank > 5;
                })
            .collect(Collectors.toList());
    int phase3Effort =
        phase3.stream()
            .mapToInt(
                p -> {
                  Map<String, Object> effort = (Map<String, Object>) p.get("effort");
                  return (int) effort.get("personDays");
                })
            .sum();

    return Map.of(
        "phase1",
            Map.of(
                "name",
                "Quick Wins",
                "duration",
                "0-2 weeks",
                "items",
                phase1,
                "effort",
                phase1Effort),
        "phase2",
            Map.of(
                "name",
                "High Priority",
                "duration",
                "2-8 weeks",
                "items",
                phase2,
                "effort",
                phase2Effort),
        "phase3",
            Map.of(
                "name",
                "Medium Priority",
                "duration",
                "8-16 weeks",
                "items",
                phase3,
                "effort",
                phase3Effort),
        "totalEffort", phase1Effort + phase2Effort + phase3Effort);
  }

  /** Build prioritization record for storage */
  private Map<String, Object> buildPrioritizationRecord(
      String workflowId,
      String runId,
      Map<String, Object> proposalsData,
      List<Map<String, Object>> ranked,
      List<Map<String, Object>> quickWins,
      Map<String, Object> roadmap) {
    // Calculate summary stats
    double avgRICEScore =
        ranked.stream()
            .mapToDouble(
                p -> {
                  Map<String, Object> rice = (Map<String, Object>) p.get("riceScore");
                  return (double) rice.get("score");
                })
            .average()
            .orElse(0.0);

    return Map.of(
        "_id",
        "prioritization-" + UUID.randomUUID().toString().substring(0, 8),
        "workflowId",
        workflowId,
        "runId",
        runId,
        "proposalId",
        proposalsData.get("_id"),
        "ranked",
        ranked,
        "quickWins",
        quickWins,
        "roadmap",
        roadmap,
        "summary",
        Map.of(
            "totalItems", ranked.size(),
            "quickWinsCount", quickWins.size(),
            "averageRICEScore", avgRICEScore,
            "totalEffort", roadmap.get("totalEffort")),
        "prioritizedAt",
        Instant.now().toString(),
        "status",
        "PRIORITIZED");
  }
}
