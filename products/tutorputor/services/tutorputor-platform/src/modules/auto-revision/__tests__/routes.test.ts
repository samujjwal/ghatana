import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { registerAutoRevisionRoutes } from "../routes";

describe("auto-revision routes", () => {
  const service = {
    detectDrift: vi.fn(),
    monitorDrift: vi.fn(),
    queueExperienceRegeneration: vi.fn(),
    processRegenerationQueue: vi.fn(),
    createABExperiment: vi.fn(),
    evaluateABExperiments: vi.fn(),
    getRegenerationHistory: vi.fn(),
    getABExperimentResults: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    service.createABExperiment.mockResolvedValue({ id: "ab-1" });

    app = Fastify();
    registerAutoRevisionRoutes(app, service as never);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects malformed A/B experiment payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/experiences/exp-1/ab-experiment",
      headers: {
        "x-user-role": "admin",
        "x-tenant-id": "tenant-1",
      },
      payload: {
        treatmentVersion: 0,
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.createABExperiment).not.toHaveBeenCalled();
  });

  it("requires role for drift endpoint", async () => {
    const response = await app.inject({
      method: "GET",
      url: "/experiences/exp-1/drift",
      headers: {
        "x-tenant-id": "tenant-1",
      },
    });

    expect(response.statusCode).toBe(403);
  });

  it("accepts valid A/B experiment payload", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/experiences/exp-1/ab-experiment",
      headers: {
        "x-user-role": "admin",
        "x-tenant-id": "tenant-1",
      },
      payload: {
        treatmentVersion: 2,
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.createABExperiment).toHaveBeenCalledWith("exp-1", 2);
  });
});
