import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { paymentRoutes } from "../routes.js";

describe("paymentRoutes", () => {
  const service = {
    listPlans: vi.fn(),
    createSubscription: vi.fn(),
    getCurrentSubscription: vi.fn(),
    cancelSubscription: vi.fn(),
    changePlan: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();

    service.listPlans.mockResolvedValue([]);
    service.createSubscription.mockResolvedValue({ id: "sub-1" });
    service.getCurrentSubscription.mockResolvedValue({ id: "sub-1" });
    service.cancelSubscription.mockResolvedValue({ id: "sub-1", status: "canceled" });
    service.changePlan.mockResolvedValue({ id: "sub-1", tier: "professional" });

    app = Fastify();
    await app.register(paymentRoutes, { service: service as never });

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

  it("rejects invalid subscription creation payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/payments/subscriptions",
      headers: {
        "x-tenant-id": "tenant-1",
      },
      payload: {
        planId: "",
        billingInterval: "monthly",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.createSubscription).not.toHaveBeenCalled();
  });

  it("rejects invalid plan change payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/payments/subscription/change",
      headers: {
        "x-tenant-id": "tenant-1",
      },
      payload: {
        planId: "starter",
        billingInterval: "weekly",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.changePlan).not.toHaveBeenCalled();
  });

  it("rejects invalid billing portal return urls", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/payments/portal",
      headers: {
        "x-tenant-id": "tenant-1",
      },
      payload: {
        returnUrl: "not-a-url",
      },
    });

    expect(response.statusCode).toBe(400);
  });

  it("forwards valid subscription creation requests", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/payments/subscriptions",
      headers: {
        "x-tenant-id": "tenant-1",
      },
      payload: {
        planId: "starter",
        billingInterval: "monthly",
      },
    });

    expect(response.statusCode).toBe(201);
    expect(service.createSubscription).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      planId: "starter",
      billingInterval: "monthly",
    });
  });
});
