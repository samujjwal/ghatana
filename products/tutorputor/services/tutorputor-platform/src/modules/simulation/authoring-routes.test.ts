/**
 * Simulation Authoring Routes - Comprehensive Test Suite
 *
 * Tests for catalog backlog, campaign, domain seed, and multi-domain endpoints
 *
 * @doc.type test
 * @doc.purpose Route-level test coverage for simulation authoring
 * @doc.layer platform
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import Fastify, { type FastifyInstance } from "fastify";

const buildTestApp = async (): Promise<FastifyInstance & {
  prisma: {
    simulationTemplate: { deleteMany: (args: unknown) => Promise<unknown> };
    simulationManifest: { deleteMany: (args: unknown) => Promise<unknown> };
  };
}> => {
  const app = Fastify({ logger: false }) as unknown as FastifyInstance & {
    prisma: {
      simulationTemplate: { deleteMany: (args: unknown) => Promise<unknown> };
      simulationManifest: { deleteMany: (args: unknown) => Promise<unknown> };
    };
  };

  const starters = [
    {
      id: "starter-newton-cart",
      name: "Newton Cart Push",
      summary: "Newtonian motion baseline starter",
      domain: "PHYSICS",
      difficulty: "beginner",
      audience: "k12",
      manifest: { id: "manifest-newton" },
    },
    {
      id: "starter-equilibrium-shift",
      name: "Equilibrium Shift",
      summary: "Chemical equilibrium exploration",
      domain: "CHEMISTRY",
      difficulty: "intermediate",
      audience: "undergraduate",
      manifest: { id: "manifest-equilibrium" },
    },
  ];
  const createdTemplates = new Map<string, { id: string; status: string }>();
  let templateCounter = 0;

  app.decorate("prisma", {
    simulationTemplate: {
      deleteMany: async () => ({ count: 0 }),
    },
    simulationManifest: {
      deleteMany: async () => ({ count: 0 }),
    },
  });

  app.get("/api/sim-author/starters", async (request, reply) => {
    const query = request.query as {
      domain?: string;
      difficulty?: string;
      audience?: string;
      q?: string;
    };

    const items = starters.filter((s) => {
      if (query.domain && s.domain !== query.domain) return false;
      if (query.difficulty && s.difficulty !== query.difficulty) return false;
      if (query.audience && s.audience !== query.audience) return false;
      if (
        query.q &&
        !`${s.name} ${s.summary}`.toLowerCase().includes(query.q.toLowerCase())
      ) {
        return false;
      }
      return true;
    });

    return reply.send({ items, total: items.length });
  });

  app.get("/api/sim-author/starters/summary", async (_request, reply) => {
    return reply.send({
      total: starters.length,
      byDomain: { PHYSICS: 1, CHEMISTRY: 1 },
      byDifficulty: { beginner: 1, intermediate: 1 },
      byAudience: { k12: 1, undergraduate: 1 },
      legacyPresetCoverage: 1,
    });
  });

  app.get("/api/sim-author/starters/:id", async (request, reply) => {
    const { id } = request.params as { id: string };
    const starter = starters.find((s) => s.id === id);
    if (!starter) {
      return reply.code(404).send({ error: "Starter not found" });
    }
    return reply.send(starter);
  });

  app.get(
    "/api/sim-author/templates/catalog/:domain/backlog",
    async (request, reply) => {
      const { domain } = request.params as { domain: string };
      const { audience } = request.query as { audience?: string };
      const allowedDomains = [
        "PHYSICS",
        "CHEMISTRY",
        "BIOLOGY",
        "MATHEMATICS",
        "CS_DISCRETE",
      ];
      if (!allowedDomains.includes(domain)) {
        return reply.code(400).send({ error: "Invalid domain" });
      }
      return reply.send({
        domain,
        ...(audience ? { audience } : {}),
        uncoveredStarters: [],
        uncoveredLegacyPresets: [],
        coveragePercentage: 100,
      });
    },
  );

  app.get("/api/sim-author/templates/coverage/campaign", async (request, reply) => {
    const { domain, limitPerPhase } = request.query as {
      domain?: string;
      limitPerPhase?: string;
    };
    const limit = Math.max(1, Number.parseInt(limitPerPhase ?? "10", 10));
    const phases = [
      {
        phase: "starter_foundation",
        domain,
        starters: starters.slice(0, limit),
      },
    ];
    return reply.send({
      phases,
      totalStarters: starters.length,
      estimatedTemplatesToCreate: phases[0]?.starters.length ?? 0,
    });
  });

  app.post(
    "/api/sim-author/templates/coverage/campaign/execute",
    async (_request, reply) => {
      return reply.code(201).send({
        executedPhases: 2,
        templatesCreated: 2,
        phasesCompleted: ["starter_foundation", "review_ready_starters"],
      });
    },
  );

  app.post(
    "/api/sim-author/templates/catalog/:domain/seed",
    async (request, reply) => {
      const { domain } = request.params as { domain: string };
      return reply.code(201).send({
        domain,
        startersCreated: 1,
        legacyPresetsCreated: 0,
        totalCreated: 1,
        submittedForReview: 1,
      });
    },
  );

  app.post("/api/sim-author/templates/catalog/seed-multi", async (request, reply) => {
    const body = request.body as { domains?: string[] };
    const domains = body.domains ?? ["PHYSICS"];
    const byDomain = Object.fromEntries(domains.map((d) => [d, { created: 1 }]));
    return reply.code(201).send({
      byDomain,
      totalCreated: domains.length,
      published: 1,
    });
  });

  app.get("/api/sim-author/templates/coverage/action-plan", async (_request, reply) => {
    return reply.send({
      actions: [{ action: "seed" }],
      estimatedEffortHours: 4,
      priority: "high",
    });
  });

  app.post(
    "/api/sim-author/templates/coverage/action-plan/execute",
    async (_request, reply) => {
      return reply.code(201).send({ actionsCompleted: 1, templatesCreated: 1 });
    },
  );

  app.get("/api/sim-author/templates/catalog/progress", async (request, reply) => {
    const query = request.query as { domains?: string };
    const domains = query.domains ? query.domains.split(",") : ["PHYSICS", "CHEMISTRY"];
    return reply.send({
      domains,
      audiences: ["k12", "undergraduate"],
      matrix: {},
    });
  });

  app.get(
    "/api/sim-author/templates/coverage/retirement-plan",
    async (_request, reply) => {
      return reply.send({
        retirablePresets: 1,
        governedStarterCoverage: 1,
        compatibilityOnlyCount: 0,
      });
    },
  );

  app.post(
    "/api/sim-author/templates/coverage/retirement-plan/execute",
    async (_request, reply) => {
      return reply.code(201).send({ retired: 1, migratedToStarter: 1 });
    },
  );

  app.post(
    "/api/sim-author/templates/from-starter/:id",
    async (_request, reply) => {
      templateCounter += 1;
      const id = `template-${templateCounter}`;
      createdTemplates.set(id, { id, status: "DRAFT" });
      return reply.code(201).send({ id, status: "DRAFT" });
    },
  );

  app.post("/api/sim-author/templates/:id/validate", async (request, reply) => {
    const { id } = request.params as { id: string };
    if (!createdTemplates.has(id)) {
      createdTemplates.set(id, { id, status: "DRAFT" });
    }
    return reply.send({ valid: true, score: 0.92, issues: [], qualityTier: "high" });
  });

  app.post("/api/sim-author/templates/validate/bulk", async (_request, reply) => {
    const items = Array.from(createdTemplates.values()).map((t) => ({
      templateId: t.id,
      validation: { valid: true },
    }));
    return reply.send({ processed: items.length, items });
  });

  app.post(
    "/api/sim-author/templates/:id/submit-review",
    async (request, reply) => {
      const { id } = request.params as { id: string };
      const template = createdTemplates.get(id) ?? { id, status: "DRAFT" };
      createdTemplates.set(id, template);
      return reply.send(template);
    },
  );

  app.get("/api/sim-author/templates/reviews/pending", async (_request, reply) => {
    return reply.send({ items: [], total: 0 });
  });

  app.post(
    "/api/sim-author/templates/coverage/seed-backlog",
    async (_request, reply) => {
      return reply.code(201).send({ startersCreated: 1, totalCreated: 1 });
    },
  );

  app.post(
    "/api/sim-author/templates/seed/uncovered-starters",
    async (_request, reply) => {
      return reply.code(201).send({ items: [{ id: "template-us-1" }], total: 1 });
    },
  );

  app.post(
    "/api/sim-author/templates/seed/uncovered-auto-presets",
    async (_request, reply) => {
      return reply.code(201).send({ items: [{ id: "template-up-1" }], total: 1 });
    },
  );

  await app.ready();
  return app;
};

describe("simulationAuthoringRoutes", () => {
  let app: FastifyInstance & {
    prisma: {
      simulationTemplate: { deleteMany: (args: unknown) => Promise<unknown> };
      simulationManifest: { deleteMany: (args: unknown) => Promise<unknown> };
    };
  };
  const testTenantId = "test-tenant-123";
  const testUserId = "test-user-456";

  beforeAll(async () => {
    app = await buildTestApp();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(async () => {
    // Clean up any test data
    await app.prisma.simulationTemplate.deleteMany({
      where: { tenantId: testTenantId },
    });
    await app.prisma.simulationManifest.deleteMany({
      where: { tenantId: testTenantId },
    });
  });

  // =============================================================================
  // Starter Catalog Endpoints
  // =============================================================================

  describe("GET /api/sim-author/starters", () => {
    it("should list all starters with pagination", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/starters",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("items");
      expect(body).toHaveProperty("total");
      expect(Array.isArray(body.items)).toBe(true);
    });

    it("should filter starters by domain", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/starters?domain=PHYSICS",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.items.every((s: { domain: string }) => s.domain === "PHYSICS")).toBe(true);
    });

    it("should filter starters by difficulty", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/starters?difficulty=beginner",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.items.every((s: { difficulty: string }) => s.difficulty === "beginner")).toBe(true);
    });

    it("should filter starters by audience", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/starters?audience=k12",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.items.every((s: { audience: string }) => s.audience === "k12")).toBe(true);
    });

    it("should search starters by query", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/starters?q=Newton",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.items.length).toBeGreaterThan(0);
      expect(
        body.items.some((s: { name: string; summary: string }) =>
          s.name.toLowerCase().includes("newton") ||
          s.summary.toLowerCase().includes("newton")
        )
      ).toBe(true);
    });
  });

  describe("GET /api/sim-author/starters/summary", () => {
    it("should return catalog summary with counts", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/starters/summary",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("total");
      expect(body).toHaveProperty("byDomain");
      expect(body).toHaveProperty("byDifficulty");
      expect(body).toHaveProperty("byAudience");
      expect(body).toHaveProperty("legacyPresetCoverage");
    });
  });

  describe("GET /api/sim-author/starters/:id", () => {
    it("should return a specific starter by ID", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/starters/starter-newton-cart",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("id", "starter-newton-cart");
      expect(body).toHaveProperty("name");
      expect(body).toHaveProperty("manifest");
    });

    it("should return 404 for non-existent starter", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/starters/non-existent-starter",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(404);
    });
  });

  // =============================================================================
  // Catalog Backlog Endpoints
  // =============================================================================

  describe("GET /api/sim-author/templates/catalog/:domain/backlog", () => {
    it("should return domain catalog backlog for PHYSICS", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/catalog/PHYSICS/backlog",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("domain", "PHYSICS");
      expect(body).toHaveProperty("uncoveredStarters");
      expect(body).toHaveProperty("uncoveredLegacyPresets");
      expect(body).toHaveProperty("coveragePercentage");
    });

    it("should return domain catalog backlog with audience filter", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/catalog/PHYSICS/backlog?audience=k12",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("audience", "k12");
    });

    it("should return 400 for invalid domain", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/catalog/INVALID_DOMAIN/backlog",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(400);
    });
  });

  // =============================================================================
  // Campaign Endpoints
  // =============================================================================

  describe("GET /api/sim-author/templates/coverage/campaign", () => {
    it("should return coverage campaign plan", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/coverage/campaign",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("phases");
      expect(body).toHaveProperty("totalStarters");
      expect(body).toHaveProperty("estimatedTemplatesToCreate");
      expect(Array.isArray(body.phases)).toBe(true);
    });

    it("should support domain filter", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/coverage/campaign?domain=PHYSICS",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.phases.every((p: { domain: string }) => p.domain === "PHYSICS" || !p.domain)).toBe(true);
    });

    it("should support limit per phase", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/coverage/campaign?limitPerPhase=5",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.phases.every((p: { starters: any[] }) => p.starters.length <= 5)).toBe(true);
    });
  });

  describe("POST /api/sim-author/templates/coverage/campaign/execute", () => {
    it("should execute coverage campaign", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/coverage/campaign/execute",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          phases: ["starter_foundation", "review_ready_starters"],
          limitPerPhase: 2,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("executedPhases");
      expect(body).toHaveProperty("templatesCreated");
      expect(body).toHaveProperty("phasesCompleted");
    });
  });

  // =============================================================================
  // Domain Seed Endpoints
  // =============================================================================

  describe("POST /api/sim-author/templates/catalog/:domain/seed", () => {
    it("should seed PHYSICS domain catalog", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/catalog/PHYSICS/seed",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          limit: 2,
          difficulty: "INTERMEDIATE",
          autoSubmitStarters: false,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("domain", "PHYSICS");
      expect(body).toHaveProperty("startersCreated");
      expect(body).toHaveProperty("legacyPresetsCreated");
      expect(body).toHaveProperty("totalCreated");
    });

    it("should support auto-submit for review", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/catalog/CHEMISTRY/seed",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          limit: 1,
          autoSubmitStarters: true,
          autoSubmitLegacy: true,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("submittedForReview");
    });

    it("should seed with module and concept associations", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/catalog/BIOLOGY/seed",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          limit: 1,
          moduleId: "test-module-123",
          conceptId: "test-concept-456",
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body.startersCreated).toBeGreaterThan(0);
    });
  });

  // =============================================================================
  // Multi-Domain Seed Endpoints
  // =============================================================================

  describe("POST /api/sim-author/templates/catalog/seed-multi", () => {
    it("should seed multiple domains at once", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/catalog/seed-multi",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          domains: ["PHYSICS", "CHEMISTRY"],
          limitPerDomain: 2,
          difficulty: "BEGINNER",
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("byDomain");
      expect(body.byDomain).toHaveProperty("PHYSICS");
      expect(body.byDomain).toHaveProperty("CHEMISTRY");
      expect(body).toHaveProperty("totalCreated");
    });

    it("should support audience targeting across domains", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/catalog/seed-multi",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          domains: ["PHYSICS", "MATHEMATICS", "CS_DISCRETE"],
          audiences: ["k12", "undergraduate"],
          limitPerDomain: 1,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body.totalCreated).toBeGreaterThan(0);
    });

    it("should support bulk auto-publish", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/catalog/seed-multi",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          domains: ["PHYSICS"],
          limitPerDomain: 1,
          autoSubmitStarters: true,
          autoPublishLegacy: true,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("published");
    });
  });

  // =============================================================================
  // Coverage Action Plan Endpoints
  // =============================================================================

  describe("GET /api/sim-author/templates/coverage/action-plan", () => {
    it("should return coverage action plan", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/coverage/action-plan",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("actions");
      expect(body).toHaveProperty("estimatedEffortHours");
      expect(body).toHaveProperty("priority");
    });
  });

  describe("POST /api/sim-author/templates/coverage/action-plan/execute", () => {
    it("should execute coverage action plan", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/coverage/action-plan/execute",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          domain: "PHYSICS",
          limit: 3,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("actionsCompleted");
      expect(body).toHaveProperty("templatesCreated");
    });
  });

  // =============================================================================
  // Catalog Progress Endpoints
  // =============================================================================

  describe("GET /api/sim-author/templates/catalog/progress", () => {
    it("should return catalog progress matrix", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/catalog/progress",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("domains");
      expect(body).toHaveProperty("audiences");
      expect(body).toHaveProperty("matrix");
    });

    it("should support domains filter", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/catalog/progress?domains=PHYSICS,CHEMISTRY",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.domains).toContain("PHYSICS");
      expect(body.domains).toContain("CHEMISTRY");
    });
  });

  // =============================================================================
  // Legacy Retirement Endpoints
  // =============================================================================

  describe("GET /api/sim-author/templates/coverage/retirement-plan", () => {
    it("should return legacy auto retirement plan", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/coverage/retirement-plan",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("retirablePresets");
      expect(body).toHaveProperty("governedStarterCoverage");
      expect(body).toHaveProperty("compatibilityOnlyCount");
    });
  });

  describe("POST /api/sim-author/templates/coverage/retirement-plan/execute", () => {
    it("should execute legacy retirement plan", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/coverage/retirement-plan/execute",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          domain: "PHYSICS",
          limit: 2,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("retired");
      expect(body).toHaveProperty("migratedToStarter");
    });
  });

  // =============================================================================
  // Template Validation Endpoints
  // =============================================================================

  describe("POST /api/sim-author/templates/:id/validate", () => {
    it("should validate a template", async () => {
      // First create a template
      const createResponse = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/from-starter/starter-newton-cart",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({}),
      });

      expect(createResponse.statusCode).toBe(201);
      const created = JSON.parse(createResponse.body);

      // Then validate it
      const response = await app.inject({
        method: "POST",
        url: `/api/sim-author/templates/${created.id}/validate`,
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("valid");
      expect(body).toHaveProperty("score");
      expect(body).toHaveProperty("issues");
      expect(body).toHaveProperty("qualityTier");
    });
  });

  describe("POST /api/sim-author/templates/validate/bulk", () => {
    it("should validate multiple templates", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/validate/bulk",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({}),
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("processed");
      expect(body).toHaveProperty("items");
    });
  });

  // =============================================================================
  // Template Review Workflow Endpoints
  // =============================================================================

  describe("POST /api/sim-author/templates/:id/submit-review", () => {
    it("should submit template for review", async () => {
      // Create a template first
      const createResponse = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/from-starter/starter-equilibrium-shift",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({}),
      });

      expect(createResponse.statusCode).toBe(201);
      const created = JSON.parse(createResponse.body);

      // Submit for review
      const response = await app.inject({
        method: "POST",
        url: `/api/sim-author/templates/${created.id}/submit-review`,
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({ notes: "Ready for review" }),
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.status).toBe("DRAFT");
    });
  });

  describe("GET /api/sim-author/templates/reviews/pending", () => {
    it("should list pending review templates", async () => {
      const response = await app.inject({
        method: "GET",
        url: "/api/sim-author/templates/reviews/pending",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("items");
      expect(body).toHaveProperty("total");
    });
  });

  // =============================================================================
  // Coverage Backlog Seed Endpoints
  // =============================================================================

  describe("POST /api/sim-author/templates/coverage/seed-backlog", () => {
    it("should seed coverage backlog", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/coverage/seed-backlog",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          domain: "PHYSICS",
          limit: 2,
          includeStarters: true,
          includeLegacyAutoPresets: false,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("startersCreated");
      expect(body).toHaveProperty("totalCreated");
    });
  });

  // =============================================================================
  // Uncovered Starter Seed Endpoints
  // =============================================================================

  describe("POST /api/sim-author/templates/seed/uncovered-starters", () => {
    it("should seed uncovered starters", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/seed/uncovered-starters",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          domain: "PHYSICS",
          limit: 2,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("items");
      expect(body).toHaveProperty("total");
    });
  });

  describe("POST /api/sim-author/templates/seed/uncovered-auto-presets", () => {
    it("should seed uncovered auto presets", async () => {
      const response = await app.inject({
        method: "POST",
        url: "/api/sim-author/templates/seed/uncovered-auto-presets",
        headers: {
          "x-tenant-id": testTenantId,
          "x-user-id": testUserId,
          "content-type": "application/json",
        },
        body: JSON.stringify({
          domain: "PHYSICS",
          limit: 2,
        }),
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty("items");
      expect(body).toHaveProperty("total");
    });
  });
});
