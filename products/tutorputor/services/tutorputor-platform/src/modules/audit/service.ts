/**
 * @doc.type module
 * @doc.purpose Audit log service
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
import type { TenantId, UserId, PaginatedResult } from '@ghatana/tutorputor-contracts';

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
    metadata?: Record<string, any>;
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

export class AuditServiceImpl {
    constructor(private readonly prisma: PrismaClient) { }

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

        const where: any = { tenantId: tenantId as string };

        if (actorId) where.actorId = actorId as string;
        if (action) where.action = action;
        if (resourceType) where.resourceType = resourceType;
        if (resourceId) where.resourceId = resourceId;

        if (startDate || endDate) {
            where.createdAt = {};
            if (startDate) where.createdAt.gte = new Date(startDate);
            if (endDate) where.createdAt.lte = new Date(endDate);
        }

        const orderBy: any = {};
        if (sortBy) {
            orderBy[sortBy] = sortOrder ?? 'desc';
        } else {
            orderBy.createdAt = 'desc';
        }

        const [events, totalCount] = await Promise.all([
            this.prisma.auditLog.findMany({
                where,
                take: take + 1,
                skip: cursor ? 1 : 0,
                cursor: cursor ? { id: cursor } : undefined,
                orderBy,
            }),
            this.prisma.auditLog.count({ where }),
        ]);

        const hasMore = events.length > take;
        const items = hasMore ? events.slice(0, -1) : events;
        const nextCursor = hasMore ? items[items.length - 1]?.id : undefined;

        return {
            items: items.map((e: any) => ({
                id: e.id,
                tenantId: e.tenantId as TenantId,
                timestamp: e.createdAt.toISOString(),
                actorId: e.actorId as UserId,
                actorEmail: e.actorEmail ?? undefined,
                action: e.action,
                resourceType: e.resourceType,
                resourceId: e.resourceId ?? '',
                metadata: (e.metadata as Record<string, any>) ?? {},
                ipAddress: e.ipAddress ?? undefined,
                userAgent: e.userAgent ?? undefined,
            })),
            nextCursor,
            totalCount,
            hasMore,
        };
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
                    where: { tenantId, createdAt: { gte: startDate } },
                }),
                this.prisma.auditLog.groupBy({
                    by: ['actorId'],
                    where: { tenantId, createdAt: { gte: startDate } },
                }),
                this.prisma.auditLog.groupBy({
                    by: ['action'],
                    where: { tenantId, createdAt: { gte: startDate } },
                    _count: { action: true },
                    orderBy: { _count: { action: 'desc' } },
                    take: 5,
                }),
                this.prisma.auditLog.groupBy({
                    by: ['resourceType'],
                    where: { tenantId, createdAt: { gte: startDate } },
                    _count: { resourceType: true },
                    orderBy: { _count: { resourceType: 'desc' } },
                    take: 5,
                }),
                this.prisma.auditLog.findMany({
                    where: { tenantId },
                    orderBy: { createdAt: 'desc' },
                    take: 10,
                }),
            ]);

        return {
            totalEvents: totalEvents as number,
            uniqueActors: (actors as any[]).length,
            topActions: (topActions as any[]).map((a: any) => ({
                action: a.action,
                count: a._count.action,
            })),
            topResourceTypes: (topResources as any[]).map((r: any) => ({
                resourceType: r.resourceType,
                count: r._count.resourceType,
            })),
            recentEvents: (recent as any[]).map((e: any) => ({
                id: e.id,
                tenantId: e.tenantId as TenantId,
                timestamp: e.createdAt.toISOString(),
                actorId: e.actorId as UserId,
                actorEmail: e.actorEmail ?? undefined,
                action: e.action,
                resourceType: e.resourceType,
                resourceId: e.resourceId ?? '',
                metadata: (e.metadata as Record<string, any>) ?? {},
            })),
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
