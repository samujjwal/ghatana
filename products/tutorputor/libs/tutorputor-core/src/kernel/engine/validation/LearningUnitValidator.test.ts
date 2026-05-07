import { describe, expect, it } from "vitest";
import {
  CANONICAL_CBM_SCORING,
  type LearningUnit,
} from "@tutorputor/contracts/v1/learning-unit";
import { LearningUnitValidator } from "./LearningUnitValidator";

function validLearningUnit(overrides: Partial<LearningUnit> = {}): LearningUnit {
  return {
    id: "LU-claim-evidence-task",
    version: 1,
    domain: "physics",
    level: "secondary",
    status: "draft",
    intent: {
      problem: "Learners confuse velocity with acceleration in motion graphs.",
      motivation: "Motion graphs explain everyday movement and scientific measurement.",
    },
    claims: [
      {
        id: "C1",
        text: "Predict how changing acceleration changes velocity over time.",
        bloom: "apply",
      },
    ],
    evidence: [
      {
        id: "E1",
        claimRef: "C1",
        type: "prediction_vs_outcome",
        description: "Prediction and outcome comparison for acceleration changes.",
        observables: [{ name: "predicted_trend", type: "string" }],
      },
      {
        id: "E2",
        claimRef: "C1",
        type: "parameter_targeting",
        description: "Simulation parameter targeting for acceleration changes.",
        observables: [{ name: "target_velocity_error", type: "number" }],
      },
    ],
    tasks: [
      {
        id: "T1",
        type: "prediction",
        claimRef: "C1",
        evidenceRef: "E1",
        prompt: "Predict the velocity trend when acceleration is positive.",
        confidenceRequired: true,
        options: ["increases", "decreases", "stays constant"],
        correctAnswer: "increases",
      },
      {
        id: "T2",
        type: "simulation",
        claimRef: "C1",
        evidenceRef: "E2",
        prompt: "Tune acceleration until the object reaches the target velocity.",
        simulationRef: "sim-motion-acceleration",
        goal: "Reach the target velocity within tolerance.",
        successCriteria: {
          rmse: "<= 0.25",
          maxAttempts: 3,
          timeLimit: 180,
        },
      },
    ],
    artifacts: [
      {
        type: "simulation",
        ref: "sim-motion-acceleration",
        claims: ["C1"],
      },
    ],
    telemetry: {
      events: ["sim.start", "assess.answer.submit", "assess.confidence.submit"],
      processFeatures: ["time_on_task_seconds", "attempt_count"],
    },
    assessment: {
      model: "cbm_plus_process",
      confidenceLevels: ["low", "medium", "high"],
      scoring: CANONICAL_CBM_SCORING,
    },
    publishReadiness: {
      simulationConfigured: true,
      assessmentCoverage: "complete",
      accessibilityNotes: "Keyboard, reduced-motion, captions, and text alternatives configured.",
      telemetryEnabled: true,
      aiUseDisclosure: {
        required: true,
        configured: true,
        summary: "AI assisted draft generation; SME reviewed before publish.",
      },
      reviewStatus: "complete",
      unresolvedValidationErrors: [],
    },
    createdAt: "2026-05-06T00:00:00.000Z",
    updatedAt: "2026-05-06T00:00:00.000Z",
    createdBy: "author-1",
    tenantId: "tenant-1",
    ...overrides,
  };
}

describe("LearningUnitValidator evidence-centered publish gates", () => {
  it("allows publish when every claim maps to evidence and every evidence item maps to a task", () => {
    const result = new LearningUnitValidator().validate(validLearningUnit());

    expect(result.valid).toBe(true);
    expect(result.issues.filter((issue) => issue.severity === "error")).toEqual(
      [],
    );
  });

  it("blocks publish when a claim has no evidence", () => {
    const result = new LearningUnitValidator().validate(
      validLearningUnit({
        claims: [
          {
            id: "C1",
            text: "Predict how acceleration changes velocity over time.",
            bloom: "apply",
          },
          {
            id: "C2",
            text: "Explain why acceleration changes velocity over time.",
            bloom: "understand",
          },
        ],
      }),
    );

    expect(result.valid).toBe(false);
    expect(result.issues).toContainEqual(
      expect.objectContaining({
        field: "evidence",
        severity: "error",
        message: "Claims without evidence: C2",
      }),
    );
  });

  it("blocks publish when an evidence item has no producing task", () => {
    const result = new LearningUnitValidator().validate(
      validLearningUnit({
        evidence: [
          {
            id: "E1",
            claimRef: "C1",
            type: "prediction_vs_outcome",
            description: "Prediction and outcome comparison.",
            observables: [{ name: "predicted_trend", type: "string" }],
          },
          {
            id: "E2",
            claimRef: "C1",
            type: "explanation_quality",
            description: "Explanation of why acceleration changes velocity.",
            observables: [{ name: "uses_causal_language", type: "boolean" }],
          },
        ],
        tasks: [
          {
            id: "T1",
            type: "prediction",
            claimRef: "C1",
            evidenceRef: "E1",
            prompt: "Predict the velocity trend when acceleration is positive.",
            confidenceRequired: true,
            options: ["increases", "decreases"],
          },
        ],
      }),
    );

    expect(result.valid).toBe(false);
    expect(result.issues).toContainEqual(
      expect.objectContaining({
        field: "tasks",
        severity: "error",
        message: "Evidence without producing tasks: E2",
      }),
    );
  });

  it("blocks publish when a task references unknown claim or evidence IDs", () => {
    const result = new LearningUnitValidator().validate(
      validLearningUnit({
        tasks: [
          {
            id: "T1",
            type: "prediction",
            claimRef: "C404",
            evidenceRef: "E404",
            prompt: "Predict the velocity trend.",
            confidenceRequired: true,
            options: ["increases", "decreases"],
          },
        ],
      }),
    );

    expect(result.valid).toBe(false);
    expect(result.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          field: "tasks[0].claimRef",
          severity: "error",
        }),
        expect.objectContaining({
          field: "tasks[0].evidenceRef",
          severity: "error",
        }),
      ]),
    );
  });

  it("blocks publish when CMS quality gates are incomplete", () => {
    const result = new LearningUnitValidator().validate(
      validLearningUnit({
        publishReadiness: {
          simulationConfigured: false,
          assessmentCoverage: "partial",
          accessibilityNotes: "",
          telemetryEnabled: false,
          aiUseDisclosure: {
            required: true,
            configured: false,
          },
          reviewStatus: "qa_approved",
          unresolvedValidationErrors: ["missing-alt-text"],
        },
      }),
    );

    expect(result.valid).toBe(false);
    expect(result.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          field: "publishReadiness.simulationConfigured",
          severity: "error",
        }),
        expect.objectContaining({
          field: "publishReadiness.assessmentCoverage",
          severity: "error",
        }),
        expect.objectContaining({
          field: "publishReadiness.accessibilityNotes",
          severity: "error",
        }),
        expect.objectContaining({
          field: "publishReadiness.telemetryEnabled",
          severity: "error",
        }),
        expect.objectContaining({
          field: "publishReadiness.aiUseDisclosure",
          severity: "error",
        }),
        expect.objectContaining({
          field: "publishReadiness.reviewStatus",
          severity: "error",
        }),
        expect.objectContaining({
          field: "publishReadiness.unresolvedValidationErrors",
          severity: "error",
        }),
      ]),
    );
  });
});
