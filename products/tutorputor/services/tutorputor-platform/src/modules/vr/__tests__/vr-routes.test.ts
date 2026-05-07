/**
 * VR Routes — session ownership enforcement tests.
 *
 * Verifies that GET /sessions/:sessionId enforces self-or-privileged access,
 * and that all write operations are properly role-gated.
 * Also verifies the vr_webxr feature flag preHandler blocks all routes when
 * the flag is disabled.
 */
import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { vrRoutes } from "../vr-routes.js";

// ---------------------------------------------------------------------------
// Feature flag mock — default: vr_webxr is DISABLED (matches production default)
// ---------------------------------------------------------------------------
const mockIsEnabled = vi.fn().mockReturnValue(false);
vi.mock("../../feature-flags/FeatureFlagService.js", () => ({
  FeatureFlagService: class {
    isEnabled = mockIsEnabled;
  },
}));

const SESSION_OWNER_ID = "learner-1";
const OTHER_USER_ID = "learner-2";
const ADMIN_USER_ID = "admin-1";
const TENANT_ID = "tenant-1";

const mockSession = {
  id: "session-1",
  userId: SESSION_OWNER_ID,
  labId: "lab-1",
  status: "active",
  currentSceneId: "scene-1",
  deviceType: "desktop",
  deviceInfo: {},
  progress: { completedObjectives: [], totalPoints: 0, maxPoints: 100, scenesVisited: [], interactionsLog: [] },
  startedAt: new Date().toISOString(),
  lastActiveAt: new Date().toISOString(),
  totalDuration: 0,
  performanceMetrics: { averageFps: 60, minFps: 30, loadTime: 0, memoryUsage: 0, latency: 0 },
};

function buildApp(userId: string, role: string) {
  const app = Fastify();
  const mockGetSession = vi.fn().mockResolvedValue(mockSession);

  app.decorate("prisma", {
    vRLab: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      update: vi.fn().mockResolvedValue({}),
    },
    vRSession: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      create: vi.fn().mockResolvedValue(mockSession),
      update: vi.fn().mockResolvedValue(mockSession),
    },
    vRScene: {
      findMany: vi.fn().mockResolvedValue([]),
      create: vi.fn().mockResolvedValue({}),
      update: vi.fn().mockResolvedValue({}),
      delete: vi.fn().mockResolvedValue({}),
    },
    vRAnalytics: {
      findFirst: vi.fn().mockResolvedValue(null),
    },
  });

  app.addHook("preHandler", async (request) => {
    (
      request as typeof request & {
        user?: { id: string; sub: string; tenantId: string; role: string };
      }
    ).user = { id: userId, sub: userId, tenantId: TENANT_ID, role };
  });

  // Attach getSession mock on the service level by overriding the VRSessionServiceImpl
  // We test the route integration via mock Prisma: vRSession.findFirst resolves mock session
  app.decorate("mockGetSession", mockGetSession);

  void app.register(vrRoutes);

  return { app, mockGetSession };
}

