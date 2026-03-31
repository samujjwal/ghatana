import { describe, expect, it } from "vitest";
import { RecommendationEngine } from "../recommendation-engine.js";

function makeSuggestion(
  id: string,
  overrides: { asset?: Record<string, unknown>; edge?: Record<string, unknown> } = {},
) {
  return {
    asset: {
      id,
      tenantId: "tenant-1",
      slug: id,
      title: `Asset ${id}`,
      assetType: "explainer",
      domain: "physics",
      status: "published",
      currentVersion: 1,
      qualityScore: 80,
      tags: [],
      targetGrades: [],
      difficultyLevel: "intermediate",
      authorId: "author-1",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      riskLevel: "LOW",
      ...overrides.asset,
    },
    edge: {
      id: `edge-${id}`,
      sourceAssetId: "source-1",
      targetAssetId: id,
      edgeType: "follow_up",
      source: "rule_based",
      weight: 0.6,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...overrides.edge,
    },
    reason: "follow up concept",
  };
}

describe("RecommendationEngine", () => {
  it("filters completed assets and prefers easier content for lower progress learners", async () => {
    const recommendationService = {
      getRelatedAssets: async () => ({
        prerequisites: [],
        followUps: [
          makeSuggestion("advanced", {
            asset: { difficultyLevel: "advanced", qualityScore: 90 },
            edge: { weight: 0.8 },
          }),
          makeSuggestion("beginner", {
            asset: { difficultyLevel: "beginner", qualityScore: 75 },
          }),
        ],
        related: [],
        alternatives: [makeSuggestion("completed-asset")],
      }),
      getNextSteps: async () => [],
    };

    const engine = new RecommendationEngine({} as never, {
      recommendationService: recommendationService as never,
    });

    const result = await engine.getRecommendations(
      { tenantId: "tenant-1", assetId: "source-1", limit: 5 },
      {
        completedAssets: ["completed-asset"],
        userProgress: { "source-1": 0.2 },
      },
    );

    expect(result.alternatives).toHaveLength(0);
    expect(result.followUps[0].asset.id).toBe("beginner");
  });

  it("boosts recommendations that align with learner goals", async () => {
    const recommendationService = {
      getRelatedAssets: async () => ({
        prerequisites: [],
        followUps: [],
        related: [
          makeSuggestion("chemistry", {
            asset: { domain: "chemistry", title: "Chemical Bonds" },
          }),
          makeSuggestion("physics", {
            asset: { domain: "physics", title: "Forces" },
          }),
        ],
        alternatives: [],
      }),
      getNextSteps: async () => [],
    };

    const engine = new RecommendationEngine({} as never, {
      recommendationService: recommendationService as never,
    });

    const result = await engine.getRecommendations(
      { tenantId: "tenant-1", assetId: "source-1" },
      { learningGoals: ["chemistry"] },
    );

    expect(result.related[0].asset.id).toBe("chemistry");
  });
});
