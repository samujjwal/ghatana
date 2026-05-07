import { afterEach, describe, expect, it } from "vitest";
import Fastify from "fastify";
import type { FastifyInstance } from "fastify";
import { register } from "prom-client";
import { recordTutorPutorDomainMetric, setupHealthChecks } from "./metrics.js";

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

describe("recordTutorPutorDomainMetric", () => {
  it("records TutorPutor-specific AI, LTI, telemetry, scoring, content, privacy, and simulation metrics", async () => {
    recordTutorPutorDomainMetric({
      type: "ai.request",
      useCase: "tutor",
      provider: "openai",
      model: "gpt-test",
      status: "failure",
      latencySeconds: 1.25,
      costUsd: 0.03,
      failureMode: "safety_filter",
    });
    recordTutorPutorDomainMetric({
      type: "simulation.error",
      domain: "PHYSICS",
      failureMode: "seed_mismatch",
    });
    recordTutorPutorDomainMetric({
      type: "telemetry.ingest.failure",
      eventType: "sim.capture",
      failureMode: "schema_validation",
    });
    recordTutorPutorDomainMetric({
      type: "assessment.scoring.failure",
      itemType: "simulation",
      policy: "cbm",
      failureMode: "missing_confidence",
    });
    recordTutorPutorDomainMetric({
      type: "lti.passback.failure",
      platform: "canvas",
      failureMode: "ags_rejected",
    });
    recordTutorPutorDomainMetric({
      type: "content_generation.validation.failure",
      domain: "science",
      artifactType: "assessment",
      failureMode: "incorrect_answer_key",
    });
    recordTutorPutorDomainMetric({
      type: "privacy.deletion.failure",
      operation: "delete",
      failureMode: "cascade_incomplete",
    });

    const metrics = await register.metrics();

    expect(metrics).toContain("tutorputor_ai_request_duration_seconds_bucket");
    expect(metrics).toContain("tutorputor_ai_token_cost_usd_total");
    expect(metrics).toContain("tutorputor_ai_failures_total");
    expect(metrics).toContain("tutorputor_simulation_runtime_errors_total");
    expect(metrics).toContain("tutorputor_telemetry_ingest_failures_total");
    expect(metrics).toContain("tutorputor_assessment_scoring_failures_total");
    expect(metrics).toContain("tutorputor_lti_passback_failures_total");
    expect(metrics).toContain("tutorputor_content_generation_validation_failures_total");
    expect(metrics).toContain("tutorputor_privacy_deletion_failures_total");
  });
});
