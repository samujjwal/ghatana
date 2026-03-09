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

import { createHash } from 'crypto';
import type { PrismaClient } from '@ghatana/tutorputor-db';

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

  constructor(
    private readonly prisma: PrismaClient,
    options: { dedupWindowMs?: number } = {}
  ) {
    this.dedupWindowMs = options.dedupWindowMs || 24 * 60 * 60 * 1000; // 24 hours default
  }

  /**
   * Generate a unique fingerprint for a job based on its parameters.
   * This fingerprint is used to detect duplicate jobs.
   */
  generateFingerprint(
    jobType: string,
    experienceId: string,
    claimRef: string,
    payload: Record<string, any>
  ): string {
    // Create a deterministic hash of the job parameters
    const normalizedPayload = this.normalizePayload(payload);
    const fingerprintData = `${jobType}:${experienceId}:${claimRef}:${JSON.stringify(normalizedPayload)}`;
    
    return createHash('sha256').update(fingerprintData).digest('hex');
  }

  /**
   * Check if a job with the same fingerprint already exists and is pending/completed.
   */
  async checkDuplicate(fingerprint: string): Promise<DeduplicationResult> {
    // Check in database for recent jobs with same fingerprint
    const recentJob = await this.prisma.jobTracking.findFirst({
      where: {
        fingerprint,
        createdAt: {
          gte: new Date(Date.now() - this.dedupWindowMs),
        },
        status: {
          in: ['PENDING', 'PROCESSING', 'COMPLETED'],
        },
      },
      orderBy: { createdAt: 'desc' },
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
    metadata: Record<string, any>
  ): Promise<void> {
    await this.prisma.jobTracking.create({
      data: {
        jobId,
        fingerprint,
        jobType,
        status: 'PENDING',
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
    status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  ): Promise<void> {
    await this.prisma.jobTracking.update({
      where: { jobId },
      data: {
        status,
        updatedAt: new Date(),
      },
    });
  }

  /**
   * Clean up old job tracking records.
   */
  async cleanupOldJobs(olderThanMs: number = this.dedupWindowMs): Promise<number> {
    const result = await this.prisma.jobTracking.deleteMany({
      where: {
        createdAt: {
          lt: new Date(Date.now() - olderThanMs),
        },
      },
    });

    return result.count;
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
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        sorted[key] = this.normalizePayload(value);
      } else if (Array.isArray(value)) {
        sorted[key] = value.map(item => 
          item && typeof item === 'object' ? this.normalizePayload(item) : item
        );
      } else {
        sorted[key] = value;
      }
    }

    return sorted;
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
    params: Record<string, any>
  ): string {
    const data = `${operation}:${tenantId}:${resourceId}:${JSON.stringify(params)}`;
    return createHash('sha256').update(data).digest('hex');
  }

  /**
   * Generate a unique job ID with embedded idempotency info.
   */
  static generateJobId(
    jobType: string,
    tenantId: string,
    claimRef: string
  ): string {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 8);
    return `${jobType}:${tenantId}:${claimRef}:${timestamp}:${random}`;
  }
}
