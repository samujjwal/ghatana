import { beforeEach, describe, expect, it, vi } from "vitest";
import { SemanticSearchService } from "../semantic-search-service.js";

function makePrisma() {
  return {
    contentAsset: {
      findMany: vi.fn().mockResolvedValue([]),
    },
  };
}

describe("SemanticSearchService", () => {
  let prisma: ReturnType<typeof makePrisma>;

  beforeEach(() => {
    prisma = makePrisma();
  });

  it("delegates to hybrid search and returns an explanation", async () => {
    const hybridSearchService = {
      search: vi.fn().mockResolvedValue({
        results: [
          {
            asset: {
              id: "asset-1",
              tenantId: "tenant-1",
              slug: "motion",
              title: "Motion Basics",
              assetType: "explainer",
              domain: "physics",
              status: "published",
              currentVersion: 1,
              tags: [],
              targetGrades: [],
              authorId: "author-1",
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              riskLevel: "LOW",
            },
            ranking: { score: 0.92, signals: [], matchReason: "semantic" },
            highlights: [],
          },
        ],
        total: 1,
        took: 18,
        rankingSignals: ["semantic"],
      }),
    };

    const service = new SemanticSearchService(prisma as never, {
      hybridSearchService: hybridSearchService as never,
    });

    const result = await service.search({
      tenantId: "tenant-1",
      query: "motion",
      explain: true,
    });

    expect(hybridSearchService.search).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      query: "motion",
      explain: true,
    });
    expect(result.total).toBe(1);
    expect(result.explanation).toContain("motion");
    expect(result.explanation).toContain("Motion Basics");
  });

  it("falls back to keyword search when hybrid search fails", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([
      {
        id: "asset-2",
        tenantId: "tenant-1",
        slug: "newton",
        title: "Newton Laws",
        assetType: "EXPLAINER",
        domain: "physics",
        status: "PUBLISHED",
        currentVersion: 1,
        authorId: "author-1",
        createdAt: new Date("2026-01-01"),
        updatedAt: new Date("2026-01-02"),
        searchableText: "Newton laws and motion",
        tags: [],
        targetGrades: [],
        riskLevel: "LOW",
      },
    ]);

    const service = new SemanticSearchService(prisma as never, {
      hybridSearchService: {
        search: vi.fn().mockRejectedValue(new Error("search offline")),
      } as never,
    });

    const result = await service.search({
      tenantId: "tenant-1",
      query: "newton",
      explain: true,
    });

    expect(result.total).toBe(1);
    expect(result.results[0].asset.id).toBe("asset-2");
    expect(result.explanation).toContain("keyword-ranked fallback");
  });
});
