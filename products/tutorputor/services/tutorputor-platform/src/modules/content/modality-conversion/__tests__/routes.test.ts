import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerModalityConversionRoutes } from "../routes";
import { ModalityConversionService } from "../service";

describe("registerModalityConversionRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const convertSpy = vi.spyOn(ModalityConversionService.prototype, "convertAsset");

  beforeEach(async () => {
    vi.clearAllMocks();

    convertSpy.mockResolvedValue({ status: "ok" } as never);

    app = Fastify();
    registerModalityConversionRoutes(app, { prisma: {} as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed conversion body", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/modality/assets/asset-1/convert",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
      payload: {
        targetModality: "video",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(convertSpy).not.toHaveBeenCalled();
  });
});
