import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerContentAssetRoutes } from "../routes";
import { ContentAssetReadService } from "../read-service";

describe("registerContentAssetRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const listAssetsSpy = vi.spyOn(ContentAssetReadService.prototype, "listAssets");
  const relatedSpy = vi.spyOn(
    ContentAssetReadService.prototype,
    "getRelatedAssets",
  );

  beforeEach(async () => {
    vi.clearAllMocks();

    listAssetsSpy.mockResolvedValue({ assets: [], total: 0 } as never);
    relatedSpy.mockResolvedValue([] as never);

    app = Fastify();
    registerContentAssetRoutes(app, { prisma: {} as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed list query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/assets?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(listAssetsSpy).not.toHaveBeenCalled();
  });

  it("rejects malformed related query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/assets/asset-1/related?limit=-1",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(relatedSpy).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only search query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/assets?search=%20",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(listAssetsSpy).not.toHaveBeenCalled();
  });
});
