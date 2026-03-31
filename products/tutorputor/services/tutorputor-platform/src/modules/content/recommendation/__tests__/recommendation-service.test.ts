/**
 * Recommendation Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify recommendation edge management and next-step logic
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { RecommendationService } from "../recommendation-service";

// ---------------------------------------------------------------------------
// Prisma Mock
// ---------------------------------------------------------------------------

function makePrisma() {
  return {
    contentAsset: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      update: vi.fn().mockResolvedValue(makeAsset()),
    },
    recommendationEdge: {
      findMany: vi.fn().mockResolvedValue([]),
      findFirst: vi.fn().mockResolvedValue(null),
      update: vi.fn().mockImplementation((args: any) => ({
        id: args.where.id,
        ...args.data,
      })),
      create: vi.fn().mockImplementation((args: any) => ({
        id: `edge-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        ...args.data,
      })),
    },
    explorerEvent: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    evaluationRecord: {
      findMany: vi.fn().mockResolvedValue([]),
    },
  };
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeAsset(overrides: Record<string, any> = {}) {
  return {
    id: "asset-1",
    tenantId: "tenant-1",
    slug: "newtons-laws",
    title: "Newton's Laws",
    assetType: "EXPLAINER",
    domain: "physics",
    conceptId: "concept-motion",
    status: "PUBLISHED",
    currentVersion: 1,
    qualityScore: 85,
    semanticIndexStatus: "INDEXED",
    tags: ["mechanics"],
    targetGrades: ["9"],
    difficultyLevel: "INTERMEDIATE",
    authorId: "author-1",
    lastEditedBy: null,
    publishedAt: new Date("2025-06-01"),
    createdAt: new Date("2025-01-01"),
    updatedAt: new Date("2025-06-01"),
    riskLevel: "LOW",
    confidenceScore: 0.9,
    legacyModuleId: null,
    legacyExperienceId: null,
    ...overrides,
  };
}

function makeEdge(overrides: Record<string, any> = {}) {
  return {
    id: "edge-1",
    sourceAssetId: "asset-1",
    targetAssetId: "asset-2",
    edgeType: "RELATED",
    source: "RULE_BASED",
    weight: 0.5,
    confidence: null,
    reason: "Same domain: physics",
    metadata: null,
    createdAt: new Date("2025-06-01"),
    updatedAt: new Date("2025-06-01"),
    targetAsset: makeAsset({ id: "asset-2", title: "Kinematics" }),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("RecommendationService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: RecommendationService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new RecommendationService(prisma as any);
  });

  // =========================================================================
  // getRelatedAssets
  // =========================================================================

  describe("getRelatedAssets", () => {
    it("should return empty categories when no edges exist", async () => {
      const result = await service.getRelatedAssets("tenant-1", "asset-1");

      expect(result.prerequisites).toHaveLength(0);
      expect(result.followUps).toHaveLength(0);
      expect(result.related).toHaveLength(0);
      expect(result.alternatives).toHaveLength(0);
    });

    it("should group edges by type", async () => {
      prisma.recommendationEdge.findMany.mockResolvedValue([
        makeEdge({
          edgeType: "PREREQUISITE",
          targetAsset: makeAsset({ id: "prereq-1" }),
        }),
        makeEdge({
          edgeType: "FOLLOW_UP",
          targetAsset: makeAsset({ id: "follow-1" }),
        }),
        makeEdge({
          edgeType: "RELATED",
          targetAsset: makeAsset({ id: "related-1" }),
        }),
        makeEdge({
          edgeType: "ALTERNATIVE",
          targetAsset: makeAsset({ id: "alt-1" }),
        }),
      ]);

      const result = await service.getRelatedAssets("tenant-1", "asset-1");

      expect(result.prerequisites).toHaveLength(1);
      expect(result.prerequisites[0].asset.id).toBe("prereq-1");
      expect(result.followUps).toHaveLength(1);
      expect(result.followUps[0].asset.id).toBe("follow-1");
      expect(result.related).toHaveLength(1);
      expect(result.related[0].asset.id).toBe("related-1");
      expect(result.alternatives).toHaveLength(1);
      expect(result.alternatives[0].asset.id).toBe("alt-1");
    });

    it("should respect limit per category", async () => {
      const manyRelated = Array.from({ length: 15 }, (_, i) =>
        makeEdge({
          id: `edge-${i}`,
          edgeType: "RELATED",
          targetAsset: makeAsset({ id: `r-${i}` }),
        }),
      );
      prisma.recommendationEdge.findMany.mockResolvedValue(manyRelated);

      const result = await service.getRelatedAssets("tenant-1", "asset-1", 5);

      expect(result.related).toHaveLength(5);
    });

    it("should include edge data in suggestions", async () => {
      prisma.recommendationEdge.findMany.mockResolvedValue([
        makeEdge({
          edgeType: "RELATED",
          weight: 0.75,
          reason: "Same domain: physics",
        }),
      ]);

      const result = await service.getRelatedAssets("tenant-1", "asset-1");

      expect(result.related[0].edge.edgeType).toBe("related");
      expect(result.related[0].edge.source).toBe("rule_based");
      expect(result.related[0].edge.weight).toBe(0.75);
      expect(result.related[0].reason).toBe("Same domain: physics");
    });

    it("should skip edges with null targetAsset", async () => {
      prisma.recommendationEdge.findMany.mockResolvedValue([
        makeEdge({ edgeType: "RELATED", targetAsset: null }),
      ]);

      const result = await service.getRelatedAssets("tenant-1", "asset-1");

      expect(result.related).toHaveLength(0);
    });

    it("should lowercase asset types in response", async () => {
      prisma.recommendationEdge.findMany.mockResolvedValue([
        makeEdge({ edgeType: "RELATED" }),
      ]);

      const result = await service.getRelatedAssets("tenant-1", "asset-1");

      expect(result.related[0].asset.assetType).toBe("explainer");
      expect(result.related[0].asset.status).toBe("published");
    });
  });

  // =========================================================================
  // getNextSteps
  // =========================================================================

  describe("getNextSteps", () => {
    it("should return empty when no follow-up edges exist", async () => {
      const result = await service.getNextSteps("tenant-1", "asset-1");
      expect(result).toHaveLength(0);
    });

    it("should query FOLLOW_UP and DEEPER_DIVE edges only", async () => {
      prisma.recommendationEdge.findMany.mockResolvedValue([]);

      await service.getNextSteps("tenant-1", "asset-1");

      const call = prisma.recommendationEdge.findMany.mock.calls[0][0];
      expect(call.where.edgeType).toEqual({ in: ["FOLLOW_UP", "DEEPER_DIVE"] });
    });

    it("should respect limit parameter", async () => {
      const edges = Array.from({ length: 8 }, (_, i) =>
        makeEdge({
          id: `e-${i}`,
          edgeType: "FOLLOW_UP",
          targetAsset: makeAsset({ id: `f-${i}` }),
        }),
      );
      prisma.recommendationEdge.findMany.mockResolvedValue(edges);

      await service.getNextSteps("tenant-1", "asset-1", 3);

      // Service uses take: limit in query, mock returns all — but result should be bounded
      // Actually mock returns all 8 but filter is at query level. Let's test the query param.
      const call = prisma.recommendationEdge.findMany.mock.calls[0][0];
      expect(call.take).toBe(3);
    });

    it("should provide meaningful reason text", async () => {
      prisma.recommendationEdge.findMany.mockResolvedValue([
        makeEdge({
          edgeType: "FOLLOW_UP",
          reason: null,
          targetAsset: makeAsset({ id: "f-1" }),
        }),
      ]);

      const result = await service.getNextSteps("tenant-1", "asset-1");

      expect(result[0].reason).toContain("follow up");
    });
  });

  // =========================================================================
  // bootstrapEdges
  // =========================================================================

  describe("bootstrapEdges", () => {
    it("should return zero counts when asset not found", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(null);

      const result = await service.bootstrapEdges("tenant-1", "nonexistent");

      expect(result.created).toBe(0);
      expect(result.skipped).toBe(0);
    });

    it("should create RELATED edges for same-domain assets", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ conceptId: null, difficultyLevel: null }),
      );
      prisma.contentAsset.findMany.mockResolvedValue([
        makeAsset({ id: "domain-peer-1" }),
        makeAsset({ id: "domain-peer-2" }),
      ]);

      const result = await service.bootstrapEdges("tenant-1", "asset-1");

      expect(result.created).toBe(2);
      expect(prisma.recommendationEdge.create).toHaveBeenCalledTimes(2);

      const firstCreate = prisma.recommendationEdge.create.mock.calls[0][0];
      expect(firstCreate.data.edgeType).toBe("RELATED");
      expect(firstCreate.data.source).toBe("RULE_BASED");
      expect(firstCreate.data.reason).toContain("physics");
    });

    it("should create ALTERNATIVE edges for same-concept same-type assets", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ difficultyLevel: null }),
      );
      // First call: same-domain query
      prisma.contentAsset.findMany
        .mockResolvedValueOnce([]) // same domain (empty)
        .mockResolvedValueOnce([
          // same concept
          makeAsset({
            id: "alt-1",
            assetType: "EXPLAINER",
            conceptId: "concept-motion",
          }),
        ]);

      await service.bootstrapEdges("tenant-1", "asset-1");

      const altCreate = prisma.recommendationEdge.create.mock.calls.find(
        (c: any) => c[0].data.edgeType === "ALTERNATIVE",
      );
      expect(altCreate).toBeDefined();
    });

    it("should create PREREQUISITE edges for easier difficulty assets", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ difficultyLevel: "INTERMEDIATE", conceptId: null }),
      );
      prisma.contentAsset.findMany
        .mockResolvedValueOnce([]) // same domain
        .mockResolvedValueOnce([
          // prerequisite (easier)
          makeAsset({ id: "easy-1", difficultyLevel: "BEGINNER" }),
        ])
        .mockResolvedValueOnce([]); // follow-up

      await service.bootstrapEdges("tenant-1", "asset-1");

      const prereqCreate = prisma.recommendationEdge.create.mock.calls.find(
        (c: any) => c[0].data.edgeType === "PREREQUISITE",
      );
      expect(prereqCreate).toBeDefined();
      expect(prereqCreate![0].data.weight).toBe(0.8);
    });

    it("should create FOLLOW_UP edges for harder difficulty assets", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ difficultyLevel: "INTERMEDIATE", conceptId: null }),
      );
      prisma.contentAsset.findMany
        .mockResolvedValueOnce([]) // same domain
        .mockResolvedValueOnce([]) // prerequisite
        .mockResolvedValueOnce([
          // follow-up (harder)
          makeAsset({ id: "hard-1", difficultyLevel: "ADVANCED" }),
        ]);

      await service.bootstrapEdges("tenant-1", "asset-1");

      const followCreate = prisma.recommendationEdge.create.mock.calls.find(
        (c: any) => c[0].data.edgeType === "FOLLOW_UP",
      );
      expect(followCreate).toBeDefined();
      expect(followCreate![0].data.weight).toBe(0.75);
    });

    it("should skip existing edges instead of duplicating", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ conceptId: null, difficultyLevel: null }),
      );
      prisma.contentAsset.findMany.mockResolvedValue([
        makeAsset({ id: "peer-1" }),
      ]);
      // Edge already exists
      prisma.recommendationEdge.findFirst.mockResolvedValue({ id: "existing" });

      const result = await service.bootstrapEdges("tenant-1", "asset-1");

      expect(result.created).toBe(0);
      expect(result.skipped).toBe(1);
      expect(prisma.recommendationEdge.create).not.toHaveBeenCalled();
    });

    it("should handle asset with no concept or difficulty", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ conceptId: null, difficultyLevel: null }),
      );
      prisma.contentAsset.findMany.mockResolvedValue([]);

      const result = await service.bootstrapEdges("tenant-1", "asset-1");

      expect(result.created).toBe(0);
      expect(result.skipped).toBe(0);
    });

    it("should query only PUBLISHED target assets", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ conceptId: null, difficultyLevel: null }),
      );
      prisma.contentAsset.findMany.mockResolvedValue([]);

      await service.bootstrapEdges("tenant-1", "asset-1");

      const findCall = prisma.contentAsset.findMany.mock.calls[0][0];
      expect(findCall.where.status).toBe("PUBLISHED");
    });
  });

  describe("recomputeOutcomeAwareEdges", () => {
    it("creates outcome-aware edges when learner signals are strong", async () => {
      prisma.contentAsset.findMany
        .mockResolvedValueOnce([makeAsset({ id: "asset-1" })])
        .mockResolvedValueOnce([
          makeAsset({ id: "asset-2", conceptId: "concept-motion" }),
        ]);
      prisma.explorerEvent.findMany.mockResolvedValue([
        { assetId: "asset-2", eventType: "IMPRESSION" },
        { assetId: "asset-2", eventType: "CLICK" },
        { assetId: "asset-2", eventType: "ASSET_COMPLETE" },
        {
          assetId: "asset-2",
          eventType: "RANKING_FEEDBACK",
          feedbackLabel: "helpful",
        },
      ]);
      prisma.evaluationRecord.findMany.mockResolvedValue([
        { assetId: "asset-2", overallScore: 0.92, createdAt: new Date() },
      ]);

      const result = await service.recomputeOutcomeAwareEdges("tenant-1", {
        sourceAssetId: "asset-1",
      });

      expect(result.processedAssets).toBe(1);
      expect(result.updatedEdges).toBe(1);
      expect(prisma.recommendationEdge.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            source: "OUTCOME_AWARE",
            targetAssetId: "asset-2",
          }),
        }),
      );
      expect(prisma.contentAsset.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: "asset-1" },
          data: { recommendationStatus: "COMPUTED" },
        }),
      );
    });

    it("skips edge mutation when telemetry is insufficient", async () => {
      prisma.contentAsset.findMany
        .mockResolvedValueOnce([makeAsset({ id: "asset-1" })])
        .mockResolvedValueOnce([
          makeAsset({ id: "asset-2", conceptId: "concept-motion" }),
        ]);
      prisma.explorerEvent.findMany.mockResolvedValue([
        { assetId: "asset-2", eventType: "IMPRESSION" },
      ]);

      const result = await service.recomputeOutcomeAwareEdges("tenant-1", {
        sourceAssetId: "asset-1",
      });

      expect(result.updatedEdges).toBe(0);
      expect(result.skippedEdges).toBe(1);
      expect(prisma.recommendationEdge.create).not.toHaveBeenCalled();
    });

    it("can recompute outcome-aware edges for all assets linked to an experience", async () => {
      prisma.contentAsset.findMany
        .mockResolvedValueOnce([{ id: "asset-1" }, { id: "asset-2" }])
        .mockResolvedValueOnce([makeAsset({ id: "asset-1" })])
        .mockResolvedValueOnce([makeAsset({ id: "asset-peer-1", conceptId: "concept-motion" })])
        .mockResolvedValueOnce([makeAsset({ id: "asset-2" })])
        .mockResolvedValueOnce([makeAsset({ id: "asset-peer-2", conceptId: "concept-motion" })]);
      prisma.explorerEvent.findMany.mockResolvedValue([
        { assetId: "asset-peer-1", eventType: "IMPRESSION" },
        { assetId: "asset-peer-1", eventType: "CLICK" },
        { assetId: "asset-peer-1", eventType: "ASSET_COMPLETE" },
        { assetId: "asset-peer-2", eventType: "IMPRESSION" },
        { assetId: "asset-peer-2", eventType: "CLICK" },
        { assetId: "asset-peer-2", eventType: "ASSET_COMPLETE" },
      ]);

      const result = await service.recomputeOutcomeAwareEdgesForExperience(
        "tenant-1",
        "experience-1",
      );

      expect(result.processedAssets).toBe(2);
      expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            legacyExperienceId: "experience-1",
          }),
        }),
      );
    });
  });
});
