import { describe, expect, it } from "vitest";
import { VivaInterventionWorkflow } from "./VivaInterventionWorkflow";
import type {
  CohortStats,
  PredictionRecord,
  SimulationRecord,
} from "@tutorputor/core/kernel/engine/analytics/VivaEngine";

const cohortStats: CohortStats = {
  completionTimeP10: 10,
  completionTimeP50: 30,
  completionTimeP90: 60,
  avgAttempts: 3,
  avgParameterChanges: 5,
};

function prediction(learnerId: string, correct = true): PredictionRecord {
  return {
    learnerId,
    claimId: "claim-1",
    correct,
    confidence: "high",
    completionTimeSeconds: 30,
    timestamp: new Date(),
  };
}

function simulation(learnerId: string): SimulationRecord {
  return {
    learnerId,
    claimId: "claim-1",
    goalAchieved: true,
    attempts: 1,
    parameterChanges: 4,
    completionTimeSeconds: 30,
    timestamp: new Date(),
  };
}

describe("VivaInterventionWorkflow", () => {
  it("schedules anomaly-triggered viva with slot, rubric, recording, remediation, and re-viva state", async () => {
    const workflow = new VivaInterventionWorkflow(cohortStats, {
      randomSamplingRate: 0,
    });
    const results = await workflow.processEvidence(
      "learner-risk",
      [
        prediction("learner-risk", false),
        prediction("learner-risk", false),
      ],
      [simulation("learner-risk")],
    );

    expect(results[0]?.scheduledVivaId).toBeDefined();
    const viva = workflow.getVivaQueue()[0]!;
    expect(viva.slot.durationMinutes).toBe(15);
    expect(viva.rubric).toHaveLength(3);

    workflow.recordVivaResult(
      viva.id,
      "fail",
      "Could not explain the simulation evidence.",
      "https://recordings.example/viva-1",
    );
    workflow.completeRemediation(viva.id);

    const queue = workflow.getVivaQueue();
    expect(queue[0]?.recordingUrl).toContain("recordings.example");
    expect(queue[0]?.remediationTask?.status).toBe("completed");
    expect(queue.some((entry) => entry.reVivaForId === viva.id)).toBe(true);
  });

  it("selects exactly 10 percent of non-anomalous learners for random viva sampling", async () => {
    const workflow = new VivaInterventionWorkflow(cohortStats, {
      randomSamplingRate: 0.1,
    });
    const predictions = Array.from({ length: 100 }, (_, index) =>
      prediction(`learner-${String(index).padStart(3, "0")}`),
    );

    await workflow.processEvidence(
      "*",
      predictions,
      predictions.map((item) => simulation(item.learnerId)),
    );

    const queue = workflow.getVivaQueue();
    expect(queue).toHaveLength(10);
    expect(queue.every((entry) => entry.reason === "random_sampling")).toBe(true);
  });
});
