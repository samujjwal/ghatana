import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { marketplaceRoutes } from "../routes.js";

describe("marketplaceRoutes", () => {
  const service = {
    listListings: vi.fn(),
    createListing: vi.fn(),
    adminUpdateListing: vi.fn(),
    updateListing: vi.fn(),
    listSimulationTemplates: vi.fn(),
    checkHealth: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    service.listListings.mockResolvedValue({ items: [], nextCursor: null });
    service.createListing.mockResolvedValue({ id: "listing-1" });
    service.listSimulationTemplates.mockResolvedValue({ items: [] });

    app = Fastify();
    await app.register(marketplaceRoutes, { service: service as never });

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

  it("rejects invalid listing query limits", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/listings?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.listListings).not.toHaveBeenCalled();
  });

  it("rejects invalid listing creation payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/listings",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "creator-1",
        "x-user-role": "creator",
      },
      payload: {
        moduleId: "",
        priceCents: 100,
        visibility: "PUBLIC",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.createListing).not.toHaveBeenCalled();
  });

  it("forwards valid templates query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/templates?page=1&pageSize=12&difficulties=BEGINNER",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.listSimulationTemplates).toHaveBeenCalledWith(
      expect.objectContaining({
        tenantId: "tenant-1",
        page: 1,
        pageSize: 12,
        difficulties: ["BEGINNER"],
      }),
    );
  });
});
