/**
 * Data Retention Enforcement Worker
 *
 * Periodically scans for expired data and processes scheduled deletions.
 * Enforces per-tenant retention policies by purging data that has exceeded
 * its configured retention window.
 *
 * @doc.type worker
 * @doc.purpose Enforce data retention policies via scheduled cleanup
 * @doc.layer backend-worker
 * @doc.pattern ScheduledWorker
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';

// =============================================================================
// Types
// =============================================================================

export interface RetentionPolicy {
    tenantId: string;
    /** Resource type (e.g., 'audit_log', 'session_data', 'analytics_event') */
    resourceType: string;
    /** Retention period in days */
    retentionDays: number;
    /** Whether to archive before deleting */
    archiveBeforeDelete: boolean;
    /** Whether this policy is active */
    enabled: boolean;
}

export interface RetentionRunResult {
    tenantId: string;
    resourceType: string;
    deletedCount: number;
    archivedCount: number;
    errors: string[];
    durationMs: number;
}

export interface RetentionSummary {
    runAt: Date;
    totalDeleted: number;
    totalArchived: number;
    results: RetentionRunResult[];
    errors: string[];
}

// =============================================================================
// Default policies
// =============================================================================

const DEFAULT_POLICIES: Omit<RetentionPolicy, 'tenantId'>[] = [
    { resourceType: 'session_data', retentionDays: 90, archiveBeforeDelete: false, enabled: true },
    { resourceType: 'analytics_event', retentionDays: 365, archiveBeforeDelete: true, enabled: true },
    { resourceType: 'audit_log', retentionDays: 730, archiveBeforeDelete: true, enabled: true },
    { resourceType: 'temp_export', retentionDays: 7, archiveBeforeDelete: false, enabled: true },
    { resourceType: 'deleted_user_data', retentionDays: 30, archiveBeforeDelete: false, enabled: true },
];

// =============================================================================
// Worker
// =============================================================================

export class DataRetentionWorker {
    private readonly prisma: PrismaClient;
    private readonly batchSize: number;

    constructor(prisma: PrismaClient, options?: { batchSize?: number }) {
        this.prisma = prisma;
        this.batchSize = options?.batchSize ?? 500;
    }

    /**
     * Run retention enforcement for all tenants.
     */
    async runAll(): Promise<RetentionSummary> {
        const runAt = new Date();
        const results: RetentionRunResult[] = [];
        const errors: string[] = [];

        // 1. Process scheduled user deletions that have passed their grace period
        try {
            const deletionResult = await this.processScheduledDeletions();
            results.push(deletionResult);
        } catch (err: any) {
            errors.push(`Scheduled deletions failed: ${err.message}`);
        }

        // 2. Fetch tenant-specific policies (or use defaults)
        const tenants = await this.prisma.tenant.findMany({
            select: { id: true },
        });

        for (const tenant of tenants) {
            const policies = await this.getPoliciesForTenant(tenant.id);
            for (const policy of policies) {
                if (!policy.enabled) continue;
                try {
                    const result = await this.enforcePolicy(policy);
                    if (result.deletedCount > 0 || result.archivedCount > 0) {
                        results.push(result);
                    }
                } catch (err: any) {
                    errors.push(`Policy ${policy.resourceType}@${policy.tenantId}: ${err.message}`);
                }
            }
        }

        return {
            runAt,
            totalDeleted: results.reduce((sum, r) => sum + r.deletedCount, 0),
            totalArchived: results.reduce((sum, r) => sum + r.archivedCount, 0),
            results,
            errors,
        };
    }

    /**
     * Process scheduled user deletions whose grace period has expired.
     */
    async processScheduledDeletions(): Promise<RetentionRunResult> {
        const start = Date.now();
        let deletedCount = 0;
        const errors: string[] = [];

        const pendingDeletions = await this.prisma.dataDeletionRequest.findMany({
            where: {
                status: 'scheduled',
                scheduledDeletionAt: { lte: new Date() },
            },
            take: this.batchSize,
        });

        for (const request of pendingDeletions) {
            try {
                await this.deleteUserData(request.tenantId, request.userId);

                await this.prisma.dataDeletionRequest.update({
                    where: { id: request.id },
                    data: {
                        status: 'completed',
                        completedAt: new Date(),
                    },
                });

                deletedCount++;
            } catch (err: any) {
                errors.push(`User ${request.userId}: ${err.message}`);

                await this.prisma.dataDeletionRequest.update({
                    where: { id: request.id },
                    data: {
                        status: 'failed',
                        error: err.message,
                    },
                });
            }
        }

        return {
            tenantId: '*',
            resourceType: 'user_deletion',
            deletedCount,
            archivedCount: 0,
            errors,
            durationMs: Date.now() - start,
        };
    }

