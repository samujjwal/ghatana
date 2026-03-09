/**
 * Simulation Item Helper Tests
 *
 * Unit tests for simulation item construction, validation, and scoring utilities.
 *
 * @doc.type test
 * @doc.purpose Test simulation assessment item helpers
 * @doc.layer libs
 * @doc.pattern Test
 */

import { describe, it, expect } from "vitest";
import {
  validateSimulationItem,
  createSimulationItemBuilder,
  createPredictionItem,
  createManipulationItem,
  createExplanationItem,
  calculatePredictionScore,
  calculateManipulationScore,
  applyCBMScoring,
} from "../simulation-item";
import type { SimulationAssessmentItem } from "@ghatana/tutorputor-contracts/v1/assessments";

describe("validateSimulationItem", () => {
  it("should return valid for a complete item", () => {
    const item: SimulationAssessmentItem = {
      id: "test-item-1" as any,
      type: "simulation",
      mode: "prediction",
      prompt: "Predict the final velocity",
      points: 10,
      simulationRef: {
        manifestId: "manifest-1" as any,
        domain: "PHYSICS",
      },
      predictionOptions: {
        targetVariables: [
          {
            variableId: "velocity",
            variableName: "Velocity",
            expectedValue: 10,
            tolerance: 0.5,
            toleranceType: "absolute",
          },
        ],
        showActualAfterSubmit: true,
      },
      gradingStrategy: {
        method: "kernel_replay",
        partialCredit: true,
      },
    };

    const result = validateSimulationItem(item);
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });

  it("should return error for missing required fields", () => {
    const item = {
      type: "simulation",
      mode: "prediction",
      // Missing id, prompt, points, simulationRef, gradingStrategy
    } as SimulationAssessmentItem;

    const result = validateSimulationItem(item);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
    expect(result.errors.some((e) => e.field === "id")).toBe(true);
    expect(result.errors.some((e) => e.field === "prompt")).toBe(true);
  });

  it("should return error for prediction mode without target variables", () => {
    const item: SimulationAssessmentItem = {
      id: "test-item" as any,
      type: "simulation",
      mode: "prediction",
      prompt: "Predict something",
      points: 10,
      simulationRef: { manifestId: "m1" as any },
      gradingStrategy: { method: "kernel_replay", partialCredit: true },
      // Missing predictionOptions
    };

    const result = validateSimulationItem(item);
    expect(result.valid).toBe(false);
    expect(
      result.errors.some((e) => e.field === "predictionOptions.targetVariables")
    ).toBe(true);
  });

  it("should return error for manipulation mode without target conditions", () => {
    const item: SimulationAssessmentItem = {
      id: "test-item" as any,
      type: "simulation",
      mode: "manipulation",
      prompt: "Manipulate to achieve target",
      points: 15,
      simulationRef: { manifestId: "m1" as any },
      gradingStrategy: { method: "state_comparison", partialCredit: true },
      // Missing manipulationOptions
    };

    const result = validateSimulationItem(item);
    expect(result.valid).toBe(false);
    expect(
      result.errors.some((e) => e.field === "manipulationOptions.targetConditions")
    ).toBe(true);
  });

  it("should return warning for explanation mode without rubric criteria", () => {
    const item: SimulationAssessmentItem = {
      id: "test-item" as any,
      type: "simulation",
      mode: "explanation",
      prompt: "Explain the behavior",
      points: 20,
      simulationRef: { manifestId: "m1" as any },
      gradingStrategy: { method: "rubric", partialCredit: true },
      explanationOptions: {
        requiredConcepts: ["concept1"],
        rubricCriteria: [], // Empty
      },
    };

    const result = validateSimulationItem(item);
    expect(result.valid).toBe(true); // Still valid, just warning
    expect(
      result.warnings.some((w) => w.field === "explanationOptions.rubricCriteria")
    ).toBe(true);
  });
});

describe("SimulationItemBuilder", () => {
  it("should build a valid prediction item", () => {
    const item = createSimulationItemBuilder()
      .withId("pred-1")
      .withMode("prediction")
      .withPrompt("Predict the outcome")
      .withPoints(10)
      .withSimulationRef({
        manifestId: "manifest-1" as any,
        domain: "PHYSICS",
      })
      .withPredictionOptions({
        targetVariables: [
          {
            variableId: "energy",
            variableName: "Energy",
            expectedValue: 100,
            tolerance: 5,
            toleranceType: "absolute",
          },
        ],
        showActualAfterSubmit: true,
      })
      .withGradingStrategy({
        method: "kernel_replay",
        partialCredit: true,
      })
      .build();

    expect(item.id).toBe("pred-1");
    expect(item.mode).toBe("prediction");
    expect(item.points).toBe(10);
    expect(item.predictionOptions?.targetVariables).toHaveLength(1);
  });

  it("should throw for invalid item", () => {
    expect(() => {
      createSimulationItemBuilder()
        .withId("invalid")
        .withMode("prediction")
        // Missing required fields
        .build();
    }).toThrow();
  });
});

describe("createPredictionItem", () => {
  it("should create a valid prediction item", () => {
    const item = createPredictionItem({
      id: "physics-pred-1",
      prompt: "Predict final position",
      manifestId: "phys-manifest-1",
      domain: "PHYSICS",
      targetVariables: [
        {
          variableId: "position",
          variableName: "Position",
          unit: "m",
          expectedValue: 50,
          tolerance: 2,
          toleranceType: "absolute",
        },
      ],
      points: 15,
      taxonomyLevel: "apply",
    });

    expect(item.mode).toBe("prediction");
    expect(item.points).toBe(15);
    expect(item.gradingStrategy.method).toBe("kernel_replay");
    expect(item.taxonomyLevel).toBe("apply");
  });
});

