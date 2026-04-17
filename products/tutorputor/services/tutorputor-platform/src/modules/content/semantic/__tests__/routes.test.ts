import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@tutorputor/core/db", () => ({
  Prisma: {
    JsonNull: null,
  },
}));

import { registerSemanticRoutes } from "../routes";
import { SemanticChunkService } from "../chunk-service";
import { SemanticSearchService } from "../semantic-search-service";

describe("registerSemanticRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const pendingSpy = vi.spyOn(SemanticChunkService.prototype, "getPendingChunks");
  const searchSpy = vi.spyOn(SemanticSearchService.prototype, "search");

  beforeEach(async () => {
    vi.clearAllMocks();

    pendingSpy.mockResolvedValue([] as never);
    searchSpy.mockResolvedValue({ items: [] } as never);

    app = Fastify();
    registerSemanticRoutes(app, { prisma: {} as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed pending query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/semantic/pending?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(pendingSpy).not.toHaveBeenCalled();
  });

  it("rejects empty search query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/search?q=",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(searchSpy).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only search query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/search?q=%20",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(searchSpy).not.toHaveBeenCalled();
  });
});
