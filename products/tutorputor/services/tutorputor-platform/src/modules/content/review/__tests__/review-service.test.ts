/**
 * Review Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify generation review decision submission and retrieval
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { GenerationReviewService } from "../review-service";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeRequest(overrides: Record<string, any> = {}) {
  return {
    id: "req-1",
    tenantId: "tenant-1",
    status: "COMPLETED",
    ...overrides,
  };
}

function makeDecisionRow(overrides: Record<string, any> = {}) {
  return {
    id: "dec-1",
    tenantId: "tenant-1",
    requestId: "req-1",
    status: "APPROVED",
    reviewedBy: "user-1",
    decisionNote: "Looks good",
    regenerateJobIds: null,
    reviewedAt: new Date("2025-06-01"),
    createdAt: new Date("2025-06-01"),
    updatedAt: new Date("2025-06-01"),
    ...overrides,
  };
}

function makePrisma() {
  return {
    generationRequest: {
      findFirst: vi.fn(),
      update: vi.fn(),
    },
    generationReviewDecision: {
      create: vi.fn().mockResolvedValue(makeDecisionRow()),
      findMany: vi.fn().mockResolvedValue([]),
      findFirst: vi.fn().mockResolvedValue(null),
    },
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("GenerationReviewService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: GenerationReviewService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new GenerationReviewService(prisma as any);
  });

  describe("submitDecision", () => {
    it("throws when request not found", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(null);

      await expect(
        service.submitDecision("tenant-1", "user-1", {
          requestId: "missing-req",
          status: "approved",
        }),
      ).rejects.toThrow("not found");
    });

    it("throws when request is in non-reviewable state", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(
        makeRequest({ status: "EXECUTING" }),
      );

      await expect(
        service.submitDecision("tenant-1", "user-1", {
          requestId: "req-1",
          status: "approved",
        }),
      ).rejects.toThrow("not reviewable");
    });

    it("persists a review decision with correct data", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(makeRequest());
      prisma.generationReviewDecision.create.mockResolvedValue(
        makeDecisionRow({ status: "APPROVED" }),
      );

      const result = await service.submitDecision("tenant-1", "user-1", {
        requestId: "req-1",
        status: "approved",
        decisionNote: "Approved",
      });

      expect(prisma.generationReviewDecision.create).toHaveBeenCalledOnce();
      const args = prisma.generationReviewDecision.create.mock.calls[0][0];
      expect(args.data.tenantId).toBe("tenant-1");
      expect(args.data.requestId).toBe("req-1");
      expect(args.data.reviewedBy).toBe("user-1");
      expect(result.status).toBe("approved");
    });

    it("maps decision status to lowercase", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(makeRequest());
      prisma.generationReviewDecision.create.mockResolvedValue(
        makeDecisionRow({ status: "REJECTED" }),
      );

      const result = await service.submitDecision("tenant-1", "user-1", {
        requestId: "req-1",
        status: "rejected",
        decisionNote: "Needs rework",
      });

      expect(result.status).toBe("rejected");
    });

    it("includes regenerateJobIds when provided", async () => {
      prisma.generationRequest.findFirst.mockResolvedValue(makeRequest());
      const jobIds = ["job-1", "job-2"];
      prisma.generationReviewDecision.create.mockResolvedValue(
        makeDecisionRow({ regenerateJobIds: jobIds }),
      );

      await service.submitDecision("tenant-1", "user-1", {
        requestId: "req-1",
        status: "regeneration_requested",
        regenerateJobIds: jobIds,
      });

      const args = prisma.generationReviewDecision.create.mock.calls[0][0];
      expect(args.data.regenerateJobIds).toEqual(jobIds);
    });
  });

  describe("listDecisions", () => {
    it("returns empty list when no decisions exist", async () => {
      prisma.generationReviewDecision.findMany.mockResolvedValue([]);

      const decisions = await service.listDecisions("tenant-1", "req-1");

      expect(decisions).toEqual([]);
    });

    it("returns mapped decisions in descending order", async () => {
      prisma.generationReviewDecision.findMany.mockResolvedValue([
        makeDecisionRow({ id: "dec-2" }),
        makeDecisionRow({ id: "dec-1" }),
      ]);

      const decisions = await service.listDecisions("tenant-1", "req-1");

      expect(decisions).toHaveLength(2);
      expect(decisions[0].id).toBe("dec-2");
    });
  });

  describe("getLatestDecision", () => {
    it("returns null when no decisions exist", async () => {
      prisma.generationReviewDecision.findFirst.mockResolvedValue(null);

      const decision = await service.getLatestDecision("tenant-1", "req-1");

      expect(decision).toBeNull();
    });

    it("returns the latest decision", async () => {
      prisma.generationReviewDecision.findFirst.mockResolvedValue(
        makeDecisionRow(),
      );

      const decision = await service.getLatestDecision("tenant-1", "req-1");

      expect(decision).not.toBeNull();
      expect(decision!.id).toBe("dec-1");
    });
  });

  describe("ensurePendingDecision", () => {
    it("reuses an existing pending review decision", async () => {
      prisma.generationReviewDecision.findFirst.mockResolvedValue(
        makeDecisionRow({ status: "PENDING" }),
      );

      const decision = await service.ensurePendingDecision("tenant-1", "req-1");

      expect(decision.status).toBe("pending");
      expect(prisma.generationReviewDecision.create).not.toHaveBeenCalled();
    });

    it("creates a pending review decision when one does not exist", async () => {
      prisma.generationReviewDecision.findFirst.mockResolvedValueOnce(null);
      prisma.generationReviewDecision.create.mockResolvedValue(
        makeDecisionRow({
          status: "PENDING",
          reviewedBy: null,
          reviewedAt: null,
          decisionNote: "Awaiting reviewer",
        }),
      );

      const decision = await service.ensurePendingDecision(
        "tenant-1",
        "req-1",
        "Awaiting reviewer",
      );

      expect(prisma.generationReviewDecision.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            tenantId: "tenant-1",
            requestId: "req-1",
            status: "PENDING",
            decisionNote: "Awaiting reviewer",
          }),
        }),
      );
      expect(decision.status).toBe("pending");
    });
  });
});
