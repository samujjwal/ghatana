import type {
    TenantId,
} from "@ghatana/tutorputor-contracts/v1/types";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";
import { z } from "zod";

// =============================================================================
// Domain Pack Types (In-Memory / Configuration)
// =============================================================================

export const SIMULATION_DOMAINS = [
    "CS_DISCRETE",
    "PHYSICS",
    "CHEMISTRY",
    "BIOLOGY",
    "MEDICINE",
    "ECONOMICS",
    "ENGINEERING",
    "MATHEMATICS",
] as const;

export type SimulationDomain = (typeof SIMULATION_DOMAINS)[number];
export type PackStatus = "draft" | "active" | "deprecated" | "archived";
export type PackVisibility = "private" | "institution" | "public";

export interface DomainPack {
    id: string;
    tenantId: string;
    metadata: {
        name: string;
        description?: string;
        version: string;
        domain: SimulationDomain;
        tags: string[];
        thumbnailUrl?: string;
        author?: string;
        license?: string;
    };
    status: PackStatus;
    visibility: PackVisibility;
    simulations: {
        manifestId: string;
        title: string;
        order: number;
    }[];
    createdAt: Date;
    updatedAt: Date;
}

export interface DomainPackFilter {
    domain?: SimulationDomain;
    status?: PackStatus;
    visibility?: PackVisibility;
    search?: string;
    page?: number;
    limit?: number;
}

// =============================================================================
// Tenant Service Types
// =============================================================================

export interface TenantSettings {
    allowPublicRegistration: boolean;
    requireEmailVerification: boolean;
    defaultUserRole: string;
    maxUsersPerClassroom: number;
    enabledFeatures: string[];
    enabledDomainPacks: string[];
    simulationQuotas: {
        maxConcurrentSessions: number;
        monthlyRuns: number;
    };
}

export interface TenantConfig {
    tenantId: TenantId;
    name: string;
    subdomain: string;
    settings: TenantSettings;
}

// =============================================================================
// Constants
// =============================================================================

const DEFAULT_SETTINGS: TenantSettings = {
    allowPublicRegistration: false,
    requireEmailVerification: true,
    defaultUserRole: "student",
    maxUsersPerClassroom: 50,
    enabledFeatures: [],
    enabledDomainPacks: [],
    simulationQuotas: {
        maxConcurrentSessions: 10,
        monthlyRuns: 1000,
    },
};

// =============================================================================
// In-Memory Store for Domain Packs
// =============================================================================

const DOMAIN_PACK_STORE = new Map<string, DomainPack>();

// =============================================================================
// Service Implementation
// =============================================================================

