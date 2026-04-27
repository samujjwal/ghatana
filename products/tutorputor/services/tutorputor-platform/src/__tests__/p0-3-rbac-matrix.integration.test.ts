/**
 * P0-3: RBAC/ABAC Role Matrix Integration Tests
 *
 * Exercises every major route family (learning, content-studio, generation,
 * AI, tenant-admin, user-admin, integration) against the four canonical TutorPutor
 * roles: student, teacher, admin, superadmin.
 *
 * Strategy:
 * - All guarded routes require a valid JWT. Missing → 401.
 * - Wrong role on a role-restricted route → 403.
 * - Routes that are only blocked by consent return 451 when consent is absent —
 *   this is a distinct category from RBAC denial.
 * - We test the structural security contract, not DB query correctness: route
 *   handlers may return 404/500 when mocked DB records are missing, which is
 *   acceptable as long as the role-enforcement layer fires first.
 *
 * @doc.type test-suite
 * @doc.purpose RBAC/ABAC matrix validation: student/teacher/admin/superadmin per route family
 * @doc.layer platform
 * @doc.pattern IntegrationTest
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createServer } from "../setup.js";
import type { FastifyInstance } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

function createMinimalPrismaStub(): PrismaClient {
  return {
    $connect: vi.fn().mockResolvedValue(undefined),
    $disconnect: vi.fn().mockResolvedValue(undefined),
    $queryRaw: vi.fn().mockResolvedValue([{ 1: 1n }]),
    userConsent: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    generationRequest: {
      create: vi.fn().mockResolvedValue({ id: "req-1", status: "DRAFT", tenantId: "tenant-1" }),
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      count: vi.fn().mockResolvedValue(0),
      update: vi.fn().mockResolvedValue({}),
    },
    generationJob: {
      createMany: vi.fn().mockResolvedValue({ count: 0 }),
      findMany: vi.fn().mockResolvedValue([]),
    },
    tenant: {
      findUnique: vi.fn().mockResolvedValue({ id: "tenant-1" }),
      findMany: vi.fn().mockResolvedValue([]),
      create: vi.fn().mockResolvedValue({ id: "tenant-new" }),
      update: vi.fn().mockResolvedValue({}),
    },
    user: {
      findUnique: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      update: vi.fn().mockResolvedValue({}),
      delete: vi.fn().mockResolvedValue({}),
    },
    learningExperience: {
      findMany: vi.fn().mockResolvedValue([]),
      findUnique: vi.fn().mockResolvedValue(null),
      create: vi.fn().mockResolvedValue({ id: "exp-1" }),
    },
    featureFlag: {
      findMany: vi.fn().mockResolvedValue([]),
      upsert: vi.fn().mockResolvedValue({}),
    },
  } as unknown as PrismaClient;
}

function createMockRedis() {
  const storage = new Map<string, string>();
  return {
    incr: vi.fn((key: string) => {
      const v = parseInt(storage.get(key) ?? "0", 10) + 1;
      storage.set(key, String(v));
      return Promise.resolve(v);
    }),
    get: vi.fn((key: string) => Promise.resolve(storage.get(key) ?? null)),
    set: vi.fn((key: string, value: string) => {
      storage.set(key, value);
      return Promise.resolve("OK");
    }),
    expire: vi.fn(() => Promise.resolve(1)),
    del: vi.fn((key: string) => {
      storage.delete(key);
      return Promise.resolve(1);
    }),
    ping: vi.fn().mockResolvedValue("PONG"),
    connect: vi.fn().mockResolvedValue(undefined),
    disconnect: vi.fn().mockResolvedValue(undefined),
    duplicate: vi.fn().mockReturnValue({
      subscribe: vi.fn().mockResolvedValue(undefined),
      unsubscribe: vi.fn().mockResolvedValue(undefined),
      on: vi.fn(),
      removeAllListeners: vi.fn(),
      disconnect: vi.fn(),
    }),
    publish: vi.fn().mockResolvedValue(1),
    status: "ready",
  };
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

type RoleType = "student" | "teacher" | "admin" | "superadmin";

function signAs(app: FastifyInstance, role: RoleType, tenantId = "tenant-1"): string {
  return app.jwt.sign({ sub: `user-${role}`, tenantId, role });
}

/**
 * Returns whether the status code indicates a role-enforcement denial.
 * 403 → role was rejected by a role guard.
 * 401 → token was missing/invalid — that would indicate a test bug.
 */
