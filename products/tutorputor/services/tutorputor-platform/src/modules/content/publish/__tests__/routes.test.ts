import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerPublishRoutes } from "../routes";
import { PublishService } from "../publish-service";

describe("registerPublishRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const publishAssetSpy = vi.spyOn(PublishService.prototype, "publishAsset");
  const publishAllSpy = vi.spyOn(
    PublishService.prototype,
    "publishByGenerationRequest",
  );

  beforeEach(async () => {
    vi.clearAllMocks();

    publishAssetSpy.mockResolvedValue({ published: true } as never);
    publishAllSpy.mockResolvedValue({ publishedCount: 1 } as never);

    app = Fastify();
    registerPublishRoutes(app, { prisma: {} as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed publish payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/publish/assets/asset-1",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
      },
      payload: {
        bypassEvaluationCheck: "yes",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(publishAssetSpy).not.toHaveBeenCalled();
  });

  it("rejects blank request id path", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/publish/requests/%20/publish-all",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(publishAllSpy).not.toHaveBeenCalled();
  });

  it("rejects malformed publish body type", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/publish/assets/asset-2",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
      },
      payload: {
        bypassEvaluationCheck: 1,
      },
    });

    expect(response.statusCode).toBe(400);
    expect(publishAssetSpy).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only asset id path", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/publish/assets/%20",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
      },
      payload: {},
    });

    expect(response.statusCode).toBe(400);
    expect(publishAssetSpy).not.toHaveBeenCalled();
  });
});
