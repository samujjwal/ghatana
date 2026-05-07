/**
 * @doc.type module
 * @doc.purpose Audit log service
 * @doc.layer product
 * @doc.pattern Service
 */

import type { Prisma, PrismaClient } from '@tutorputor/core/db';
import type { TenantId, UserId, PaginatedResult } from '@tutorputor/contracts';

// =============================================================================
// Types
// =============================================================================

export interface AuditLogEntry {
    id: string;
    tenantId: TenantId;
    timestamp: string;
    actorId: UserId;
    actorEmail?: string;
    action: string;
    resourceType: string;
    resourceId: string;
    metadata?: Record<string, unknown>;
    ipAddress?: string;
    userAgent?: string;
}

export interface AuditLogQuery {
    tenantId: TenantId;
    actorId?: UserId;
    action?: string;
    resourceType?: string;
    resourceId?: string;
    startDate?: string;
    endDate?: string;
    pagination: {
        cursor?: string;
        limit?: number;
        sortBy?: string;
        sortOrder?: 'asc' | 'desc';
    };
}

export interface AuditLogSummary {
    totalEvents: number;
    uniqueActors: number;
    topActions: Array<{ action: string; count: number }>;
    topResourceTypes: Array<{ resourceType: string; count: number }>;
    recentEvents: AuditLogEntry[];
}

const AUDIT_LOG_SORT_KEYS = new Set<keyof Prisma.AuditLogOrderByWithRelationInput>([
    'id',
    'tenantId',
    'actorId',
    'action',
    'resourceType',
    'resourceId',
    'timestamp',
]);

function isAuditLogSortKey(
    value: string,
): value is keyof Prisma.AuditLogOrderByWithRelationInput {
    return AUDIT_LOG_SORT_KEYS.has(value as keyof Prisma.AuditLogOrderByWithRelationInput);
}

export class AuditServiceImpl {
    constructor(private readonly prisma: PrismaClient) { }

    private parseMetadata(metadata: string | null): Record<string, unknown> {
        if (!metadata) {
            return {};
        }

        try {
            const parsed = JSON.parse(metadata);
            return parsed && typeof parsed === 'object' ? parsed as Record<string, unknown> : {};
        } catch {
            return {};
        }
    }

    private toAuditLogEntry(e: {
        id: string;
        tenantId: string;
        timestamp: Date;
        actorId: string;
        action: string;
        resourceType: string;
        resourceId: string;
        metadata: string;
        ipAddress: string | null;
        userAgent: string | null;
    }): AuditLogEntry {
        const entry: AuditLogEntry = {
            id: e.id,
            tenantId: e.tenantId as TenantId,
            timestamp: e.timestamp.toISOString(),
            actorId: e.actorId as UserId,
            action: e.action,
            resourceType: e.resourceType,
            resourceId: e.resourceId ?? '',
            metadata: this.parseMetadata(e.metadata),
        };

        if (e.ipAddress) {
            entry.ipAddress = e.ipAddress;
        }
        if (e.userAgent) {
            entry.userAgent = e.userAgent;
        }

        return entry;
    }

    async queryAuditEvents(query: AuditLogQuery): Promise<PaginatedResult<AuditLogEntry>> {
        const {
            tenantId,
            actorId,
            action,
            resourceType,
            resourceId,
            startDate,
            endDate,
            pagination,
        } = query;

        const { cursor, limit = 50, sortBy, sortOrder } = pagination;
        const take = Math.min(limit, 200);

        const where: Prisma.AuditLogWhereInput = { tenantId: tenantId as string };

        if (actorId) where.actorId = actorId as string;
        if (action) where.action = action;
        if (resourceType) where.resourceType = resourceType;
        if (resourceId) where.resourceId = resourceId;

        if (startDate || endDate) {
            where.timestamp = {};
            if (startDate) where.timestamp.gte = new Date(startDate);
            if (endDate) where.timestamp.lte = new Date(endDate);
        }

        const orderBy: Prisma.AuditLogOrderByWithRelationInput = {};
        if (sortBy && isAuditLogSortKey(sortBy)) {
            orderBy[sortBy] = sortOrder ?? 'desc';
        } else {
            orderBy.timestamp = 'desc';
        }

        const queryArgs: Prisma.AuditLogFindManyArgs = {
            where,
            take: take + 1,
            skip: cursor ? 1 : 0,
            orderBy,
        };
        if (cursor) {
            queryArgs.cursor = { id: cursor };
        }

        const [events, totalCount] = await Promise.all([
            this.prisma.auditLog.findMany(queryArgs),
            this.prisma.auditLog.count({ where }),
        ]);

        const hasMore = events.length > take;
        const items = hasMore ? events.slice(0, -1) : events;
        const nextCursor = hasMore ? items[items.length - 1]?.id : undefined;

        const result: PaginatedResult<AuditLogEntry> = {
            items: items.map((e) => this.toAuditLogEntry(e)),
            totalCount,
            hasMore,
        };
        if (nextCursor) {
            result.nextCursor = nextCursor;
        }

        return result;
    }

    async getAuditSummary(args: {
        tenantId: TenantId;
        days?: number;
    }): Promise<AuditLogSummary> {
        const { tenantId, days = 30 } = args;
        const startDate = new Date(Date.now() - days * 24 * 60 * 60 * 1000);

        const [totalEvents, actors, topActions, topResources, recent] =
            await Promise.all([
                this.prisma.auditLog.count({
                    where: { tenantId, timestamp: { gte: startDate } },
                }),
                this.prisma.auditLog.groupBy({
                    by: ['actorId'],
                    where: { tenantId, timestamp: { gte: startDate } },
                }),
                this.prisma.auditLog.groupBy({
                    by: ['action'],
                    where: { tenantId, timestamp: { gte: startDate } },
                    _count: { action: true },
                    orderBy: { _count: { action: 'desc' } },
                    take: 5,
                }),
                this.prisma.auditLog.groupBy({
                    by: ['resourceType'],
                    where: { tenantId, timestamp: { gte: startDate } },
                    _count: { resourceType: true },
                    orderBy: { _count: { resourceType: 'desc' } },
                    take: 5,
                }),
                this.prisma.auditLog.findMany({
                    where: { tenantId },
                    orderBy: { timestamp: 'desc' },
                    take: 10,
                }),
            ]);

        return {
            totalEvents: totalEvents as number,
            uniqueActors: actors.length,
            topActions: topActions.map((a) => ({
                action: a.action,
                count: a._count.action,
            })),
            topResourceTypes: topResources.map((r) => ({
                resourceType: r.resourceType,
                count: r._count.resourceType,
            })),
            recentEvents: recent.map((e) => this.toAuditLogEntry(e)),
        };
    }

    async exportAuditLog(args: {
        tenantId: TenantId;
        startDate: string;
        endDate: string;
    }): Promise<{ downloadUrl: string }> {
        // In production, this would trigger a background job and upload to S3
        // For now, we'll return a mock URL
        return {
            downloadUrl: `https://api.tutorputor.com/exports/audit-${args.tenantId}-${Date.now()}.csv`,
        };
    }

    async checkHealth(): Promise<boolean> {
        await this.prisma.$queryRaw`SELECT 1`;
        return true;
    }
}
