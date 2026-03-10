package com.ghatana.yappc.agent.enhancement;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: ENHANCEMENT / Approve.
 *
 * <p>Approves prioritized enhancements for implementation based on governance criteria. Ensures
 * alignment with business objectives, resource availability, and risk tolerance.
 *
 * <h3>Core Responsibilities:</h3>
 *
 * <ul>
 *   <li>Review prioritized enhancements against approval criteria
 *   <li>Validate resource availability and capacity
 *   <li>Assess risks and mitigation strategies
 *   <li>Ensure business alignment and stakeholder buy-in
 *   <li>Create approved backlog for implementation
 * </ul>
 *
 * <h3>Approval Criteria:</h3>
 *
 * <ul>
 *   <li><b>Business Value</b>: Positive ROI, strategic alignment
 *   <li><b>Resource Capacity</b>: Team availability, skill match
 *   <li><b>Risk Assessment</b>: Acceptable risk level, mitigation plan
 *   <li><b>Dependencies</b>: No blockers, clear path forward
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Enhancement phase approve step - approves enhancements for implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ApproveStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public ApproveStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = dbClient;
    this.eventClient = eventClient;
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext ctx) {
    String workflowId = ctx.getWorkflowId();
    String runId = (String) ctx.get("runId");

    // Load prioritization data
    return loadPrioritization(ctx)
        .then(
            prioritizationData -> {
              List<Map<String, Object>> ranked =
                  (List<Map<String, Object>>) prioritizationData.get("ranked");
              Map<String, Object> roadmap = (Map<String, Object>) prioritizationData.get("roadmap");

              // Check resource capacity
              Map<String, Object> capacityCheck = checkResourceCapacity(roadmap);

              // Evaluate approval criteria for each proposal
              List<Map<String, Object>> approvalDecisions = new ArrayList<>();
              for (Map<String, Object> proposal : ranked) {
                Map<String, Object> decision = evaluateApprovalCriteria(proposal, capacityCheck);
                approvalDecisions.add(decision);
              }

              // Filter approved items
              List<Map<String, Object>> approved =
                  approvalDecisions.stream()
                      .filter(d -> "APPROVED".equals(d.get("decision")))
                      .toList();

              // Build approval record
              Map<String, Object> approvalRecord =
                  buildApprovalRecord(
                      workflowId,
                      runId,
                      prioritizationData,
                      approvalDecisions,
                      approved,
                      capacityCheck);

              return dbClient
                  .insert("enhancement_approvals", approvalRecord)
                  .map(
                      approvalId -> {
                        // Publish approval completed event
                        Map<String, Object> event =
                            Map.of(
                                "eventType",
                                "ENHANCEMENTS_APPROVED",
                                "workflowId",
                                workflowId,
                                "runId",
                                runId,
                                "approvalId",
                                approvalId,
                                "approvedCount",
                                approved.size(),
                                "totalReviewed",
                                approvalDecisions.size(),
                                "timestamp",
                                Instant.now().toString());
                        eventClient.publish("enhancement.workflow", event);

                        // Build output context
                        ctx.put("approvalId", approvalId);
                        ctx.put("prioritizationId", prioritizationData.get("_id"));
                        ctx.put("approvedCount", approved.size());
                        ctx.put("rejectedCount", approvalDecisions.size() - approved.size());
                        ctx.put("status", "APPROVED");
                        return ctx;
                      });
            })
        .whenException(
            e -> {
              Map<String, Object> errorEvent =
                  Map.of(
                      "eventType",
                      "APPROVAL_FAILED",
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

  /** Load prioritization data from previous step */
  private Promise<Map<String, Object>> loadPrioritization(WorkflowContext ctx) {
    String prioritizationId = (String) ctx.get("prioritizationId");
    if (prioritizationId == null) {
      return Promise.ofException(new IllegalStateException("prioritizationId required in context"));
    }

    return dbClient
        .query("enhancement_prioritization", Map.of("_id", prioritizationId), 100)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("Prioritization not found: " + prioritizationId);
              }
              return results.get(0);
            });
  }

  /** Check resource capacity for implementation */
  private Map<String, Object> checkResourceCapacity(Map<String, Object> roadmap) {
    // Simulate capacity planning
    // In production: integrate with JIRA, capacity planning tools

    int availableCapacity = 80; // person-days per quarter
    int totalEffort = (int) roadmap.get("totalEffort");

    Map<String, Object> phase1 = (Map<String, Object>) roadmap.get("phase1");
    Map<String, Object> phase2 = (Map<String, Object>) roadmap.get("phase2");
    Map<String, Object> phase3 = (Map<String, Object>) roadmap.get("phase3");

    boolean phase1Feasible = (int) phase1.get("effort") <= availableCapacity * 0.2; // 20% capacity
    boolean phase2Feasible = (int) phase2.get("effort") <= availableCapacity * 0.5; // 50% capacity
    boolean phase3Feasible = (int) phase3.get("effort") <= availableCapacity * 0.3; // 30% capacity

    return Map.of(
        "availableCapacity", availableCapacity,
        "totalRequested", totalEffort,
        "utilizationRate", Math.min(100.0, (totalEffort * 100.0) / availableCapacity),
        "phase1Feasible", phase1Feasible,
        "phase2Feasible", phase2Feasible,
        "phase3Feasible", phase3Feasible,
        "overCapacity", totalEffort > availableCapacity);
  }

  /** Evaluate approval criteria for a proposal */
  private Map<String, Object> evaluateApprovalCriteria(
      Map<String, Object> proposal, Map<String, Object> capacityCheck) {
    String proposalId = (String) proposal.get("id");
    Map<String, Object> riceScore = (Map<String, Object>) proposal.get("riceScore");
    Map<String, Object> roi = (Map<String, Object>) proposal.get("roi");
    List<Map<String, Object>> risks = (List<Map<String, Object>>) proposal.get("risks");

    // Criteria evaluation
    Map<String, Boolean> criteria = new HashMap<>();
    Map<String, String> reasoning = new HashMap<>();

    // 1. Business Value (ROI must be positive)
    double roiMultiplier = (double) roi.get("roiMultiplier");
    boolean positiveROI = roiMultiplier > 0;
    criteria.put("businessValue", positiveROI);
    reasoning.put("businessValue", positiveROI ? "ROI: " + roiMultiplier + "x" : "Negative ROI");

    // 2. Resource Capacity (effort must be within capacity)
    boolean overCapacity = (boolean) capacityCheck.get("overCapacity");
    boolean withinCapacity = !overCapacity;
    criteria.put("resourceCapacity", withinCapacity);
    reasoning.put("resourceCapacity", withinCapacity ? "Within capacity" : "Over capacity");

    // 3. Risk Assessment (no high-severity risks without mitigation)
    boolean acceptableRisk =
        risks.stream()
            .noneMatch(r -> "HIGH".equals(r.get("severity")) && r.get("mitigation") == null);
    criteria.put("riskAssessment", acceptableRisk);
    reasoning.put(
        "riskAssessment", acceptableRisk ? "Risks mitigated" : "High risks without mitigation");

    // 4. Strategic Alignment (RICE score above threshold)
    double riceScoreValue = (double) riceScore.get("score");
    boolean strategicAlignment = riceScoreValue >= 1.0; // Minimum viable score
    criteria.put("strategicAlignment", strategicAlignment);
    reasoning.put(
        "strategicAlignment",
        strategicAlignment ? "RICE: " + riceScoreValue : "RICE score too low");

    // Decision: APPROVED if all required criteria met
    boolean allCriteriaMet = criteria.values().stream().allMatch(Boolean::booleanValue);
    String decision = allCriteriaMet ? "APPROVED" : "REJECTED";

    return Map.of(
        "proposalId", proposalId,
        "decision", decision,
        "criteria", criteria,
        "reasoning", reasoning,
        "evaluatedAt", Instant.now().toString());
  }

  /** Build approval record for storage */
  private Map<String, Object> buildApprovalRecord(
      String workflowId,
      String runId,
      Map<String, Object> prioritizationData,
      List<Map<String, Object>> approvalDecisions,
      List<Map<String, Object>> approved,
      Map<String, Object> capacityCheck) {
    long rejectedCount =
        approvalDecisions.stream().filter(d -> "REJECTED".equals(d.get("decision"))).count();

    return Map.of(
        "_id",
        "approval-" + UUID.randomUUID().toString().substring(0, 8),
        "workflowId",
        workflowId,
        "runId",
        runId,
        "prioritizationId",
        prioritizationData.get("_id"),
        "decisions",
        approvalDecisions,
        "approved",
        approved,
        "capacityCheck",
        capacityCheck,
        "summary",
        Map.of(
            "totalReviewed",
            approvalDecisions.size(),
            "approvedCount",
            approved.size(),
            "rejectedCount",
            rejectedCount,
            "approvalRate",
            (approved.size() * 100.0) / approvalDecisions.size()),
        "approvedAt",
        Instant.now().toString(),
        "status",
        "COMPLETED");
  }
}
