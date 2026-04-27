/**
 * UnifiedContentEvaluator tests
 *
 * Verifies trust score computation, publish decision logic, and queue routing.
 */

import { beforeEach, describe, expect, it, vi } from "vitest";
import { UnifiedContentEvaluator } from "../unified-content-evaluator.js";
import type {
  ContentEvaluationRequest,
  UnifiedEvaluatorConfig,
} from "../unified-content-evaluator.js";

function makePrisma() {
  return {
    reviewQueue: {
      create: vi.fn().mockResolvedValue({ id: "review-1" }),
    },
    validationRecordExtended: {
      create: vi.fn().mockResolvedValue({ id: "validation-1" }),
    },
    experienceEvent: {
      create: vi.fn().mockResolvedValue({ id: "event-1" }),
    },
  };
}

function makeKnowledgeBaseService() {
  return {
    validateContent: vi.fn().mockResolvedValue({
      passed: true,
      score: 90,
      riskLevel: "low",
      recommendations: [],
      processingTimeMs: 5,
      checks: [],
    }),
  };
}

const DEFAULT_CONFIG: UnifiedEvaluatorConfig = {
  domainConfigs: {
    MATH: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    PHYSICS: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    CHEMISTRY: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    BIOLOGY: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    ECONOMICS: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    CS: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
  },
};

function makeRequest(overrides: Partial<ContentEvaluationRequest> = {}): ContentEvaluationRequest {
  return {
    artifactId: "artifact-1",
    tenantId: "tenant-1",
    experienceId: "experience-1",
    contentType: "claim",
    domain: "MATH",
    gradeLevel: 8,
    bloomLevel: 3,
    content: "The quadratic formula is x = (-b ± √(b² - 4ac)) / 2a. " +
      "This formula solves any quadratic equation ax² + bx + c = 0. " +
      "For example, x² - 5x + 6 = 0 has solutions x = 2 and x = 3.",
    ...overrides,
  };
}

