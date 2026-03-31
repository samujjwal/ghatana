import { beforeEach, describe, expect, it, vi } from "vitest";
import { SearchSystemValidator } from "../search-validator.js";

function makePrisma() {
  return {
    contentAsset: {
      findMany: vi.fn(),
    },
  };
}

describe("SearchSystemValidator", () => {
  let prisma: ReturnType<typeof makePrisma>;

  beforeEach(() => {
    prisma = makePrisma();
  });

  it("returns a critical issue when no published assets are available", async () => {
    prisma.contentAsset.findMany.mockResolvedValue([]);

    const validator = new SearchSystemValidator(prisma as never, {
      searchService: {} as never,
      recommendationEngine: {} as never,
      recommendationService: {} as never,
    });

    const result = await validator.validateDiscoverySystem("tenant-1");

    expect(result.overallScore).toBe(0);
    expect(result.criticalIssues[0]).toContain("No published content assets");
  });

  it("aggregates search, recommendation, and autocomplete validation", async () => {
    prisma.contentAsset.findMany
      .mockResolvedValueOnce([
        {
          id: "asset-1",
          title: "Newton Laws",
          domain: "physics",
          assetType: "EXPLAINER",
          tags: ["motion", "force"],
        },
        {
          id: "asset-2",
          title: "Chemical Reactions",
          domain: "chemistry",
          assetType: "SIMULATION",
          tags: ["reactions"],
        },
      ])
      .mockResolvedValue([
        { title: "Newton Laws", domain: "physics" },
        { title: "Physics Motion", domain: "physics" },
      ]);

    const searchService = {
      search: vi.fn().mockImplementation(async ({ query }: { query: string }) => ({
        results: [
          {
            asset: {
              id: "asset-1",
              tenantId: "tenant-1",
              slug: "newton-laws",
              title: "Newton Laws",
              assetType: "explainer",
              domain: query.includes("chemistry") ? "chemistry" : "physics",
              status: "published",
              currentVersion: 1,
              tags: [],
              targetGrades: [],
              authorId: "author-1",
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              riskLevel: "LOW",
            },
            ranking: {
              score: 0.88,
              signals: [],
              matchReason: "match",
            },
            highlights: [],
          },
        ],
        total: 1,
        queryTime: 10,
      })),
    };

    const recommendationEngine = {
      getRecommendations: vi.fn().mockResolvedValue({
        prerequisites: [],
        followUps: [],
        related: [
          {
            asset: { id: "r-1", domain: "physics", title: "Motion" },
            edge: { weight: 0.8 },
            reason: "related",
          },
          {
            asset: { id: "r-2", domain: "physics", title: "Forces" },
            edge: { weight: 0.7 },
            reason: "related",
          },
        ],
        alternatives: [],
      }),
    };

    const recommendationService = {
      bootstrapEdges: vi.fn().mockResolvedValue({ created: 0, skipped: 0 }),
    };

    const validator = new SearchSystemValidator(prisma as never, {
      searchService: searchService as never,
      recommendationEngine: recommendationEngine as never,
      recommendationService: recommendationService as never,
    });

    const result = await validator.validateDiscoverySystem("tenant-1");

    expect(result.searchTests.length).toBeGreaterThan(0);
    expect(result.recommendationTests.length).toBeGreaterThan(0);
    expect(result.autocompleteTests.length).toBeGreaterThan(0);
    expect(result.overallScore).toBeGreaterThan(0);
    expect(searchService.search).toHaveBeenCalled();
    expect(recommendationEngine.getRecommendations).toHaveBeenCalled();
  });
});
