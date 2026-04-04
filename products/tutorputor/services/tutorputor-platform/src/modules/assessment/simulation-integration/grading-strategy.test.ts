/**
 * Simulation Grading Strategy Tests
 *
 * Covers the scoring formula components, IRT calibration, question type cycling,
 * needsReview boundary, and earnedPoints proportionality for TPUT-FR-031.
 *
 * @doc.type test
 * @doc.purpose Simulation grading strategy: parameter coverage, prediction scoring, explanation scoring, IRT calibration, question type cycling
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  createSimulationAssessmentIntegration,
  scoreSimulationAssessmentResponse,
  summarizeSimulationAttempt,
} from "./service";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makePrisma() {
  return {
    simulationManifest: {
      findMany: vi.fn(),
    },
  };
}

/**
 * Minimal manifest record used across IRT / question-type tests.
 */
function makeManifest(
  overrides: Partial<{
    id: string;
    title: string;
    domain: string;
    manifest: unknown;
  }> = {},
) {
  return {
    id: overrides.id ?? "sim-manifest-1",
    title: overrides.title ?? "Projectile Motion",
    description: "Explore projectile motion.",
    domain: overrides.domain ?? "PHYSICS",
    manifest: overrides.manifest ?? {},
    moduleId: null,
  };
}

/**
 * Scoring helper that makes it easy to supply minimal metadata.
 */