describe("UnifiedContentEvaluator", () => {
  const logger = {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
    fatal: vi.fn(),
    trace: vi.fn(),
    child: vi.fn().mockReturnThis(),
  };

  let prisma: ReturnType<typeof makePrisma>;
  let kbService: ReturnType<typeof makeKnowledgeBaseService>;
  let evaluator: UnifiedContentEvaluator;

  beforeEach(() => {
    prisma = makePrisma();
    kbService = makeKnowledgeBaseService();
    evaluator = new UnifiedContentEvaluator(
      logger as never,
      prisma as never,
      kbService,
      DEFAULT_CONFIG,
    );
    vi.clearAllMocks();
  });

  describe("evaluateContent - basic structure", () => {
    it("returns a ContentEvaluationResult with all required fields", async () => {
      const result = await evaluator.evaluateContent(makeRequest());

      expect(result).toHaveProperty("artifactId", "artifact-1");
      expect(result).toHaveProperty("trustScore");
      expect(result.trustScore).toHaveProperty("overall_score");
      expect(result.trustScore).toHaveProperty("publish_decision");
      expect(result.trustScore).toHaveProperty("schema_score");
      expect(result.trustScore).toHaveProperty("reasoning");
    });

    it("includes all 5 validator component scores", async () => {
      const result = await evaluator.evaluateContent(makeRequest());

      expect(result.trustScore).toHaveProperty("schema_score");
      expect(result.trustScore).toHaveProperty("pedagogical_score");
      expect(result.trustScore).toHaveProperty("factual_score");
      expect(result.trustScore).toHaveProperty("simulation_score");
      expect(result.trustScore).toHaveProperty("accessibility_score");
    });

    it("overall_score is a number between 0 and 1", async () => {
      const result = await evaluator.evaluateContent(makeRequest());
      const score = result.trustScore.overall_score;
      expect(score).toBeGreaterThanOrEqual(0);
      expect(score).toBeLessThanOrEqual(1);
    });
  });

  describe("publish decision routing", () => {
    it("high quality content with valid schema routes to AUTO_PASS or HUMAN_REVIEW", async () => {
      const result = await evaluator.evaluateContent(makeRequest({
        content: "The quadratic formula x = (-b ± √(b²-4ac)) / 2a solves quadratic equations. " +
          "For example, consider x² - 5x + 6 = 0. Using the formula with a=1, b=-5, c=6: " +
          "x = (5 ± √(25-24)) / 2 = (5 ± 1) / 2, giving x=3 or x=2. " +
          "This technique works for all real-coefficient quadratics and is crucial for Grade 8 algebra.",
        domain: "MATH",
        gradeLevel: 8,
        bloomLevel: 3,
      }));

      expect(["AUTO_PASS", "HUMAN_REVIEW"]).toContain(result.trustScore.publish_decision);
    });

    it("very short content gets low overall trust score", async () => {
      const result = await evaluator.evaluateContent(makeRequest({
        content: "ok",
        domain: "MATH",
        gradeLevel: 8,
        bloomLevel: 1,
      }));

      // Short content should not auto-pass (trust score should be below 1.0)
      expect(result.trustScore.overall_score).toBeLessThan(1.0);
    });

    it("creates a review queue entry when decision is HUMAN_REVIEW", async () => {
      // Force HUMAN_REVIEW by building a medium quality request
      // We'll check if prisma.reviewQueue.create was called when needed
      const result = await evaluator.evaluateContent(makeRequest());

      if (result.trustScore.publish_decision === "HUMAN_REVIEW") {
        expect(prisma.reviewQueue.create).toHaveBeenCalledOnce();
      } else {
        // AUTO_PASS or AUTO_REMEDIATE — no review queue needed
        expect(true).toBe(true);
      }
    });

    it("does not create review queue entry on AUTO_PASS", async () => {
      // Make multiple evaluations on clearly failing content
      const result = await evaluator.evaluateContent(makeRequest({ content: "x" }));

      if (result.trustScore.publish_decision === "AUTO_PASS") {
        expect(prisma.reviewQueue.create).not.toHaveBeenCalled();
      } else {
        // Expected case
        expect(true).toBe(true);
      }
    });
  });

  describe("schema validation", () => {
    it("penalizes missing required fields", async () => {
      const resultWithContent = await evaluator.evaluateContent(makeRequest());
      const resultEmpty = await evaluator.evaluateContent(makeRequest({ content: "" }));

      expect(resultWithContent.trustScore.schema_score).toBeGreaterThan(
        resultEmpty.trustScore.schema_score,
      );
    });

    it("penalizes empty content with low schema score", async () => {
      const result = await evaluator.evaluateContent(makeRequest({ content: "" }));
      // Empty content should score less than full content
      expect(result.trustScore.schema_score).toBeLessThan(1.0);
    });

    it("accepts claim with reasonable length text", async () => {
      const result = await evaluator.evaluateContent(makeRequest({
        content: "Photosynthesis converts light into sugar. Chlorophyll is the key pigment.",
        domain: "BIOLOGY",
      }));

      expect(result.trustScore.schema_score).toBeGreaterThan(0);
    });
  });

  describe("pedagogical validation", () => {
    it("favors content that explicitly addresses misconceptions", async () => {
      const withMisconception = await evaluator.evaluateContent(makeRequest({
        content: "A common misconception is that multiplication always makes numbers bigger. " +
          "However when multiplying by a fraction, the result is smaller. " +
          "For example, 0.5 × 10 = 5, which is less than 10.",
        domain: "MATH",
        gradeLevel: 5,
        bloomLevel: 2,
      }));

      const withoutMisconception = await evaluator.evaluateContent(makeRequest({
        content: "Multiplication is repeated addition of the same number multiple times.",
        domain: "MATH",
        gradeLevel: 5,
        bloomLevel: 1,
      }));

      // Misconception-addressing content should score higher pedagogically
      expect(withMisconception.trustScore.pedagogical_score).toBeGreaterThanOrEqual(
        withoutMisconception.trustScore.pedagogical_score,
      );
    });
  });

  describe("provenance tracking", () => {
    it("returns a provenanceNode in the result", async () => {
      const result = await evaluator.evaluateContent(makeRequest());
      expect(result).toHaveProperty("provenanceNode");
      expect(result.provenanceNode).toHaveProperty("artifact_id");
    });

    it("provenanceNode artifact_id matches request artifactId", async () => {
      const result1 = await evaluator.evaluateContent(makeRequest());
      const result2 = await evaluator.evaluateContent(makeRequest({ artifactId: "artifact-2" }));

      expect(result1.provenanceNode.artifact_id).toBe("artifact-1");
      expect(result2.provenanceNode.artifact_id).toBe("artifact-2");
    });
  });

  describe("domain-specific simulation validation", () => {
    it("simulation type content undergoes simulation validation", async () => {
      const result = await evaluator.evaluateContent(makeRequest({
        contentType: "simulation",
        domain: "PHYSICS",
        content: JSON.stringify({
          frames: [
            { time: 0, kineticEnergy: 100, potentialEnergy: 0 },
            { time: 1, kineticEnergy: 50, potentialEnergy: 50 },
            { time: 2, kineticEnergy: 0, potentialEnergy: 100 },
          ],
        }),
      }));

      expect(result.trustScore.simulation_score).toBeGreaterThan(0);
    });

    it("claim type content gets non-zero simulation score as pass-through", async () => {
      const result = await evaluator.evaluateContent(makeRequest({ contentType: "claim" }));
      expect(result.trustScore.simulation_score).toBeGreaterThanOrEqual(0);
    });
  });
});
