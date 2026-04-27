import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { registerContentRoutes } from "../routes";

describe("registerContentRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const contentService = {
    listModules: vi.fn(),
    getModuleBySlug: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();

    contentService.listModules.mockResolvedValue({ items: [] });
    contentService.getModuleBySlug.mockResolvedValue({
      module: { slug: "intro" },
      enrollment: null,
    });

    app = Fastify();
    await registerContentRoutes(app, {
      contentService: contentService as never,
    });

    // Inject auth context so getTenantId/getUserId resolve without a real JWT stack.
    // The role is read from the x-user-role header (if present) so individual
    // tests can override it; admin is used as the safe default.
    app.addHook("preHandler", async (request) => {
      const roleHeader = request.headers["x-user-role"];
      const role = typeof roleHeader === "string" ? roleHeader : "admin";
      (
        request as typeof request & {
          user?: { id: string; sub: string; tenantId: string; role: string };
        }
      ).user = { id: "user-1", sub: "user-1", tenantId: "tenant-1", role };
    });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed module list query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/v1/modules?domain=%20",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentService.listModules).not.toHaveBeenCalled();
  });

  it("rejects malformed module slug param", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/v1/modules/%20",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentService.getModuleBySlug).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only cursor query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/v1/modules?cursor=%20",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentService.listModules).not.toHaveBeenCalled();
  });
});
