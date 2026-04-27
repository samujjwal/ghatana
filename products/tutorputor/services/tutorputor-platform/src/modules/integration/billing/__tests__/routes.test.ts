import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { billingRoutes } from "../routes.js";

describe("billingRoutes", () => {
  const service = {
    createCheckoutSession: vi.fn(),
    verifyPayment: vi.fn(),
    listPurchases: vi.fn(),
    hasPurchased: vi.fn(),
    checkHealth: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    service.createCheckoutSession.mockResolvedValue({ id: "checkout-1" });
    service.verifyPayment.mockResolvedValue({ id: "checkout-1", status: "COMPLETED" });
    service.listPurchases.mockResolvedValue({ items: [] });
    service.hasPurchased.mockResolvedValue(false);

    app = Fastify();
    app.decorate("prisma", {
      checkoutSession: { updateMany: vi.fn() },
      subscription: { updateMany: vi.fn() },
    });
    await app.register(billingRoutes, { service: service as never });

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

  it("rejects malformed checkout payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/checkout",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: { listingId: "", successUrl: "https://ok.local" },
    });

    expect(response.statusCode).toBe(400);
    expect(service.createCheckoutSession).not.toHaveBeenCalled();
  });

  it("rejects invalid purchases query limit", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/purchases?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.listPurchases).not.toHaveBeenCalled();
  });

  it("forwards valid verify payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/verify",
      headers: {
        "x-tenant-id": "tenant-1",
      },
      payload: { sessionId: "cs_123" },
    });

    expect(response.statusCode).toBe(200);
    expect(service.verifyPayment).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      sessionId: "cs_123",
    });
  });
});
