/**
 * Tenant Isolation Middleware for Fastify
 *
 * Extracts tenant identity from request headers and decorates the request
 * with tenant context for downstream route handlers.
 *
 * Header extraction priority:
 *   1. X-Tenant-ID header — explicit tenant scope
 *   2. JWT payload `tenantId` claim (if JWT auth is active)
 *   3. Falls back to "default-tenant" (configurable strict mode rejects instead)
 *
 * Usage:
 *   // Register as a plugin (applies to all routes)
 *   app.register(tenantIsolation, { strict: false });
 *
 *   // Or use as preHandler on specific routes
 *   { preHandler: [extractTenant] }
 *
 *   // Access in route handler
 *   const tenantId = request.tenantId; // string
 *   const principal = request.principal; // { name, roles, tenantId }
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import fp from 'fastify-plugin';

const TENANT_HEADER = 'x-tenant-id';
const PRINCIPAL_HEADER = 'x-principal';
const ROLES_HEADER = 'x-roles';

export interface TenantPrincipal {
  name: string;
  roles: string[];
  tenantId: string;
}

declare module 'fastify' {
  interface FastifyRequest {
    tenantId: string;
    principal: TenantPrincipal;
  }
}

export interface TenantIsolationOptions {
  /** If true, requests without X-Tenant-ID header receive 403. Default: false */
  strict?: boolean;
  /** Custom header name for tenant ID. Default: "x-tenant-id" */
  tenantHeader?: string;
}

/**
 * Extract tenant context from a single request.
 * Can be used as a standalone preHandler hook.
 */
export async function extractTenant(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  const tenantId =
    (request.headers[TENANT_HEADER] as string) ||
    (request.user as { tenantId?: string } | undefined)?.tenantId ||
    'default-tenant';

  const principalName =
    (request.headers[PRINCIPAL_HEADER] as string) ||
    (request.user as { sub?: string } | undefined)?.sub ||
    'anonymous';

  const rolesHeader = request.headers[ROLES_HEADER] as string | undefined;
  const roles = rolesHeader ? rolesHeader.split(',').map((r) => r.trim()) : [];

  request.tenantId = tenantId;
  request.principal = { name: principalName, roles, tenantId };
}

/**
 * Fastify plugin that applies tenant isolation to all requests.
 */
async function tenantIsolationPlugin(
  app: FastifyInstance,
  opts: TenantIsolationOptions
): Promise<void> {
  const strict = opts.strict ?? false;
  const headerName = opts.tenantHeader ?? TENANT_HEADER;

  // Decorate request with default values
  app.decorateRequest('tenantId', 'default-tenant');
  app.decorateRequest('principal', {
    name: 'anonymous',
    roles: [] as string[],
    tenantId: 'default-tenant',
  });

  app.addHook('onRequest', async (request, reply) => {
    const headerTenant = request.headers[headerName] as string | undefined;

    if (!headerTenant && strict) {
      return reply.code(403).send({
        error: {
          code: 'TENANT_REQUIRED',
          message: `Missing required ${headerName} header`,
        },
      });
    }

    const tenantId =
      headerTenant ||
      (request.user as { tenantId?: string } | undefined)?.tenantId ||
      'default-tenant';

    const principalName =
      (request.headers[PRINCIPAL_HEADER] as string) ||
      (request.user as { sub?: string } | undefined)?.sub ||
      'anonymous';

    const rolesHeader = request.headers[ROLES_HEADER] as string | undefined;
    const roles = rolesHeader
      ? rolesHeader.split(',').map((r) => r.trim())
      : [];

    request.tenantId = tenantId;
    request.principal = { name: principalName, roles, tenantId };
  });
}

export default fp(tenantIsolationPlugin, {
  name: 'tenant-isolation',
  fastify: '5.x',
});
