import "fastify";
import type { Span } from "@opentelemetry/api";
import type { UserRole } from "@ghatana/tutorputor-contracts/v1/types";

declare module "fastify" {
  interface FastifySchema {
    description?: string;
    tags?: string[];
  }

  interface FastifyRequest {
    user?: {
      id: string;
      email?: string;
      displayName?: string;
      role?: UserRole;
    };
    tenantId?: string;
    correlationId?: string;
    metricsStartTime?: bigint;
    otelSpan?: Span;
  }
}

