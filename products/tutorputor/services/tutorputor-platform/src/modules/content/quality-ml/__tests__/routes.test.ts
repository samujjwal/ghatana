import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerQualityMLRoutes } from "../routes";
import { ContentQualityMLPipeline } from "../pipeline";

describe("registerQualityMLRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const predictBatchSpy = vi.spyOn(
    ContentQualityMLPipeline.prototype,
    "backfillPredictions",
  );

  beforeEach(async () => {
    vi.clearAllMocks();

    predictBatchSpy.mockResolvedValue([] as never);

    app = Fastify();
    registerQualityMLRoutes(app, { prisma: {} as never });

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

  it("rejects malformed predict-batch body", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/quality-ml/predict-batch",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        limit: -1,
      },
    });

    expect(response.statusCode).toBe(400);
    expect(predictBatchSpy).not.toHaveBeenCalled();
  });
});
