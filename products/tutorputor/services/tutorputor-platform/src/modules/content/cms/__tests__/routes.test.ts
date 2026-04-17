import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { registerCMSRoutes } from "../routes";

describe("registerCMSRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const cmsService = {
    listModules: vi.fn(),
    createModuleDraft: vi.fn(),
    updateModuleDraft: vi.fn(),
    publishModule: vi.fn(),
    generateDraftFromIntent: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();

    cmsService.listModules.mockResolvedValue({ items: [] });

    app = Fastify();
    registerCMSRoutes(app, { cmsService: cmsService as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed modules list query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/v1/cms/modules?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(cmsService.listModules).not.toHaveBeenCalled();
  });

  it("rejects malformed generate draft payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/v1/cms/modules/generate",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "teacher",
      },
      payload: {
        intent: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(cmsService.generateDraftFromIntent).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only intent payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/v1/cms/modules/generate",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "teacher",
      },
      payload: {
        intent: "   ",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(cmsService.generateDraftFromIntent).not.toHaveBeenCalled();
  });
});
