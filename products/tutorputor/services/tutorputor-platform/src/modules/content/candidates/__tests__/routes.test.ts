import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerCandidateRoutes } from "../routes";
import { RegenerationCandidateService } from "../candidate-service";

describe("registerCandidateRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const createSpy = vi.spyOn(
    RegenerationCandidateService.prototype,
    "createCandidate",
  );
  const queueSpy = vi.spyOn(
    RegenerationCandidateService.prototype,
    "queueCandidate",
  );

  beforeEach(async () => {
    vi.clearAllMocks();

    createSpy.mockResolvedValue({ id: "candidate-1" } as never);
    queueSpy.mockResolvedValue({ id: "candidate-1" } as never);

    app = Fastify();
    registerCandidateRoutes(app, { prisma: {} as never });

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

  it("rejects malformed candidate payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/candidates",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        assetId: "asset-1",
        trigger: "",
        reason: "needs refresh",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(createSpy).not.toHaveBeenCalled();
  });

  it("rejects malformed queue payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/candidates/candidate-1/queue",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        generationRequestId: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(queueSpy).not.toHaveBeenCalled();
  });

  it("rejects malformed candidate priority", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/candidates",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        assetId: "asset-1",
        trigger: "poor_engagement",
        reason: "insufficient signal",
        priority: 0,
      },
    });

    expect(response.statusCode).toBe(400);
    expect(createSpy).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only candidate reason", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/candidates",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        assetId: "asset-1",
        trigger: "poor_engagement",
        reason: "   ",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(createSpy).not.toHaveBeenCalled();
  });
});
