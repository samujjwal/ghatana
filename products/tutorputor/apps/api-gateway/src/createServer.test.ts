import { afterEach, describe, expect, it, vi } from "vitest";
import type { FastifyInstance } from "fastify";
import { createServer } from "./createServer";
import { setupPlatform } from "@ghatana/tutorputor-platform";

vi.mock("@ghatana/tutorputor-platform", () => ({
  setupPlatform: vi.fn(async () => undefined),
}));

const setupPlatformMock = vi.mocked(setupPlatform);

describe("createServer", () => {
  let app: FastifyInstance | null = null;

  afterEach(async () => {
    if (app) {
      await app.close();
      app = null;
    }
    setupPlatformMock.mockClear();
  });

  it("sets up platform and serves root route", async () => {
    app = await createServer();

    expect(setupPlatformMock).toHaveBeenCalledTimes(1);

    const response = await app.inject({
      method: "GET",
      url: "/",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      service: "TutorPutor API Gateway",
      architecture: "Consolidated Platform (v2)",
      status: "Operational",
    });
  });
});
