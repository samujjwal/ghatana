/**
 * Audit Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for audit log query, summary, and export
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AuditServiceImpl } from '../service';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        auditLog: {
            findMany: vi.fn(),
            count: vi.fn(),
            groupBy: vi.fn(),
        },
        $queryRaw: vi.fn(),
    } as unknown as PrismaClient;
}

function makeAuditRow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'audit-1',
        tenantId: 'tenant-1',
        createdAt: new Date('2025-01-15T10:00:00Z'),
        actorId: 'user-1',
        actorEmail: 'user@example.com',
        action: 'module.create',
        resourceType: 'module',
        resourceId: 'mod-1',
        metadata: { version: 1 },
        ipAddress: '127.0.0.1',
        userAgent: 'Mozilla/5.0',
        ...overrides,
    };
}

describe('AuditServiceImpl', () => {
    let service: AuditServiceImpl;
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        service = new AuditServiceImpl(prisma);
    });

    // =========================================================================
    // queryAuditEvents
    // =========================================================================
    describe('queryAuditEvents', () => {
        it('returns paginated audit events', async () => {
            const rows = [makeAuditRow(), makeAuditRow({ id: 'audit-2' })];
            (prisma.auditLog.findMany as any).mockResolvedValue(rows);
            (prisma.auditLog.count as any).mockResolvedValue(2);

            const result = await service.queryAuditEvents({
                tenantId: 'tenant-1' as any,
                pagination: { limit: 50 },
            });

            expect(result.items).toHaveLength(2);
            expect(result.totalCount).toBe(2);
            expect(result.hasMore).toBe(false);
            expect(result.items[0]).toMatchObject({
                id: 'audit-1',
                action: 'module.create',
                resourceType: 'module',
            });
        });

        it('handles cursor-based pagination', async () => {
            const rows = [makeAuditRow({ id: 'audit-3' }), makeAuditRow({ id: 'audit-4' }), makeAuditRow({ id: 'audit-5' })];
            (prisma.auditLog.findMany as any).mockResolvedValue(rows);
            (prisma.auditLog.count as any).mockResolvedValue(10);

            const result = await service.queryAuditEvents({
                tenantId: 'tenant-1' as any,
                pagination: { cursor: 'audit-2', limit: 2 },
            });

            // 3 rows returned means hasMore = true (take + 1 pattern)
            expect(result.hasMore).toBe(true);
            expect(result.items).toHaveLength(2);
            expect(result.nextCursor).toBe('audit-4');
        });

        it('filters by actorId', async () => {
            (prisma.auditLog.findMany as any).mockResolvedValue([]);
            (prisma.auditLog.count as any).mockResolvedValue(0);

            await service.queryAuditEvents({
                tenantId: 'tenant-1' as any,
                actorId: 'user-1' as any,
                pagination: {},
            });

            const callArgs = (prisma.auditLog.findMany as any).mock.calls[0][0];
            expect(callArgs.where.actorId).toBe('user-1');
        });

        it('filters by action', async () => {
            (prisma.auditLog.findMany as any).mockResolvedValue([]);
            (prisma.auditLog.count as any).mockResolvedValue(0);

            await service.queryAuditEvents({
                tenantId: 'tenant-1' as any,
                action: 'module.create',
                pagination: {},
            });

            const callArgs = (prisma.auditLog.findMany as any).mock.calls[0][0];
            expect(callArgs.where.action).toBe('module.create');
        });

        it('filters by date range', async () => {
            (prisma.auditLog.findMany as any).mockResolvedValue([]);
            (prisma.auditLog.count as any).mockResolvedValue(0);

            await service.queryAuditEvents({
                tenantId: 'tenant-1' as any,
                startDate: '2025-01-01',
                endDate: '2025-01-31',
                pagination: {},
            });

            const callArgs = (prisma.auditLog.findMany as any).mock.calls[0][0];
            expect(callArgs.where.createdAt.gte).toEqual(new Date('2025-01-01'));
            expect(callArgs.where.createdAt.lte).toEqual(new Date('2025-01-31'));
        });

        it('caps limit at 200', async () => {
            (prisma.auditLog.findMany as any).mockResolvedValue([]);
            (prisma.auditLog.count as any).mockResolvedValue(0);

            await service.queryAuditEvents({
                tenantId: 'tenant-1' as any,
                pagination: { limit: 500 },
            });

            const callArgs = (prisma.auditLog.findMany as any).mock.calls[0][0];
            expect(callArgs.take).toBe(201); // 200 + 1 for hasMore check
        });

        it('maps optional fields to undefined when null', async () => {
            const row = makeAuditRow({
                actorEmail: null,
                ipAddress: null,
                userAgent: null,
                resourceId: null,
                metadata: null,
            });
            (prisma.auditLog.findMany as any).mockResolvedValue([row]);
            (prisma.auditLog.count as any).mockResolvedValue(1);

            const result = await service.queryAuditEvents({
                tenantId: 'tenant-1' as any,
                pagination: {},
            });

            expect(result.items[0].actorEmail).toBeUndefined();
            expect(result.items[0].ipAddress).toBeUndefined();
            expect(result.items[0].userAgent).toBeUndefined();
        });
    });

    // =========================================================================
    // getAuditSummary
    // =========================================================================
    describe('getAuditSummary', () => {
        it('returns summary with top actions and resource types', async () => {
            (prisma.auditLog.count as any).mockResolvedValue(100);
            (prisma.auditLog.groupBy as any)
                .mockResolvedValueOnce([{ actorId: 'u1' }, { actorId: 'u2' }])
                .mockResolvedValueOnce([{ action: 'module.create', _count: { action: 50 } }])
                .mockResolvedValueOnce([{ resourceType: 'module', _count: { resourceType: 80 } }]);
            (prisma.auditLog.findMany as any).mockResolvedValue([makeAuditRow()]);

            const summary = await service.getAuditSummary({
                tenantId: 'tenant-1' as any,
                days: 30,
            });

            expect(summary.totalEvents).toBe(100);
            expect(summary.uniqueActors).toBe(2);
            expect(summary.topActions).toHaveLength(1);
            expect(summary.topActions[0].action).toBe('module.create');
            expect(summary.topResourceTypes).toHaveLength(1);
            expect(summary.recentEvents).toHaveLength(1);
        });

        it('defaults to 30 days', async () => {
            (prisma.auditLog.count as any).mockResolvedValue(0);
            (prisma.auditLog.groupBy as any).mockResolvedValue([]);
            (prisma.auditLog.findMany as any).mockResolvedValue([]);

            await service.getAuditSummary({ tenantId: 'tenant-1' as any });

            const countCall = (prisma.auditLog.count as any).mock.calls[0][0];
            const startDate = countCall.where.createdAt.gte;
            const daysDiff = (Date.now() - startDate.getTime()) / (24 * 60 * 60 * 1000);
            expect(daysDiff).toBeCloseTo(30, 0);
        });
    });

    // =========================================================================
    // exportAuditLog
    // =========================================================================
    describe('exportAuditLog', () => {
        it('returns a download URL', async () => {
            const result = await service.exportAuditLog({
                tenantId: 'tenant-1' as any,
                startDate: '2025-01-01',
                endDate: '2025-01-31',
            });

            expect(result.downloadUrl).toContain('tenant-1');
            expect(result.downloadUrl).toContain('.csv');
        });
    });

    // =========================================================================
    // checkHealth
    // =========================================================================
    describe('checkHealth', () => {
        it('returns true when DB is reachable', async () => {
            (prisma.$queryRaw as any).mockResolvedValue([{ 1: 1 }]);

            const healthy = await service.checkHealth();
            expect(healthy).toBe(true);
        });
    });
});
