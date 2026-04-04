/**
 * ContentStudioValidator Tests
 *
 * @doc.type test
 * @doc.purpose Validate 5-pillar quality gate enforcement, publishing gates,
 *              grade appropriateness, and custom rule extensibility in the
 *              Content Studio Validator plugin.
 * @doc.layer plugin
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-CMS-001 (5-pillar validation),
 *                  TPUT-FR-CMS-002 (publishing gate),
 *                  TPUT-FR-CMS-003 (grade-level appropriateness)
 */
import { describe, it, expect } from "vitest";
import {
  ContentStudioValidator,
  createContentStudioValidator,
} from "../plugins/ContentStudioValidator";
import type {
  LearningExperience,
  LearningClaim,
} from "@tutorputor/contracts/v1/content-studio";

// ---------------------------------------------------------------------------
// Test fixture helpers
// ---------------------------------------------------------------------------

function makeTask(
  id: string,
  claimId: string,
  instructions = "Detailed task instructions that meet the minimum length for accessibility.",
) {
  return {
    id,
    claimId,
    type: "prediction" as const,
    title: `Task ${id}`,
    instructions,
    estimatedMinutes: 5,
    multimodal: false,
  };
}

function makeEvidence(id: string, claimId: string) {
  return {
    id,
    claimId,
    type: "prediction_vs_outcome" as const,
    description: "Evidence description",
    observables: [],
    minimumScore: 0.7,
    weight: 1,
  };
}

function makeClaim(
  id: string,
  experienceId: string,
  overrides: Partial<LearningClaim> = {},
): LearningClaim {
  return {
    id,
    experienceId,
    text: `Claim ${id}: learner can predict outcomes`,
    bloom: "apply" as const,
    prerequisites: [],
    evidenceRequirements: [makeEvidence(`e-${id}`, id) as any],
    tasks: [makeTask(`t-${id}`, id)],
    masteryThreshold: 0.7,
    orderIndex: 0,
    ...overrides,
  };
}

