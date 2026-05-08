/**
 * WorkedExampleManifest Schema Tests
 *
 * Tests for typed WorkedExampleManifest schema validation.
 * Ensures generated examples fail persistence if evidence/provenance/schema fields are missing.
 *
 * @doc.type test
 * @doc.purpose WorkedExampleManifest schema validation tests
 * @doc.layer contracts
 * @doc.pattern Unit Test
 */

import { describe, it, expect } from "vitest";
import {
  WorkedExampleManifestSchema,
  validateWorkedExampleManifest,
  createWorkedExampleTemplate,
  type WorkedExampleManifest,
} from "../worked-example-manifest";

describe("WorkedExampleManifest Schema", () => {
  describe("valid manifests", () => {
    it("should accept a valid minimal manifest", () => {
      const manifest: WorkedExampleManifest = {
        schemaVersion: "1.0.0",
        manifestType: "WorkedExample",
        claimRef: "claim-123",
        evidenceRefs: [],
        domain: "MATH",
        gradeBand: "grade_6_8",
        pedagogicalIntent: "illustrate_concept",
        exampleFamily: "worked-solution",
        learnerGoal: "Understand the concept",
        givens: [],
        reasoningSteps: [
          {
            stepNumber: 1,
            description: "First step",
            checkpoint: false,
          },
        ],
        explanationSteps: [
          {
            stepNumber: 1,
            content: "Explanation",
          },
        ],
        misconceptionCheckpoints: [],
        transferPrompts: [],
        adaptationRules: [],
        difficultyEstimate: 0.5,
        estimatedTimeMinutes: 5,
        prerequisites: [],
        evaluationHints: {
          correctIndicators: [],
          misconceptionIndicators: [],
        },
        provenance: {
          generatedBy: "ai",
          createdAt: new Date().toISOString(),
        },
        telemetryProfile: {
          enabled: true,
          privacyLevel: "anonymous",
        },
      };

      const result = WorkedExampleManifestSchema.safeParse(manifest);
      expect(result.success).toBe(true);
    });

    it("should accept a manifest with all optional fields populated", () => {
      const manifest: WorkedExampleManifest = {
        schemaVersion: "1.0.0",
        manifestType: "WorkedExample",
        claimRef: "claim-123",
        evidenceRefs: ["ev-1", "ev-2"],
        objectiveRefs: ["obj-1"],
        domain: "SCIENCE",
        gradeBand: "grade_9_12",
        pedagogicalIntent: "demonstrate_principle",
        exampleFamily: "real-world",
        learnerGoal: "Apply Newton's laws",
        givens: [
          {
            id: "given-1",
            description: "Mass of object",
            value: 10,
            unit: "kg",
          },
        ],
        reasoningSteps: [
          {
            stepNumber: 1,
            description: "Identify forces",
            hint: "Consider gravity and normal force",
            checkpoint: false,
          },
          {
            stepNumber: 2,
            description: "Apply Newton's second law",
            checkpoint: true,
          },
        ],
        explanationSteps: [
          {
            stepNumber: 1,
            content: "Newton's second law states F=ma",
            emphasizes: ["force", "mass", "acceleration"],
          },
        ],
        misconceptionCheckpoints: [
          {
            id: "miscon-1",
            commonError: "Assuming force causes velocity",
            warningSign: "Student writes F=mv",
            correctiveGuidance: "Force causes acceleration, not velocity",
            relatedStepNumber: 2,
          },
        ],
        transferPrompts: [
          {
            id: "transfer-1",
            prompt: "How would this change on the moon?",
            expectedAnswer: "Gravity is weaker, so weight decreases",
            hints: ["Consider gravitational acceleration"],
          },
        ],
        adaptationRules: [
          {
            gradeBand: "grade_3_5",
            modifications: {
              simplifyLanguage: true,
              addScaffolding: true,
            },
          },
        ],
        difficultyEstimate: 0.7,
        estimatedTimeMinutes: 10,
        prerequisites: ["basic_algebra", "force_concept"],
        evaluationHints: {
          correctIndicators: ["Correct application of F=ma"],
          misconceptionIndicators: ["Confusing force and velocity"],
          followUpQuestions: ["What happens if mass doubles?"],
        },
        provenance: {
          generatedBy: "hybrid",
          generationId: "gen-123",
          model: "gpt-4",
          promptHash: "hash-abc123",
          createdAt: "2024-01-15T10:30:00Z",
          updatedAt: "2024-01-15T11:00:00Z",
          authorId: "user-456",
          reviewDecision: "approved",
          reviewNotes: "Reviewed and approved for publication",
        },
        validators: [
          {
            validatorId: "schema-validator",
            validatorType: "schema",
            passed: true,
            validationAt: "2024-01-15T10:35:00Z",
          },
          {
            validatorId: "content-validator",
            validatorType: "content",
            passed: true,
            validationAt: "2024-01-15T10:36:00Z",
          },
        ],
        telemetryProfile: {
          enabled: true,
          events: ["step_viewed", "hint_requested", "answer_submitted"],
          samplingRate: 0.1,
          privacyLevel: "user-id",
          retentionDays: 365,
        },
        createdAt: "2024-01-15T10:30:00Z",
        updatedAt: "2024-01-15T11:00:00Z",
        validationStatus: "valid",
      };

      const result = WorkedExampleManifestSchema.safeParse(manifest);
      expect(result.success).toBe(true);
    });
  });

  describe("invalid manifests", () => {
    it("should reject manifest without schemaVersion", () => {
      const manifest = {
        manifestType: "WorkedExample",
        claimRef: "claim-123",
        evidenceRefs: [],
        domain: "MATH",
        gradeBand: "grade_6_8",
        pedagogicalIntent: "illustrate_concept",
        exampleFamily: "worked-solution" as const,
        learnerGoal: "Understand",
        givens: [],
        reasoningSteps: [{ stepNumber: 1, description: "Step 1" }],
        explanationSteps: [{ stepNumber: 1, content: "Content" }],
        misconceptionCheckpoints: [],
        transferPrompts: [],
        adaptationRules: [],
        difficultyEstimate: 0.5,
        estimatedTimeMinutes: 5,
        prerequisites: [],
        evaluationHints: { correctIndicators: [], misconceptionIndicators: [] },
      };

      const result = WorkedExampleManifestSchema.safeParse(manifest);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((e: any) => e.path.includes("schemaVersion"))).toBe(
          true,
        );
      }
    });

    it("should reject manifest without claimRef", () => {
      const manifest = {
        schemaVersion: "1.0.0" as const,
        manifestType: "WorkedExample" as const,
        evidenceRefs: [],
        domain: "MATH",
        gradeBand: "grade_6_8",
        pedagogicalIntent: "illustrate_concept",
        exampleFamily: "worked-solution" as const,
        learnerGoal: "Understand",
        givens: [],
        reasoningSteps: [{ stepNumber: 1, description: "Step 1" }],
        explanationSteps: [{ stepNumber: 1, content: "Content" }],
        misconceptionCheckpoints: [],
        transferPrompts: [],
        adaptationRules: [],
        difficultyEstimate: 0.5,
        estimatedTimeMinutes: 5,
        prerequisites: [],
        evaluationHints: { correctIndicators: [], misconceptionIndicators: [] },
      };

      const result = WorkedExampleManifestSchema.safeParse(manifest);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((e: any) => e.path.includes("claimRef"))).toBe(true);
      }
    });

    it("should reject manifest without evidenceRefs", () => {
      const manifest = {
        schemaVersion: "1.0.0" as const,
        manifestType: "WorkedExample" as const,
        claimRef: "claim-123",
        domain: "MATH",
        gradeBand: "grade_6_8",
        pedagogicalIntent: "illustrate_concept",
        exampleFamily: "worked-solution" as const,
        learnerGoal: "Understand",
        givens: [],
        reasoningSteps: [{ stepNumber: 1, description: "Step 1" }],
        explanationSteps: [{ stepNumber: 1, content: "Content" }],
        misconceptionCheckpoints: [],
        transferPrompts: [],
        adaptationRules: [],
        difficultyEstimate: 0.5,
        estimatedTimeMinutes: 5,
        prerequisites: [],
        evaluationHints: { correctIndicators: [], misconceptionIndicators: [] },
      };

      const result = WorkedExampleManifestSchema.safeParse(manifest);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((e: any) => e.path.includes("evidenceRefs"))).toBe(
          true,
        );
      }
    });

    it("should reject manifest with empty reasoningSteps", () => {
      const manifest = {
        schemaVersion: "1.0.0" as const,
        manifestType: "WorkedExample" as const,
        claimRef: "claim-123",
        evidenceRefs: [],
        domain: "MATH",
        gradeBand: "grade_6_8",
        pedagogicalIntent: "illustrate_concept",
        exampleFamily: "worked-solution" as const,
        learnerGoal: "Understand",
        givens: [],
        reasoningSteps: [],
        explanationSteps: [{ stepNumber: 1, content: "Content" }],
        misconceptionCheckpoints: [],
        transferPrompts: [],
        adaptationRules: [],
        difficultyEstimate: 0.5,
        estimatedTimeMinutes: 5,
        prerequisites: [],
        evaluationHints: { correctIndicators: [], misconceptionIndicators: [] },
      };

      const result = WorkedExampleManifestSchema.safeParse(manifest);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((e: any) => e.path.includes("reasoningSteps"))).toBe(
          true,
        );
      }
    });

    it("should reject manifest with empty explanationSteps", () => {
      const manifest = {
        schemaVersion: "1.0.0" as const,
        manifestType: "WorkedExample" as const,
        claimRef: "claim-123",
        evidenceRefs: [],
        domain: "MATH",
        gradeBand: "grade_6_8",
        pedagogicalIntent: "illustrate_concept",
        exampleFamily: "worked-solution" as const,
        learnerGoal: "Understand",
        givens: [],
        reasoningSteps: [{ stepNumber: 1, description: "Step 1" }],
        explanationSteps: [],
        misconceptionCheckpoints: [],
        transferPrompts: [],
        adaptationRules: [],
        difficultyEstimate: 0.5,
        estimatedTimeMinutes: 5,
        prerequisites: [],
        evaluationHints: { correctIndicators: [], misconceptionIndicators: [] },
      };

      const result = WorkedExampleManifestSchema.safeParse(manifest);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((e: any) => e.path.includes("explanationSteps"))).toBe(
          true,
        );
      }
    });

    it("should reject manifest with invalid difficultyEstimate", () => {
      const manifest = {
        schemaVersion: "1.0.0" as const,
        manifestType: "WorkedExample" as const,
        claimRef: "claim-123",
        evidenceRefs: [],
        domain: "MATH",
        gradeBand: "grade_6_8",
        pedagogicalIntent: "illustrate_concept",
        exampleFamily: "worked-solution" as const,
        learnerGoal: "Understand",
        givens: [],
        reasoningSteps: [{ stepNumber: 1, description: "Step 1" }],
        explanationSteps: [{ stepNumber: 1, content: "Content" }],
        misconceptionCheckpoints: [],
        transferPrompts: [],
        adaptationRules: [],
        difficultyEstimate: 1.5, // Invalid: must be between 0 and 1
        estimatedTimeMinutes: 5,
        prerequisites: [],
        evaluationHints: { correctIndicators: [], misconceptionIndicators: [] },
      };

      const result = WorkedExampleManifestSchema.safeParse(manifest);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.some((e: any) => e.path.includes("difficultyEstimate"))).toBe(
          true,
        );
      }
    });
  });

  describe("validation helper", () => {
    it("should return success for valid manifest", () => {
      const manifest = createWorkedExampleTemplate("claim-123");
      manifest.domain = "MATH";
      manifest.gradeBand = "grade_6_8";
      manifest.pedagogicalIntent = "illustrate_concept";
      manifest.learnerGoal = "Understand";
      manifest.reasoningSteps = [{ stepNumber: 1, description: "Step 1", checkpoint: false }];
      manifest.explanationSteps = [{ stepNumber: 1, content: "Content" }];

      const result = validateWorkedExampleManifest(manifest);
      expect(result.success).toBe(true);
      expect(result.data).toBeDefined();
    });

    it("should return errors for invalid manifest", () => {
      const invalidManifest = {
        schemaVersion: "1.0.0" as const,
        manifestType: "WorkedExample" as const,
        // Missing required fields
      };

      const result = validateWorkedExampleManifest(invalidManifest);
      expect(result.success).toBe(false);
      expect(result.errors).toBeDefined();
    });
  });

  describe("template creation", () => {
    it("should create a valid template with all required fields", () => {
      const template = createWorkedExampleTemplate("claim-123");
      expect(template.schemaVersion).toBe("1.0.0");
      expect(template.manifestType).toBe("WorkedExample");
      expect(template.claimRef).toBe("claim-123");
      expect(template.evidenceRefs).toEqual([]);
      expect(template.domain).toBe("");
      expect(template.gradeBand).toBe("");
      expect(template.pedagogicalIntent).toBe("");
      expect(template.provenance).toBeDefined();
      expect(template.telemetryProfile).toBeDefined();
      expect(template.createdAt).toBeDefined();
      expect(template.updatedAt).toBeDefined();
    });

    it("should create template with valid structure", () => {
      const template = createWorkedExampleTemplate("claim-123");
      const result = WorkedExampleManifestSchema.safeParse(template);
      expect(result.success).toBe(true);
    });
  });

  describe("example family types", () => {
    const validFamilies = ["real-world", "analogy", "worked-solution", "counterexample", "case-study"] as const;

    validFamilies.forEach((family) => {
      it(`should accept example family: ${family}`, () => {
        const manifest = createWorkedExampleTemplate("claim-123");
        manifest.exampleFamily = family;
        manifest.domain = "MATH";
        manifest.gradeBand = "grade_6_8";
        manifest.pedagogicalIntent = "illustrate_concept";
        manifest.learnerGoal = "Understand";
        manifest.reasoningSteps = [{ stepNumber: 1, description: "Step 1", checkpoint: false }];
        manifest.explanationSteps = [{ stepNumber: 1, content: "Content" }];

        const result = WorkedExampleManifestSchema.safeParse(manifest);
        expect(result.success).toBe(true);
      });
    });
  });
});
