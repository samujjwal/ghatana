import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerABTestingRoutes } from "../routes";
import { ABTestingService } from "../service";

describe("registerABTestingRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const createSpy = vi.spyOn(
    ABTestingService.prototype,
    "createExperienceExperiment",
  );

  beforeEach(async () => {
    vi.clearAllMocks();

    createSpy.mockResolvedValue({ id: "exp-1" } as never);

    app = Fastify();
    app.addHook("preHandler", async (request) => {
      const mutableRequest = request as typeof request & {
        user?: { id: string; tenantId: string; role: string };
      };
      mutableRequest.user = {
        id: "user-1",
        tenantId: "tenant-1",
        role: "admin",
      };
    });
    registerABTestingRoutes(app, { prisma: {} as never });

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

  it("rejects malformed experiment creation payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/experiments/ab",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        experienceId: "",
        controlVersion: 0,
        treatmentVersion: 1,
      },
    });

    expect(response.statusCode).toBe(400);
    expect(createSpy).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only experiment id fields", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/experiments/ab",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        experienceId: "   ",
        controlVersion: 1,
        treatmentVersion: 2,
      },
    });

    expect(response.statusCode).toBe(400);
    expect(createSpy).not.toHaveBeenCalled();
  });
});
