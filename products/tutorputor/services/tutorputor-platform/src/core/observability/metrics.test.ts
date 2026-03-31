import { afterEach, describe, expect, it } from "vitest";
import Fastify from "fastify";
import type { FastifyInstance } from "fastify";
import { setupHealthChecks } from "./metrics.js";

describe("setupHealthChecks", () => {
  let app: FastifyInstance | null = null;

  afterEach(async () => {
    if (app) {
      await app.close();
      app = null;
    }
  });

  it("marks readiness as failed when learner-profile grpc is enabled but not running", async () => {
    app = Fastify();
    app.decorate("learnerProfileGrpcRuntimeState", {
      enabled: true,
      status: "starting",
      address: "127.0.0.1:50052",
    });

    await setupHealthChecks(
      app,
      {
        $queryRaw: async () => [{ ok: 1 }],
      } as never,
      {
        ping: async () => "PONG",
      } as never,
    );
    await app.ready();

    const response = await app.inject({
      method: "GET",
      url: "/health/ready",
    });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toMatchObject({
      status: "not-ready",
      checks: {
        learnerProfileGrpc: {
          status: "starting",
        },
      },
    });
  });

  it("reports learner-profile grpc state in deep health output", async () => {
    app = Fastify();
    app.decorate("learnerProfileGrpcRuntimeState", {
      enabled: true,
      status: "running",
      address: "127.0.0.1:50052",
      port: 50052,
    });

    await setupHealthChecks(
      app,
      {
        $queryRaw: async () => [{ ok: 1 }],
      } as never,
      {
        ping: async () => "PONG",
      } as never,
    );
    await app.ready();

    const response = await app.inject({
      method: "GET",
      url: "/health",
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      status: "healthy",
      checks: {
        learnerProfileGrpc: {
          status: "ok",
          mode: "running",
          port: 50052,
        },
      },
    });
  });
});
