import { beforeEach, describe, expect, it, vi } from "vitest";
import { AutoPublishGatingService } from "../auto-publish-gating";

function makePrisma() {
  return {
    claimExample: {
      count: vi.fn().mockResolvedValue(10),
    },
    auditLog: {
      create: vi.fn().mockResolvedValue({ id: "audit-1" }),
    },
    reviewQueue: {
      create: vi.fn().mockResolvedValue({ id: "review-2" }),
    },
  };
}

function makeLogger() {
  return {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
  };
}

describe("AutoPublishGatingService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let logger: ReturnType<typeof makeLogger>;
  let validatorService: {
    validateGeneratedContent: ReturnType<typeof vi.fn>;
  };
  let factScoreEvaluator: {
    evaluate: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    prisma = makePrisma();
    logger = makeLogger();
    validatorService = {
      validateGeneratedContent: vi.fn(),
    };
    factScoreEvaluator = {
      evaluate: vi.fn().mockResolvedValue({
        precision: 0.99,
        recall: 0.99,
        f1Score: 0.99,
        supportedClaims: 1,
        totalClaims: 1,
        unsupportedClaims: [],
        ambiguousClaims: [],
      }),
    };
  });

  it("persists formal audit records for auto-publish decisions", async () => {
    validatorService.validateGeneratedContent.mockResolvedValue({
      validationRecordId: "validation-1",
      validatorVersion: "independent-content-validator@2026-04-18",
      inputHash: "hash-1",
      overallStatus: "PASS",
      score: 99,
      riskLevel: "low",
      requiresHumanReview: false,
      checks: [],
      recommendations: [],
    });

    const service = new AutoPublishGatingService(
      prisma as never,
      logger as never,
      {
        factScoreEvaluator: factScoreEvaluator as never,
        validatorService: validatorService as never,
      },
    );

    const result = await service.evaluateForAutoPublish({
      tenantId: "tenant-1",
      actorId: "system",
      experienceId: "exp-1",
      artifactId: "asset-1",
      artifactType: "example",
      content: "Plants convert light energy into glucose during photosynthesis.",
      domain: "biology",
      gradeRange: "grade_9_12",
      evidenceBundle: {
        bundleId: "bundle-1",
        claimRef: "C1",
        domain: "biology",
        gradeBand: "GRADE_9_12",
        evidences: [],
        bundleConfidence: 0.98,
        coverageScore: 0.95,
        coverageGaps: [],
        contradictionDetected: false,
        freshnessOverall: "CURRENT",
        sourceDistribution: {
          OPENSTAX: 1,
        },
        generatedAt: new Date("2026-04-18T00:00:00.000Z"),
      },
    });

    expect(result.canAutoPublish).toBe(true);
    expect(result.auditLogId).toBe("audit-1");
    expect(prisma.auditLog.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          tenantId: "tenant-1",
          actorId: "system",
          action: "AUTO_PUBLISH_EVALUATED",
          resourceId: "asset-1",
        }),
      }),
    );
  });

  it("queues review when validator or policy rules block auto-publish", async () => {
    validatorService.validateGeneratedContent.mockResolvedValue({
      validationRecordId: "validation-1",
      validatorVersion: "independent-content-validator@2026-04-18",
      inputHash: "hash-1",
      overallStatus: "WARN",
      score: 74,
      riskLevel: "medium",
      requiresHumanReview: false,
      checks: [],
      recommendations: ["Needs reviewer confirmation"],
    });

    const service = new AutoPublishGatingService(
      prisma as never,
      logger as never,
      {
        factScoreEvaluator: factScoreEvaluator as never,
        validatorService: validatorService as never,
      },
    );

    const result = await service.evaluateForAutoPublish({
      tenantId: "tenant-1",
      actorId: "system",
      experienceId: "exp-1",
      artifactId: "asset-1",
      artifactType: "simulation",
      content: "Medical simulation output",
      domain: "medical",
      gradeRange: "professional",
      evidenceBundle: {
        bundleId: "bundle-1",
        claimRef: "C1",
        domain: "medical",
        gradeBand: "PROFESSIONAL",
        evidences: [],
        bundleConfidence: 0.9,
        coverageScore: 0.92,
        coverageGaps: [],
        contradictionDetected: false,
        freshnessOverall: "CURRENT",
        sourceDistribution: {
          TEXTBOOK: 1,
        },
        generatedAt: new Date("2026-04-18T00:00:00.000Z"),
      },
    });

    expect(result.canAutoPublish).toBe(false);
    expect(result.requiresHumanReview).toBe(true);
    expect(result.reviewQueueId).toBe("review-2");
    expect(prisma.reviewQueue.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          tenantId: "tenant-1",
          experienceId: "exp-1",
        }),
      }),
    );
  });
});