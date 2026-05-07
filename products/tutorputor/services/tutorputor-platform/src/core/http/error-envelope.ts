import type { FastifyRequest } from "fastify";

export interface CanonicalErrorEnvelope {
  error: {
    code: string;
    message: string;
    details?: unknown;
  };
  traceId: string;
  timestamp: string;
  statusCode: number;
}

export interface ErrorEnvelopeInput {
  code: string;
  message: string;
  statusCode: number;
  details?: unknown;
  traceId?: string;
}

export function getRequestTraceId(request?: FastifyRequest): string {
  const headerTraceId =
    request?.headers["x-correlation-id"] ??
    request?.headers["x-request-id"] ??
    request?.id;
  if (Array.isArray(headerTraceId)) {
    return headerTraceId[0] ?? "trace-unavailable";
  }
  return typeof headerTraceId === "string" && headerTraceId.length > 0
    ? headerTraceId
    : `trace_${Date.now().toString(36)}`;
}

export function createErrorEnvelope(
  input: ErrorEnvelopeInput,
  request?: FastifyRequest,
): CanonicalErrorEnvelope {
  return {
    error: {
      code: input.code,
      message: input.message,
      ...(input.details !== undefined ? { details: input.details } : {}),
    },
    traceId: input.traceId ?? getRequestTraceId(request),
    timestamp: new Date().toISOString(),
    statusCode: input.statusCode,
  };
}