describe("createManipulationItem", () => {
  it("should create a valid manipulation item", () => {
    const item = createManipulationItem({
      id: "chem-manip-1",
      prompt: "Adjust conditions to maximize yield",
      manifestId: "chem-manifest-1",
      domain: "CHEMISTRY",
      targetConditions: [
        {
          conditionId: "yield-80",
          description: "Achieve at least 80% yield",
          evaluator: "yield >= 0.8",
        },
      ],
      maxActions: 10,
      points: 20,
    });

    expect(item.mode).toBe("manipulation");
    expect(item.manipulationOptions?.maxActions).toBe(10);
    expect(item.gradingStrategy.method).toBe("state_comparison");
  });
});

describe("createExplanationItem", () => {
  it("should create a valid explanation item", () => {
    const item = createExplanationItem({
      id: "bio-exp-1",
      prompt: "Explain the cellular process",
      manifestId: "bio-manifest-1",
      domain: "BIOLOGY",
      requiredConcepts: ["mitosis", "cell division", "chromosomes"],
      minWordCount: 100,
      maxWordCount: 300,
      points: 25,
    });

    expect(item.mode).toBe("explanation");
    expect(item.explanationOptions?.requiredConcepts).toHaveLength(3);
    expect(item.gradingStrategy.method).toBe("rubric");
    expect(item.gradingStrategy.rubricConfig?.criteria).toHaveLength(3);
  });
});

describe("calculatePredictionScore", () => {
  it("should return 100% for perfect predictions", () => {
    const predictions = [
      { variableId: "v1", predictedValue: 10 },
      { variableId: "v2", predictedValue: 20 },
    ];
    const targets = [
      {
        variableId: "v1",
        variableName: "V1",
        expectedValue: 10,
        tolerance: 1,
        toleranceType: "absolute" as const,
      },
      {
        variableId: "v2",
        variableName: "V2",
        expectedValue: 20,
        tolerance: 2,
        toleranceType: "absolute" as const,
      },
    ];

    const score = calculatePredictionScore(predictions, targets);
    expect(score).toBe(100);
  });

  it("should return 0% for predictions outside tolerance", () => {
    const predictions = [{ variableId: "v1", predictedValue: 100 }];
    const targets = [
      {
        variableId: "v1",
        variableName: "V1",
        expectedValue: 10,
        tolerance: 1,
        toleranceType: "absolute" as const,
      },
    ];

    const score = calculatePredictionScore(predictions, targets);
    expect(score).toBe(0);
  });

  it("should handle percentage tolerance", () => {
    const predictions = [{ variableId: "v1", predictedValue: 105 }];
    const targets = [
      {
        variableId: "v1",
        variableName: "V1",
        expectedValue: 100,
        tolerance: 10, // 10% tolerance
        toleranceType: "percentage" as const,
      },
    ];

    const score = calculatePredictionScore(predictions, targets);
    expect(score).toBe(100); // 5% error is within 10% tolerance
  });

  it("should handle missing predictions", () => {
    const predictions: Array<{ variableId: string; predictedValue: number }> = [];
    const targets = [
      {
        variableId: "v1",
        variableName: "V1",
        expectedValue: 10,
        tolerance: 1,
        toleranceType: "absolute" as const,
      },
    ];

    const score = calculatePredictionScore(predictions, targets);
    expect(score).toBe(0);
  });
});

describe("calculateManipulationScore", () => {
  it("should return 100% when all conditions achieved", () => {
    const results = [
      { conditionId: "c1", achieved: true },
      { conditionId: "c2", achieved: true },
    ];
    const conditions = [
      { conditionId: "c1", description: "Condition 1", evaluator: "true" },
      { conditionId: "c2", description: "Condition 2", evaluator: "true" },
    ];

    const score = calculateManipulationScore(results, conditions);
    expect(score).toBe(100);
  });

  it("should return 0% when no conditions achieved", () => {
    const results = [{ conditionId: "c1", achieved: false }];
    const conditions = [
      { conditionId: "c1", description: "Condition 1", evaluator: "false" },
    ];

    const score = calculateManipulationScore(results, conditions);
    expect(score).toBe(0);
  });

  it("should handle partial credit", () => {
    const results = [
      { conditionId: "c1", achieved: false, partialScore: 0.5 },
    ];
    const conditions = [
      {
        conditionId: "c1",
        description: "Condition 1",
        evaluator: "x > 5",
        partialCreditThreshold: 0.8,
      },
    ];

    const score = calculateManipulationScore(results, conditions);
    expect(score).toBe(50); // 0.5 partial score * 100
  });
});

describe("applyCBMScoring", () => {
  const cbmConfig = {
    confidenceLevels: [
      { level: 1, label: "Low", correctMultiplier: 1.0, incorrectPenalty: 0 },
      { level: 2, label: "Medium", correctMultiplier: 1.5, incorrectPenalty: 10 },
      { level: 3, label: "High", correctMultiplier: 2.0, incorrectPenalty: 20 },
    ],
  };

  it("should multiply score for correct answers with high confidence", () => {
    const result = applyCBMScoring(80, 3, cbmConfig);
    expect(result).toBe(100); // 80 * 2.0, capped at 100
  });

  it("should apply penalty for incorrect answers with high confidence", () => {
    const result = applyCBMScoring(30, 3, cbmConfig);
    expect(result).toBe(10); // 30 - 20 penalty
  });

  it("should not change score for low confidence", () => {
    const result = applyCBMScoring(70, 1, cbmConfig);
    expect(result).toBe(70);
  });

  it("should not go below 0", () => {
    const result = applyCBMScoring(10, 3, cbmConfig);
    expect(result).toBe(0); // 10 - 20 = -10, capped at 0
  });
});
