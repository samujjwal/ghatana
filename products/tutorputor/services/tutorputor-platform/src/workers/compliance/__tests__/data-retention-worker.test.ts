/**
 * Data Retention Worker Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test scheduled cleanup, policy enforcement, user deletion
 * @doc.layer backend-worker
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { DataRetentionWorker } from '../data-retention-worker';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        tenant: { findMany: vi.fn().mockResolvedValue([]) },
        dataDeletionRequest: {
            findMany: vi.fn().mockResolvedValue([]),
            update: vi.fn().mockResolvedValue({}),
        },
        sessionEvent: { deleteMany: vi.fn().mockResolvedValue({ count: 0 }) },
        auditLog: { updateMany: vi.fn().mockResolvedValue({ count: 0 }) },
        tempExport: { deleteMany: vi.fn().mockResolvedValue({ count: 0 }) },
        user: {
            update: vi.fn().mockResolvedValue({}),
            deleteMany: vi.fn().mockResolvedValue({ count: 0 }),
        },
        enrollment: { deleteMany: vi.fn().mockResolvedValue({ count: 0 }) },
        assessmentAttempt: { deleteMany: vi.fn().mockResolvedValue({ count: 0 }) },
        studyGroupMember: { deleteMany: vi.fn().mockResolvedValue({ count: 0 }) },
    } as unknown as PrismaClient;
}

describe('DataRetentionWorker', () => {
    let worker: DataRetentionWorker;
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        worker = new DataRetentionWorker(prisma);
    });

    // =========================================================================
    // processScheduledDeletions
    // =========================================================================
    describe('processScheduledDeletions', () => {
        it('returns zero counts when no pending deletions', async () => {
            const result = await worker.processScheduledDeletions();
            expect(result.deletedCount).toBe(0);
            expect(result.errors).toHaveLength(0);
        });

        it('processes expired deletion requests', async () => {
            (prisma.dataDeletionRequest.findMany as any).mockResolvedValue([
                { id: 'dr-1', tenantId: 't1', userId: 'u1', scheduledDeletionAt: new Date(Date.now() - 1000) },
            ]);

            const result = await worker.processScheduledDeletions();

            expect(result.deletedCount).toBe(1);
            expect(prisma.user.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    where: { id: 'u1' },
                    data: expect.objectContaining({ name: 'Deleted User' }),
                }),
            );
            expect(prisma.enrollment.deleteMany).toHaveBeenCalledWith(
                expect.objectContaining({ where: { tenantId: 't1', userId: 'u1' } }),
            );
            expect(prisma.dataDeletionRequest.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    where: { id: 'dr-1' },
                    data: expect.objectContaining({ status: 'completed' }),
                }),
            );
        });

        it('marks failed deletions', async () => {
            (prisma.dataDeletionRequest.findMany as any).mockResolvedValue([
                { id: 'dr-1', tenantId: 't1', userId: 'u1', scheduledDeletionAt: new Date(Date.now() - 1000) },
            ]);
            (prisma.user.update as any).mockRejectedValue(new Error('DB error'));

            const result = await worker.processScheduledDeletions();

            expect(result.deletedCount).toBe(0);
            expect(result.errors).toHaveLength(1);
            expect(prisma.dataDeletionRequest.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({ status: 'failed' }),
                }),
            );
        });
    });

    // =========================================================================
    // enforcePolicy
    // =========================================================================
    describe('enforcePolicy', () => {
        it('deletes expired session data', async () => {
            (prisma.sessionEvent.deleteMany as any).mockResolvedValue({ count: 15 });

            const result = await worker.enforcePolicy({
                tenantId: 't1',
                resourceType: 'session_data',
                retentionDays: 90,
                archiveBeforeDelete: false,
                enabled: true,
            });

            expect(result.deletedCount).toBe(15);
            expect(result.archivedCount).toBe(0);
        });

        it('archives before deleting audit logs', async () => {
            (prisma.auditLog.updateMany as any).mockResolvedValue({ count: 10 });

            const result = await worker.enforcePolicy({
                tenantId: 't1',
                resourceType: 'audit_log',
                retentionDays: 730,
                archiveBeforeDelete: true,
                enabled: true,
            });

            expect(result.archivedCount).toBe(10);
            expect(result.deletedCount).toBe(0); // Audit logs only archived, never deleted
        });

        it('returns error for unknown resource type', async () => {
            const result = await worker.enforcePolicy({
                tenantId: 't1',
                resourceType: 'unknown_type',
                retentionDays: 30,
                archiveBeforeDelete: false,
                enabled: true,
            });

            expect(result.errors).toHaveLength(1);
            expect(result.errors[0]).toContain('No handler');
        });

        it('deletes temp exports', async () => {
            (prisma.tempExport.deleteMany as any).mockResolvedValue({ count: 5 });

            const result = await worker.enforcePolicy({
                tenantId: 't1',
                resourceType: 'temp_export',
                retentionDays: 7,
                archiveBeforeDelete: false,
                enabled: true,
            });

            expect(result.deletedCount).toBe(5);
        });
    });

    // =========================================================================
    // runAll
    // =========================================================================
    describe('runAll', () => {
        it('returns summary with timestamps', async () => {
            const summary = await worker.runAll();

            expect(summary.runAt).toBeInstanceOf(Date);
            expect(summary.totalDeleted).toBeGreaterThanOrEqual(0);
            expect(summary.totalArchived).toBeGreaterThanOrEqual(0);
        });

        it('processes all tenants', async () => {
            (prisma.tenant.findMany as any).mockResolvedValue([
                { id: 't1' },
                { id: 't2' },
            ]);

            const summary = await worker.runAll();

            // Should have at least the scheduled deletion result
            expect(summary.results.length).toBeGreaterThanOrEqual(1);
        });

        it('catches errors from individual policies without stopping', async () => {
            (prisma.tenant.findMany as any).mockResolvedValue([{ id: 't1' }]);
            (prisma.sessionEvent.deleteMany as any).mockRejectedValue(new Error('Connection lost'));

            const summary = await worker.runAll();

            // Should still complete without throwing
            expect(summary).toBeDefined();
        });
    });

    // =========================================================================
    // User data deletion
    // =========================================================================
    describe('user data deletion', () => {
        it('anonymizes user email and name', async () => {
            (prisma.dataDeletionRequest.findMany as any).mockResolvedValue([
                { id: 'dr-1', tenantId: 't1', userId: 'u1', scheduledDeletionAt: new Date(Date.now() - 1000) },
            ]);

            await worker.processScheduledDeletions();

            expect(prisma.user.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({
                        email: expect.stringContaining('anonymized.local'),
                        name: 'Deleted User',
                        deletedAt: expect.any(Date),
                    }),
                }),
            );
        });

        it('deletes enrollments and assessment attempts', async () => {
            (prisma.dataDeletionRequest.findMany as any).mockResolvedValue([
                { id: 'dr-1', tenantId: 't1', userId: 'u1', scheduledDeletionAt: new Date(Date.now() - 1000) },
            ]);

            await worker.processScheduledDeletions();

            expect(prisma.enrollment.deleteMany).toHaveBeenCalled();
            expect(prisma.assessmentAttempt.deleteMany).toHaveBeenCalled();
            expect(prisma.studyGroupMember.deleteMany).toHaveBeenCalled();
        });
    });
});
