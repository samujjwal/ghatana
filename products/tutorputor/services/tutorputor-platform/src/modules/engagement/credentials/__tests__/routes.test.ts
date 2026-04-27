import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { credentialsRoutes } from "../routes.js";

describe("engagement credentialsRoutes", () => {
  let app: ReturnType<typeof Fastify>;
  const mockBadgeFindFirst = vi.fn();
  const mockBadgeEarnedCreate = vi.fn();
  const mockBadgeEarnedFindMany = vi.fn();

  beforeEach(async () => {
    mockBadgeFindFirst.mockReset();
    mockBadgeEarnedCreate.mockReset();
    mockBadgeEarnedFindMany.mockReset();

    app = Fastify();
    app.decorate("prisma", {
      badge: {
        findMany: vi.fn().mockResolvedValue([]),
        findFirst: mockBadgeFindFirst,
      },
      badgeEarned: {
        findMany: mockBadgeEarnedFindMany.mockResolvedValue([]),
        create: mockBadgeEarnedCreate.mockResolvedValue({
          id: "ub-1",
          badge: { id: "badge-1", name: "Test Badge" },
        }),
      },
      certificate: {
        findMany: vi.fn().mockResolvedValue([]),
        create: vi.fn().mockResolvedValue({ id: "cert-1" }),
      },
      learningEvent: {
        create: vi.fn().mockResolvedValue({ id: "evt-1" }),
        findMany: vi.fn().mockResolvedValue([]),
      },
    });
    await app.register(credentialsRoutes);

    // Inject auth context so getTenantId/getUserId resolve without a real JWT stack.
    app.addHook("preHandler", async (request) => {
      const userIdHeader = request.headers["x-user-id"];
      const userId = typeof userIdHeader === "string" ? userIdHeader : "user-1";
      const roleHeader = request.headers["x-user-role"];
      const role = typeof roleHeader === "string" ? roleHeader : "admin";
      (
        request as typeof request & {
          user?: { id: string; sub: string; tenantId: string; role: string };
        }
      ).user = { id: userId, sub: userId, tenantId: "tenant-1", role };
    });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed badge award payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/badges/award",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "teacher-1",
        "x-user-role": "teacher",
      },
      payload: {
        userId: "user-1",
        badgeId: "",
      },
    });

    expect(response.statusCode).toBe(400);
  });

  it("returns 404 when badge does not belong to the tenant", async () => {
    mockBadgeFindFirst.mockResolvedValue(null);

    const response = await app.inject({
      method: "POST",
      url: "/badges/award",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "admin-1",
        "x-user-role": "admin",
      },
      payload: {
        userId: "user-1",
        badgeId: "foreign-badge-99",
      },
    });

    expect(response.statusCode).toBe(404);
    const body = JSON.parse(response.body) as { error: string };
    expect(body.error).toBe("Badge not found");
    expect(mockBadgeFindFirst).toHaveBeenCalledWith({
      where: { id: "foreign-badge-99", tenantId: "tenant-1" },
    });
  });

  it("awards badge when badge belongs to tenant", async () => {
    mockBadgeFindFirst.mockResolvedValue({ id: "badge-1", tenantId: "tenant-1" });

    const response = await app.inject({
      method: "POST",
      url: "/badges/award",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "admin-1",
        "x-user-role": "admin",
      },
      payload: {
        userId: "user-1",
        badgeId: "badge-1",
      },
    });

    expect(response.statusCode).toBe(201);
    expect(mockBadgeEarnedCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          tenantId: "tenant-1",
          userId: "user-1",
          badgeId: "badge-1",
        }),
      }),
    );
  });

  it("rejects malformed certificate payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/certificates/generate",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        moduleId: "module-1",
        completionDate: "bad-date",
      },
    });

    expect(response.statusCode).toBe(400);
  });

  it("accepts valid certificate payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/certificates/generate",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        moduleId: "module-1",
        completionDate: "2026-04-16T12:00:00.000Z",
      },
    });

    expect(response.statusCode).toBe(201);
  });
});
