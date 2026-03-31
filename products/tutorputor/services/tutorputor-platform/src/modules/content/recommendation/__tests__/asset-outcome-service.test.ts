import { beforeEach, describe, expect, it, vi } from "vitest";
import { AssetOutcomeService } from "../asset-outcome-service";

function makePrisma() {
  return {
    contentAsset: {
      findFirst: vi.fn().mockResolvedValue({
        id: "asset-1",
        tenantId: "tenant-1",
        assetType: "EXPLAINER",
        status: "PUBLISHED",
        qualityScore: 0.82,
        recommendationStatus: "STALE",
      }),
      findMany: vi.fn().mockResolvedValue([{ id: "asset-1" }]),
      update: vi.fn().mockResolvedValue({ id: "asset-1" }),
    },
    explorerEvent: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    evaluationRecord: {
      findFirst: vi.fn().mockResolvedValue(null),
    },
    generationReviewDecision: {
      findFirst: vi.fn().mockResolvedValue(null),
    },
    aBExperimentObservation: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    regenerationCandidate: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      create: vi.fn().mockResolvedValue({
        id: "cand-1",
        tenantId: "tenant-1",
        assetId: "asset-1",
        assetType: "explainer",
        trigger: "POOR_DISCOVERY_PERFORMANCE",
        severity: "MEDIUM",
        reason: "Poor learner engagement",
        evidence: {},
        priority: 70,
        status: "OPEN",
        generationRequestId: null,
        resolvedBy: null,
        resolvedAt: null,
        createdAt: new Date("2025-06-01T00:00:00Z"),
        updatedAt: new Date("2025-06-01T00:00:00Z"),
      }),
    },
  };
}

describe("AssetOutcomeService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: AssetOutcomeService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new AssetOutcomeService(prisma as never);
    vi.spyOn(
      (service as any).recommendationService,
      "recomputeOutcomeAwareEdges",
    ).mockResolvedValue({
      processedAssets: 1,
      updatedEdges: 2,
      skippedEdges: 0,
    });
  });

  it("summarizes a healthy asset with positive learner signals", async () => {
    prisma.explorerEvent.findMany.mockResolvedValue([
      { eventType: "IMPRESSION" },
      { eventType: "CLICK" },
      { eventType: "ASSET_COMPLETE" },
      { eventType: "RANKING_FEEDBACK", feedbackLabel: "helpful" },
    ]);
    prisma.evaluationRecord.findFirst.mockResolvedValue({
      overallScore: 0.91,
      recommendation: "AUTO_PUBLISH",
      generationRequestId: "req-1",
    });
    prisma.generationReviewDecision.findFirst.mockResolvedValue({
      status: "APPROVED",
    });

    const result = await service.analyzeAsset("tenant-1", "asset-1");

    expect(result.healthStatus).toBe("healthy");
    expect(result.recommendedActions).toContain("keep_published");
    expect(result.telemetry.positiveFeedback).toBe(1);
    expect(result.telemetry.completions).toBe(1);
  });

  it("persists outcome signals and creates a candidate for poor engagement", async () => {
    prisma.explorerEvent.findMany.mockResolvedValue([
      { eventType: "IMPRESSION" },
      { eventType: "IMPRESSION" },
      { eventType: "CLICK" },
      { eventType: "RANKING_FEEDBACK", feedbackLabel: "negative" },
      { eventType: "RANKING_FEEDBACK", feedbackLabel: "negative" },
      { eventType: "RANKING_FEEDBACK", feedbackLabel: "negative" },
    ]);

    const result = await service.analyzeAsset("tenant-1", "asset-1", {
      apply: true,
    });

    expect(result.healthStatus).toBe("intervene");
    expect(prisma.contentAsset.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "asset-1" },
        data: expect.objectContaining({
          confidenceScore: expect.any(Number),
          recommendationStatus: "STALE",
        }),
      }),
    );
    expect(prisma.regenerationCandidate.create).toHaveBeenCalled();
  });

  it("incorporates experiment observations into outcome analysis", async () => {
    prisma.aBExperimentObservation.findMany.mockResolvedValue([
      { variant: "control", metricValue: 0.45 },
      { variant: "control", metricValue: 0.5 },
      { variant: "treatment", metricValue: 0.72 },
      { variant: "treatment", metricValue: 0.75 },
    ]);

    const result = await service.analyzeAsset("tenant-1", "asset-1");

    expect(result.experimentSummary).toEqual(
      expect.objectContaining({
        observationCount: 4,
        dominantVariant: "balanced",
      }),
    );
    expect(result.recommendedActions).toContain(
      "promote_successful_variant_signals",
    );
  });

  it("can recompute outcome-aware recommendations for a published asset", async () => {
    const result = await service.analyzeAsset("tenant-1", "asset-1", {
      recomputeRecommendations: true,
    });

    expect(result.recommendationRefresh).toEqual({
      processedAssets: 1,
      updatedEdges: 2,
      skippedEdges: 0,
    });
  });

  it("can summarize all assets linked to an experience", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([
      { id: "asset-1" },
      { id: "asset-2" },
    ]);
    prisma.contentAsset.findFirst
      .mockResolvedValueOnce({
        id: "asset-1",
        tenantId: "tenant-1",
        assetType: "EXPLAINER",
        status: "PUBLISHED",
        qualityScore: 0.82,
        recommendationStatus: "STALE",
      })
      .mockResolvedValueOnce({
        id: "asset-2",
        tenantId: "tenant-1",
        assetType: "EXPLAINER",
        status: "PUBLISHED",
        qualityScore: 0.61,
        recommendationStatus: "COMPUTED",
      });

    const result = await service.analyzeExperienceAssets(
      "tenant-1",
      "experience-1",
    );

    expect(result.totalAssets).toBe(2);
    expect(result.assets).toHaveLength(2);
  });
});