function makeScoreArgs(args: {
  questionType?:
    | "prediction"
    | "parameter_identification"
    | "process_explanation";
  expectedParameters?: string[];
  expectedOutcomeKeywords?: string[];
  response?: {
    trace?: {
      interactions?: Array<{
        type?: string;
        parameterId?: string;
        predictedOutcome?: string;
        observedOutcome?: string;
      }>;
      summary?: string;
      durationMs?: number;
    };
  };
  points?: number;
}) {
  return {
    item: {
      id: "sim-item-test",
      points: args.points ?? 10,
      metadata: {
        simulationManifestId: "sim-1",
        simulationTitle: "Test Simulation",
        simulationDomain: "PHYSICS",
        questionType: args.questionType ?? "prediction",
        interactionType: "parameter_exploration",
        expectedParameters: args.expectedParameters ?? [],
        expectedOutcomeKeywords: args.expectedOutcomeKeywords ?? [],
        irt: { discrimination: 1.05, difficulty: 0, guessing: 0.15 },
      },
    },
    response: args.response,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("scoreSimulationAssessmentResponse", () => {
  describe("no trace provided", () => {
    it("returns earnedPoints=0 when response is undefined", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({ response: undefined }),
      );
      expect(result.earnedPoints).toBe(0);
      expect(result.feedback.scorePercent).toBe(0);
      expect(result.feedback.needsReview).toBe(true);
    });

    it("returns earnedPoints=0 when trace is undefined", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({ response: {} as { trace?: Record<string, unknown> } }),
      );
      expect(result.earnedPoints).toBe(0);
      expect(result.feedback.scorePercent).toBe(0);
      expect(result.feedback.needsReview).toBe(true);
    });

    it("includes a comments field explaining the absence", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({ response: undefined }),
      );
      expect(result.feedback.comments).toMatch(/no simulation trace/i);
    });
  });

  describe("parameter exploration coverage", () => {
    it("full parameter coverage scores higher than partial coverage", () => {
      const full = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "parameter_identification",
          expectedParameters: ["pressure", "temperature"],
          response: {
            trace: {
              interactions: [
                { type: "parameter_change", parameterId: "pressure" },
                { type: "parameter_change", parameterId: "temperature" },
              ],
            },
          },
        }),
      );

      const partial = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "parameter_identification",
          expectedParameters: ["pressure", "temperature"],
          response: {
            trace: {
              interactions: [
                { type: "parameter_change", parameterId: "pressure" },
                // temperature NOT touched
              ],
            },
          },
        }),
      );

      expect(full.feedback.scorePercent).toBeGreaterThan(
        partial.feedback.scorePercent,
      );
    });

    it("partial parameter coverage marks response as needsReview", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "parameter_identification",
          expectedParameters: ["pressure", "temperature"],
          response: {
            trace: {
              interactions: [
                { type: "parameter_change", parameterId: "pressure" },
                // temperature NOT touched
              ],
            },
          },
        }),
      );
      // 0.5 parameter match with no predictions or keywords → score < 65
      expect(result.feedback.needsReview).toBe(true);
    });

    it("includes an improvement when fewer than 60% of parameters are explored", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "parameter_identification",
          expectedParameters: ["pressure", "temperature", "volume"],
          response: {
            trace: {
              interactions: [
                { type: "parameter_change", parameterId: "pressure" },
                // Only 1 of 3 → matchedParameters ≈ 0.33 < 0.6
              ],
            },
          },
        }),
      );
      expect(result.feedback.improvements).toBeDefined();
      const hasParameterImprovement = (result.feedback.improvements ?? []).some(
        (msg: string) => /parameter/i.test(msg),
      );
      expect(hasParameterImprovement).toBe(true);
    });
  });

  describe("prediction scoring", () => {
    it("correct predictions produce a higher score than incorrect predictions", () => {
      const correct = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "prediction",
          expectedParameters: ["velocity"],
          response: {
            trace: {
              interactions: [
                {
                  type: "parameter_change",
                  parameterId: "velocity",
                  predictedOutcome: "distance increases",
                  observedOutcome: "distance increases", // match
                },
              ],
            },
          },
        }),
      );

      const incorrect = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "prediction",
          expectedParameters: ["velocity"],
          response: {
            trace: {
              interactions: [
                {
                  type: "parameter_change",
                  parameterId: "velocity",
                  predictedOutcome: "distance decreases",
                  observedOutcome: "distance increases", // mismatch
                },
              ],
            },
          },
        }),
      );

      expect(correct.feedback.scorePercent).toBeGreaterThan(
        incorrect.feedback.scorePercent,
      );
    });

    it("adds a prediction-improvement hint when prediction accuracy is below 60%", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "prediction",
          expectedParameters: ["angle"],
          response: {
            trace: {
              interactions: [
                {
                  type: "parameter_change",
                  parameterId: "angle",
                  predictedOutcome: "range increases",
                  observedOutcome: "range decreases", // wrong
                },
              ],
            },
          },
        }),
      );
      const hasHint = (result.feedback.improvements ?? []).some((msg: string) =>
        /predict/i.test(msg),
      );
      expect(hasHint).toBe(true);
    });
  });

  describe("explanation / keyword scoring", () => {
    it("matching expected keywords in summary produces a higher score than no keywords", () => {
      const withKeywords = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "process_explanation",
          expectedParameters: [],
          expectedOutcomeKeywords: ["gravity", "acceleration"],
          response: {
            trace: {
              interactions: [],
              summary:
                "Under gravity, the object experiences constant acceleration downward.",
            },
          },
        }),
      );

      const withoutKeywords = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "process_explanation",
          expectedParameters: [],
          expectedOutcomeKeywords: ["gravity", "acceleration"],
          response: {
            trace: {
              interactions: [],
              summary: "I clicked some buttons and observed things.",
            },
          },
        }),
      );

      expect(withKeywords.feedback.scorePercent).toBeGreaterThan(
        withoutKeywords.feedback.scorePercent,
      );
    });

    it("adds an explanation-improvement hint when explanation coverage is below 60%", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "process_explanation",
          expectedParameters: [],
          expectedOutcomeKeywords: ["gravity", "acceleration", "velocity"],
          response: {
            trace: {
              interactions: [],
              summary: "I moved things.", // no matching keywords
            },
          },
        }),
      );
      const hasHint = (result.feedback.improvements ?? []).some((msg: string) =>
        /summary|explanation|causal/i.test(msg),
      );
      expect(hasHint).toBe(true);
    });
  });

  describe("needsReview boundary", () => {
    it("does not flag needsReview for a high-quality trace scoring well above 65", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "prediction",
          expectedParameters: ["velocity"],
          expectedOutcomeKeywords: ["speed", "distance"],
          response: {
            trace: {
              interactions: [
                {
                  type: "parameter_change",
                  parameterId: "velocity",
                  predictedOutcome: "distance increases",
                  observedOutcome: "distance increases",
                },
              ],
              summary:
                "Increasing speed causes greater distance traveled due to the relationship between velocity and displacement.",
            },
          },
        }),
      );
      expect(result.feedback.needsReview).toBe(false);
      expect(result.feedback.scorePercent).toBeGreaterThanOrEqual(65);
    });

    it("flags needsReview for a poor-quality trace with score below 65", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          questionType: "prediction",
          expectedParameters: ["pressure", "temperature"],
          expectedOutcomeKeywords: ["gas law", "volume", "pressure"],
          response: {
            trace: {
              interactions: [],
              summary: "Did not finish.", // no keywords at all
            },
          },
        }),
      );
      expect(result.feedback.needsReview).toBe(true);
      expect(result.feedback.scorePercent).toBeLessThan(65);
    });
  });

  describe("earnedPoints proportionality", () => {
    it("earnedPoints scales with item.points for the same trace quality", () => {
      const makeArgs = (points: number) =>
        makeScoreArgs({
          questionType: "prediction",
          expectedParameters: ["velocity"],
          expectedOutcomeKeywords: ["speed"],
          points,
          response: {
            trace: {
              interactions: [
                {
                  type: "parameter_change",
                  parameterId: "velocity",
                  predictedOutcome: "increases",
                  observedOutcome: "increases",
                },
              ],
              summary: "The speed increases proportionally.",
            },
          },
        });

      const result10 = scoreSimulationAssessmentResponse(makeArgs(10));
      const result20 = scoreSimulationAssessmentResponse(makeArgs(20));

      // Same trace quality → proportional earnedPoints
      expect(result20.earnedPoints).toBeGreaterThan(result10.earnedPoints);
      // The ratio should be approximately 2
      expect(result20.earnedPoints / result10.earnedPoints).toBeCloseTo(2, 0);
    });

    it("earnedPoints is 0 for 0-point item regardless of trace quality", () => {
      const result = scoreSimulationAssessmentResponse(
        makeScoreArgs({
          points: 0,
          response: {
            trace: {
              interactions: [
                {
                  type: "parameter_change",
                  parameterId: "velocity",
                  predictedOutcome: "increases",
                  observedOutcome: "increases",
                },
              ],
              summary: "Excellent simulation interaction with clear outcomes.",
            },
          },
        }),
      );
      expect(result.earnedPoints).toBe(0);
    });
  });

  describe("graceful handling of missing metadata", () => {
    it("handles null metadata without throwing", () => {
      expect(() =>
        scoreSimulationAssessmentResponse({
          item: { id: "no-meta", points: 10, metadata: null },
          response: {
            trace: {
              interactions: [],
              summary: "some summary",
            },
          },
        }),
      ).not.toThrow();
    });

    it("returns a defined score when metadata is null", () => {
      const result = scoreSimulationAssessmentResponse({
        item: { id: "no-meta", points: 10, metadata: null },
        response: {
          trace: {
            interactions: [],
            summary: "A longer summary with more than twenty-four characters.",
          },
        },
      });
      expect(result.feedback.scorePercent).toBeGreaterThanOrEqual(0);
      expect(result.feedback.scorePercent).toBeLessThanOrEqual(100);
    });
  });
});

