import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerRecommendationRoutes } from "../routes";
import { RecommendationEngine } from "../recommendation-engine";
import { RecommendationService } from "../recommendation-service";
import { ExperienceRemediationService } from "../experience-remediation-service";

describe("registerRecommendationRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const getRecommendationsSpy = vi.spyOn(
    RecommendationEngine.prototype,
    "getRecommendations",
  );
  const recomputeSpy = vi.spyOn(
    RecommendationService.prototype,
    "recomputeOutcomeAwareEdges",
  );
  const applyTenantSpy = vi.spyOn(
    ExperienceRemediationService.prototype,
    "applyTenantPortfolioInterventions",
  );

  beforeEach(async () => {
    vi.clearAllMocks();

    getRecommendationsSpy.mockResolvedValue({
      prerequisites: [],
      followUps: [],
      related: [],
      alternatives: [],
    } as never);
    recomputeSpy.mockResolvedValue({ updatedEdges: 0 } as never);
    applyTenantSpy.mockResolvedValue({ applied: 0 } as never);

    app = Fastify();
    registerRecommendationRoutes(app, { prisma: {} as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed recommendations limit query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/assets/asset-1/recommendations?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(getRecommendationsSpy).not.toHaveBeenCalled();
  });

  it("rejects malformed remediation apply payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/remediation-policy/tenant/interventions/apply",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        maxActions: 0,
      },
    });

    expect(response.statusCode).toBe(400);
    expect(applyTenantSpy).not.toHaveBeenCalled();
  });

  it("rejects malformed recompute payload limit", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/recommendations/recompute",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        sourceAssetId: "asset-1",
        limit: 0,
      },
    });

    expect(response.statusCode).toBe(400);
    expect(recomputeSpy).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only source asset id", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/recommendations/recompute",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        sourceAssetId: "   ",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(recomputeSpy).not.toHaveBeenCalled();
  });
});
