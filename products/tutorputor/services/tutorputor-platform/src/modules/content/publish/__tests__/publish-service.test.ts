/**
 * Publish Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify governed publish and downstream reindex/recompute signaling
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { beforeEach, describe, expect, it, vi } from "vitest";
import { PublishService } from "../publish-service";

function makeAsset(overrides: Record<string, unknown> = {}) {
  return {
    id: "asset-1",
    tenantId: "tenant-1",
    assetType: "SIMULATION",
    manifestData: { id: "manifest-1", title: "Newton Lab" },
    ...overrides,
  };
}

function makeEvaluation(overrides: Record<string, unknown> = {}) {
  return {
    id: "eval-1",
    tenantId: "tenant-1",
    assetId: "asset-1",
    generationRequestId: "req-1",
    recommendation: "AUTO_PUBLISH",
    createdAt: new Date("2025-06-01T00:00:00Z"),
    ...overrides,
  };
}

function makePrisma() {
  return {
    contentAsset: {
      findFirst: vi.fn().mockResolvedValue(null),
      update: vi.fn().mockResolvedValue({ id: "asset-1" }),
    },
    evaluationRecord: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
    },
  };
}

describe("PublishService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: PublishService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new PublishService(prisma as never);
  });

  it("rejects publish when asset is missing", async () => {
    const result = await service.publishAsset("tenant-1", "admin-1", {
      assetId: "missing",
    });

    expect(result.published).toBe(false);
    expect(result.reason).toContain("not found");
  });

  it("rejects publish when no evaluation exists", async () => {
    prisma.contentAsset.findFirst.mockResolvedValue(makeAsset());
    prisma.evaluationRecord.findFirst.mockResolvedValue(null);

    const result = await service.publishAsset("tenant-1", "admin-1", {
      assetId: "asset-1",
    });

    expect(result.published).toBe(false);
    expect(result.reason).toContain("Run evaluation before publishing");
  });

  it("rejects publish when evaluation blocks the asset", async () => {
    prisma.contentAsset.findFirst.mockResolvedValue(makeAsset());
    prisma.evaluationRecord.findFirst.mockResolvedValue(
      makeEvaluation({ recommendation: "BLOCK" }),
    );

    const result = await service.publishAsset("tenant-1", "admin-1", {
      assetId: "asset-1",
    });

    expect(result.published).toBe(false);
    expect(result.reason).toContain("blocking");
  });

  it("rejects structured assets without manifests", async () => {
    prisma.contentAsset.findFirst.mockResolvedValue(
      makeAsset({ manifestData: null }),
    );
    prisma.evaluationRecord.findFirst.mockResolvedValue(makeEvaluation());

    const result = await service.publishAsset("tenant-1", "admin-1", {
      assetId: "asset-1",
    });

    expect(result.published).toBe(false);
    expect(result.reason).toContain("require a valid manifest");
  });

  it("publishes evaluated assets and marks downstream work stale", async () => {
    prisma.contentAsset.findFirst.mockResolvedValue(makeAsset());
    prisma.evaluationRecord.findFirst.mockResolvedValue(makeEvaluation());

    const result = await service.publishAsset("tenant-1", "admin-1", {
      assetId: "asset-1",
    });

    expect(prisma.contentAsset.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "asset-1" },
        data: expect.objectContaining({
          status: "PUBLISHED",
          semanticIndexStatus: "PENDING",
          recommendationStatus: "STALE",
        }),
      }),
    );
    expect(result.published).toBe(true);
    expect(result.semanticIndexStatus).toBe("pending");
    expect(result.recommendationStatus).toBe("stale");
  });

  it("bulk publishes all passing assets for a generation request", async () => {
    prisma.evaluationRecord.findMany.mockResolvedValue([
      makeEvaluation({ assetId: "asset-1" }),
      makeEvaluation({ assetId: "asset-2" }),
    ]);
    prisma.contentAsset.findFirst
      .mockResolvedValueOnce(makeAsset({ id: "asset-1" }))
      .mockResolvedValueOnce(makeAsset({ id: "asset-2" }));

    const result = await service.publishByGenerationRequest(
      "tenant-1",
      "admin-1",
      "req-1",
    );

    expect(result.published).toBe(2);
    expect(result.skipped).toBe(0);
    expect(result.results).toHaveLength(2);
  });
});
