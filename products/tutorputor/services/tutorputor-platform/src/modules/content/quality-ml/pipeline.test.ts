import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PrismaClient } from "@tutorputor/core/db";
import { ContentQualityMLPipeline } from "./pipeline";

function makePrisma() {
  return {
    contentAsset: {
      findFirst: vi.fn(),
      findMany: vi.fn(),
      update: vi.fn(),
    },
    explorerEvent: {
      findMany: vi.fn(),
    },
    evaluationRecord: {
      findFirst: vi.fn(),
    },
  } as unknown as PrismaClient;
}

describe("ContentQualityMLPipeline", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let pipeline: ContentQualityMLPipeline;

  beforeEach(() => {
    prisma = makePrisma();
    pipeline = new ContentQualityMLPipeline(prisma);
  });

  it("predicts quality from asset, telemetry, and evaluation data", async () => {
    (prisma.contentAsset.findFirst as any).mockResolvedValue({
      id: "asset-1",
      searchableText:
        "Clear explanation of Newton's second law with worked examples, scaffolded prompts, concept checks, and multiple contextualized examples that help learners connect force, mass, and acceleration across familiar and unfamiliar scenarios.",
      qualityScore: 0.82,
      confidenceScore: 0.78,
      blocks: [
        { payload: { html: "<p>Force, mass, and acceleration.</p>" } },
        { payload: { text: "Worked example" } },
        { payload: { text: "Reflection and summary" } },
      ],
    });
    (prisma.explorerEvent.findMany as any).mockResolvedValue([
      { eventType: "impression", feedbackLabel: null, sessionId: "s1" },
      { eventType: "click", feedbackLabel: null, sessionId: "s1" },
      { eventType: "asset_complete", feedbackLabel: null, sessionId: "s1" },
      { eventType: "ranking_feedback", feedbackLabel: "helpful", sessionId: "s1" },
      { eventType: "impression", feedbackLabel: null, sessionId: "s2" },
      { eventType: "click", feedbackLabel: null, sessionId: "s2" },
      { eventType: "asset_complete", feedbackLabel: null, sessionId: "s2" },
      { eventType: "ranking_feedback", feedbackLabel: "relevant", sessionId: "s2" },
    ]);
    (prisma.evaluationRecord.findFirst as any).mockResolvedValue({
      overallScore: 0.88,
    });

    const result = await pipeline.predictAssetQuality("tenant-1", "asset-1");

    expect(result.predictedQuality).toBe("high");
    expect(result.confidence).toBeGreaterThan(0.75);
    expect(result.suggestions).not.toContain(
      "Address evaluation issues before republishing this asset.",
    );
    expect(result.suggestions.length).toBeLessThanOrEqual(1);
  });

  it("returns suggestions for weak assets", async () => {
    (prisma.contentAsset.findFirst as any).mockResolvedValue({
      id: "asset-2",
      searchableText: "Short text",
      qualityScore: 0.35,
      confidenceScore: 0.3,
      blocks: [{ payload: { text: "Short text" } }],
    });
    (prisma.explorerEvent.findMany as any).mockResolvedValue([
      { eventType: "impression", feedbackLabel: null, sessionId: "s1" },
      { eventType: "ranking_feedback", feedbackLabel: "negative", sessionId: "s1" },
    ]);
    (prisma.evaluationRecord.findFirst as any).mockResolvedValue({
      overallScore: 0.4,
    });

    const result = await pipeline.predictAssetQuality("tenant-1", "asset-2");

    expect(result.predictedQuality).toBe("low");
    expect(result.suggestions.length).toBeGreaterThan(0);
  });

  it("applies predictions back onto the canonical asset", async () => {
    (prisma.contentAsset.findFirst as any).mockResolvedValue({
      id: "asset-3",
      searchableText:
        "A detailed and well-structured explanation with examples and summaries.",
      qualityScore: 0.76,
      confidenceScore: 0.71,
      blocks: [
        { payload: { text: "Introduction" } },
        { payload: { text: "Worked example" } },
        { payload: { text: "Summary" } },
      ],
    });
    (prisma.explorerEvent.findMany as any).mockResolvedValue([
      { eventType: "impression", feedbackLabel: null, sessionId: "s1" },
      { eventType: "click", feedbackLabel: null, sessionId: "s1" },
      { eventType: "asset_complete", feedbackLabel: null, sessionId: "s1" },
    ]);
    (prisma.evaluationRecord.findFirst as any).mockResolvedValue({
      overallScore: 0.82,
    });
    (prisma.contentAsset.update as any).mockResolvedValue({ id: "asset-3" });

    const result = await pipeline.applyPrediction("tenant-1", "asset-3");

    expect(result.applied).toBe(true);
    expect(prisma.contentAsset.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "asset-3" },
        data: expect.objectContaining({
          qualityScore: expect.any(Number),
          confidenceScore: expect.any(Number),
        }),
      }),
    );
  });

  it("applies predictions for all assets linked to an experience", async () => {
    (prisma.contentAsset.findMany as any).mockResolvedValue([
      { id: "asset-e1" },
      { id: "asset-e2" },
    ]);
    (prisma.contentAsset.findFirst as any)
      .mockResolvedValueOnce({
        id: "asset-e1",
        searchableText: "Detailed concept walkthrough with worked examples.",
        qualityScore: 0.78,
        confidenceScore: 0.7,
        blocks: [{ payload: { text: "Example" } }, { payload: { text: "Summary" } }],
      })
      .mockResolvedValueOnce({
        id: "asset-e2",
        searchableText: "Second detailed concept walkthrough with worked examples.",
        qualityScore: 0.8,
        confidenceScore: 0.72,
        blocks: [{ payload: { text: "Intro" } }, { payload: { text: "Reflection" } }],
      });
    (prisma.explorerEvent.findMany as any).mockResolvedValue([]);
    (prisma.evaluationRecord.findFirst as any).mockResolvedValue({
      overallScore: 0.8,
    });
    (prisma.contentAsset.update as any).mockResolvedValue({});

    const results = await pipeline.applyPredictionsForExperience(
      "tenant-1",
      "experience-1",
    );

    expect(results).toHaveLength(2);
    expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          legacyExperienceId: "experience-1",
        }),
      }),
    );
  });
});
