import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { credentialsRoutes } from "../routes.js";

describe("engagement credentialsRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    app = Fastify();
    app.decorate("prisma", {
      badge: { findMany: vi.fn().mockResolvedValue([]) },
      userBadge: {
        findMany: vi.fn().mockResolvedValue([]),
        create: vi.fn().mockResolvedValue({ id: "ub-1" }),
      },
      certificate: {
        findMany: vi.fn().mockResolvedValue([]),
        create: vi.fn().mockResolvedValue({ id: "cert-1" }),
      },
    });
    await app.register(credentialsRoutes);
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
