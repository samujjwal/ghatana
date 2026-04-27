import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerReviewRoutes } from "../routes";
import { GenerationReviewService } from "../review-service";

describe("registerReviewRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const submitSpy = vi.spyOn(GenerationReviewService.prototype, "submitDecision");

  beforeEach(async () => {
    vi.clearAllMocks();

    submitSpy.mockResolvedValue({ id: "decision-1" } as never);

    app = Fastify();
    registerReviewRoutes(app, { prisma: {} as never });

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

  it("rejects malformed review decision body", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/review/requests/request-1/decisions",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "reviewer-1",
        "x-user-role": "admin",
      },
      payload: {
        status: "pending",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(submitSpy).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only decision note", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/review/requests/request-1/decisions",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "reviewer-1",
        "x-user-role": "admin",
      },
      payload: {
        status: "approved",
        decisionNote: "   ",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(submitSpy).not.toHaveBeenCalled();
  });
});
