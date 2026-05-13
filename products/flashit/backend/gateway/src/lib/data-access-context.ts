/**
 * FlashIt Data Access Context
 *
 * @doc.type contract
 * @doc.purpose Canonical tenant/principal/correlation/audit metadata for FlashIt persistence operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */

import { randomUUID } from "crypto";
import {
  DataAccessContextError,
  createDataAccessContext,
  resolveTenantForPrincipal,
  type DataAccessContext,
  type TenantResolver,
} from "@ghatana/data-access-context";
import type { FastifyRequest } from "fastify";

export type FlashItAuditClassification =
  | "PERSONAL_MEMORY_READ"
  | "PERSONAL_MEMORY_WRITE"
  | "SPHERE_ACCESS_READ"
  | "SPHERE_ACCESS_WRITE"
  | "SEARCH_ACTIVITY_READ";

export interface FlashItDataAccessContext extends DataAccessContext {
  readonly correlationId: string;
  readonly auditClassification: FlashItAuditClassification;
  readonly dataOwnerScope: string;
}

export interface FlashItDataAccessOptions {
  readonly auditClassification: FlashItAuditClassification;
  readonly dataOwnerScope: string;
  readonly idempotencyKey?: string;
  readonly requireIdempotencyKey?: boolean;
  readonly tenantResolver?: FlashItTenantResolver;
}

export class FlashItDataAccessContextError extends DataAccessContextError {
  constructor(message: string) {
    super(message);
    this.name = "FlashItDataAccessContextError";
  }
}

type HeaderValue = string | string[] | undefined;

type AuthenticatedFastifyRequest = FastifyRequest & {
  readonly user: {
    readonly userId: string;
    readonly email?: string;
    readonly role?: string;
  };
};

export type FlashItTenantResolver = TenantResolver;

const resolvePersonalTenant: FlashItTenantResolver = ({ principalId, requestedTenantId }) => {
  try {
    return resolveTenantForPrincipal({ principalId, requestedTenantId });
  } catch (error) {
    if (error instanceof DataAccessContextError) {
      throw new FlashItDataAccessContextError(
        "x-tenant-id is not authorized for the authenticated FlashIt principal",
      );
    }
    throw error;
  }
};

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
  try {
    const authenticatedRequest = request as AuthenticatedFastifyRequest;
    const principalId = requireNonEmpty(authenticatedRequest.user.userId, "principalId");
    const requestedTenantId = firstHeaderValue(request.headers["x-tenant-id"]);
    const tenantId = (options.tenantResolver ?? resolvePersonalTenant)({
      principalId,
      requestedTenantId,
    });
    const correlationId = firstHeaderValue(request.headers["x-correlation-id"]) ?? randomUUID();
    const idempotencyKey =
      options.idempotencyKey ?? firstHeaderValue(request.headers["x-idempotency-key"]);

    return createDataAccessContext(tenantId, principalId, {
      correlationId,
      auditClassification: options.auditClassification,
      dataOwnerScope: options.dataOwnerScope,
      idempotencyKey,
      requireCorrelationId: true,
      requireAuditClassification: true,
      requireDataOwnerScope: true,
      requireIdempotencyKey: options.requireIdempotencyKey,
      metadata: {
        product: "flashit",
      },
    }) as FlashItDataAccessContext;
  } catch (error) {
    if (error instanceof FlashItDataAccessContextError) {
      throw error;
    }
    if (error instanceof DataAccessContextError) {
      throw new FlashItDataAccessContextError(error.message);
    }
    throw error;
  }
};
