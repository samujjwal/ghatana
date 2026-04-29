/**
 * Data Retention Service
 *
 * Manages data retention policies by periodically cleaning up old records
 * based on configurable retention periods. Follows platform/java/database patterns
 * for TTL and cleanup jobs.
 *
 * @doc.type class
 * @doc.purpose Manage data retention policies and cleanup of old records
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type { FastifyBaseLogger } from "fastify";

export interface RetentionPolicy {
  /** Entity type to apply retention to */
  entityType: string;
  /** Retention period in days */
  retentionDays: number;
  /** Custom WHERE clause for filtering (optional) */
  whereClause?: string;
  /** Whether this policy is enabled */
  enabled: boolean;
}

export interface RetentionConfig {
  policies: RetentionPolicy[];
}

export class DataRetentionService {
  private readonly DEFAULT_POLICIES: RetentionPolicy[] = [
    {
      entityType: "ExplorerEvent",
      retentionDays: 90,
      enabled: true,
    },
    {
      entityType: "ProvenanceNode",
      retentionDays: 365,
      enabled: true,
    },
    {
      entityType: "GenerationJob",
      retentionDays: 180,
      whereClause: "status = 'COMPLETED'",
      enabled: true,
    },
    {
      entityType: "GenerationRequest",
      retentionDays: 365,
      whereClause: "status = 'COMPLETED'",
      enabled: true,
    },
  ];

  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: FastifyBaseLogger,
    private readonly config?: RetentionConfig,
  ) {}

  /**
   * Get the active retention policies
   */
  getActivePolicies(): RetentionPolicy[] {
    const policies = this.config?.policies ?? this.DEFAULT_POLICIES;
    return policies.filter((p) => p.enabled);
  }

  /**
   * Execute cleanup for all active retention policies
   */
  async executeCleanup(): Promise<{ entityType: string; deletedCount: number }[]> {
    const results: { entityType: string; deletedCount: number }[] = [];
    const policies = this.getActivePolicies();

    this.logger.info(
      { policyCount: policies.length },
      "Starting data retention cleanup",
    );

    for (const policy of policies) {
      try {
        const deletedCount = await this.cleanupEntity(policy);
        results.push({ entityType: policy.entityType, deletedCount });

        this.logger.info(
          {
            entityType: policy.entityType,
            retentionDays: policy.retentionDays,
            deletedCount,
          },
          `Cleaned up ${policy.entityType} records`,
        );
      } catch (error) {
        this.logger.error(
          { error, entityType: policy.entityType },
          `Failed to cleanup ${policy.entityType}`,
        );
        results.push({ entityType: policy.entityType, deletedCount: 0 });
      }
    }

    const totalDeleted = results.reduce((sum, r) => sum + r.deletedCount, 0);
    this.logger.info(
      { totalDeleted, policyCount: policies.length },
      "Data retention cleanup completed",
    );

    return results;
  }

  /**
   * Cleanup records for a specific entity type based on retention policy
   */
  private async cleanupEntity(policy: RetentionPolicy): Promise<number> {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - policy.retentionDays);

    switch (policy.entityType) {
      case "ExplorerEvent":
        return await this.cleanupExplorerEvents(cutoffDate, policy);
      case "ProvenanceNode":
        return await this.cleanupProvenanceNodes(cutoffDate, policy);
      case "GenerationJob":
        return await this.cleanupGenerationJobs(cutoffDate, policy);
      case "GenerationRequest":
        return await this.cleanupGenerationRequests(cutoffDate, policy);
      default:
        this.logger.warn(
          { entityType: policy.entityType },
          "Unknown entity type for retention cleanup",
        );
        return 0;
    }
  }

  private async cleanupExplorerEvents(
    cutoffDate: Date,
    policy: RetentionPolicy,
  ): Promise<number> {
    const where: Record<string, unknown> = {
      occurredAt: { lt: cutoffDate },
    };

    if (policy.whereClause) {
      // Parse simple where clause (e.g., "status = 'COMPLETED'")
      // For now, we'll skip complex where clauses
      this.logger.warn(
        { whereClause: policy.whereClause },
        "Custom where clauses not yet supported for ExplorerEvent",
      );
    }

    const result = await this.prisma.explorerEvent.deleteMany({ where });
    return result.count;
  }

  private async cleanupProvenanceNodes(
    cutoffDate: Date,
    policy: RetentionPolicy,
  ): Promise<number> {
    const where: Record<string, unknown> = {
      createdAt: { lt: cutoffDate },
    };

    if (policy.whereClause) {
      this.logger.warn(
        { whereClause: policy.whereClause },
        "Custom where clauses not yet supported for ProvenanceNode",
      );
    }

    const result = await this.prisma.provenanceNode.deleteMany({ where });
    return result.count;
  }

  private async cleanupGenerationJobs(
    cutoffDate: Date,
    policy: RetentionPolicy,
  ): Promise<number> {
    const where: Record<string, unknown> = {
      createdAt: { lt: cutoffDate },
    };

    if (policy.whereClause?.includes("COMPLETED")) {
      where["status"] = "COMPLETED";
    }

    const result = await this.prisma.generationJob.deleteMany({ where });
    return result.count;
  }

  private async cleanupGenerationRequests(
    cutoffDate: Date,
    policy: RetentionPolicy,
  ): Promise<number> {
    const where: Record<string, unknown> = {
      createdAt: { lt: cutoffDate },
    };

    if (policy.whereClause?.includes("COMPLETED")) {
      where["status"] = "COMPLETED";
    }

    const result = await this.prisma.generationRequest.deleteMany({ where });
    return result.count;
  }

  /**
   * Get statistics about data that would be cleaned up (dry run)
   */
  async getCleanupStats(): Promise<
    { entityType: string; wouldDeleteCount: number; retentionDays: number }[]
  > {
    const policies = this.getActivePolicies();
    const stats: {
      entityType: string;
      wouldDeleteCount: number;
      retentionDays: number;
    }[] = [];

    for (const policy of policies) {
      const cutoffDate = new Date();
      cutoffDate.setDate(cutoffDate.getDate() - policy.retentionDays);

      let count = 0;
      switch (policy.entityType) {
        case "ExplorerEvent":
          count = await this.prisma.explorerEvent.count({
            where: { occurredAt: { lt: cutoffDate } },
          });
          break;
        case "ProvenanceNode":
          count = await this.prisma.provenanceNode.count({
            where: { createdAt: { lt: cutoffDate } },
          });
          break;
        case "GenerationJob":
          count = await this.prisma.generationJob.count({
            where: {
              createdAt: { lt: cutoffDate },
              ...(policy.whereClause?.includes("COMPLETED")
                ? { status: "COMPLETED" }
                : {}),
            },
          });
          break;
        case "GenerationRequest":
          count = await this.prisma.generationRequest.count({
            where: {
              createdAt: { lt: cutoffDate },
              ...(policy.whereClause?.includes("COMPLETED")
                ? { status: "COMPLETED" }
                : {}),
            },
          });
          break;
      }

      stats.push({
        entityType: policy.entityType,
        wouldDeleteCount: count,
        retentionDays: policy.retentionDays,
      });
    }

    return stats;
  }
}
