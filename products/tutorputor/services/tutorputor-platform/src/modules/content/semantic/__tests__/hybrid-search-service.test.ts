/**
 * Hybrid Search Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify hybrid search ranking with lexical + semantic signals
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { HybridSearchService } from "../hybrid-search-service";

// ---------------------------------------------------------------------------
// Prisma Mock
// ---------------------------------------------------------------------------

function makePrisma() {
  return {
    contentAsset: {
      findMany: vi.fn().mockResolvedValue([]),
      count: vi.fn().mockResolvedValue(0),
    },
    semanticChunk: {
      findMany: vi.fn().mockResolvedValue([]),
    },
  };
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeCandidate(overrides: Record<string, any> = {}) {
  return {
    id: "asset-1",
    tenantId: "tenant-1",
    slug: "newtons-laws",
    title: "Newton's Laws of Motion",
    assetType: "EXPLAINER",
    domain: "physics",
    conceptId: null,
    status: "PUBLISHED",
    currentVersion: 1,
    qualityScore: 85,
    semanticIndexStatus: "INDEXED",
    tags: ["mechanics"],
    targetGrades: ["9"],
    difficultyLevel: "intermediate",
    authorId: "author-1",
    lastEditedBy: null,
    publishedAt: new Date("2025-06-01"),
    createdAt: new Date("2025-01-01"),
    updatedAt: new Date("2025-06-01"),
    searchableText:
      "Force equals mass times acceleration. Newton's three laws.",
    promptHash: null,
    riskLevel: "LOW",
    confidenceScore: 0.9,
    legacyModuleId: null,
    legacyExperienceId: null,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("HybridSearchService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: HybridSearchService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new HybridSearchService(prisma as any);
  });

  // =========================================================================
  // Empty / No results
  // =========================================================================

  it("should return empty when no candidates found", async () => {
    const result = await service.search({
      tenantId: "tenant-1",
      query: "quantum mechanics",
    });

    expect(result.results).toHaveLength(0);
    expect(result.total).toBe(0);
    expect(result.took).toBeGreaterThanOrEqual(0);
    expect(result.rankingSignals).toContain("lexical");
    expect(result.rankingSignals).toContain("semantic");
  });

  // =========================================================================
  // Basic ranking
  // =========================================================================

  it("should rank results by combined score", async () => {
    const candidates = [
      makeCandidate({
        id: "a1",
        title: "Unrelated Topic",
        searchableText: "Something else",
        qualityScore: 50,
      }),
      makeCandidate({
        id: "a2",
        title: "Newton Laws",
        searchableText: "Force and motion Newton laws",
        qualityScore: 90,
      }),
    ];
    prisma.contentAsset.findMany.mockResolvedValue(candidates);
    prisma.contentAsset.count.mockResolvedValue(2);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "Newton laws",
    });

    expect(result.results.length).toBe(2);
    // a2 should rank higher (better lexical + quality match)
    expect(result.results[0].asset.id).toBe("a2");
    expect(result.results[0].ranking.score).toBeGreaterThan(
      result.results[1].ranking.score,
    );
  });

  // =========================================================================
  // Lexical signal
  // =========================================================================

  it("should boost results with title-start match", async () => {
    const candidates = [
      makeCandidate({
        id: "a1",
        title: "Introduction to Newton",
        searchableText: "forces and motion study",
        qualityScore: 80,
      }),
      makeCandidate({
        id: "a2",
        title: "Newton Laws Explained",
        searchableText: "forces and motion study",
        qualityScore: 80,
      }),
    ];
    prisma.contentAsset.findMany.mockResolvedValue(candidates);
    prisma.contentAsset.count.mockResolvedValue(2);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "newton",
      explain: true,
    });

    // a2 title starts with "Newton" → title bonus should boost lexical score
    const a2Result = result.results.find((r) => r.asset.id === "a2");
    const a1Result = result.results.find((r) => r.asset.id === "a1");
    const a2Lex = a2Result!.ranking.signals.find(
      (s) => s.source === "lexical",
    )!;
    const a1Lex = a1Result!.ranking.signals.find(
      (s) => s.source === "lexical",
    )!;
    expect(a2Lex.rawScore).toBeGreaterThan(a1Lex.rawScore);
  });

  // =========================================================================
  // Semantic signal
  // =========================================================================

  it("should incorporate semantic chunk overlap in ranking", async () => {
    const candidates = [
      makeCandidate({
        id: "a1",
        title: "Motion Study",
        searchableText: "velocity acceleration",
        qualityScore: 80,
      }),
      makeCandidate({
        id: "a2",
        title: "Force Study",
        searchableText: "push pull",
        qualityScore: 80,
      }),
    ];
    prisma.contentAsset.findMany.mockResolvedValue(candidates);
    prisma.contentAsset.count.mockResolvedValue(2);

    // a1 has chunks matching the query
    prisma.semanticChunk.findMany.mockResolvedValue([
      { assetId: "a1", text: "velocity and acceleration at constant rate" },
      { assetId: "a1", text: "motion equations derive velocity from distance" },
    ]);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "velocity equations",
    });

    // a1 should rank higher due to chunk overlap
    expect(result.results[0].asset.id).toBe("a1");
  });

  // =========================================================================
  // Quality signal
  // =========================================================================

  it("should prefer higher quality assets when other signals are equal", async () => {
    const candidates = [
      makeCandidate({
        id: "a1",
        title: "Physics Basics",
        searchableText: "energy conservation",
        qualityScore: 30,
        updatedAt: new Date("2025-06-01"),
      }),
      makeCandidate({
        id: "a2",
        title: "Physics Basics",
        searchableText: "energy conservation",
        qualityScore: 95,
        updatedAt: new Date("2025-06-01"),
      }),
    ];
    prisma.contentAsset.findMany.mockResolvedValue(candidates);
    prisma.contentAsset.count.mockResolvedValue(2);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "energy conservation",
    });

    expect(result.results[0].asset.id).toBe("a2");
  });

  // =========================================================================
  // Recency signal
  // =========================================================================

  it("should prefer more recent assets when other signals are close", async () => {
    const now = new Date();
    const yearAgo = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000);

    const candidates = [
      makeCandidate({
        id: "old",
        title: "Physics Guide",
        searchableText: "thermodynamics",
        qualityScore: 80,
        updatedAt: yearAgo,
      }),
      makeCandidate({
        id: "new",
        title: "Physics Guide",
        searchableText: "thermodynamics",
        qualityScore: 80,
        updatedAt: now,
      }),
    ];
    prisma.contentAsset.findMany.mockResolvedValue(candidates);
    prisma.contentAsset.count.mockResolvedValue(2);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "thermodynamics",
    });

    expect(result.results[0].asset.id).toBe("new");
  });

  // =========================================================================
  // Explain mode
  // =========================================================================

  it("should include signal details when explain=true", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([makeCandidate()]);
    prisma.contentAsset.count.mockResolvedValue(1);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "Newton",
      explain: true,
    });

    expect(result.results).toHaveLength(1);
    const ranking = result.results[0].ranking;
    expect(ranking.signals.length).toBe(6);
    expect(ranking.signals.map((s) => s.source)).toEqual(
      expect.arrayContaining([
        "lexical",
        "semantic",
        "quality",
        "recency",
        "popularity",
        "learner_fit",
      ]),
    );
    expect(ranking.matchReason).toBeDefined();
    expect(ranking.score).toBeGreaterThan(0);
  });

  it("should omit signal details when explain=false", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([makeCandidate()]);
    prisma.contentAsset.count.mockResolvedValue(1);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "Newton",
      explain: false,
    });

    expect(result.results[0].ranking.signals).toHaveLength(0);
    expect(result.results[0].ranking.score).toBeGreaterThan(0);
    expect(result.results[0].ranking.matchReason).toBeDefined();
  });

  // =========================================================================
  // Filters
  // =========================================================================

  it("should pass assetType filter to query", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([]);
    prisma.contentAsset.count.mockResolvedValue(0);

    await service.search({
      tenantId: "tenant-1",
      query: "math",
      assetTypes: ["simulation", "assessment"],
    });

    const findCall = prisma.contentAsset.findMany.mock.calls[0][0];
    expect(findCall.where.assetType).toEqual({
      in: ["SIMULATION", "ASSESSMENT"],
    });
  });

  it("should pass domain filter to query", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([]);
    prisma.contentAsset.count.mockResolvedValue(0);

    await service.search({
      tenantId: "tenant-1",
      query: "algebra",
      domain: "mathematics",
    });

    const findCall = prisma.contentAsset.findMany.mock.calls[0][0];
    expect(findCall.where.domain).toBe("mathematics");
  });

  // =========================================================================
  // Pagination
  // =========================================================================

  it("should respect limit and offset", async () => {
    const candidates = Array.from({ length: 5 }, (_, i) =>
      makeCandidate({
        id: `a${i}`,
        title: `Result ${i}`,
        searchableText: `search term ${i}`,
        qualityScore: 80 - i,
      }),
    );
    prisma.contentAsset.findMany.mockResolvedValue(candidates);
    prisma.contentAsset.count.mockResolvedValue(20);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "search term",
      limit: 2,
      offset: 1,
    });

    expect(result.results).toHaveLength(2);
    expect(result.total).toBe(20);
  });

  // =========================================================================
  // Custom weights
  // =========================================================================

  it("should apply custom signal weights", async () => {
    const candidates = [
      makeCandidate({
        id: "a1",
        title: "Exact Title Match",
        searchableText: "exact title match content",
        qualityScore: 20,
      }),
      makeCandidate({
        id: "a2",
        title: "Other Topic",
        searchableText: "other topic other",
        qualityScore: 100,
      }),
    ];
    prisma.contentAsset.findMany.mockResolvedValue(candidates);
    prisma.contentAsset.count.mockResolvedValue(2);

    // Heavily weight quality over lexical
    const qualityFirst = await service.search({
      tenantId: "tenant-1",
      query: "exact title match",
      weights: { quality: 0.9, lexical: 0.05, semantic: 0.05 },
      explain: true,
    });

    // a2 should win because quality is so heavily weighted
    expect(qualityFirst.results[0].asset.id).toBe("a2");
  });

  // =========================================================================
  // Highlights
  // =========================================================================

  it("should generate highlights for matched fields", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([
      makeCandidate({ searchableText: "Force equals mass times acceleration" }),
    ]);
    prisma.contentAsset.count.mockResolvedValue(1);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "force",
    });

    expect(result.results[0].highlights.length).toBeGreaterThan(0);
    const highlight = result.results[0].highlights[0];
    expect(highlight.field).toBeDefined();
    expect(highlight.snippet).toContain("orce"); // case-insensitive match
  });

  // =========================================================================
  // Asset mapping
  // =========================================================================

  it("should lowercase assetType and status in response", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([makeCandidate()]);
    prisma.contentAsset.count.mockResolvedValue(1);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "Newton",
    });

    expect(result.results[0].asset.assetType).toBe("explainer");
    expect(result.results[0].asset.status).toBe("published");
  });

  // =========================================================================
  // Ranking metadata
  // =========================================================================

  it("should include took time in response", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([makeCandidate()]);
    prisma.contentAsset.count.mockResolvedValue(1);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "Newton",
    });

    expect(typeof result.took).toBe("number");
    expect(result.took).toBeGreaterThanOrEqual(0);
  });

  it("should list all ranking signals used", async () => {
    const result = await service.search({
      tenantId: "tenant-1",
      query: "anything",
    });

    expect(result.rankingSignals).toEqual(
      expect.arrayContaining([
        "lexical",
        "semantic",
        "quality",
        "recency",
        "popularity",
        "learner_fit",
      ]),
    );
  });

  it("should output matchReason referencing dominant signal", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([makeCandidate()]);
    prisma.contentAsset.count.mockResolvedValue(1);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "Newton",
      explain: true,
    });

    const reason = result.results[0].ranking.matchReason;
    expect(reason).toMatch(/Primarily matched via \w+/);
  });

  // =========================================================================
  // Edge cases
  // =========================================================================

  it("should handle asset with null qualityScore gracefully", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([
      makeCandidate({ qualityScore: null }),
    ]);
    prisma.contentAsset.count.mockResolvedValue(1);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "Newton",
      explain: true,
    });

    const qualitySignal = result.results[0].ranking.signals.find(
      (s) => s.source === "quality",
    );
    expect(qualitySignal!.rawScore).toBe(0.5); // neutral default
  });

  it("should handle asset with null updatedAt gracefully", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([
      makeCandidate({ updatedAt: null }),
    ]);
    prisma.contentAsset.count.mockResolvedValue(1);

    const result = await service.search({
      tenantId: "tenant-1",
      query: "Newton",
      explain: true,
    });

    const recencySignal = result.results[0].ranking.signals.find(
      (s) => s.source === "recency",
    );
    expect(recencySignal!.rawScore).toBe(0);
  });

  it("should only query PUBLISHED assets", async () => {
    await service.search({
      tenantId: "tenant-1",
      query: "test",
    });

    const findCall = prisma.contentAsset.findMany.mock.calls[0][0];
    expect(findCall.where.status).toBe("PUBLISHED");
  });
});
