import { describe, expect, it } from "vitest";
import type { FastifyRequest } from "fastify";
import { createErrorEnvelope, getRequestTraceId } from "../error-envelope.js";

describe("canonical error envelope", () => {
  it("uses correlation/request id as traceId", () => {
    const request = {
      id: "request-id",
      headers: {
        "x-correlation-id": "correlation-id",
        "x-request-id": "header-request-id",
      },
    } as unknown as FastifyRequest;

    expect(getRequestTraceId(request)).toBe("correlation-id");
  });

  it("builds one stable API error shape", () => {
    const envelope = createErrorEnvelope(
      {
        code: "VALIDATION_ERROR",
        message: "Request validation failed",
        statusCode: 400,
        details: { field: "moduleId" },
        traceId: "trace-1",
      },
    );

    expect(envelope).toEqual({
      error: {
        code: "VALIDATION_ERROR",
        message: "Request validation failed",
        details: { field: "moduleId" },
      },
      traceId: "trace-1",
      timestamp: expect.any(String),
      statusCode: 400,
    });
  });

  it.each([
    ["UNAUTHORIZED", 401],
    ["FORBIDDEN", 403],
    ["NOT_FOUND", 404],
    ["AI_REQUEST_FAILED", 502],
    ["TELEMETRY_INGEST_FAILED", 422],
    ["LTI_PASSBACK_FAILED", 502],
  ])("supports %s as a canonical product error", (code, statusCode) => {
    const envelope = createErrorEnvelope({
      code,
      message: code,
      statusCode,
      traceId: "trace-product",
    });

    expect(envelope.error.code).toBe(code);
    expect(envelope.statusCode).toBe(statusCode);
    expect(envelope.traceId).toBe("trace-product");
  });
});
