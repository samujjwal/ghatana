import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { registerContentRoutes } from "../routes";

describe("registerContentRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const contentService = {
    listModules: vi.fn(),
    getModuleBySlug: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();

    contentService.listModules.mockResolvedValue({ items: [] });
    contentService.getModuleBySlug.mockResolvedValue({
      module: { slug: "intro" },
      enrollment: null,
    });

    app = Fastify();
    await registerContentRoutes(app, {
      contentService: contentService as never,
    });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed module list query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/v1/modules?domain=%20",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentService.listModules).not.toHaveBeenCalled();
  });

  it("rejects malformed module slug param", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/v1/modules/%20",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentService.getModuleBySlug).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only cursor query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/v1/modules?cursor=%20",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentService.listModules).not.toHaveBeenCalled();
  });
});
