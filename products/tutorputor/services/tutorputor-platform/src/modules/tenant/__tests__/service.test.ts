/**
 * Tenant Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for config, quotas, domain packs
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createTenantService } from '../service';

function makeMockPrisma() {
    return {
        tenant: {
            findUnique: vi.fn(),
        },
        tenantSettings: {
            findUnique: vi.fn(),
            upsert: vi.fn(),
        },
        $queryRaw: vi.fn().mockResolvedValue([{ 1: 1 }]),
    } as any;
}

describe('createTenantService', () => {
    let prisma: ReturnType<typeof makeMockPrisma>;
    let service: ReturnType<typeof createTenantService>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        service = createTenantService(prisma);
    });

    // =========================================================================
    // getTenantConfig
    // =========================================================================
    describe('getTenantConfig', () => {
        it('returns config with defaults when no settings exist', async () => {
            // tenant.findUnique with include:{settings:true} returns nested settings
            (prisma.tenant.findUnique as any).mockResolvedValue({
                id: 't1',
                name: 'Acme School',
                subdomain: 'acme',
                settings: null,
            });

            const config = await service.getTenantConfig('t1' as any);

            expect(config.tenantId).toBe('t1');
            expect(config.name).toBe('Acme School');
            expect(config.subdomain).toBe('acme');
            expect(config.settings.allowPublicRegistration).toBe(false);
            expect(config.settings.requireEmailVerification).toBe(true);
            expect(config.settings.defaultUserRole).toBe('student');
            expect(config.settings.maxUsersPerClassroom).toBe(50);
            expect(config.settings.simulationQuotas.maxConcurrentSessions).toBe(10);
            expect(config.settings.simulationQuotas.monthlyRuns).toBe(1000);
        });

        it('returns config from DB settings when they exist', async () => {
            (prisma.tenant.findUnique as any).mockResolvedValue({
                id: 't1',
                name: 'Acme School',
                subdomain: 'acme',
                settings: {
                    allowPublicRegistration: true,
                    requireEmailVerification: false,
                    defaultUserRole: 'teacher',
                    maxUsersPerClassroom: 100,
                    enabledFeatures: JSON.stringify(['ai', 'forums']),
                    enabledDomainPacks: JSON.stringify(['physics-101']),
                    simulationQuotas: JSON.stringify({ maxConcurrentSessions: 20, monthlyRuns: 5000 }),
                },
            });

            const config = await service.getTenantConfig('t1' as any);

            expect(config.settings.allowPublicRegistration).toBe(true);
            expect(config.settings.requireEmailVerification).toBe(false);
            expect(config.settings.defaultUserRole).toBe('teacher');
            expect(config.settings.maxUsersPerClassroom).toBe(100);
            expect(config.settings.enabledFeatures).toEqual(['ai', 'forums']);
            expect(config.settings.enabledDomainPacks).toEqual(['physics-101']);
            expect(config.settings.simulationQuotas.maxConcurrentSessions).toBe(20);
        });

        it('throws for non-existent tenant', async () => {
            (prisma.tenant.findUnique as any).mockResolvedValue(null);

            await expect(service.getTenantConfig('unknown' as any)).rejects.toThrow('Tenant not found');
        });

        it('handles malformed JSON gracefully', async () => {
            (prisma.tenant.findUnique as any).mockResolvedValue({
                id: 't1',
                name: 'Acme',
                subdomain: 'acme',
                settings: {
                    allowPublicRegistration: false,
                    requireEmailVerification: true,
                    defaultUserRole: 'student',
                    maxUsersPerClassroom: 50,
                    enabledFeatures: '{bad-json}',
                    enabledDomainPacks: null,
                    simulationQuotas: null,
                },
            });

            const config = await service.getTenantConfig('t1' as any);

            // parseJSON falls back to defaults on bad/null JSON
            expect(config.settings.enabledFeatures).toEqual([]);
            expect(config.settings.enabledDomainPacks).toEqual([]);
            expect(config.settings.simulationQuotas.maxConcurrentSessions).toBe(10);
        });
    });

    // =========================================================================
    // updateTenantConfig
    // =========================================================================
    describe('updateTenantConfig', () => {
        it('upserts settings with partial updates', async () => {
            (prisma.tenantSettings.findUnique as any).mockResolvedValue(null);
            (prisma.tenantSettings.upsert as any).mockResolvedValue({});
            // getTenantConfig is called internally after upsert
            (prisma.tenant.findUnique as any).mockResolvedValue({
                id: 't1',
                name: 'Acme',
                subdomain: 'acme',
                settings: {
                    allowPublicRegistration: true,
                    requireEmailVerification: true,
                    defaultUserRole: 'student',
                    maxUsersPerClassroom: 50,
                    enabledFeatures: '[]',
                    enabledDomainPacks: '[]',
                    simulationQuotas: JSON.stringify({ maxConcurrentSessions: 10, monthlyRuns: 1000 }),
                },
            });

            const config = await service.updateTenantConfig('t1' as any, {
                allowPublicRegistration: true,
            });

            expect(prisma.tenantSettings.upsert).toHaveBeenCalled();
            const callArgs = (prisma.tenantSettings.upsert as any).mock.calls[0][0];
            expect(callArgs.update.allowPublicRegistration).toBe(true);
        });

        it('merges simulation quotas with existing', async () => {
            (prisma.tenantSettings.findUnique as any).mockResolvedValue({
                tenantId: 't1',
                simulationQuotas: JSON.stringify({ maxConcurrentSessions: 10, monthlyRuns: 1000 }),
            });
            (prisma.tenantSettings.upsert as any).mockResolvedValue({});
            (prisma.tenant.findUnique as any).mockResolvedValue({
                id: 't1',
                name: 'Acme',
                subdomain: 'acme',
                settings: {
                    allowPublicRegistration: false,
                    requireEmailVerification: true,
                    defaultUserRole: 'student',
                    maxUsersPerClassroom: 50,
                    enabledFeatures: '[]',
                    enabledDomainPacks: '[]',
                    simulationQuotas: JSON.stringify({ maxConcurrentSessions: 10, monthlyRuns: 5000 }),
                },
            });

            await service.updateTenantConfig('t1' as any, {
                simulationQuotas: { maxConcurrentSessions: 10, monthlyRuns: 5000 },
            });

            const callArgs = (prisma.tenantSettings.upsert as any).mock.calls[0][0];
            const quotas = JSON.parse(callArgs.update.simulationQuotas);
            expect(quotas.monthlyRuns).toBe(5000);
            expect(quotas.maxConcurrentSessions).toBe(10);
        });
    });

    // =========================================================================
    // Domain Packs (in-memory via DOMAIN_PACK_STORE)
    // =========================================================================
    describe('domain packs', () => {
        const testPack = {
            id: 'pack-1',
            tenantId: 't1',
            status: 'active' as const,
            visibility: 'public' as const,
            metadata: {
                name: 'Physics 101',
                description: 'Introductory physics',
                version: '1.0.0',
                domain: 'PHYSICS' as const,
                tags: ['mechanics', 'kinematics'],
            },
            simulations: [
                { manifestId: 'sim-1', title: 'Projectile motion', order: 0 },
            ],
            createdAt: new Date(),
            updatedAt: new Date(),
        };

        it('creates and retrieves a domain pack', async () => {
            const created = await service.createDomainPack(testPack as any);
            expect(created.id).toBe('pack-1');

            const fetched = await service.getDomainPack('pack-1');
            expect(fetched).not.toBeNull();
            expect(fetched!.metadata.name).toBe('Physics 101');
        });

        it('lists domain packs for tenant', async () => {
            await service.createDomainPack(testPack as any);

            const { packs, total } = await service.listDomainPacks('t1');
            expect(total).toBeGreaterThanOrEqual(1);
            expect(packs.some((p: any) => p.id === 'pack-1')).toBe(true);
        });

        it('filters domain packs by search', async () => {
            await service.createDomainPack(testPack as any);

            const { packs } = await service.listDomainPacks('t1', { search: 'physics' });
            expect(packs).toHaveLength(1);

            const { packs: empty } = await service.listDomainPacks('t1', { search: 'chemistry' });
            expect(empty).toHaveLength(0);
        });

        it('filters domain packs by domain', async () => {
            await service.createDomainPack(testPack as any);

            const { packs } = await service.listDomainPacks('t1', { domain: 'PHYSICS' as any });
            expect(packs).toHaveLength(1);

            const { packs: empty } = await service.listDomainPacks('t1', { domain: 'CHEMISTRY' as any });
            expect(empty).toHaveLength(0);
        });

        it('updates a domain pack', async () => {
            await service.createDomainPack(testPack as any);

            const updated = await service.updateDomainPack('pack-1', {
                status: 'archived' as any,
            });

            expect(updated).not.toBeNull();
            expect(updated!.status).toBe('archived');
        });

        it('returns null when updating non-existent pack', async () => {
            const result = await service.updateDomainPack('nonexistent', { status: 'archived' as any });
            expect(result).toBeNull();
        });

        it('deletes a domain pack', async () => {
            await service.createDomainPack(testPack as any);

            const deleted = await service.deleteDomainPack('pack-1');
            expect(deleted).toBe(true);

            const fetched = await service.getDomainPack('pack-1');
            expect(fetched).toBeNull();
        });

        it('returns false when deleting non-existent pack', async () => {
            const deleted = await service.deleteDomainPack('nonexistent');
            expect(deleted).toBe(false);
        });
    });

    // =========================================================================
    // Health
    // =========================================================================
    describe('checkHealth', () => {
        it('returns true when DB is reachable', async () => {
            const healthy = await service.checkHealth();
            expect(healthy).toBe(true);
        });

        it('throws when DB is unreachable', async () => {
            (prisma.$queryRaw as any).mockRejectedValue(new Error('Connection refused'));

            await expect(service.checkHealth()).rejects.toThrow('Connection refused');
        });
    });
});
