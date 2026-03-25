/**
 * Regeneration Candidate Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify regeneration candidate lifecycle and auto-detection
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { beforeEach, describe, expect, it, vi } from "vitest";
import { RegenerationCandidateService } from "../candidate-service";

function makeCandidate(overrides: Record<string, unknown> = {}) {
  return {
    id: "cand-1",
    tenantId: "tenant-1",
    assetId: "asset-1",
    assetType: "explainer",
    trigger: "POOR_DISCOVERY_PERFORMANCE",
    severity: "MEDIUM",
    reason: "Negative feedback signals",
    evidence: { negativeCount: 4 },
    priority: 60,
    status: "OPEN",
    generationRequestId: null,
    resolvedBy: null,
    resolvedAt: null,
    createdAt: new Date("2025-06-01T00:00:00Z"),
    updatedAt: new Date("2025-06-01T00:00:00Z"),
    ...overrides,
  };
}

function makePrisma() {
  return {
    regenerationCandidate: {
      create: vi.fn().mockResolvedValue(makeCandidate()),
      findMany: vi.fn().mockResolvedValue([]),
      findFirst: vi.fn().mockResolvedValue(null),
      update: vi
        .fn()
        .mockResolvedValue(
          makeCandidate({ status: "DISMISSED", resolvedBy: "admin-1" }),
        ),
    },
    explorerEvent: {
      groupBy: vi.fn().mockResolvedValue([]),
    },
  };
}

describe("RegenerationCandidateService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: RegenerationCandidateService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new RegenerationCandidateService(prisma as never);
  });

  it("creates a candidate with default severity and open status", async () => {
    const result = await service.createCandidate("tenant-1", {
      assetId: "asset-1",
      trigger: "poor_discovery_performance",
      reason: "Search performance dropped",
    });

    expect(prisma.regenerationCandidate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          trigger: "POOR_DISCOVERY_PERFORMANCE",
          severity: "MEDIUM",
          status: "OPEN",
        }),
      }),
    );
    expect(result.status).toBe("open");
  });

  it("lists open candidates with optional filters", async () => {
    prisma.regenerationCandidate.findMany.mockResolvedValue([
      makeCandidate({ id: "cand-1" }),
      makeCandidate({ id: "cand-2", assetId: "asset-2" }),
    ]);

    const result = await service.listOpenCandidates("tenant-1", {
      trigger: "poor_discovery_performance",
    });

    expect(prisma.regenerationCandidate.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          trigger: "POOR_DISCOVERY_PERFORMANCE",
          status: "OPEN",
        }),
      }),
    );
    expect(result).toHaveLength(2);
  });

  it("dismisses an existing candidate", async () => {
    prisma.regenerationCandidate.findFirst.mockResolvedValue(makeCandidate());

    const result = await service.dismissCandidate(
      "tenant-1",
      "cand-1",
      "admin-1",
    );

    expect(prisma.regenerationCandidate.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "cand-1" },
        data: expect.objectContaining({ status: "DISMISSED" }),
      }),
    );
    expect(result.status).toBe("dismissed");
  });

  it("queues an existing candidate for regeneration", async () => {
    prisma.regenerationCandidate.findFirst.mockResolvedValue(makeCandidate());
    prisma.regenerationCandidate.update.mockResolvedValue(
      makeCandidate({ status: "QUEUED", generationRequestId: "req-1" }),
    );

    const result = await service.queueCandidate("tenant-1", "cand-1", "req-1");

    expect(result.status).toBe("queued");
    expect(result.generationRequestId).toBe("req-1");
  });

  it("detects candidates from repeated negative feedback", async () => {
    prisma.explorerEvent.groupBy.mockResolvedValue([
      { assetId: "asset-1", _count: { assetId: 4 } },
    ]);
    prisma.regenerationCandidate.findFirst.mockResolvedValue(null);

    const result = await service.detectFromFeedback("tenant-1");

    expect(result).toBe(1);
    expect(prisma.regenerationCandidate.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          assetId: "asset-1",
          trigger: "POOR_DISCOVERY_PERFORMANCE",
        }),
      }),
    );
  });
});
