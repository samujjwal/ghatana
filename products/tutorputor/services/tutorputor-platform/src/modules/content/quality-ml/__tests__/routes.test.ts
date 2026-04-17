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
