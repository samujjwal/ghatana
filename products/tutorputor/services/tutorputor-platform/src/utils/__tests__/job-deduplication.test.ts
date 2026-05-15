/**
 * @doc.type test
 * @doc.purpose Unit tests for JobDeduplicator — Redis NX lock, Prisma fallback, and fingerprinting
 * @doc.layer infrastructure
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

import { JobDeduplicator } from "../job-deduplication";
import type { PrismaClient } from "@tutorputor/core/db";
import type Redis from "ioredis";

// ---------------------------------------------------------------------------
// Mock factories
// ---------------------------------------------------------------------------
function makePrisma(): PrismaClient {
  return {
    jobTracking: {
      findFirst: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      deleteMany: vi.fn(),
    },
  } as unknown as PrismaClient;
}

function makeRedis(): Redis {
  return {
    set: vi.fn(),
    del: vi.fn(),
  } as unknown as Redis;
}

const FINGERPRINT = "abc123def456";
const JOB_ID = "job-uuid-001";

describe("JobDeduplicator", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let redis: ReturnType<typeof makeRedis>;

  beforeEach(() => {
    prisma = makePrisma();
    redis = makeRedis();
    vi.clearAllMocks();
  });

  // -------------------------------------------------------------------------
  // checkDuplicate — with Redis
  // -------------------------------------------------------------------------
  describe("checkDuplicate (with Redis)", () => {
    it("returns isDuplicate=false when Redis NX lock is acquired (SET returns OK)", async () => {
      vi.mocked(redis.set).mockResolvedValue("OK");

      const dedup = new JobDeduplicator(prisma, {
        redis,
        dedupWindowMs: 60_000,
      });
      const result = await dedup.checkDuplicate(FINGERPRINT);

      expect(result.isDuplicate).toBe(false);
      expect(result.fingerprint).toBe(FINGERPRINT);

      // Verify NX + EX were used
      expect(redis.set).toHaveBeenCalledWith(
        expect.stringContaining(FINGERPRINT),
        "1",
        "EX",
        expect.any(Number),
        "NX",
      );
    });

    it("returns isDuplicate=true when Redis NX lock is already held (SET returns null)", async () => {
      vi.mocked(redis.set).mockResolvedValue(null);

      const dedup = new JobDeduplicator(prisma, {
        redis,
        dedupWindowMs: 60_000,
      });
      const result = await dedup.checkDuplicate(FINGERPRINT);

      expect(result.isDuplicate).toBe(true);
      expect(result.existingJobId).toBeUndefined();
      // Prisma should NOT be consulted when Redis says duplicate
      expect(prisma.jobTracking.findFirst).not.toHaveBeenCalled();
    });
  });

  // -------------------------------------------------------------------------
  // checkDuplicate — without Redis (Prisma fallback)
  // -------------------------------------------------------------------------
  describe("checkDuplicate (Prisma fallback, no Redis)", () => {
    it("uses Prisma when Redis is not configured", async () => {
      vi.mocked(
        prisma.jobTracking.findFirst as ReturnType<typeof vi.fn>,
      ).mockResolvedValue(null);

      const dedup = new JobDeduplicator(prisma, { dedupWindowMs: 60_000 });
      const result = await dedup.checkDuplicate(FINGERPRINT);

      expect(prisma.jobTracking.findFirst).toHaveBeenCalled();
      expect(result.isDuplicate).toBe(false);
    });

    it("returns isDuplicate=true when Prisma finds a recent matching job", async () => {
      vi.mocked(
        prisma.jobTracking.findFirst as ReturnType<typeof vi.fn>,
      ).mockResolvedValue({
        jobId: JOB_ID,
        fingerprint: FINGERPRINT,
        status: "COMPLETED",
        createdAt: new Date(),
      });

      const dedup = new JobDeduplicator(prisma, { dedupWindowMs: 60_000 });
      const result = await dedup.checkDuplicate(FINGERPRINT);

      expect(result.isDuplicate).toBe(true);
      expect(result.existingJobId).toBe(JOB_ID);
    });
  });

  // -------------------------------------------------------------------------
  // trackJob
  // -------------------------------------------------------------------------
  describe("trackJob", () => {
    it("creates a job tracking record with PENDING status", async () => {
      vi.mocked(
        prisma.jobTracking.create as ReturnType<typeof vi.fn>,
      ).mockResolvedValue({});

      const dedup = new JobDeduplicator(prisma);
      await dedup.trackJob(JOB_ID, FINGERPRINT, "EXAMPLE_GENERATION", {
        key: "val",
      });

      expect(prisma.jobTracking.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            jobId: JOB_ID,
            fingerprint: FINGERPRINT,
            jobType: "EXAMPLE_GENERATION",
            status: "PENDING",
          }),
        }),
      );
    });
  });

  // -------------------------------------------------------------------------
  // updateJobStatus
  // -------------------------------------------------------------------------
  describe("updateJobStatus", () => {
    it("updates job status in Prisma", async () => {
      vi.mocked(
        prisma.jobTracking.update as ReturnType<typeof vi.fn>,
      ).mockResolvedValue({
        jobId: JOB_ID,
        fingerprint: FINGERPRINT,
        status: "COMPLETED",
      });

      const dedup = new JobDeduplicator(prisma);
      await dedup.updateJobStatus(JOB_ID, "COMPLETED");

      expect(prisma.jobTracking.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { jobId: JOB_ID },
          data: expect.objectContaining({ status: "COMPLETED" }),
        }),
      );
    });

    it("releases Redis lock when job status is FAILED", async () => {
      vi.mocked(
        prisma.jobTracking.update as ReturnType<typeof vi.fn>,
      ).mockResolvedValue({
        jobId: JOB_ID,
        fingerprint: FINGERPRINT,
        status: "FAILED",
      });
      vi.mocked(redis.del).mockResolvedValue(1);

      const dedup = new JobDeduplicator(prisma, { redis });
      await dedup.updateJobStatus(JOB_ID, "FAILED");

      expect(redis.del).toHaveBeenCalledWith(
        expect.stringContaining(FINGERPRINT),
      );
    });

    it("does NOT release Redis lock for non-FAILED terminal statuses", async () => {
      vi.mocked(
        prisma.jobTracking.update as ReturnType<typeof vi.fn>,
      ).mockResolvedValue({
        jobId: JOB_ID,
        fingerprint: FINGERPRINT,
        status: "COMPLETED",
      });

      const dedup = new JobDeduplicator(prisma, { redis });
      await dedup.updateJobStatus(JOB_ID, "COMPLETED");

      expect(redis.del).not.toHaveBeenCalled();
    });
  });

  // -------------------------------------------------------------------------
  // generateFingerprint
  // -------------------------------------------------------------------------
  describe("generateFingerprint", () => {
    it("produces a deterministic SHA-256 hex string", () => {
      const dedup = new JobDeduplicator(prisma);
      const fp1 = dedup.generateFingerprint("EXAMPLE", "exp-1", "claim-1", {
        a: 1,
      });
      const fp2 = dedup.generateFingerprint("EXAMPLE", "exp-1", "claim-1", {
        a: 1,
      });
      expect(fp1).toBe(fp2);
      expect(fp1).toMatch(/^[0-9a-f]{64}$/);
    });

    it("produces different fingerprints for different inputs", () => {
      const dedup = new JobDeduplicator(prisma);
      const fp1 = dedup.generateFingerprint("EXAMPLE", "exp-1", "claim-1", {
        a: 1,
      });
      const fp2 = dedup.generateFingerprint("EXAMPLE", "exp-1", "claim-2", {
        a: 1,
      });
      expect(fp1).not.toBe(fp2);
    });

    it("normalizes payload keys for consistency (sorted keys)", () => {
      const dedup = new JobDeduplicator(prisma);
      const fp1 = dedup.generateFingerprint("T", "e", "c", { b: 2, a: 1 });
      const fp2 = dedup.generateFingerprint("T", "e", "c", { a: 1, b: 2 });
      // Sorted keys → same fingerprint regardless of insertion order
      expect(fp1).toBe(fp2);
    });
  });
});