    /**
     * Enforce a single retention policy.
     */
    async enforcePolicy(policy: RetentionPolicy): Promise<RetentionRunResult> {
        const start = Date.now();
        const cutoffDate = new Date(Date.now() - policy.retentionDays * 24 * 60 * 60 * 1000);
        let deletedCount = 0;
        let archivedCount = 0;
        const errors: string[] = [];

        const handler = this.getResourceHandler(policy.resourceType);
        if (!handler) {
            return {
                tenantId: policy.tenantId,
                resourceType: policy.resourceType,
                deletedCount: 0,
                archivedCount: 0,
                errors: [`No handler for resource type: ${policy.resourceType}`],
                durationMs: Date.now() - start,
            };
        }

        try {
            if (policy.archiveBeforeDelete) {
                archivedCount = await handler.archive(policy.tenantId, cutoffDate, this.batchSize);
            }
            deletedCount = await handler.delete(policy.tenantId, cutoffDate, this.batchSize);
        } catch (err: any) {
            errors.push(err.message);
        }

        return {
            tenantId: policy.tenantId,
            resourceType: policy.resourceType,
            deletedCount,
            archivedCount,
            errors,
            durationMs: Date.now() - start,
        };
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private async getPoliciesForTenant(tenantId: string): Promise<RetentionPolicy[]> {
        // In production, fetch from a tenant_retention_policy table
        // For now, use defaults for every tenant
        return DEFAULT_POLICIES.map((p) => ({ ...p, tenantId }));
    }

    private getResourceHandler(resourceType: string): {
        archive: (tenantId: string, cutoff: Date, limit: number) => Promise<number>;
        delete: (tenantId: string, cutoff: Date, limit: number) => Promise<number>;
    } | null {
        const handlers: Record<string, ReturnType<typeof this.getResourceHandler>> = {
            session_data: {
                archive: async () => 0,
                delete: async (tenantId, cutoff, limit) => {
                    const result = await this.prisma.sessionEvent.deleteMany({
                        where: { tenantId, createdAt: { lt: cutoff } },
                    });
                    return result.count;
                },
            },
            audit_log: {
                archive: async (tenantId, cutoff, limit) => {
                    // Archive = mark as archived (real impl would export to cold storage)
                    const result = await this.prisma.auditLog.updateMany({
                        where: { tenantId, createdAt: { lt: cutoff }, archived: false },
                    });
                    return result.count;
                },
                delete: async () => 0, // Never delete audit logs, only archive
            },
            temp_export: {
                archive: async () => 0,
                delete: async (tenantId, cutoff, limit) => {
                    const result = await this.prisma.tempExport.deleteMany({
                        where: { tenantId, createdAt: { lt: cutoff } },
                    });
                    return result.count;
                },
            },
            deleted_user_data: {
                archive: async () => 0,
                delete: async (tenantId, cutoff, limit) => {
                    // Permanently purge soft-deleted user data after grace period
                    const result = await this.prisma.user.deleteMany({
                        where: {
                            tenantId,
                            deletedAt: { not: null, lt: cutoff },
                        },
                    });
                    return result.count;
                },
            },
        };

        return handlers[resourceType] ?? null;
    }

    /**
     * Delete all personal data for a user (GDPR right to erasure).
     */
    private async deleteUserData(tenantId: string, userId: string): Promise<void> {
        // Anonymize user record instead of hard delete (preserve referential integrity)
        await this.prisma.user.update({
            where: { id: userId },
            data: {
                email: `deleted-${userId}@anonymized.local`,
                name: 'Deleted User',
                deletedAt: new Date(),
            },
        });

        // Delete personal learning data
        await this.prisma.enrollment.deleteMany({ where: { tenantId, userId } });
        await this.prisma.assessmentAttempt.deleteMany({ where: { userId } });

        // Anonymize social data
        await this.prisma.studyGroupMember.deleteMany({ where: { userId } });
    }
}
