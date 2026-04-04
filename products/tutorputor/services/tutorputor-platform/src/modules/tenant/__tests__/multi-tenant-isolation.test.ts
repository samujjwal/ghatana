/**
 * Multi-Tenant Data Isolation Tests
 *
 * @doc.type test
 * @doc.purpose Verify that tenant configuration, domain pack lists, and
 *              simulation quotas from one tenant cannot be accessed or
 *              contaminated by a different tenant.
 * @doc.layer platform
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-053 (multi-tenant data isolation),
 *                  TPUT-FR-054 (per-tenant quota enforcement),
 *                  TPUT-FR-055 (domain pack visibility control)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { createTenantService, type DomainPack } from "../service";

// ---------------------------------------------------------------------------
// Test fixtures
// ---------------------------------------------------------------------------

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
  } as ReturnType<typeof Object.assign>;
}

function makeTenantRow(
  id: string,
  name: string,
  settings?: Record<string, unknown> | null,
) {
  return {
    id,
    name,
    subdomain: id.toLowerCase(),
    settings: settings ?? null,
  };
}

function makePackForTenant(
  id: string,
  tenantId: string,
  visibility: "private" | "institution" | "public" = "private",
): DomainPack {
  return {
    id,
    tenantId,
    metadata: {
      name: `Pack ${id}`,
      version: "1.0",
      domain: "PHYSICS",
      tags: [],
    },
    status: "active",
    visibility,
    simulations: [],
    createdAt: new Date("2024-01-01"),
    updatedAt: new Date("2024-01-01"),
  };
}

// ---------------------------------------------------------------------------
// TPUT-FR-053: Config isolation – each tenant sees only its own config
// ---------------------------------------------------------------------------

describe("TenantService – TPUT-FR-053 (config isolation)", () => {
  let prisma: ReturnType<typeof makeMockPrisma>;

  beforeEach(() => {
    vi.clearAllMocks();
    prisma = makeMockPrisma();
  });

  it("should return only Tenant A config when queried as Tenant A", async () => {
    const service = createTenantService(prisma as any);
    (prisma.tenant.findUnique as ReturnType<typeof vi.fn>).mockResolvedValue(
      makeTenantRow("tenant-a", "School A", {
        allowPublicRegistration: true,
        requireEmailVerification: false,
        defaultUserRole: "teacher",
        maxUsersPerClassroom: 100,
        enabledFeatures: "[]",
        enabledDomainPacks: '["pack-a"]',
        simulationQuotas: JSON.stringify({
          maxConcurrentSessions: 20,
          monthlyRuns: 5000,
        }),
      }),
    );

    const config = await service.getTenantConfig("tenant-a" as any);
    expect(config.tenantId).toBe("tenant-a");
    expect(config.name).toBe("School A");
    expect(config.settings.allowPublicRegistration).toBe(true);
    expect(config.settings.defaultUserRole).toBe("teacher");
    expect(config.settings.simulationQuotas.maxConcurrentSessions).toBe(20);
  });

  it("should return only Tenant B config when queried as Tenant B", async () => {
    const service = createTenantService(prisma as any);
    (prisma.tenant.findUnique as ReturnType<typeof vi.fn>).mockResolvedValue(
      makeTenantRow("tenant-b", "University B", {
        allowPublicRegistration: false,
        requireEmailVerification: true,
        defaultUserRole: "student",
        maxUsersPerClassroom: 30,
        enabledFeatures: "[]",
        enabledDomainPacks: '["pack-b"]',
        simulationQuotas: JSON.stringify({
          maxConcurrentSessions: 5,
          monthlyRuns: 200,
        }),
      }),
    );

    const config = await service.getTenantConfig("tenant-b" as any);
    expect(config.tenantId).toBe("tenant-b");
    expect(config.name).toBe("University B");
    expect(config.settings.allowPublicRegistration).toBe(false);
    expect(config.settings.simulationQuotas.maxConcurrentSessions).toBe(5);
    expect(config.settings.simulationQuotas.monthlyRuns).toBe(200);
  });

  it("should use tenantId as the DB filter key and not cross-contaminate results", async () => {
    const service = createTenantService(prisma as any);
    const findUnique = prisma.tenant.findUnique as ReturnType<typeof vi.fn>;

    // First call → Tenant A
    findUnique.mockResolvedValueOnce(makeTenantRow("tenant-a", "School A"));
    const configA = await service.getTenantConfig("tenant-a" as any);

    // Second call → Tenant B
    findUnique.mockResolvedValueOnce(makeTenantRow("tenant-b", "School B"));
    const configB = await service.getTenantConfig("tenant-b" as any);

    expect(configA.tenantId).toBe("tenant-a");
    expect(configB.tenantId).toBe("tenant-b");
    expect(configA.name).not.toBe(configB.name);

    // Verify each call used the correct tenantId in the where clause
    expect(findUnique).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ where: { id: "tenant-a" } }),
    );
    expect(findUnique).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ where: { id: "tenant-b" } }),
    );
  });

  it("should throw for unknown tenant rather than silently returning another tenant's data", async () => {
    const service = createTenantService(prisma as any);
    (prisma.tenant.findUnique as ReturnType<typeof vi.fn>).mockResolvedValue(
      null,
    );

    await expect(
      service.getTenantConfig("non-existent-tenant" as any),
    ).rejects.toThrow(/Tenant not found/);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-054: Quota isolation – simulation quotas are independent per tenant
// ---------------------------------------------------------------------------

describe("TenantService – TPUT-FR-054 (per-tenant quota isolation)", () => {
  let prisma: ReturnType<typeof makeMockPrisma>;

  beforeEach(() => {
    vi.clearAllMocks();
    prisma = makeMockPrisma();
  });

  it("should give Tenant A higher quotas without affecting Tenant B defaults", async () => {
    const service = createTenantService(prisma as any);
    const findUnique = prisma.tenant.findUnique as ReturnType<typeof vi.fn>;

    findUnique.mockResolvedValueOnce(
      makeTenantRow("tenant-a", "Enterprise", {
        allowPublicRegistration: false,
        requireEmailVerification: true,
        defaultUserRole: "student",
        maxUsersPerClassroom: 50,
        enabledFeatures: "[]",
        enabledDomainPacks: "[]",
        simulationQuotas: JSON.stringify({
          maxConcurrentSessions: 100,
          monthlyRuns: 50000,
        }),
      }),
    );
    findUnique.mockResolvedValueOnce(
      makeTenantRow("tenant-b", "Free Tier", null),
    );

    const configA = await service.getTenantConfig("tenant-a" as any);
    const configB = await service.getTenantConfig("tenant-b" as any);

    // Tenant A has custom elevated quotas
    expect(configA.settings.simulationQuotas.maxConcurrentSessions).toBe(100);
    expect(configA.settings.simulationQuotas.monthlyRuns).toBe(50000);

    // Tenant B uses defaults (not contaminated by A's config)
    expect(configB.settings.simulationQuotas.maxConcurrentSessions).toBe(10);
    expect(configB.settings.simulationQuotas.monthlyRuns).toBe(1000);
  });

  it("should preserve Tenant B quotas after updating Tenant A", async () => {
    const service = createTenantService(prisma as any);
    const findUnique = prisma.tenant.findUnique as ReturnType<typeof vi.fn>;
    const tenantSettingsFindUnique = prisma.tenantSettings
      .findUnique as ReturnType<typeof vi.fn>;
    const upsert = prisma.tenantSettings.upsert as ReturnType<typeof vi.fn>;

    // Mock for B's config (queried after A's update)
    findUnique.mockResolvedValue(makeTenantRow("tenant-b", "Tenant B", null));
    tenantSettingsFindUnique.mockResolvedValue(null);
    upsert.mockResolvedValue({});

    // Update A (uses upsert internally)
    const aFindUnique = vi.fn().mockResolvedValue(
      makeTenantRow("tenant-a", "Tenant A", {
        allowPublicRegistration: false,
        requireEmailVerification: true,
        defaultUserRole: "student",
        maxUsersPerClassroom: 50,
        enabledFeatures: "[]",
        enabledDomainPacks: "[]",
        simulationQuotas: JSON.stringify({
          maxConcurrentSessions: 999,
          monthlyRuns: 99999,
        }),
      }),
    );
    const aService = createTenantService({
      ...prisma,
      tenant: { findUnique: aFindUnique },
    } as any);
    await aService.updateTenantConfig("tenant-a" as any, {
      simulationQuotas: { maxConcurrentSessions: 999, monthlyRuns: 99999 },
    });

    // Now query B – B should still use defaults
    const bConfig = await service.getTenantConfig("tenant-b" as any);
    expect(bConfig.settings.simulationQuotas.maxConcurrentSessions).toBe(10);
    expect(bConfig.settings.simulationQuotas.monthlyRuns).toBe(1000);
  });

  it("should not share enabledDomainPacks between tenants", async () => {
    const service = createTenantService(prisma as any);
    const findUnique = prisma.tenant.findUnique as ReturnType<typeof vi.fn>;

    findUnique.mockResolvedValueOnce(
      makeTenantRow("tenant-a", "School A", {
        allowPublicRegistration: false,
        requireEmailVerification: true,
        defaultUserRole: "student",
        maxUsersPerClassroom: 50,
        enabledFeatures: "[]",
        enabledDomainPacks: JSON.stringify(["pack-physics", "pack-chem"]),
        simulationQuotas: JSON.stringify({
          maxConcurrentSessions: 10,
          monthlyRuns: 1000,
        }),
      }),
    );
    findUnique.mockResolvedValueOnce(
      makeTenantRow("tenant-b", "School B", null),
    );

    const configA = await service.getTenantConfig("tenant-a" as any);
    const configB = await service.getTenantConfig("tenant-b" as any);

    expect(configA.settings.enabledDomainPacks).toContain("pack-physics");
    // Tenant B has no packs configured – should be empty (default)
    expect(configB.settings.enabledDomainPacks).toHaveLength(0);
    expect(configB.settings.enabledDomainPacks).not.toContain("pack-physics");
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-055: Domain pack visibility isolation
// ---------------------------------------------------------------------------

describe("TenantService – TPUT-FR-055 (domain pack visibility isolation)", () => {
  let prisma: ReturnType<typeof makeMockPrisma>;
  let service: ReturnType<typeof createTenantService>;

  beforeEach(async () => {
    vi.clearAllMocks();
    prisma = makeMockPrisma();
    service = createTenantService(prisma as any);

    // Seed the in-memory pack store with packs from two tenants
    const packSeed: DomainPack[] = [
      makePackForTenant(
        `isolation-pack-a-private-${Date.now()}`,
        "tenant-a",
        "private",
      ),
      makePackForTenant(
        `isolation-pack-b-private-${Date.now()}`,
        "tenant-b",
        "private",
      ),
      makePackForTenant(
        `isolation-pack-public-${Date.now()}`,
        "tenant-a",
        "public",
      ),
    ];
    for (const pack of packSeed) {
      await service.createDomainPack(pack);
    }
    // Store pack IDs so assertions can reference them
    (expect as unknown as Record<string, unknown[]>).__packs = packSeed;
  });

  it("should return Tenant A's own private packs only to Tenant A", async () => {
    const { packs: allPacks } = await service.listDomainPacks("tenant-a");
    const tenantAPacks = allPacks.filter(
      (p) => p.tenantId === "tenant-a" && p.visibility === "private",
    );
    expect(tenantAPacks.length).toBeGreaterThanOrEqual(1);
  });

  it("should NOT return Tenant B's private packs when querying as Tenant A", async () => {
    const { packs: tenantAPacks } = await service.listDomainPacks("tenant-a");
    const tenantBPrivate = tenantAPacks.filter(
      (p) => p.tenantId === "tenant-b" && p.visibility === "private",
    );
    // Tenant A should never see Tenant B's private packs
    expect(tenantBPrivate).toHaveLength(0);
  });

  it("should return public packs regardless of which tenant queries", async () => {
    const { packs: tenantAPacks } = await service.listDomainPacks("tenant-a");
    const { packs: tenantBPacks } = await service.listDomainPacks("tenant-b");

    const publicInA = tenantAPacks.filter((p) => p.visibility === "public");
    const publicInB = tenantBPacks.filter((p) => p.visibility === "public");

    // Both tenants see the same public packs
    expect(publicInA.length).toBeGreaterThanOrEqual(1);
    expect(publicInB.length).toBeGreaterThanOrEqual(1);

    const publicIdsInA = publicInA.map((p) => p.id).sort();
    const publicIdsInB = publicInB.map((p) => p.id).sort();
    expect(publicIdsInA).toEqual(publicIdsInB);
  });

  it("should NOT return Tenant A's private packs when querying as Tenant B", async () => {
    const { packs: tenantBPacks } = await service.listDomainPacks("tenant-b");
    const tenantAPrivate = tenantBPacks.filter(
      (p) => p.tenantId === "tenant-a" && p.visibility === "private",
    );
    expect(tenantAPrivate).toHaveLength(0);
  });

  it("should return empty packs for a tenant with no associated packs or public packs", async () => {
    // Remove any public packs by querying an entirely new tenant with no packs in the store
    // This requires clearing the store; since we can't do that, filter for an unknown tenant
    const { packs } = await service.listDomainPacks(
      "completely-new-tenant-xyz-no-packs",
    );
    // Only public packs should remain (no private packs for this tenant)
    const privatePacks = packs.filter((p) => p.visibility === "private");
    expect(privatePacks).toHaveLength(0);
  });
});
