import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { complianceRoutes } from "../routes.js";

describe("complianceRoutes", () => {
  const service = {
    requestUserExport: vi.fn(),
    createDeletionVerification: vi.fn(),
    verifyAndProcessDeletion: vi.fn(),
  };

  const prisma = {
    user: {
      findFirst: vi.fn(),
      findUnique: vi.fn(),
    },
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();

    prisma.user.findFirst.mockResolvedValue({
      id: "user-1",
      tenantId: "tenant-1",
      email: "user@example.com",
    });
    prisma.user.findUnique.mockResolvedValue({
      id: "user-1",
      email: "user@example.com",
    });

    service.requestUserExport.mockResolvedValue({ id: "req-1", status: "pending" });
    service.verifyAndProcessDeletion.mockResolvedValue({
      success: true,
      message: "Scheduled",
    });

    app = Fastify();
    app.decorate("prisma", prisma as never);
    await app.register(complianceRoutes, { service: service as never });

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

  it("rejects malformed export payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/export",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        userId: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.requestUserExport).not.toHaveBeenCalled();
  });

  it("allows self export requests", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/export",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {},
    });

    expect(response.statusCode).toBe(200);
    expect(service.requestUserExport).toHaveBeenCalledWith({
      userId: "user-1",
      tenantId: "tenant-1",
      requestedBy: "user-1",
    });
  });

  it("rejects deletion verification without token", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/deletion/verify",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        token: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.verifyAndProcessDeletion).not.toHaveBeenCalled();
  });

  it("forwards valid deletion verification tokens", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/deletion/verify",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        token: "valid-token",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.verifyAndProcessDeletion).toHaveBeenCalledWith({
      userId: "user-1",
      tenantId: "tenant-1",
      token: "valid-token",
    });
  });
});
