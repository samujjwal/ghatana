import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { registerContentStudioRoutes } from "../routes";

describe("registerContentStudioRoutes", () => {
  let app: ReturnType<typeof Fastify>;

  const contentStudioService = {
    listExperiences: vi.fn(),
    createExperience: vi.fn(),
    getExperience: vi.fn(),
    generateClaims: vi.fn(),
    getExperienceEvents: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();

    contentStudioService.listExperiences.mockResolvedValue({
      experiences: [],
      total: 0,
    });
    contentStudioService.createExperience.mockResolvedValue({ id: "exp-1" });
    contentStudioService.getExperience.mockResolvedValue({
      id: "exp-1",
      tenantId: "tenant-1",
    });
    contentStudioService.generateClaims.mockResolvedValue([]);
    contentStudioService.getExperienceEvents.mockResolvedValue([]);

    app = Fastify();
    registerContentStudioRoutes(app, {
      contentStudioService: contentStudioService as never,
    });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed experience list query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/content-studio/experiences?limit=0",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentStudioService.listExperiences).not.toHaveBeenCalled();
  });

  it("rejects malformed experience creation payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/content-studio/experiences",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
      },
      payload: {
        title: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentStudioService.createExperience).not.toHaveBeenCalled();
  });

  it("rejects malformed ai generate-claims payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/content-studio/ai/generate-claims",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
      payload: {
        experienceId: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentStudioService.generateClaims).not.toHaveBeenCalled();
  });

  it("rejects malformed experience events query", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/content-studio/experiences/exp-1/events?limit=-5",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "admin",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentStudioService.getExperienceEvents).not.toHaveBeenCalled();
  });

  it("rejects whitespace-only experience title", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/content-studio/experiences",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "user-1",
        "x-user-role": "admin",
      },
      payload: {
        title: "   ",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(contentStudioService.createExperience).not.toHaveBeenCalled();
  });
});
