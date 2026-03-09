/**
 * Compliance Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for GDPR export, deletion, verification, reporting
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ComplianceServiceImpl } from '../service';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        user: { findUnique: vi.fn().mockResolvedValue({ id: 'user-1', email: 'u@test.com' }) },
        deletionVerification: {
            create: vi.fn().mockResolvedValue({ id: 'dv-1', token: 'tok', expiresAt: new Date() }),
            findUnique: vi.fn(),
            delete: vi.fn(),
        },
        dataDeletionRequest: {
            create: vi.fn().mockResolvedValue({ id: 'dr-1', status: 'scheduled' }),
        },
    } as unknown as PrismaClient;
}

describe('ComplianceServiceImpl', () => {
    let service: ComplianceServiceImpl;
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        service = new ComplianceServiceImpl(prisma);
    });

    // =========================================================================
    // requestUserExport
    // =========================================================================
    describe('requestUserExport', () => {
        it('creates an export request with pending status', async () => {
            const result = await service.requestUserExport({
                userId: 'user-1' as any,
                tenantId: 'tenant-1' as any,
                requestedBy: 'admin-1' as any,
            });

            expect(result.id).toMatch(/^req_/);
            expect(result.status).toBe('pending');
            expect(result.userId).toBe('user-1');
            expect(result.tenantId).toBe('tenant-1');
            expect(result.estimatedCompletionAt).toBeDefined();
        });

        it('sets estimated completion to 24 hours from now', async () => {
            const before = Date.now();
            const result = await service.requestUserExport({
                userId: 'user-1' as any,
                tenantId: 'tenant-1' as any,
                requestedBy: 'admin-1' as any,
            });

            const estimated = new Date(result.estimatedCompletionAt).getTime();
            const diff = estimated - before;
            // Should be approximately 24 hours (86400000ms), allow 5s tolerance
            expect(diff).toBeGreaterThan(86400000 - 5000);
            expect(diff).toBeLessThan(86400000 + 5000);
        });
    });

    // =========================================================================
    // getExportStatus
    // =========================================================================
    describe('getExportStatus', () => {
        it('returns not_found for unknown request IDs', async () => {
            const result = await service.getExportStatus({
                requestId: 'nonexistent',
                tenantId: 'tenant-1' as any,
            });

            expect(result.status).toBe('not_found');
            expect(result.error).toContain('not found');
        });
    });

    // =========================================================================
    // downloadExport
    // =========================================================================
    describe('downloadExport', () => {
        it('returns a download URL and expiry', async () => {
            const result = await service.downloadExport({
                requestId: 'req-1',
                tenantId: 'tenant-1' as any,
            });

            expect(result.downloadUrl).toBeDefined();
            expect(result.expiresAt).toBeDefined();
        });
    });

    // =========================================================================
    // requestUserDeletion
    // =========================================================================
    describe('requestUserDeletion', () => {
        it('creates a deletion request with pending status', async () => {
            const result = await service.requestUserDeletion({
                userId: 'user-1' as any,
                tenantId: 'tenant-1' as any,
                requestedBy: 'admin-1' as any,
                reason: 'User requested account deletion',
            });

            expect(result.id).toMatch(/^del_/);
            expect(result.status).toBe('pending');
            expect(result.userId).toBe('user-1');
        });
    });

    // =========================================================================
    // cancelDeletionRequest
    // =========================================================================
    describe('cancelDeletionRequest', () => {
        it('returns cancelled status', async () => {
            const result = await service.cancelDeletionRequest({
                requestId: 'del-1',
                tenantId: 'tenant-1' as any,
            });

            expect(result.status).toBe('cancelled');
        });
    });

    // =========================================================================
    // createDeletionVerification
    // =========================================================================
    describe('createDeletionVerification', () => {
        it('creates a verification token that expires in 24 hours', async () => {
            const before = Date.now();
            const result = await service.createDeletionVerification({
                userId: 'user-1' as any,
                userEmail: 'user@test.com',
            });

            expect(result.message).toContain('confirmation token');
            expect(result.expiresAt.getTime()).toBeGreaterThan(before);
            expect(prisma.deletionVerification.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    data: expect.objectContaining({
                        userId: 'user-1',
                        token: expect.any(String),
                        expiresAt: expect.any(Date),
                    }),
                }),
            );
        });
    });

    // =========================================================================
    // verifyAndProcessDeletion
    // =========================================================================
    describe('verifyAndProcessDeletion', () => {
        it('schedules deletion when token is valid', async () => {
            (prisma.deletionVerification.findUnique as any).mockResolvedValue({
                id: 'dv-1',
                userId: 'user-1',
                token: 'valid-token',
                expiresAt: new Date(Date.now() + 3600000),
            });

            const result = await service.verifyAndProcessDeletion({
                userId: 'user-1' as any,
                tenantId: 'tenant-1' as any,
                token: 'valid-token',
            });

            expect(result.success).toBe(true);
            expect(result.message).toContain('30 days');
            expect(prisma.dataDeletionRequest.create).toHaveBeenCalled();
            expect(prisma.deletionVerification.delete).toHaveBeenCalledWith(
                expect.objectContaining({ where: { id: 'dv-1' } }),
            );
        });

        it('throws on invalid token', async () => {
            (prisma.deletionVerification.findUnique as any).mockResolvedValue(null);

            await expect(
                service.verifyAndProcessDeletion({
                    userId: 'user-1' as any,
                    tenantId: 'tenant-1' as any,
                    token: 'bad-token',
                }),
            ).rejects.toThrow('Invalid or expired');
        });

        it('throws on expired token', async () => {
            (prisma.deletionVerification.findUnique as any).mockResolvedValue({
                id: 'dv-1',
                userId: 'user-1',
                token: 'expired-token',
                expiresAt: new Date(Date.now() - 3600000), // 1 hour ago
            });

            await expect(
                service.verifyAndProcessDeletion({
                    userId: 'user-1' as any,
                    tenantId: 'tenant-1' as any,
                    token: 'expired-token',
                }),
            ).rejects.toThrow('Invalid or expired');
        });

        it('throws when userId does not match token owner', async () => {
            (prisma.deletionVerification.findUnique as any).mockResolvedValue({
                id: 'dv-1',
                userId: 'user-2', // Different user
                token: 'valid-token',
                expiresAt: new Date(Date.now() + 3600000),
            });

            await expect(
                service.verifyAndProcessDeletion({
                    userId: 'user-1' as any,
                    tenantId: 'tenant-1' as any,
                    token: 'valid-token',
                }),
            ).rejects.toThrow('Invalid or expired');
        });
    });

    // =========================================================================
    // getComplianceReport
    // =========================================================================
    describe('getComplianceReport', () => {
        it('returns a compliance report structure', async () => {
            const report = await service.getComplianceReport('tenant-1' as any);

            expect(report.reportType).toBe('gdpr');
            expect(report.tenantId).toBe('tenant-1');
            expect(report.generatedAt).toBeInstanceOf(Date);
            expect(report.data).toHaveProperty('totalUsers');
            expect(report.data).toHaveProperty('activeDataRequests');
            expect(report.data).toHaveProperty('dataRetentionPolicies');
        });
    });

    // =========================================================================
    // checkHealth
    // =========================================================================
    describe('checkHealth', () => {
        it('returns true', async () => {
            expect(await service.checkHealth()).toBe(true);
        });
    });
});