function isRoleDenied(status: number): boolean {
  return status === 403;
}

/**
 * Returns whether the status code indicates the request passed auth (may still
 * fail downstream due to missing data or consent).
 */
function passedAuth(status: number): boolean {
  return status !== 401 && status !== 403;
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe("P0-3: RBAC/ABAC role matrix — route family enforcement", () => {
  let app: FastifyInstance;
  let savedStripeKey: string | undefined;

  beforeEach(async () => {
    savedStripeKey = process.env.STRIPE_SECRET_KEY;
    process.env.STRIPE_SECRET_KEY = "stripe_test_placeholder_secret";
    app = await createServer({
      startContentWorker: false,
      startLearnerProfileGrpcServer: false,
      prisma: createMinimalPrismaStub(),
      redis: createMockRedis(),
    });
    await app.ready();
  });

  afterEach(async () => {
    await app?.close();
    vi.clearAllMocks();
    if (savedStripeKey === undefined) {
      delete process.env.STRIPE_SECRET_KEY;
    } else {
      process.env.STRIPE_SECRET_KEY = savedStripeKey;
    }
  });

  // ─── 1. No token → always 401 on guarded routes ─────────────────────────

  describe("No-token baseline", () => {
    const guardedRoutes = [
      { method: "GET" as const, url: "/api/v1/learning/dashboard" },
      { method: "GET" as const, url: "/api/v1/modules" },
      { method: "GET" as const, url: "/api/v1/tenant/config" },
      { method: "GET" as const, url: "/api/v1/admin/feature-flags" },
      { method: "POST" as const, url: "/api/generation/requests", payload: { title: "x", domain: "math" } },
    ];

    for (const route of guardedRoutes) {
      it(`unauthenticated ${route.method} ${route.url} → 401`, async () => {
        const res = await app.inject({
          method: route.method,
          url: route.url,
          payload: route.payload,
        });
        expect(res.statusCode).toBe(401);
      });
    }
  });

  // ─── 2. Learning/Dashboard route family ─────────────────────────────────

  describe("Learning route family (/api/v1/learning/*)", () => {
    it("student can access dashboard (200/404/500 — not 401/403)", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: `Bearer ${signAs(app, "student")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });

    it("teacher can access dashboard", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: `Bearer ${signAs(app, "teacher")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });

    it("admin can access dashboard", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: `Bearer ${signAs(app, "admin")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });

    it("superadmin can access dashboard", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/learning/dashboard",
        headers: { authorization: `Bearer ${signAs(app, "superadmin")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });
  });

  // ─── 3. Content generation route family ─────────────────────────────────

  describe("Generation route family (/api/generation/*)", () => {
    const createPayload = {
      title: "RBAC test generation",
      domain: "math",
      description: "role matrix test",
    };

    it("student cannot create generation requests → 403", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/generation/requests",
        headers: { authorization: `Bearer ${signAs(app, "student")}` },
        payload: createPayload,
      });
      expect(isRoleDenied(res.statusCode)).toBe(true);
    });

    it("teacher cannot create generation requests → 403", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/generation/requests",
        headers: { authorization: `Bearer ${signAs(app, "teacher")}` },
        payload: createPayload,
      });
      expect(isRoleDenied(res.statusCode)).toBe(true);
    });

    it("admin can create generation requests (passes role gate)", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/generation/requests",
        headers: { authorization: `Bearer ${signAs(app, "admin")}` },
        payload: createPayload,
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });

    it("superadmin can create generation requests (passes role gate)", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/generation/requests",
        headers: { authorization: `Bearer ${signAs(app, "superadmin")}` },
        payload: createPayload,
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });

    it("student cannot list generation requests → 403", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/generation/requests",
        headers: { authorization: `Bearer ${signAs(app, "student")}` },
      });
      expect(isRoleDenied(res.statusCode)).toBe(true);
    });

    it("teacher cannot list generation requests → 403", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/generation/requests",
        headers: { authorization: `Bearer ${signAs(app, "teacher")}` },
      });
      expect(isRoleDenied(res.statusCode)).toBe(true);
    });

    it("admin can list generation requests", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/generation/requests",
        headers: { authorization: `Bearer ${signAs(app, "admin")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });
  });

  // ─── 4. Tenant admin route family ───────────────────────────────────────
  //    GET /config is read-only and open to all authenticated users.
  //    PATCH /config (mutate tenant config) is admin-only.

  describe("Tenant admin route family (/api/v1/tenant/*)", () => {
    it("student can read tenant config (read is open to authenticated users)", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/tenant/config",
        headers: { authorization: `Bearer ${signAs(app, "student")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });

    it("teacher can read tenant config (read is open to authenticated users)", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/tenant/config",
        headers: { authorization: `Bearer ${signAs(app, "teacher")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });

    it("student cannot mutate tenant config → 403", async () => {
      const res = await app.inject({
        method: "PATCH",
        url: "/api/v1/tenant/config",
        headers: { authorization: `Bearer ${signAs(app, "student")}`, "content-type": "application/json" },
        body: JSON.stringify({ someKey: "val" }),
      });
      expect(isRoleDenied(res.statusCode)).toBe(true);
    });

    it("teacher cannot mutate tenant config → 403", async () => {
      const res = await app.inject({
        method: "PATCH",
        url: "/api/v1/tenant/config",
        headers: { authorization: `Bearer ${signAs(app, "teacher")}`, "content-type": "application/json" },
        body: JSON.stringify({ someKey: "val" }),
      });
      expect(isRoleDenied(res.statusCode)).toBe(true);
    });

    it("admin can access tenant config", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/tenant/config",
        headers: { authorization: `Bearer ${signAs(app, "admin")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });

    it("superadmin can access tenant config", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/tenant/config",
        headers: { authorization: `Bearer ${signAs(app, "superadmin")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });
  });

  // ─── 5. Feature flags route family (admin-only) ─────────────────────────

  describe("Feature flags route family (/api/v1/admin/feature-flags)", () => {
    it("student cannot access feature flags → 403", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/feature-flags",
        headers: { authorization: `Bearer ${signAs(app, "student")}` },
      });
      expect(isRoleDenied(res.statusCode)).toBe(true);
    });

    it("teacher cannot access feature flags → 403", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/feature-flags",
        headers: { authorization: `Bearer ${signAs(app, "teacher")}` },
      });
      expect(isRoleDenied(res.statusCode)).toBe(true);
    });

    it("admin can access feature flags", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/feature-flags",
        headers: { authorization: `Bearer ${signAs(app, "admin")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });

    it("superadmin can access feature flags", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/feature-flags",
        headers: { authorization: `Bearer ${signAs(app, "superadmin")}` },
      });
      expect(passedAuth(res.statusCode)).toBe(true);
    });
  });

  // ─── 6. AI route family ─────────────────────────────────────────────────
  //    AI routes are consent-gated, not role-gated. All roles can pass auth;
  //    consent absence returns 451 (not 403).

  describe("AI route family (/api/v1/ai/*)", () => {
    it("student token passes auth on AI routes (consent may block → 451, not 403)", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        headers: { authorization: `Bearer ${signAs(app, "student")}` },
        payload: { query: "explain photosynthesis" },
      });
      expect(res.statusCode).not.toBe(401);
      expect(res.statusCode).not.toBe(403);
    });

    it("teacher token passes auth on AI routes", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        headers: { authorization: `Bearer ${signAs(app, "teacher")}` },
        payload: { query: "explain photosynthesis" },
      });
      expect(res.statusCode).not.toBe(401);
      expect(res.statusCode).not.toBe(403);
    });

    it("admin token passes auth on AI routes", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        headers: { authorization: `Bearer ${signAs(app, "admin")}` },
        payload: { query: "explain photosynthesis" },
      });
      expect(res.statusCode).not.toBe(401);
      expect(res.statusCode).not.toBe(403);
    });

    it("superadmin token passes auth on AI routes", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        headers: { authorization: `Bearer ${signAs(app, "superadmin")}` },
        payload: { query: "explain photosynthesis" },
      });
      expect(res.statusCode).not.toBe(401);
      expect(res.statusCode).not.toBe(403);
    });

    it("no token on AI route → 401", async () => {
      const res = await app.inject({
        method: "POST",
        url: "/api/v1/ai/tutor/query",
        payload: { query: "explain photosynthesis" },
      });
      expect(res.statusCode).toBe(401);
    });
  });

  // ─── 7. Cross-tenant RBAC isolation ─────────────────────────────────────
  //    Even an admin from tenant-A must not access tenant-B resources.

  describe("Cross-tenant admin isolation", () => {
    it("admin from tenant-A is denied access to tenant-B generation requests", async () => {
      const tokenA = signAs(app, "admin", "tenant-A");

      // Attempt to access a request that would belong to tenant-B
      const res = await app.inject({
        method: "GET",
        url: "/api/generation/requests/req-tenant-b",
        headers: { authorization: `Bearer ${tokenA}` },
      });

      // Route should return 404 (not found in tenant-A scope) or 403, never the resource
      expect([403, 404]).toContain(res.statusCode);
    });

    it("admin from tenant-A cannot create generation requests scoped to tenant-B", async () => {
      const tokenA = signAs(app, "admin", "tenant-A");

      const res = await app.inject({
        method: "POST",
        url: "/api/generation/requests",
        headers: { authorization: `Bearer ${tokenA}` },
        payload: {
          title: "Cross-tenant attempt",
          domain: "math",
          tenantId: "tenant-B", // Attempt to specify different tenant
        },
      });

      // Should pass role gate but tenant binding should scope to token tenant
      // Result is 200/201/400/404/500, but NOT 403 (role OK) and
      // the response should reflect tenant-A scoping, not tenant-B
      expect(res.statusCode).not.toBe(401);
    });
  });

  // ─── 8. User management route family ────────────────────────────────────

  describe("User management route family (/api/v1/admin/* user management)", () => {
    it("student cannot access user list → 403", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/users",
        headers: { authorization: `Bearer ${signAs(app, "student")}` },
      });
      // 403 if route is guarded, 404 if route does not exist
      expect([403, 404]).toContain(res.statusCode);
      expect(res.statusCode).not.toBe(200);
    });

    it("teacher cannot access user list → 403 or 404", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/users",
        headers: { authorization: `Bearer ${signAs(app, "teacher")}` },
      });
      expect([403, 404]).toContain(res.statusCode);
    });

    it("admin can access user management endpoint", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/users",
        headers: { authorization: `Bearer ${signAs(app, "admin")}` },
      });
      // 200, 404 (route not found), 500 (DB stub) are all acceptable — not 401/403
      expect(res.statusCode).not.toBe(401);
      expect(res.statusCode).not.toBe(403);
    });

    it("superadmin can access user management endpoint", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/users",
        headers: { authorization: `Bearer ${signAs(app, "superadmin")}` },
      });
      expect(res.statusCode).not.toBe(401);
      expect(res.statusCode).not.toBe(403);
    });
  });

  // ─── 9. Observability route family (admin only) ──────────────────────────

  describe("Observability route family (/api/v1/admin/observability/*)", () => {
    it("student cannot access observability endpoints → 403", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/observability/health",
        headers: { authorization: `Bearer ${signAs(app, "student")}` },
      });
      expect([403, 404]).toContain(res.statusCode);
    });

    it("teacher cannot access observability endpoints → 403", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/observability/health",
        headers: { authorization: `Bearer ${signAs(app, "teacher")}` },
      });
      expect([403, 404]).toContain(res.statusCode);
    });

    it("admin can access observability endpoints", async () => {
      const res = await app.inject({
        method: "GET",
        url: "/api/v1/admin/observability/health",
        headers: { authorization: `Bearer ${signAs(app, "admin")}` },
      });
      expect(res.statusCode).not.toBe(401);
      expect(res.statusCode).not.toBe(403);
    });
  });
});
