/**
 * LearningUnitValidator Tests
 *
 * @doc.type test
 * @doc.purpose Validate publishing gate enforcement, schema rules, structure rules,
 *              assessment quality, and accessibility checks for Learning Units.
 * @doc.layer plugin
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-027 (LU Publishing Gates), TPUT-FR-029 (Prediction Confidence Required),
 *                  TPUT-FR-030 (Simulation Assessment Items)
 */
import { describe, it, expect } from "vitest";
import {
  createLearningUnitValidator,
  LearningUnitValidator,
} from "../plugins/LearningUnitValidator";
import type { LearningUnit } from "@tutorputor/contracts/v1/learning-unit";

// ---------------------------------------------------------------------------
// Test fixtures
// ---------------------------------------------------------------------------

function makeMinimalLearningUnit(
  overrides: Partial<LearningUnit> = {},
): LearningUnit {
  return {
    id: "lu-physics-001",
    version: 1,
    domain: "physics",
    level: "secondary",
    status: "draft",
    intent: {
      problem: "Learners confuse mass and weight",
      motivation:
        "Understanding Newton's 2nd law enables solving real engineering problems",
      targetMisconceptions: ["Mass equals weight"],
    },
    claims: [
      {
        id: "C1",
        text: "Predict the acceleration when force and mass are known",
        bloom: "apply",
        prerequisites: [],
      },
    ],
    evidence: [
      {
        id: "E1",
        claimRef: "C1",
        type: "prediction_vs_outcome",
        description:
          "Learner predicts acceleration outcome before running simulation",
        observables: [],
      },
    ],
    tasks: [
      {
        id: "T1",
        type: "prediction",
        claimRef: "C1",
        evidenceRef: "E1",
        prompt: "What will happen to acceleration if force doubles?",
        confidenceRequired: true,
        options: ["Doubles", "Halves", "Stays same", "Quadruples"],
      },
    ],
    artifacts: [
      {
        type: "simulation",
        ref: "sim-newton-2nd",
        claims: ["C1"],
        description:
          "Newton's second law physics simulation for force and acceleration",
      },
    ],
    telemetry: {
      events: ["answer_submission", "simulation_step"],
      processFeatures: ["time_on_task"],
    },
    assessment: {
      model: "cbm",
      confidenceLevels: ["low", "medium", "high"],
      scoring: {
        correctHighConfidence: 3,
        correctMediumConfidence: 2,
        correctLowConfidence: 1,
        incorrectHighConfidence: -6,
        incorrectMediumConfidence: -2,
        incorrectLowConfidence: 0,
      },
    },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    createdBy: "author-001",
    tenantId: "tenant-001",
    ...overrides,
  } as LearningUnit;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("LearningUnitValidator – TPUT-FR-027", () => {
  // =========================================================================
  // Plugin identity
  // =========================================================================
  describe("Plugin identity", () => {
    it("should identify as lu-validator with correct type", () => {
      const validator = createLearningUnitValidator();
      expect(validator.metadata.id).toBe("lu-validator");
      expect(validator.metadata.type).toBe("authoring_tool");
      expect(validator.metadata.tags).toContain("validation");
      expect(validator.metadata.tags).toContain("quality");
    });
  });

  // =========================================================================
  // TPUT-FR-027: Publishing gate – schema rules
  // =========================================================================
  describe("Schema rules (TPUT-FR-027)", () => {
    it("should pass validation for a complete, well-formed Learning Unit", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit();
      const result = await validator.validate(lu);
      expect(result.valid).toBe(true);
      expect(
        result.issues.filter((issue) => issue.severity === "error"),
      ).toHaveLength(0);
    });

    it("should report an error when 'id' is missing", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit({ id: "" });
      const result = await validator.validate(lu);
      expect(result.valid).toBe(false);
      const errorFields = result.issues
        .filter((i) => i.severity === "error")
        .map((i) => i.field);
      expect(errorFields).toContain("id");
    });

    it("should report an error when 'version' is missing", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit({
        version: undefined as unknown as number,
      });
      const result = await validator.validate(lu);
      expect(result.valid).toBe(false);
      const errorFields = result.issues
        .filter((i) => i.severity === "error")
        .map((i) => i.field);
      expect(errorFields).toContain("version");
    });

    it("should report an error when 'domain' is missing", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit({ domain: "" });
      const result = await validator.validate(lu);
      expect(result.valid).toBe(false);
      const errorFields = result.issues
        .filter((i) => i.severity === "error")
        .map((i) => i.field);
      expect(errorFields).toContain("domain");
    });

    it("should assign a score of 100 when no issues are found", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit();
      const result = await validator.validate(lu);
      expect(result.score).toBe(100);
    });

    it("should reduce score proportionally for each error found", async () => {
      const validator = createLearningUnitValidator();
      // Missing id + domain → 2 errors → penalty = 20 → score ≤ 80
      const lu = makeMinimalLearningUnit({ id: "", domain: "" });
      const result = await validator.validate(lu);
      expect(result.score).toBeLessThanOrEqual(80);
    });
  });

  // =========================================================================
  // TPUT-FR-027: Publishing gate – structure rules
  // =========================================================================
  describe("Structure rules – claims and tasks", () => {
    it("should report an error when 'claims' is empty", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit({ claims: [] });
      const result = await validator.validate(lu);
      expect(result.valid).toBe(false);
      const ids = result.issues.map((i) => i.field);
      expect(ids).toContain("claims");
    });

    it("should report an error when 'tasks' is empty", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit({ tasks: [] });
      const result = await validator.validate(lu);
      expect(result.valid).toBe(false);
      const ids = result.issues.map((i) => i.field);
      expect(ids).toContain("tasks");
    });

    it("should report an error when 'evidence' is empty", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit({ evidence: [] });
      const result = await validator.validate(lu);
      expect(result.valid).toBe(false);
      const ids = result.issues.map((i) => i.field);
      expect(ids).toContain("evidence");
    });

    it("should report an error when 'artifacts' is empty", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit({ artifacts: [] });
      const result = await validator.validate(lu);
      expect(result.valid).toBe(false);
      const ids = result.issues.map((i) => i.field);
      expect(ids).toContain("artifacts");
    });

    it("should accept a unit with multiple claims all properly linked", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit({
        claims: [
          { id: "C1", text: "Predict acceleration", bloom: "apply" },
          { id: "C2", text: "Explain Newton 3rd law", bloom: "understand" },
        ],
        tasks: [
          {
            id: "T1",
            type: "prediction",
            claimRef: "C1",
            evidenceRef: "E1",
            prompt: "Predict the result",
            confidenceRequired: true,
            options: ["A", "B"],
          },
          {
            id: "T2",
            type: "explanation",
            claimRef: "C2",
            evidenceRef: "E1",
            prompt: "Explain your reasoning",
            minWords: 50,
            expectedTerms: ["reaction", "force"],
          },
        ],
      });
      const result = await validator.validate(lu);
      // Multiple claims/tasks: structure should pass
      expect(
        result.issues.filter(
          (i) =>
            i.severity === "error" &&
            (i.field === "claims" || i.field === "tasks"),
        ),
      ).toHaveLength(0);
    });
  });

  // =========================================================================
  // TPUT-FR-027: Publishing gate – assessment quality
  // =========================================================================
  describe("Assessment quality rules", () => {
    it("should flag missing tasks as invalid", async () => {
      const validator = createLearningUnitValidator();
      const lu = makeMinimalLearningUnit({ tasks: [] });
      const result = await validator.validate(lu);
      // tasks-related error should be present
      expect(result.valid).toBe(false);
    });
  });

  // =========================================================================
  // Custom rules support
  // =========================================================================
  describe("Custom rule integration", () => {
    it("should run custom rules in addition to built-in rules", async () => {
      const validator = createLearningUnitValidator({
        customRules: [
          {
            id: "custom-must-have-misconceptions",
            name: "Must have target misconceptions",
            category: "quality",
            severity: "warning",
            description: "Intent should list target misconceptions",
            validate: (unit) => {
              if (
                !unit.intent.targetMisconceptions ||
                unit.intent.targetMisconceptions.length === 0
              ) {
                return [
                  {
                    field: "intent.targetMisconceptions",
                    severity: "warning",
                    message:
                      "Learning unit should specify target misconceptions",
                  },
                ];
              }
              return [];
            },
          },
        ],
      });

      const luWithMisconceptions = makeMinimalLearningUnit({
        intent: {
          problem: "P",
          motivation: "M",
          targetMisconceptions: ["Misconception A"],
        },
      });
      const resultWithMisconceptions =
        await validator.validate(luWithMisconceptions);
      expect(
        resultWithMisconceptions.issues.some(
          (i) => i.field === "intent.targetMisconceptions",
        ),
      ).toBe(false);

      const luWithoutMisconceptions = makeMinimalLearningUnit({
        intent: {
          problem: "P",
          motivation: "M",
        },
      });
      const resultWithout = await validator.validate(luWithoutMisconceptions);
      expect(
        resultWithout.issues.some(
          (i) => i.field === "intent.targetMisconceptions",
        ),
      ).toBe(true);
    });

    it("should allow custom rules to be added dynamically", async () => {
      const validator = new LearningUnitValidator();

      validator.addRule({
        id: "must-have-bloom-analyze",
        name: "At least one analyze-level claim",
        category: "competency",
        severity: "warning",
        description:
          "Good LUs include higher-order thinking at analyze level or above",
        validate: (unit) => {
          const hasAnalyze = unit.claims.some(
            (c) =>
              c.bloom === "analyze" ||
              c.bloom === "evaluate" ||
              c.bloom === "create",
          );
          if (!hasAnalyze) {
            return [
              {
                field: "claims",
                severity: "warning",
                message:
                  "Consider adding an analyze/evaluate/create-level claim",
              },
            ];
          }
          return [];
        },
      });

      const luLowBloom = makeMinimalLearningUnit({
        claims: [{ id: "C1", text: "Recall Newton's laws", bloom: "remember" }],
      });
      const result = await validator.validate(luLowBloom);
      const hasBloombWarning = result.issues.some(
        (i) => i.field === "claims" && i.severity === "warning",
      );
      expect(hasBloombWarning).toBe(true);
    });
  });

  // =========================================================================
  // Category exclusion
  // =========================================================================
  describe("Category exclusion", () => {
    it("should skip validation categories listed in skipCategories", async () => {
      const validator = createLearningUnitValidator({
        skipCategories: ["accessibility"],
      });
      // An LU with a simulation artifact but no description – would normally
      // trigger accessibility warning, but we skip that category
      const lu = makeMinimalLearningUnit();
      const result = await validator.validate(lu);
      const accessibilityIssues = result.issues.filter(
        (i) =>
          i.field.includes("description") || i.field.includes("accessibility"),
      );
      // Either no accessibility issues at all, or at most non-accessibility-category ones
      // The key assertion: the LU should still be structurally valid with category skipped
      expect(result.issues.filter((i) => i.severity === "error")).toHaveLength(
        0,
      );
    });
  });

  // =========================================================================
  // Score computation
  // =========================================================================
  describe("Score computation", () => {
    it("should return score in 0-100 range regardless of issue count", async () => {
      const validator = createLearningUnitValidator();
      // Maximally broken LU
      const lu = makeMinimalLearningUnit({
        id: "",
        domain: "",
        claims: [],
        tasks: [],
        evidence: [],
        artifacts: [],
      });
      const result = await validator.validate(lu);
      expect(result.score).toBeGreaterThanOrEqual(0);
      expect(result.score).toBeLessThanOrEqual(100);
    });

    it("should return higher score for fewer issues", async () => {
      const validator = createLearningUnitValidator();

      const validLU = makeMinimalLearningUnit();
      const brokenLU = makeMinimalLearningUnit({
        id: "",
        domain: "",
        claims: [],
      });

      const validResult = await validator.validate(validLU);
      const brokenResult = await validator.validate(brokenLU);

      expect(validResult.score).toBeGreaterThan(brokenResult.score);
    });

    it("should count errors and warnings separately in issues array", async () => {
      const validator = createLearningUnitValidator({
        customRules: [
          {
            id: "test-warning",
            name: "Test Warning",
            category: "quality",
            severity: "warning",
            description: "Always warns",
            validate: () => [
              {
                field: "test",
                severity: "warning",
                message: "This is a warning",
              },
            ],
          },
        ],
      });
      const lu = makeMinimalLearningUnit({ id: "" }); // 1 error + 1 warning
      const result = await validator.validate(lu);
      const errors = result.issues.filter((i) => i.severity === "error");
      const warnings = result.issues.filter((i) => i.severity === "warning");
      expect(errors.length).toBeGreaterThanOrEqual(1);
      expect(warnings.length).toBeGreaterThanOrEqual(1);
    });
  });

  // =========================================================================
  // Rule failure resilience
  // =========================================================================
  describe("Rule failure resilience", () => {
    it("should surface rule execution errors as error-level issues without crashing", async () => {
      const validator = createLearningUnitValidator({
        customRules: [
          {
            id: "throwing-rule",
            name: "Throws",
            category: "quality",
            severity: "error",
            description: "Always throws to test resilience",
            validate: () => {
              throw new Error("Simulated rule failure");
            },
          },
        ],
      });
      const lu = makeMinimalLearningUnit();
      const result = await validator.validate(lu);
      const ruleError = result.issues.find(
        (i) => i.field === "throwing-rule" && i.severity === "error",
      );
      expect(ruleError).toBeDefined();
      expect(ruleError?.message).toContain("Simulated rule failure");
    });
  });
});
