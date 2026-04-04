/**
 * Job Deduplication and Idempotency Utilities
 *
 * Provides mechanisms to prevent duplicate job execution and ensure
 * idempotent operations for content generation.
 *
 * @doc.type utility
 * @doc.purpose Prevent duplicate content generation jobs
 * @doc.layer infrastructure
 */

import { createHash } from "crypto";
import type { PrismaClient } from "@tutorputor/core/db";
import type Redis from "ioredis";

interface JobTrackingRecord {
  jobId: string;
  fingerprint: string;
}

interface JobTrackingDelegate {
  findFirst(args: Record<string, unknown>): Promise<JobTrackingRecord | null>;
  create(args: Record<string, unknown>): Promise<unknown>;
  update(args: Record<string, unknown>): Promise<JobTrackingRecord>;
  deleteMany(args: Record<string, unknown>): Promise<{ count: number }>;
}

interface RedisLockClient {
  set(
    key: string,
    value: string,
    mode: "EX",
    durationSeconds: number,
    condition: "NX",
  ): Promise<string | null>;
}

type JobTrackingPrismaClient = PrismaClient & {
  jobTracking?: JobTrackingDelegate;
};

export interface JobFingerprint {
  jobType: string;
  experienceId: string;
  claimRef: string;
  contentHash: string;
  timestamp: number;
}

export interface DeduplicationResult {
  isDuplicate: boolean;
  existingJobId?: string;
  fingerprint: string;
}

export class JobDeduplicator {
  private readonly dedupWindowMs: number;
  private readonly lockTtlSeconds: number;
  private readonly redis?: Redis;
  private readonly jobTracking?: JobTrackingDelegate;

  constructor(
    prisma: PrismaClient,
    options: { dedupWindowMs?: number; redis?: Redis } = {},
  ) {
    const extendedPrisma = prisma as JobTrackingPrismaClient;
    this.dedupWindowMs = options.dedupWindowMs || 24 * 60 * 60 * 1000; // 24 hours default
    this.lockTtlSeconds = Math.max(1, Math.floor(this.dedupWindowMs / 1000));
    this.jobTracking = extendedPrisma.jobTracking;
    if (options.redis) {
      this.redis = options.redis;
    }
  }

  /**
   * Generate a unique fingerprint for a job based on its parameters.
   * This fingerprint is used to detect duplicate jobs.
   */
  generateFingerprint(
    jobType: string,
    experienceId: string,
    claimRef: string,
    payload: Record<string, any>,
  ): string {
    // Create a deterministic hash of the job parameters
    const normalizedPayload = this.normalizePayload(payload);
    const fingerprintData = `${jobType}:${experienceId}:${claimRef}:${JSON.stringify(normalizedPayload)}`;

    return createHash("sha256").update(fingerprintData).digest("hex");
  }

  /**
   * Check if a job with the same fingerprint already exists and is pending/completed.
   */
  async checkDuplicate(fingerprint: string): Promise<DeduplicationResult> {
    const lockKey = this.getRedisLockKey(fingerprint);

    // Fast distributed duplicate check via Redis lock.
    if (this.redis) {
      const lockResult = await (this.redis as unknown as RedisLockClient).set(
        lockKey,
        "1",
        "NX",
        "EX",
        this.lockTtlSeconds,
      );
      if (lockResult !== "OK") {
        return {
          isDuplicate: true,
          fingerprint,
        };
      }
    }

    // Check in database for recent jobs with same fingerprint
    const recentJob = await this.jobTracking?.findFirst({
      where: {
        fingerprint,
        createdAt: {
          gte: new Date(Date.now() - this.dedupWindowMs),
        },
        status: {
          in: ["PENDING", "PROCESSING", "COMPLETED"],
        },
      },
      orderBy: { createdAt: "desc" },
    });

    if (recentJob) {
      return {
        isDuplicate: true,
        existingJobId: recentJob.jobId,
        fingerprint,
      };
    }

    return {
      isDuplicate: false,
      fingerprint,
    };
  }

  /**
   * Track a new job for deduplication purposes.
   */
  async trackJob(
    jobId: string,
    fingerprint: string,
    jobType: string,
    metadata: Record<string, any>,
  ): Promise<void> {
    await this.jobTracking?.create({
      data: {
        jobId,
        fingerprint,
        jobType,
        status: "PENDING",
        metadata: JSON.stringify(metadata),
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    });
  }

  /**
   * Update job status in tracking table.
   */
  async updateJobStatus(
    jobId: string,
    status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED",
  ): Promise<void> {
    const updated = await this.jobTracking?.update({
      where: { jobId },
      data: {
        status,
        updatedAt: new Date(),
      },
    });

    // Allow retries for failed jobs by releasing the distributed lock.
    if (status === "FAILED" && this.redis) {
      const fingerprint = updated?.fingerprint;
      if (fingerprint) {
        await this.redis.del(this.getRedisLockKey(fingerprint));
      }
    }
  }

  /**
   * Clean up old job tracking records.
   */
  async cleanupOldJobs(
    olderThanMs: number = this.dedupWindowMs,
  ): Promise<number> {
    const result = await this.jobTracking?.deleteMany({
      where: {
        createdAt: {
          lt: new Date(Date.now() - olderThanMs),
        },
      },
    });

    return result?.count ?? 0;
  }

  /**
   * Normalize payload for consistent hashing.
   */
  private normalizePayload(payload: Record<string, any>): Record<string, any> {
    // Sort keys recursively for deterministic JSON stringification
    const sorted: Record<string, any> = {};
    const keys = Object.keys(payload).sort();

    for (const key of keys) {
      const value = payload[key];
      if (value && typeof value === "object" && !Array.isArray(value)) {
        sorted[key] = this.normalizePayload(value);
      } else if (Array.isArray(value)) {
        sorted[key] = value.map((item) =>
          item && typeof item === "object" ? this.normalizePayload(item) : item,
        );
      } else {
        sorted[key] = value;
      }
    }

    return sorted;
  }

  private getRedisLockKey(fingerprint: string): string {
    return `job-dedup:${fingerprint}`;
  }
}

/**
 * Idempotency key generator for API requests.
 */
export class IdempotencyKeyGenerator {
  /**
   * Generate an idempotency key from request parameters.
   */
  static generate(
    operation: string,
    tenantId: string,
    resourceId: string,
    params: Record<string, any>,
  ): string {
    const data = `${operation}:${tenantId}:${resourceId}:${JSON.stringify(params)}`;
    return createHash("sha256").update(data).digest("hex");
  }

  /**
   * Generate a unique job ID with embedded idempotency info.
   */
  static generateJobId(
    jobType: string,
    tenantId: string,
    claimRef: string,
  ): string {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 8);
    return `${jobType}:${tenantId}:${claimRef}:${timestamp}:${random}`;
  }
}