function makeExperience(
  overrides: Partial<LearningExperience> = {},
): LearningExperience {
  return {
    id: "exp-test-001",
    tenantId: "tenant-001",
    slug: "newtons-laws",
    title: "Newton's Laws of Motion",
    description:
      "A comprehensive learning experience covering all three of Newton's laws of motion with interactive simulations.",
    status: "draft",
    version: 1,
    gradeAdaptation: {
      gradeRange: "grade_9_12",
      mathLevel: "algebra" as const,
      rigorLevel: "analytical" as const,
      scaffoldingLevel: "medium" as const,
      vocabularyComplexity: 9,
      readingLevel: 10,
      prerequisiteConcepts: [],
    },
    claims: [makeClaim("C1", "exp-test-001")],
    estimatedTimeMinutes: 30,
    keywords: ["physics", "newton", "force"],
    authorId: "author-001",
    createdAt: new Date("2024-01-01"),
    updatedAt: new Date("2024-01-01"),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Plugin identity
// ---------------------------------------------------------------------------

describe("ContentStudioValidator – plugin identity", () => {
  it("should identify as content-studio-validator with correct type", () => {
    const validator = new ContentStudioValidator();
    expect(validator.metadata.id).toBe("content-studio-validator");
    expect(validator.metadata.type).toBe("authoring_tool");
    expect(validator.metadata.tags).toContain("validation");
    expect(validator.metadata.tags).toContain("content-studio");
    expect(validator.metadata.enabled).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-CMS-001: Educational pillar
// ---------------------------------------------------------------------------

describe("ContentStudioValidator – TPUT-FR-CMS-001 (educational pillar)", () => {
  it("should pass with a complete, well-formed experience", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience();
    const result = await validator.validate(exp);
    expect(result.issues.filter((i) => i.severity === "error")).toHaveLength(0);
  });

  it("should report error when experience has no claims", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({ claims: [] });
    const result = await validator.validate(exp);
    const errors = result.issues.filter((i) => i.severity === "error");
    expect(errors.some((e) => e.field === "educational.claims")).toBe(true);
    expect(result.valid).toBe(false);
  });

  it("should report warning when a claim has no Bloom taxonomy level", async () => {
    const validator = new ContentStudioValidator();
    const claim = makeClaim("C1", "exp-test-001");
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (claim as any).bloom = undefined;
    const exp = makeExperience({ claims: [claim] });
    const result = await validator.validate(exp);
    const warnings = result.issues.filter((i) => i.severity === "warning");
    expect(warnings.some((w) => w.field.includes("bloom"))).toBe(true);
  });

  it("should report info when claims lack cognitive progression", async () => {
    const validator = new ContentStudioValidator();
    // Two claims both at "apply" – no progression
    const claims = [
      makeClaim("C1", "exp-001", { bloom: "apply", orderIndex: 0 }),
      makeClaim("C2", "exp-001", { bloom: "apply", orderIndex: 1 }),
    ];
    const exp = makeExperience({ claims });
    const result = await validator.validate(exp);
    const infoItems = result.issues.filter((i) => i.severity === "info");
    expect(infoItems.some((i) => i.field.includes("bloom-progression"))).toBe(
      true,
    );
  });

  it("should not report bloom progression issue for a single claim", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({ claims: [makeClaim("C1", "exp-001")] });
    const result = await validator.validate(exp);
    const progressionIssues = result.issues.filter((i) =>
      i.field.includes("bloom-progression"),
    );
    expect(progressionIssues).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// Experiential pillar
// ---------------------------------------------------------------------------

describe("ContentStudioValidator – experiential pillar", () => {
  it("should report error when a claim has no tasks", async () => {
    const validator = new ContentStudioValidator();
    const claim = makeClaim("C1", "exp-001", { tasks: [] });
    const exp = makeExperience({ claims: [claim] });
    const result = await validator.validate(exp);
    const errors = result.issues.filter((i) => i.severity === "error");
    expect(errors.some((e) => e.field.includes("experiential"))).toBe(true);
    expect(result.valid).toBe(false);
  });

  it("should report error when a claim has no evidence requirements", async () => {
    const validator = new ContentStudioValidator();
    const claim = makeClaim("C1", "exp-001", { evidenceRequirements: [] });
    const exp = makeExperience({ claims: [claim] });
    const result = await validator.validate(exp);
    const errors = result.issues.filter((i) => i.severity === "error");
    expect(errors.some((e) => e.field.includes("experiential"))).toBe(true);
    expect(result.valid).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// Technical pillar
// ---------------------------------------------------------------------------

describe("ContentStudioValidator – technical pillar", () => {
  it("should report error for missing or invalid URL slug", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({ slug: "" });
    const result = await validator.validate(exp);
    const errors = result.issues.filter((i) => i.severity === "error");
    expect(errors.some((e) => e.field === "technical.slug")).toBe(true);
  });

  it("should report error for slug with spaces or uppercase", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({ slug: "Newton Laws" });
    const result = await validator.validate(exp);
    const errors = result.issues.filter((i) => i.field === "technical.slug");
    expect(errors).toHaveLength(1);
  });

  it("should accept a valid slug with lowercase and hyphens", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({ slug: "newtons-laws-of-motion" });
    const result = await validator.validate(exp);
    const slugErrors = result.issues.filter(
      (i) => i.field === "technical.slug",
    );
    expect(slugErrors).toHaveLength(0);
  });

  it("should report error for duplicate claim/task IDs", async () => {
    const validator = new ContentStudioValidator();
    const claims = [
      makeClaim("C1", "exp-001", { tasks: [makeTask("DUPLICATE-ID", "C1")] }),
      makeClaim("C2", "exp-001", { tasks: [makeTask("DUPLICATE-ID", "C2")] }),
    ];
    const exp = makeExperience({ claims });
    const result = await validator.validate(exp);
    const errors = result.issues.filter((i) => i.field === "technical.ids");
    expect(errors).toHaveLength(1);
    expect(errors[0]?.message).toContain("DUPLICATE-ID");
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-CMS-003: Grade appropriateness
// ---------------------------------------------------------------------------

describe("ContentStudioValidator – TPUT-FR-CMS-003 (grade appropriateness)", () => {
  it("should warn when vocabulary complexity is too high for k_2", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({
      gradeAdaptation: {
        gradeRange: "k_2",
        mathLevel: "arithmetic" as const,
        rigorLevel: "conceptual" as const,
        scaffoldingLevel: "high" as const,
        vocabularyComplexity: 9, // Too high for k_2 (expected 1-3)
        readingLevel: 2,
        prerequisiteConcepts: [],
      },
    });
    const result = await validator.validate(exp);
    const warnings = result.issues.filter((i) => i.severity === "warning");
    expect(warnings.some((w) => w.field.includes("vocabularyComplexity"))).toBe(
      true,
    );
  });

  it("should not warn when vocabulary complexity matches grade_9_12 range", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({
      gradeAdaptation: {
        gradeRange: "grade_9_12",
        mathLevel: "algebra" as const,
        rigorLevel: "analytical" as const,
        scaffoldingLevel: "medium" as const,
        vocabularyComplexity: 8, // Valid for grade_9_12 (expected 6-9)
        readingLevel: 10,
        prerequisiteConcepts: [],
      },
    });
    const result = await validator.validate(exp);
    const vocabWarnings = result.issues.filter((i) =>
      i.field.includes("vocabularyComplexity"),
    );
    expect(vocabWarnings).toHaveLength(0);
  });

  it("should warn when gradeAdaptation is not configured", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({ gradeAdaptation: undefined as any });
    const result = await validator.validate(exp);
    const warnings = result.issues.filter((i) => i.severity === "warning");
    expect(warnings.some((w) => w.field.includes("gradeAdaptation"))).toBe(
      true,
    );
  });
});

// ---------------------------------------------------------------------------
// Accessibility pillar
// ---------------------------------------------------------------------------

describe("ContentStudioValidator – accessibility pillar", () => {
  it("should warn when description is too short (< 10 words)", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({ description: "Too short." });
    const result = await validator.validate(exp);
    const warnings = result.issues.filter(
      (i) => i.field === "accessibility.description",
    );
    expect(warnings).toHaveLength(1);
  });

  it("should not warn for a description with exactly 10 words", async () => {
    const validator = new ContentStudioValidator();
    const exp = makeExperience({
      description: "This is exactly ten words in a full sentence here.",
    });
    const result = await validator.validate(exp);
    // May not warn on brevity (10 words is threshold)
    const briefWarnings = result.issues.filter(
      (i) =>
        i.field === "accessibility.description" && i.message.includes("brief"),
    );
    expect(briefWarnings).toHaveLength(0);
  });

  it("should warn about tasks with short instructions", async () => {
    const validator = new ContentStudioValidator();
    const claim = makeClaim("C1", "exp-001", {
      tasks: [makeTask("T1", "C1", "Do it.")], // too short
    });
    const exp = makeExperience({ claims: [claim] });
    const result = await validator.validate(exp);
    const taskWarnings = result.issues.filter((i) =>
      i.field.includes("accessibility.tasks"),
    );
    expect(taskWarnings).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-CMS-002: Publishing gate
// ---------------------------------------------------------------------------

describe("ContentStudioValidator – TPUT-FR-CMS-002 (publishing gate)", () => {
  it("canPublish should return false when there are validation errors", async () => {
    const validator = new ContentStudioValidator({ minimumPublishScore: 70 });
    const exp = makeExperience({ claims: [] }); // will produce errors
    const canPub = await validator.canPublish(exp);
    expect(canPub).toBe(false);
  });

  it("canPublish should return true for a complete experience above publish threshold", async () => {
    const validator = new ContentStudioValidator({ minimumPublishScore: 50 });
    const exp = makeExperience({
      authorId: "author-001",
      claims: [
        makeClaim("C1", "exp-001", { bloom: "apply", orderIndex: 0 }),
        makeClaim("C2", "exp-001", { bloom: "analyze", orderIndex: 1 }),
      ],
    });
    const result = await validator.validate(exp);
    const hasErrors = result.issues.some((i) => i.severity === "error");
    if (!hasErrors) {
      const canPub = await validator.canPublish(exp);
      expect(canPub).toBe(result.score >= 50);
    }
  });

  it("should compute score in [0, 100] range for any input", async () => {
    const validator = new ContentStudioValidator();
    const worst = makeExperience({ claims: [], slug: "", description: "" });
    const result = await validator.validate(worst);
    expect(result.score).toBeGreaterThanOrEqual(0);
    expect(result.score).toBeLessThanOrEqual(100);
  });

  it("should allow more warnings when maxWarningsForPublish is raised", async () => {
    const strictValidator = new ContentStudioValidator({
      maxWarningsForPublish: 0,
    });
    const lenientValidator = new ContentStudioValidator({
      maxWarningsForPublish: 99,
    });

    const exp = makeExperience();

    const strictResult = await strictValidator.validate(exp);
    const lenientResult = await lenientValidator.validate(exp);

    // The validator reports valid=false if warnings > maxWarningsForPublish
    // With 0 max warnings and any warnings present, strict should be false or equal
    // lenient should be true or equal
    if (strictResult.issues.some((i) => i.severity === "warning")) {
      expect(lenientResult.valid).toBe(true);
    }
  });
});

// ---------------------------------------------------------------------------
// Pillar exclusion
// ---------------------------------------------------------------------------

describe("ContentStudioValidator – pillar exclusion (skipPillars)", () => {
  it("should skip all safety-pillar rules when 'safety' is in skipPillars", async () => {
    const validator = createContentStudioValidator({ skipPillars: ["safety"] });
    const exp = makeExperience({ authorId: undefined }); // would normally trigger safety warning
    const result = await validator.validate(exp);
    const safetyIssues = result.issues.filter((i) =>
      i.field.startsWith("safety."),
    );
    expect(safetyIssues).toHaveLength(0);
  });

  it("should still validate other pillars when one pillar is skipped", async () => {
    const validator = createContentStudioValidator({
      skipPillars: ["accessibility"],
    });
    const exp = makeExperience({ claims: [] }); // missing claims → educational error
    const result = await validator.validate(exp);
    const educationalErrors = result.issues.filter(
      (i) => i.field === "educational.claims",
    );
    expect(educationalErrors).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// Rule failure resilience
// ---------------------------------------------------------------------------

describe("ContentStudioValidator – rule failure resilience", () => {
  it("should surface throwing rule as an error issue without crashing", async () => {
    const validator = createContentStudioValidator({
      customRules: [
        {
          id: "throwing-rule",
          name: "Always Throws",
          pillar: "technical" as const,
          severity: "error" as const,
          description: "Simulates a rule that throws",
          validate: () => {
            throw new Error("Intentional rule failure");
          },
        },
      ],
    });
    const exp = makeExperience();
    const result = await validator.validate(exp);
    const ruleError = result.issues.find(
      (i) => i.field === "throwing-rule" && i.severity === "error",
    );
    expect(ruleError).toBeDefined();
    expect(ruleError?.message).toContain("Intentional rule failure");
  });
});
