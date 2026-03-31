/**
 * Manifest Contract Unification Tests
 *
 * @doc.type test
 * @doc.purpose Verify canonical manifest contracts, validation rules, and validator
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect } from "vitest";
import {
  MANIFEST_VALIDATION_RULES,
  type WorkedExampleManifest,
  type AnimationManifest,
  type AssessmentManifest,
  type ManifestPayloadMap,
  type AnimationKeyframe,
  type WorkedExampleStep,
  type AssessmentItem,
} from "../../../../../../../contracts/v1/artifact-manifests";
import {
  validateManifest,
  validateWorkedExample,
  validateAnimation,
  validateAssessment,
} from "../manifest-validator";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeValidWorkedExample(): WorkedExampleManifest {
  return {
    schemaVersion: "1.0.0",
    exampleType: "step_by_step",
    domain: "physics",
    difficulty: "intermediate",
    problemStatement:
      "Calculate the force on a 10 kg object accelerating at 5 m/s²",
    givenData: { mass: 10, acceleration: 5 },
    steps: [
      {
        stepNumber: 1,
        label: "Identify known values",
        content: "m = 10 kg, a = 5 m/s²",
      },
      {
        stepNumber: 2,
        label: "Apply Newton's Second Law",
        content: "F = m × a = 10 × 5 = 50 N",
        expression: "F = ma",
        claimRef: "C1",
      },
    ],
    answer: "50",
    answerUnit: "N",
    misconceptions: ["Force and mass are the same thing"],
    claimRefs: ["C1"],
    scaffolding: "medium",
    estimatedTimeSeconds: 120,
    accessibility: { screenReaderNarration: true },
  };
}

function makeValidAnimation(): AnimationManifest {
  return {
    schemaVersion: "1.0.0",
    animationType: "process_visualization",
    domain: "biology",
    canvas: { width: 800, height: 600, backgroundColor: "#FFFFFF" },
    durationMs: 5000,
    fps: 30,
    playback: {
      autoPlay: false,
      loop: false,
      allowScrub: true,
      playbackRates: [0.5, 1, 2],
    },
    keyframes: [
      {
        timeMs: 0,
        entities: { cell: { x: 100, y: 100, scale: 1, label: "Cell" } },
        narration: "This is a cell",
      },
      {
        timeMs: 5000,
        entities: { cell: { x: 400, y: 300, scale: 2, label: "Divided Cell" } },
        narration: "The cell has divided",
        pause: true,
      },
    ],
    visualDesign: { theme: "modern_education", primaryColor: "#3B82F6" },
    accessibility: {
      screenReaderNarration: true,
      reducedMotion: false,
      highContrast: false,
      altText: "Animation showing cell division",
    },
    claimRefs: ["C2"],
    estimatedTimeSeconds: 10,
  };
}

function makeValidAssessment(): AssessmentManifest {
  return {
    schemaVersion: "1.0.0",
    title: "Newton's Laws Quiz",
    domain: "physics",
    purpose: "formative",
    totalPoints: 10,
    timeLimitMinutes: 15,
    itemOrder: "fixed",
    items: [
      {
        itemId: "Q1",
        itemType: "multiple_choice",
        prompt: "What is Newton's Second Law?",
        points: 5,
        bloomLevel: "understand",
        claimRef: "C1",
        options: [
          { id: "A", text: "F = ma", isCorrect: true, feedback: "Correct!" },
          { id: "B", text: "F = mv", isCorrect: false, feedback: "Not quite." },
        ],
        feedback: { correct: "Well done!", showExplanation: true },
      },
      {
        itemId: "Q2",
        itemType: "numeric",
        prompt: "Calculate F when m=5kg and a=3m/s²",
        points: 5,
        bloomLevel: "apply",
        claimRef: "C1",
        expectedAnswer: 15,
        tolerance: 0.1,
      },
    ],
    passingThreshold: 70,
    claimRefs: ["C1"],
    maxAttempts: 3,
    grading: {
      partialCredit: true,
      penalizeGuessing: false,
      showScoreImmediately: true,
    },
    accessibility: { screenReaderCompatible: true },
  };
}

// ---------------------------------------------------------------------------
// Contract shape tests
// ---------------------------------------------------------------------------

describe("Manifest Contract Types", () => {
  describe("WorkedExampleManifest", () => {
    it("has all required fields", () => {
      const example = makeValidWorkedExample();
      expect(example.schemaVersion).toBe("1.0.0");
      expect(example.exampleType).toBe("step_by_step");
      expect(example.problemStatement).toBeTruthy();
      expect(example.steps).toHaveLength(2);
      expect(example.answer).toBe("50");
      expect(example.scaffolding).toBe("medium");
    });

    it("step shape is correct", () => {
      const step: WorkedExampleStep = {
        stepNumber: 1,
        label: "Step 1",
        content: "Do the thing",
        expression: "x = 1",
        hint: "Try harder",
        claimRef: "C1",
        diagramRef: "diagram-1",
      };
      expect(step.stepNumber).toBe(1);
      expect(step.diagramRef).toBe("diagram-1");
    });

    it("supports all ExampleType values", () => {
      const types: WorkedExampleManifest["exampleType"][] = [
        "real_world_application",
        "step_by_step",
        "visual_representation",
        "problem_solving",
        "analogy",
        "case_study",
        "comparison",
        "counter_example",
      ];
      expect(types).toHaveLength(8);
    });
  });

  describe("AnimationManifest", () => {
    it("has all required fields", () => {
      const anim = makeValidAnimation();
      expect(anim.schemaVersion).toBe("1.0.0");
      expect(anim.animationType).toBe("process_visualization");
      expect(anim.canvas.width).toBe(800);
      expect(anim.durationMs).toBe(5000);
      expect(anim.keyframes).toHaveLength(2);
      expect(anim.playback.autoPlay).toBe(false);
    });

    it("keyframe shape is correct", () => {
      const kf: AnimationKeyframe = {
        timeMs: 1000,
        entities: {
          ball: { x: 50, y: 100, rotation: 45, opacity: 0.8, color: "#FF0000" },
        },
        narration: "Ball moves",
        pause: false,
      };
      expect(kf.timeMs).toBe(1000);
      expect(kf.entities.ball.rotation).toBe(45);
    });

    it("supports all AnimationContentType values", () => {
      const types: AnimationManifest["animationType"][] = [
        "process_visualization",
        "timeline",
        "spatial_relationship",
        "cause_effect",
        "transformation",
        "comparison",
        "concept_visualization",
        "data_representation",
        "process_walkthrough",
      ];
      expect(types).toHaveLength(9);
    });
  });

  describe("AssessmentManifest", () => {
    it("has all required fields", () => {
      const assessment = makeValidAssessment();
      expect(assessment.schemaVersion).toBe("1.0.0");
      expect(assessment.title).toBe("Newton's Laws Quiz");
      expect(assessment.purpose).toBe("formative");
      expect(assessment.totalPoints).toBe(10);
      expect(assessment.items).toHaveLength(2);
      expect(assessment.itemOrder).toBe("fixed");
    });

    it("item shape covers all item types", () => {
      const types: AssessmentItem["itemType"][] = [
        "multiple_choice",
        "short_answer",
        "long_answer",
        "numeric",
        "matching",
        "ordering",
        "simulation_based",
        "prediction",
        "manipulation",
        "explanation",
      ];
      expect(types).toHaveLength(10);
    });

    it("supports all assessment purposes", () => {
      const purposes: AssessmentManifest["purpose"][] = [
        "diagnostic",
        "formative",
        "summative",
        "practice",
      ];
      expect(purposes).toHaveLength(4);
    });

    it("supports all bloom levels on items", () => {
      const levels: NonNullable<AssessmentItem["bloomLevel"]>[] = [
        "remember",
        "understand",
        "apply",
        "analyze",
        "evaluate",
        "create",
      ];
      expect(levels).toHaveLength(6);
    });
  });

  describe("ManifestPayloadMap", () => {
    it("covers all four artifact types", () => {
      const keys: (keyof ManifestPayloadMap)[] = [
        "worked_example",
        "simulation",
        "animation",
        "assessment",
      ];
      expect(keys).toHaveLength(4);
    });
  });
});

// ---------------------------------------------------------------------------
// Validation rules tests
// ---------------------------------------------------------------------------

describe("MANIFEST_VALIDATION_RULES", () => {
  it("defines rules for all four manifest types", () => {
    expect(Object.keys(MANIFEST_VALIDATION_RULES)).toHaveLength(4);
    expect(MANIFEST_VALIDATION_RULES.worked_example).toBeDefined();
    expect(MANIFEST_VALIDATION_RULES.simulation).toBeDefined();
    expect(MANIFEST_VALIDATION_RULES.animation).toBeDefined();
    expect(MANIFEST_VALIDATION_RULES.assessment).toBeDefined();
  });

  it("worked_example requires schemaVersion, exampleType, problemStatement, steps, answer, scaffolding", () => {
    const fields = MANIFEST_VALIDATION_RULES.worked_example
      .filter((r) => r.severity === "error")
      .map((r) => r.field);
    expect(fields).toContain("schemaVersion");
    expect(fields).toContain("exampleType");
    expect(fields).toContain("problemStatement");
    expect(fields).toContain("steps");
    expect(fields).toContain("answer");
    expect(fields).toContain("scaffolding");
  });

  it("animation requires schemaVersion, animationType, canvas, durationMs, keyframes, playback", () => {
    const fields = MANIFEST_VALIDATION_RULES.animation
      .filter((r) => r.severity === "error")
      .map((r) => r.field);
    expect(fields).toContain("schemaVersion");
    expect(fields).toContain("animationType");
    expect(fields).toContain("canvas");
    expect(fields).toContain("durationMs");
    expect(fields).toContain("keyframes");
    expect(fields).toContain("playback");
  });

  it("assessment requires schemaVersion, title, purpose, items, totalPoints, itemOrder", () => {
    const fields = MANIFEST_VALIDATION_RULES.assessment
      .filter((r) => r.severity === "error")
      .map((r) => r.field);
    expect(fields).toContain("schemaVersion");
    expect(fields).toContain("title");
    expect(fields).toContain("purpose");
    expect(fields).toContain("items");
    expect(fields).toContain("totalPoints");
    expect(fields).toContain("itemOrder");
  });

  it("simulation requires schemaVersion, domain, canvas, playback, initialEntities, steps", () => {
    const fields = MANIFEST_VALIDATION_RULES.simulation
      .filter((r) => r.severity === "error")
      .map((r) => r.field);
    expect(fields).toContain("schemaVersion");
    expect(fields).toContain("domain");
    expect(fields).toContain("canvas");
    expect(fields).toContain("playback");
    expect(fields).toContain("initialEntities");
    expect(fields).toContain("steps");
  });

  it("warnings have severity 'warning'", () => {
    for (const [, rules] of Object.entries(MANIFEST_VALIDATION_RULES)) {
      for (const rule of rules) {
        expect(["error", "warning"]).toContain(rule.severity);
      }
    }
  });
});

// ---------------------------------------------------------------------------
// Manifest validator tests
// ---------------------------------------------------------------------------

describe("Manifest Validator", () => {
  describe("validateWorkedExample", () => {
    it("passes for a valid worked example", () => {
      const result = validateWorkedExample(makeValidWorkedExample());
      expect(result.isValid).toBe(true);
      expect(result.manifestType).toBe("worked_example");
      expect(
        result.violations.filter((v) => v.severity === "error"),
      ).toHaveLength(0);
    });

    it("fails when schemaVersion is missing", () => {
      const payload = { ...makeValidWorkedExample() } as any;
      delete payload.schemaVersion;
      const result = validateWorkedExample(payload);
      expect(result.isValid).toBe(false);
      expect(result.violations.some((v) => v.field === "schemaVersion")).toBe(
        true,
      );
    });

    it("fails when steps array is empty", () => {
      const payload = { ...makeValidWorkedExample(), steps: [] };
      const result = validateWorkedExample(payload);
      expect(result.isValid).toBe(false);
      expect(result.violations.some((v) => v.field === "steps")).toBe(true);
    });

    it("fails when answer is missing", () => {
      const payload = { ...makeValidWorkedExample() } as any;
      delete payload.answer;
      const result = validateWorkedExample(payload);
      expect(result.isValid).toBe(false);
      expect(result.violations.some((v) => v.field === "answer")).toBe(true);
    });

    it("warns when claimRefs is empty", () => {
      const payload = { ...makeValidWorkedExample(), claimRefs: [] };
      const result = validateWorkedExample(payload);
      // Still valid because claimRefs is a warning
      expect(result.isValid).toBe(true);
      expect(
        result.violations.some(
          (v) => v.field === "claimRefs" && v.severity === "warning",
        ),
      ).toBe(true);
    });
  });

  describe("validateAnimation", () => {
    it("passes for a valid animation", () => {
      const result = validateAnimation(makeValidAnimation());
      expect(result.isValid).toBe(true);
      expect(result.manifestType).toBe("animation");
    });

    it("fails when duration is too small", () => {
      const payload = { ...makeValidAnimation(), durationMs: 50 };
      const result = validateAnimation(payload);
      expect(result.isValid).toBe(false);
      expect(result.violations.some((v) => v.field === "durationMs")).toBe(
        true,
      );
    });

    it("fails when keyframes has fewer than 2 entries", () => {
      const payload = {
        ...makeValidAnimation(),
        keyframes: [{ timeMs: 0, entities: {} }],
      };
      const result = validateAnimation(payload);
      expect(result.isValid).toBe(false);
      expect(result.violations.some((v) => v.field === "keyframes")).toBe(true);
    });

    it("fails when canvas is missing", () => {
      const payload = { ...makeValidAnimation() } as any;
      delete payload.canvas;
      const result = validateAnimation(payload);
      expect(result.isValid).toBe(false);
      expect(result.violations.some((v) => v.field === "canvas")).toBe(true);
    });

    it("warns when accessibility.altText is missing", () => {
      const payload = {
        ...makeValidAnimation(),
        accessibility: { screenReaderNarration: true },
      };
      const result = validateAnimation(payload);
      expect(result.isValid).toBe(true);
      expect(
        result.violations.some(
          (v) =>
            v.field === "accessibility.altText" && v.severity === "warning",
        ),
      ).toBe(true);
    });
  });

  describe("validateAssessment", () => {
    it("passes for a valid assessment", () => {
      const result = validateAssessment(makeValidAssessment());
      expect(result.isValid).toBe(true);
      expect(result.manifestType).toBe("assessment");
    });

    it("fails when title is missing", () => {
      const payload = { ...makeValidAssessment() } as any;
      delete payload.title;
      const result = validateAssessment(payload);
      expect(result.isValid).toBe(false);
      expect(result.violations.some((v) => v.field === "title")).toBe(true);
    });

    it("fails when items is empty", () => {
      const payload = { ...makeValidAssessment(), items: [] };
      const result = validateAssessment(payload);
      expect(result.isValid).toBe(false);
      expect(result.violations.some((v) => v.field === "items")).toBe(true);
    });

    it("fails when totalPoints is zero", () => {
      const payload = { ...makeValidAssessment(), totalPoints: 0 };
      const result = validateAssessment(payload);
      expect(result.isValid).toBe(false);
      expect(result.violations.some((v) => v.field === "totalPoints")).toBe(
        true,
      );
    });

    it("warns when claimRefs is empty", () => {
      const payload = { ...makeValidAssessment(), claimRefs: [] };
      const result = validateAssessment(payload);
      expect(result.isValid).toBe(true);
      expect(
        result.violations.some(
          (v) => v.field === "claimRefs" && v.severity === "warning",
        ),
      ).toBe(true);
    });
  });

  describe("validateManifest (generic)", () => {
    it("returns valid for simulation type with complete payload", () => {
      const payload: Record<string, unknown> = {
        schemaVersion: "1.0.0",
        domain: "physics",
        canvas: { width: 800, height: 600 },
        playback: { autoPlay: true },
        initialEntities: [{ id: "e1" }],
        steps: [{ stepIndex: 0 }],
      };
      const result = validateManifest("simulation", payload);
      expect(result.isValid).toBe(true);
    });

    it("returns validatedAt timestamp", () => {
      const result = validateManifest(
        "worked_example",
        makeValidWorkedExample() as unknown as Record<string, unknown>,
      );
      expect(result.validatedAt).toBeTruthy();
      expect(new Date(result.validatedAt).getTime()).toBeGreaterThan(0);
    });

    it("accumulates multiple violations", () => {
      const result = validateManifest("worked_example", {} as any);
      // At minimum: schemaVersion, exampleType, problemStatement, steps, answer, scaffolding
      const errors = result.violations.filter((v) => v.severity === "error");
      expect(errors.length).toBeGreaterThanOrEqual(6);
    });
  });
});
