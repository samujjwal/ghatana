import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  INDEPENDENT_VALIDATOR_VERSION,
  IndependentGeneratedContentValidator,
} from "../independent-validator-service";

function makePrisma() {
  return {
    validationRecordExtended: {
      create: vi.fn().mockResolvedValue({ id: "validation-1" }),
    },
    reviewQueue: {
      create: vi.fn().mockResolvedValue({ id: "review-1" }),
    },
    experienceEvent: {
      create: vi.fn().mockResolvedValue({ id: "event-1" }),
    },
    contentAsset: {
      update: vi.fn().mockResolvedValue({ id: "asset-1" }),
    },
  };
}

describe("IndependentGeneratedContentValidator", () => {
  const knowledgeBaseService = {
    validateContent: vi.fn(),
  };

  let prisma: ReturnType<typeof makePrisma>;
  let service: IndependentGeneratedContentValidator;

  beforeEach(() => {
    prisma = makePrisma();
    knowledgeBaseService.validateContent.mockReset();
    service = new IndependentGeneratedContentValidator(
      prisma as never,
      knowledgeBaseService,
    );
  });

  it("persists a versioned validation record and skips review queue on clean pass", async () => {
    knowledgeBaseService.validateContent.mockResolvedValue({
      passed: true,
      score: 92,
      riskLevel: "low",
      recommendations: [],
      processingTimeMs: 10,
      checks: [
        {
          type: "factual_accuracy",
          passed: true,
          score: 95,
          message: "ok",
          suggestions: [],
        },
        {
          type: "completeness",
          passed: true,
          score: 90,
          message: "ok",
          suggestions: [],
        },
        {
          type: "age_appropriateness",
          passed: true,
          score: 90,
          message: "ok",
          suggestions: [],
        },
        {
          type: "clarity",
          passed: true,
          score: 88,
          message: "ok",
          suggestions: [],
        },
        {
          type: "pedagogical_soundness",
          passed: true,
          score: 91,
          message: "ok",
          suggestions: [],
        },
      ],
    });

    const result = await service.validateGeneratedContent({
      tenantId: "tenant-1",
      experienceId: "exp-1",
      actorId: "system",
      assetId: "asset-1",
      content: "Photosynthesis converts light energy into chemical energy.",
      contentType: "example",
      domain: "biology",
      gradeRange: "grade_9_12",
    });

    expect(result.overallStatus).toBe("PASS");
    expect(result.validatorVersion).toBe(INDEPENDENT_VALIDATOR_VERSION);
    expect(prisma.validationRecordExtended.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          experienceId: "exp-1",
          validatorsVersion: INDEPENDENT_VALIDATOR_VERSION,
          overallStatus: "PASS",
        }),
      }),
    );
    expect(prisma.reviewQueue.create).not.toHaveBeenCalled();
    expect(prisma.contentAsset.update).toHaveBeenCalled();
  });

  it("queues human review when validation fails", async () => {
    knowledgeBaseService.validateContent.mockResolvedValue({
      passed: false,
      score: 58,
      riskLevel: "high",
      recommendations: ["Escalate to SME review"],
      processingTimeMs: 15,
      checks: [
        {
          type: "factual_accuracy",
          passed: false,
          score: 40,
          message: "Insufficient governed evidence",
          suggestions: ["Use authoritative evidence bundle"],
        },
        {
          type: "completeness",
          passed: true,
          score: 80,
          message: "ok",
          suggestions: [],
        },
        {
          type: "age_appropriateness",
          passed: true,
          score: 80,
          message: "ok",
          suggestions: [],
        },
        {
          type: "clarity",
          passed: true,
          score: 75,
          message: "ok",
          suggestions: [],
        },
        {
          type: "pedagogical_soundness",
          passed: false,
          score: 55,
          message: "Needs better pedagogical structure",
          suggestions: ["Add learning objective"],
        },
      ],
    });

    const result = await service.validateGeneratedContent({
      tenantId: "tenant-1",
      experienceId: "exp-1",
      actorId: "review-bot",
      content: "Risky medical guidance content",
      contentType: "simulation",
      domain: "medical",
      gradeRange: "professional",
    });

    expect(result.overallStatus).toBe("FAIL");
    expect(result.requiresHumanReview).toBe(true);
    expect(result.reviewQueueId).toBe("review-1");
    expect(prisma.reviewQueue.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          tenantId: "tenant-1",
          experienceId: "exp-1",
          triggerReason: "high_risk",
        }),
      }),
    );
  });
});