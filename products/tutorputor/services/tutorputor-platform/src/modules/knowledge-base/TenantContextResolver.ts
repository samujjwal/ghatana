/**
 * Tenant Context Resolver
 *
 * Resolves and enforces tenant context for knowledge-base routes.
 * Extracts tenant from JWT claims and ensures no default tenant fallback.
 *
 * @doc.type class
 * @doc.purpose Tenant context resolution for knowledge-base routes
 * @doc.layer product
 * @doc.pattern Middleware
 */

import type { FastifyRequest } from "fastify";
import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "TenantContextResolver" });

export interface TenantContext {
  tenantId: string;
  userId: string;
  role?: string | undefined;
}

export class TenantContextResolver {
  /**
   * Resolve tenant context from request
   *
   * @param request Fastify request
   * @returns Tenant context
   * @throws Error if tenant context cannot be resolved
   */
  static resolve(request: FastifyRequest): TenantContext {
    const user = (request as any).user as
      | { sub?: string; userId?: string; tenantId?: string; role?: string }
      | undefined;

    if (!user) {
      logger.error({
        message: "No user context in request",
        path: request.url,
      });
      throw new Error("Authentication required: no user context found");
    }

    const userId = user.sub ?? user.userId;
    const tenantId = user.tenantId;

    if (!tenantId) {
      logger.error({
        message: "No tenantId in user context",
        userId,
        path: request.url,
      });
      throw new Error(
        "Tenant context required: tenantId missing from authentication token"
      );
    }

    if (!userId) {
      logger.error({
        message: "No userId in user context",
        tenantId,
        path: request.url,
      });
      throw new Error("User context required: userId missing from authentication token");
    }

    logger.debug({
      message: "Tenant context resolved",
      tenantId,
      userId,
      role: user.role,
      path: request.url,
    });

    return {
      tenantId,
      userId,
      role: user.role,
    };
  }

  /**
   * Resolve tenantId from request (convenience method)
   *
   * @param request Fastify request
   * @returns tenantId
   * @throws Error if tenantId cannot be resolved
   */
  static resolveTenantId(request: FastifyRequest): string {
    const context = TenantContextResolver.resolve(request);
    return context.tenantId;
  }

  /**
   * Create Fastify pre-handler for tenant context resolution
   *
   * @returns Fastify pre-handler function
   */
  static preHandler() {
    return async (request: FastifyRequest, _reply: any) => {
      try {
        const context = TenantContextResolver.resolve(request);
        // Attach tenant context to request for downstream handlers
        (request as any).tenantContext = context;
      } catch (error) {
        logger.error({
          message: "Tenant context resolution failed",
          error: error instanceof Error ? error.message : String(error),
          path: request.url,
        });
        throw error;
      }
    };
  }

  /**
   * Verify tenant matches expected tenant (for cross-tenant operations)
   *
   * @param request Fastify request
   * @param expectedTenantId Expected tenant ID
   * @throws Error if tenant doesn't match
   */
  static verifyTenant(request: FastifyRequest, expectedTenantId: string): void {
    const context = TenantContextResolver.resolve(request);
    
    if (context.tenantId !== expectedTenantId) {
      logger.error({
        message: "Tenant mismatch",
        requestTenantId: context.tenantId,
        expectedTenantId,
        userId: context.userId,
        path: request.url,
      });
      throw new Error(
        `Tenant mismatch: request tenant ${context.tenantId} does not match expected tenant ${expectedTenantId}`
      );
    }
  }
}

/**
 * Helper to get tenant context from request (must be attached by preHandler)
 *
 * @param request Fastify request
 * @returns Tenant context
 * @throws Error if tenant context not attached
 */
export function getTenantContext(request: FastifyRequest): TenantContext {
  const context = (request as any).tenantContext as TenantContext | undefined;
  
  if (!context) {
    throw new Error(
      "Tenant context not available. Ensure TenantContextResolver.preHandler() is registered on the route."
    );
  }
  
  return context;
}