// ---------------------------------------------------------------------------
// createSimulationAssessmentItem — IRT calibration and question type cycling
// ---------------------------------------------------------------------------
describe("createSimulationAssessmentItem", () => {
  let service: ReturnType<typeof createSimulationAssessmentIntegration>;

  beforeEach(() => {
    service = createSimulationAssessmentIntegration(makePrisma() as never);
  });

  describe("IRT calibration by difficulty", () => {
    it("sets difficulty=-0.75 for INTRO items", () => {
      const item = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 0,
        difficulty: "INTRO",
        objectiveLabel: "understand projectile ranges",
      });
      expect(
        (item.metadata as { irt: { difficulty: number } }).irt.difficulty,
      ).toBe(-0.75);
    });

    it("sets difficulty=0 for INTERMEDIATE items", () => {
      const item = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 0,
        difficulty: "INTERMEDIATE",
        objectiveLabel: "understand projectile ranges",
      });
      expect(
        (item.metadata as { irt: { difficulty: number } }).irt.difficulty,
      ).toBe(0);
    });

    it("sets difficulty=0.85 for ADVANCED items", () => {
      const item = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 0,
        difficulty: "ADVANCED",
        objectiveLabel: "understand projectile ranges",
      });
      expect(
        (item.metadata as { irt: { difficulty: number } }).irt.difficulty,
      ).toBe(0.85);
    });

    it("always sets guessing=0.15 regardless of difficulty", () => {
      const intro = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 0,
        difficulty: "INTRO",
        objectiveLabel: "objective",
      });
      const advanced = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 0,
        difficulty: "ADVANCED",
        objectiveLabel: "objective",
      });
      expect(
        (intro.metadata as { irt: { guessing: number } }).irt.guessing,
      ).toBe(0.15);
      expect(
        (advanced.metadata as { irt: { guessing: number } }).irt.guessing,
      ).toBe(0.15);
    });
  });

  describe("discrimination by question type", () => {
    it("sets discrimination=1.05 for prediction items (index 0)", () => {
      const item = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 0, // prediction
        difficulty: "INTERMEDIATE",
        objectiveLabel: "objective",
      });
      expect(
        (item.metadata as { irt: { discrimination: number } }).irt
          .discrimination,
      ).toBe(1.05);
    });

    it("sets discrimination=1.15 for parameter_identification items (index 1)", () => {
      const item = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 1, // parameter_identification
        difficulty: "INTERMEDIATE",
        objectiveLabel: "objective",
      });
      expect(
        (item.metadata as { irt: { discrimination: number } }).irt
          .discrimination,
      ).toBe(1.15);
    });

    it("sets discrimination=1.25 for process_explanation items (index 2)", () => {
      const item = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 2, // process_explanation
        difficulty: "INTERMEDIATE",
        objectiveLabel: "objective",
      });
      expect(
        (item.metadata as { irt: { discrimination: number } }).irt
          .discrimination,
      ).toBe(1.25);
    });
  });

  describe("question type cycling", () => {
    it("cycles through prediction → parameter_identification → process_explanation → prediction", () => {
      const item0 = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 0,
        difficulty: "INTERMEDIATE",
        objectiveLabel: "objective",
      });
      const item1 = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 1,
        difficulty: "INTERMEDIATE",
        objectiveLabel: "objective",
      });
      const item2 = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 2,
        difficulty: "INTERMEDIATE",
        objectiveLabel: "objective",
      });
      const item3 = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 3, // wraps back to prediction
        difficulty: "INTERMEDIATE",
        objectiveLabel: "objective",
      });

      expect((item0.metadata as { questionType: string }).questionType).toBe(
        "prediction",
      );
      expect((item1.metadata as { questionType: string }).questionType).toBe(
        "parameter_identification",
      );
      expect((item2.metadata as { questionType: string }).questionType).toBe(
        "process_explanation",
      );
      expect((item3.metadata as { questionType: string }).questionType).toBe(
        "prediction",
      );
    });
  });

  describe("manifest profile extraction", () => {
    it("extracts parameter IDs from manifest step actions", () => {
      const item = service.createSimulationAssessmentItem({
        manifest: makeManifest({
          manifest: {
            interactionType: "parameter_exploration",
            steps: [
              {
                actions: [
                  { action: "adjust_velocity", parameterId: "velocity" },
                  { action: "adjust_angle", parameterId: "angle" },
                ],
              },
            ],
          },
        }),
        itemIndex: 0,
        difficulty: "INTERMEDIATE",
        objectiveLabel: "projectile ranges",
      });

      expect(
        (item.metadata as { expectedParameters: string[] }).expectedParameters,
      ).toEqual(expect.arrayContaining(["velocity", "angle"]));
    });

    it("produces a valid simulation_interaction type item", () => {
      const item = service.createSimulationAssessmentItem({
        manifest: makeManifest(),
        itemIndex: 0,
        difficulty: "INTRO",
        objectiveLabel: "momentum and force",
      });
      expect(item.type).toBe("simulation_interaction");
      expect(item.id).toContain("sim-manifest-1");
      expect(item.prompt).toBeTruthy();
      expect(item.rubric).toBeTruthy();
      expect(typeof item.points).toBe("number");
    });
  });
});

