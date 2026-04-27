import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { tenantRoutes } from "../routes.js";

describe("tenantRoutes", () => {
  const service = {
    getTenantConfig: vi.fn(),
    updateTenantConfig: vi.fn(),
    listDomainPacks: vi.fn(),
    createDomainPack: vi.fn(),
    getDomainPack: vi.fn(),
    updateDomainPack: vi.fn(),
    deleteDomainPack: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    service.getTenantConfig.mockResolvedValue({});
    service.listDomainPacks.mockResolvedValue({ items: [], nextCursor: null });
    service.createDomainPack.mockResolvedValue({ id: "pack-1" });

    app = Fastify();
    await app.register(tenantRoutes, { service: service as never });

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

  it("rejects malformed domain-pack query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/domain-packs?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.listDomainPacks).not.toHaveBeenCalled();
  });

  it("rejects malformed domain-pack creation payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/domain-packs",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        metadata: {
          name: "Pack",
          thumbnailUrl: "not-a-url",
        },
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.createDomainPack).not.toHaveBeenCalled();
  });

  it("forwards valid domain-pack list query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/domain-packs?page=1&limit=20",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.listDomainPacks).toHaveBeenCalledWith(
      "tenant-1",
      expect.objectContaining({ page: 1, limit: 20 }),
    );
  });
});
