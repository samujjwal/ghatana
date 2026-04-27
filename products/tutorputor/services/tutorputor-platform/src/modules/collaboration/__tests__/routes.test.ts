import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { collaborationRoutes } from "../routes.js";

describe("collaborationRoutes", () => {
  const service = {
    postQuestion: vi.fn(),
    listThreads: vi.fn(),
    getThread: vi.fn(),
    reply: vi.fn(),
    markAsAnswer: vi.fn(),
    closeThread: vi.fn(),
    createSharedNote: vi.fn(),
    getSharedNote: vi.fn(),
    updateSharedNote: vi.fn(),
    shareNote: vi.fn(),
    listSharedNotes: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    service.listThreads.mockResolvedValue({ items: [], nextCursor: null });

    app = Fastify();
    await app.register(collaborationRoutes, { service: service as never });

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

  it("rejects malformed thread payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/threads",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
      payload: {
        authorName: "",
        title: "Help",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.postQuestion).not.toHaveBeenCalled();
  });

  it("rejects invalid list thread limit", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/threads?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.listThreads).not.toHaveBeenCalled();
  });

  it("forwards valid thread listing query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/threads?status=OPEN&limit=10",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.listThreads).toHaveBeenCalledWith(
      expect.objectContaining({
        tenantId: "tenant-1",
        status: "OPEN",
        limit: 10,
      }),
    );
  });
});