// ---------------------------------------------------------------------------
// summarizeSimulationAttempt — completeness and rollup
// ---------------------------------------------------------------------------
describe("summarizeSimulationAttempt", () => {
  it("returns zero counts when there are no simulation items", () => {
    const summary = summarizeSimulationAttempt({
      items: [{ id: "q1", type: "multiple_choice", metadata: null }],
      responses: {},
    });
    expect(summary.totalSimulationItems).toBe(0);
    expect(summary.completedSimulationItems).toBe(0);
    expect(summary.averageScorePercent).toBe(0);
    expect(summary.insights).toHaveLength(0);
  });

  it("counts simulation items separately from other item types", () => {
    const summary = summarizeSimulationAttempt({
      items: [
        {
          id: "sim-1",
          type: "simulation_interaction",
          metadata: {
            simulationManifestId: "m1",
            simulationTitle: "T",
            simulationDomain: "PHYSICS",
            questionType: "prediction",
            interactionType: "parameter_exploration",
            expectedParameters: [],
            expectedOutcomeKeywords: [],
            irt: { discrimination: 1, difficulty: 0, guessing: 0.15 },
          },
        },
        { id: "q1", type: "multiple_choice", metadata: null },
      ],
      responses: {
        "sim-1": {
          type: "simulation_interaction",
          trace: { interactions: [] },
        },
      },
    });
    expect(summary.totalSimulationItems).toBe(1);
  });

  it("uses stored feedback scorePercent when provided, not recomputed score", () => {
    const summary = summarizeSimulationAttempt({
      items: [
        {
          id: "sim-1",
          type: "simulation_interaction",
          metadata: {
            simulationManifestId: "m1",
            simulationTitle: "T",
            simulationDomain: "PHYSICS",
            questionType: "prediction",
            interactionType: "parameter_exploration",
            expectedParameters: [],
            expectedOutcomeKeywords: [],
            irt: { discrimination: 1, difficulty: 0, guessing: 0.15 },
          },
        },
      ],
      responses: {
        "sim-1": {
          type: "simulation_interaction",
          trace: { interactions: [] },
        },
      },
      feedback: [
        {
          itemId: "sim-1" as never,
          scorePercent: 77, // explicit stored score
          needsReview: false,
        },
      ],
    });
    expect(summary.averageScorePercent).toBe(77);
  });
});
