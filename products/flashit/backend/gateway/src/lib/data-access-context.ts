/**
 * FlashIt Data Access Context
 *
 * @doc.type contract
 * @doc.purpose Canonical tenant/principal/correlation/audit metadata for FlashIt persistence operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */

import { randomUUID } from "crypto";
import type { FastifyRequest } from "fastify";

export type FlashItAuditClassification =
  | "PERSONAL_MEMORY_READ"
  | "PERSONAL_MEMORY_WRITE"
  | "SPHERE_ACCESS_READ"
  | "SPHERE_ACCESS_WRITE"
  | "SEARCH_ACTIVITY_READ";

export interface FlashItDataAccessContext {
  readonly tenantId: string;
  readonly principalId: string;
  readonly correlationId: string;
  readonly auditClassification: FlashItAuditClassification;
  readonly dataOwnerScope: string;
  readonly idempotencyKey?: string;
}

export interface FlashItDataAccessOptions {
  readonly auditClassification: FlashItAuditClassification;
  readonly dataOwnerScope: string;
  readonly idempotencyKey?: string;
  readonly requireIdempotencyKey?: boolean;
}

export class FlashItDataAccessContextError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "FlashItDataAccessContextError";
  }
}

type HeaderValue = string | string[] | undefined;

interface AuthenticatedFastifyRequest extends FastifyRequest {
  readonly user: {
    readonly userId: string;
  };
}

const firstHeaderValue = (value: HeaderValue): string | undefined => {
  if (Array.isArray(value)) {
    return value[0];
  }
  return value;
};

const requireNonEmpty = (value: string | undefined, fieldName: string): string => {
  if (!value || value.trim().length === 0) {
    throw new FlashItDataAccessContextError(`${fieldName} is required for FlashIt data access`);
  }
  return value;
};

export const buildFlashItDataAccessContext = (
  request: FastifyRequest,
  options: FlashItDataAccessOptions,
): FlashItDataAccessContext => {
  const authenticatedRequest = request as AuthenticatedFastifyRequest;
  const principalId = requireNonEmpty(authenticatedRequest.user.userId, "principalId");
  const tenantId = firstHeaderValue(request.headers["x-tenant-id"]) ?? principalId;
  const correlationId = firstHeaderValue(request.headers["x-correlation-id"]) ?? randomUUID();
  const idempotencyKey =
    options.idempotencyKey ?? firstHeaderValue(request.headers["x-idempotency-key"]);

  if (options.requireIdempotencyKey) {
    requireNonEmpty(idempotencyKey, "idempotencyKey");
  }

  return {
    tenantId,
    principalId,
    correlationId,
    auditClassification: options.auditClassification,
    dataOwnerScope: options.dataOwnerScope,
    ...(idempotencyKey ? { idempotencyKey } : {}),
  };
};
