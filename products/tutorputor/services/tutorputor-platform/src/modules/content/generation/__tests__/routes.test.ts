import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerGenerationRoutes } from "../routes";
import { GenerationPlannerService } from "../planner-service";

describe("registerGenerationRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const createRequestSpy = vi.spyOn(
    GenerationPlannerService.prototype,
    "createRequest",
  );

  beforeEach(async () => {
    vi.clearAllMocks();

    createRequestSpy.mockResolvedValue({ id: "req-1" } as never);

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

    registerGenerationRoutes(app, { prisma: {} as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed create request payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/generation/requests",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
      },
      payload: {
        title: "",
        domain: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(createRequestSpy).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only title and domain", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/generation/requests",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
      },
      payload: {
        title: "   ",
        domain: "   ",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(createRequestSpy).not.toHaveBeenCalled();
  });
});
