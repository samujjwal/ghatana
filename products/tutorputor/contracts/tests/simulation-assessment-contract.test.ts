import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import type {
  SimulationAssessmentItem,
  SimulationItemId,
  SimulationStateId,
} from "../v1/assessments/types";
import type { SimulationId } from "../v1/simulation";

const schema = JSON.parse(
  readFileSync(
    join(
      import.meta.dirname,
      "../v1/assessments/simulation-item.schema.json",
    ),
    "utf8",
  ),
) as { required: string[]; properties: Record<string, unknown> };

describe("simulation assessment item contract", () => {
  it("requires reproducible and auditable simulation scoring metadata in schema", () => {
    expect(schema.required).toEqual(
      expect.arrayContaining([
        "seed",
        "targetState",
        "scoringTolerances",
        "processFeatures",
        "captureRules",
        "claimEvidenceMappings",
      ]),
    );
    expect(schema.properties.seed).toBeDefined();
    expect(schema.properties.targetState).toBeDefined();
    expect(schema.properties.scoringTolerances).toBeDefined();
    expect(schema.properties.processFeatures).toBeDefined();
    expect(schema.properties.captureRules).toBeDefined();
    expect(schema.properties.claimEvidenceMappings).toBeDefined();
  });

  it("types every simulation assessment item with seed, target state, tolerances, process features, capture rules, and claim/evidence mapping", () => {
    const item: SimulationAssessmentItem = {
      id: "sim-item-1" as SimulationItemId,
      type: "simulation",
      mode: "manipulation",
      prompt: "Tune the control until the output reaches the target.",
      points: 10,
      simulationRef: {
        manifestId: "starter-ohms-law" as SimulationId,
        targetStateRef: "target-state-1" as SimulationStateId,
      },
      seed: 42,
      targetState: {
        stateId: "target-state-1" as SimulationStateId,
        description: "Output reaches the expected bounded target.",
      },
      scoringTolerances: [
        {
          variableId: "baseline-output",
          tolerance: 0.001,
          toleranceType: "absolute",
        },
      ],
      processFeatures: [
        {
          featureId: "control-change-count",
          sourceEvent: "sim.control.change",
          description: "Learner adjusts the primary input before capture.",
          weight: 0.4,
        },
      ],
      captureRules: [
        {
          ruleId: "final-capture",
          eventType: "sim.capture",
          required: true,
          minCount: 1,
        },
      ],
      claimEvidenceMappings: [
        {
          claimId: "claim-ohms-law",
          evidenceId: "evidence-parameter-targeting",
          taskId: "task-simulation-manipulation",
        },
      ],
      gradingStrategy: {
        method: "hybrid",
        partialCredit: true,
        cbmConfig: {
          requireConfidence: true,
          confidenceLevels: [
            {
              level: 3,
              label: "High",
              correctMultiplier: 3,
              incorrectPenalty: -6,
            },
          ],
        },
      },
    };

    expect(item.seed).toBe(42);
    expect(item.captureRules[0]?.required).toBe(true);
    expect(item.gradingStrategy.cbmConfig?.requireConfidence).toBe(true);
  });
});