describe("VR Routes — session ownership enforcement", () => {
  beforeEach(() => {
    // Enable VR feature flag by default so existing route tests exercise the handlers.
    // The "vr_webxr feature flag preHandler" describe overrides this per test.
    mockIsEnabled.mockReturnValue(true);
  });

  describe("GET /sessions/:sessionId", () => {
    it("allows session owner to access their own session", async () => {
      const { app } = buildApp(SESSION_OWNER_ID, "learner");
      // Patch findFirst to return the session (VRSessionServiceImpl.getSession uses findFirst)
      const prisma = (app as ReturnType<typeof Fastify> & { prisma: { vRSession: { findFirst: ReturnType<typeof vi.fn> } } }).prisma;
      prisma.vRSession.findFirst.mockResolvedValue({
        id: "session-1",
        userId: SESSION_OWNER_ID,
        tenantId: TENANT_ID,
        labId: "lab-1",
        status: "active",
        currentSceneId: "scene-1",
        deviceType: "desktop",
        deviceInfo: {},
        progress: {},
        startedAt: new Date(),
        lastActiveAt: new Date(),
        endedAt: null,
        totalDuration: 0,
        performanceMetrics: {},
      });

      await app.ready();
      const response = await app.inject({
        method: "GET",
        url: "/sessions/session-1",
        headers: { "x-tenant-id": TENANT_ID },
      });

      expect(response.statusCode).toBe(200);
      await app.close();
    });

    it("returns 403 when non-owner learner attempts to access another user's session", async () => {
      const { app } = buildApp(OTHER_USER_ID, "learner");
      const prisma = (app as ReturnType<typeof Fastify> & { prisma: { vRSession: { findFirst: ReturnType<typeof vi.fn> } } }).prisma;
      prisma.vRSession.findFirst.mockResolvedValue({
        id: "session-1",
        userId: SESSION_OWNER_ID, // owned by different user
        tenantId: TENANT_ID,
        labId: "lab-1",
        status: "active",
        currentSceneId: "scene-1",
        deviceType: "desktop",
        deviceInfo: {},
        progress: {},
        startedAt: new Date(),
        lastActiveAt: new Date(),
        endedAt: null,
        totalDuration: 0,
        performanceMetrics: {},
      });

      await app.ready();
      const response = await app.inject({
        method: "GET",
        url: "/sessions/session-1",
        headers: { "x-tenant-id": TENANT_ID },
      });

      expect(response.statusCode).toBe(403);
      await app.close();
    });

    it("allows admin to access any session within tenant", async () => {
      const { app } = buildApp(ADMIN_USER_ID, "admin");
      const prisma = (app as ReturnType<typeof Fastify> & { prisma: { vRSession: { findFirst: ReturnType<typeof vi.fn> } } }).prisma;
      prisma.vRSession.findFirst.mockResolvedValue({
        id: "session-1",
        userId: SESSION_OWNER_ID, // owned by a learner, but admin can access
        tenantId: TENANT_ID,
        labId: "lab-1",
        status: "active",
        currentSceneId: "scene-1",
        deviceType: "desktop",
        deviceInfo: {},
        progress: {},
        startedAt: new Date(),
        lastActiveAt: new Date(),
        endedAt: null,
        totalDuration: 0,
        performanceMetrics: {},
      });

      await app.ready();
      const response = await app.inject({
        method: "GET",
        url: "/sessions/session-1",
        headers: { "x-tenant-id": TENANT_ID },
      });

      expect(response.statusCode).toBe(200);
      await app.close();
    });

    it("returns 404 when session does not exist", async () => {
      const { app } = buildApp(SESSION_OWNER_ID, "learner");
      const prisma = (app as ReturnType<typeof Fastify> & { prisma: { vRSession: { findFirst: ReturnType<typeof vi.fn> } } }).prisma;
      prisma.vRSession.findFirst.mockResolvedValue(null);

      await app.ready();
      const response = await app.inject({
        method: "GET",
        url: "/sessions/nonexistent-session",
        headers: { "x-tenant-id": TENANT_ID },
      });

      expect(response.statusCode).toBe(404);
      await app.close();
    });
  });

  describe("POST /labs — role guard", () => {
    it("returns 403 when a learner attempts to create a VR lab", async () => {
      mockIsEnabled.mockReturnValue(true);
      const { app } = buildApp("learner-1", "learner");
      await app.ready();

      const response = await app.inject({
        method: "POST",
        url: "/labs",
        headers: { "x-tenant-id": TENANT_ID },
        payload: { title: "Test Lab" },
      });

      expect(response.statusCode).toBe(403);
      await app.close();
    });
  });

  describe("vr_webxr feature flag preHandler", () => {
    it("returns 404 for all VR routes when flag is disabled", async () => {
      mockIsEnabled.mockReturnValue(false);
      const { app } = buildApp(SESSION_OWNER_ID, "admin");
      await app.ready();

      const response = await app.inject({
        method: "GET",
        url: "/labs",
        headers: { "x-tenant-id": TENANT_ID },
      });

      expect(response.statusCode).toBe(404);
      const body = JSON.parse(response.body) as { error: string };
      expect(body.error).toBe("VR features are not available.");
      await app.close();
    });

    it("allows through to route handlers when flag is enabled", async () => {
      mockIsEnabled.mockReturnValue(true);
      const { app } = buildApp(SESSION_OWNER_ID, "admin");
      await app.ready();

      const response = await app.inject({
        method: "GET",
        url: "/labs",
        headers: { "x-tenant-id": TENANT_ID },
      });

      // Route handler runs — not a feature-flag 404 (may be 200 or another status)
      expect(response.statusCode).not.toBe(404);
      await app.close();
    });
  });
});