export function createTenantService(prisma: TutorPrismaClient) {

    // Helper to parse JSON fields safely
    function parseJSON<T>(json: string | null, fallback: T): T {
        if (!json) return fallback;
        try {
            return JSON.parse(json);
        } catch {
            return fallback;
        }
    }

    return {
        async getTenantConfig(tenantId: TenantId): Promise<TenantConfig> {
            const tenant = await prisma.tenant.findUnique({
                where: { id: tenantId },
                include: { settings: true },
            });

            if (!tenant) {
                throw new Error(`Tenant not found: ${tenantId}`);
            }

            // Map DB TenantSettings to Service TenantSettings
            const dbSettings = tenant.settings;
            const settings: TenantSettings = dbSettings ? {
                allowPublicRegistration: dbSettings.allowPublicRegistration,
                requireEmailVerification: dbSettings.requireEmailVerification,
                defaultUserRole: dbSettings.defaultUserRole,
                maxUsersPerClassroom: dbSettings.maxUsersPerClassroom,
                enabledFeatures: parseJSON(dbSettings.enabledFeatures, []),
                enabledDomainPacks: parseJSON(dbSettings.enabledDomainPacks, []),
                simulationQuotas: parseJSON(dbSettings.simulationQuotas, DEFAULT_SETTINGS.simulationQuotas),
            } : DEFAULT_SETTINGS;

            return {
                tenantId: tenant.id as TenantId,
                name: tenant.name,
                subdomain: tenant.subdomain,
                settings,
            };
        },

        async updateTenantConfig(tenantId: TenantId, updates: Partial<TenantSettings>): Promise<TenantConfig> {
            // Upsert settings
            const existing = await prisma.tenantSettings.findUnique({ where: { tenantId } });
            const currentQuotas = existing ? parseJSON(existing.simulationQuotas, DEFAULT_SETTINGS.simulationQuotas) : DEFAULT_SETTINGS.simulationQuotas;

            const data: any = {};
            if (updates.allowPublicRegistration !== undefined) data.allowPublicRegistration = updates.allowPublicRegistration;
            if (updates.requireEmailVerification !== undefined) data.requireEmailVerification = updates.requireEmailVerification;
            if (updates.defaultUserRole !== undefined) data.defaultUserRole = updates.defaultUserRole;
            if (updates.maxUsersPerClassroom !== undefined) data.maxUsersPerClassroom = updates.maxUsersPerClassroom;
            if (updates.enabledFeatures !== undefined) data.enabledFeatures = JSON.stringify(updates.enabledFeatures);
            if (updates.enabledDomainPacks !== undefined) data.enabledDomainPacks = JSON.stringify(updates.enabledDomainPacks);
            if (updates.simulationQuotas !== undefined) data.simulationQuotas = JSON.stringify({ ...currentQuotas, ...updates.simulationQuotas });

            await prisma.tenantSettings.upsert({
                where: { tenantId },
                create: {
                    tenantId,
                    ...DEFAULT_SETTINGS, // base defaults
                    ...data // overrides
                },
                update: data,
            });

            return this.getTenantConfig(tenantId);
        },

        // --- Domain Packs (In-Memory) ---

        async listDomainPacks(tenantId: string, filter?: DomainPackFilter): Promise<{ packs: DomainPack[], total: number }> {
            let packs = Array.from(DOMAIN_PACK_STORE.values());

            // Filter by tenant (include public packs and tenant's own)
            packs = packs.filter(
                (pack) => pack.tenantId === tenantId || pack.visibility === "public"
            );

            if (filter?.domain) {
                packs = packs.filter((pack) => pack.metadata.domain === filter.domain);
            }

            if (filter?.status) {
                packs = packs.filter((pack) => pack.status === filter.status);
            }

            if (filter?.visibility) {
                packs = packs.filter((pack) => pack.visibility === filter.visibility);
            }

            if (filter?.search) {
                const searchLower = filter.search.toLowerCase();
                packs = packs.filter(
                    (pack) =>
                        pack.metadata.name.toLowerCase().includes(searchLower) ||
                        (pack.metadata.description?.toLowerCase().includes(searchLower) ?? false) ||
                        pack.metadata.tags?.some((tag) => tag.toLowerCase().includes(searchLower))
                );
            }

            // Sort by createdAt desc
            packs.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

            const total = packs.length;

            // Paginate
            const page = filter?.page || 1;
            const limit = filter?.limit || 20;
            const start = (page - 1) * limit;

            return {
                packs: packs.slice(start, start + limit),
                total
            };
        },

        async getDomainPack(id: string): Promise<DomainPack | null> {
            return DOMAIN_PACK_STORE.get(id) || null;
        },

        async createDomainPack(pack: DomainPack): Promise<DomainPack> {
            // In a real DB we would use prisma.create
            DOMAIN_PACK_STORE.set(pack.id, pack);
            return pack;
        },

        async updateDomainPack(id: string, updates: Partial<DomainPack>): Promise<DomainPack | null> {
            const existing = DOMAIN_PACK_STORE.get(id);
            if (!existing) return null;

            const updated = { ...existing, ...updates, updatedAt: new Date() };
            DOMAIN_PACK_STORE.set(id, updated);
            return updated;
        },

        async deleteDomainPack(id: string): Promise<boolean> {
            return DOMAIN_PACK_STORE.delete(id);
        },

        async checkHealth() {
            await prisma.$queryRaw`SELECT 1`;
            return true;
        }
    };
}
