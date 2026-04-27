import type { FastifyPluginAsync } from "fastify";
import { TenantId } from "@tutorputor/contracts/v1";
import {
  getTenantId,
  getUserId,
  requireRole,
  requireTenantAccess,
  respondWithErrors,
} from "../../core/http/requestContext.js";
import { buildSensitiveOperationAuditEntry } from "../policy/resource-access-helpers.js";
import type {
  IdentityProviderType,
  RoleMappingConfig,
} from "@tutorputor/contracts/v1/types";
import {
  createTenantService,
  SIMULATION_DOMAINS,
  type DomainPack,
  type PackStatus,
  type SimulationDomain,
  type TenantSettings,
} from "./service.js";
import { randomUUID } from "node:crypto";
import { z } from "zod";

const DOMAIN_PACK_STATUSES = [
  "draft",
  "active",
  "deprecated",
  "archived",
] as const;

function isSimulationDomain(value: string): value is SimulationDomain {
  return SIMULATION_DOMAINS.includes(value as SimulationDomain);
}

function isPackStatus(value: string): value is PackStatus {
  return DOMAIN_PACK_STATUSES.includes(value as PackStatus);
}

/** Query parameters for domain pack listing */
interface DomainPackListQuery {
  domain?: string;
  status?: string;
  search?: string;
  page?: string | number;
  limit?: string | number;
}

/** Request body for updating tenant configuration */
type TenantConfigUpdate = Partial<TenantSettings>;

const domainPackListQuerySchema = z.object({
  domain: z.string().min(1).optional(),
  status: z.string().min(1).optional(),
  search: z.string().optional(),
  page: z.coerce.number().int().positive().max(500).optional(),
  limit: z.coerce.number().int().positive().max(100).optional(),
});

const createDomainPackSchema = z.object({
  metadata: z
    .object({
      name: z.string().min(1).optional(),
      version: z.string().min(1).optional(),
      domain: z.string().min(1).optional(),
      tags: z.array(z.string().min(1)).optional(),
      description: z.string().optional(),
      thumbnailUrl: z.string().url().optional(),
      author: z.string().optional(),
      license: z.string().optional(),
    })
    .optional(),
  status: z.string().optional(),
  visibility: z.string().optional(),
  simulations: z.array(z.unknown()).optional(),
});

const idParamSchema = z.object({
  id: z.string().min(1),
});

/** Request body for SSO provider creation */
interface SSOProviderCreateRequest {
  type?: IdentityProviderType;
  displayName: string;
  discoveryEndpoint: string;
  clientId: string;
  clientSecret?: string | null;
  enabled?: boolean;
  roleMapping?: RoleMappingConfig;
  allowedDomains?: string[];
}

/** Request body for SSO provider update */
interface SSOProviderUpdateRequest {
  displayName?: string;
  discoveryEndpoint?: string;
  clientId?: string;
  clientSecret?: string | null;
  enabled?: boolean;
  roleMapping?: RoleMappingConfig;
  allowedDomains?: string[];
}

/** Mask the clientSecret field so it never leaks in API responses. */
function maskSecret(provider: Record<string, unknown>) {
  const { clientSecret: _secret, ...safe } = provider;
  return {
    ...safe,
    hasClientSecret: _secret != null,
  };
}

/**
 * Tenant configuration routes - multi-tenancy settings.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for tenant configuration
 * @doc.layer product
 * @doc.pattern REST API
 */
export const tenantRoutes: FastifyPluginAsync<{
  service?: ReturnType<typeof createTenantService>;
}> = async (app, options) => {
  const prisma = app.prisma;
  const tenantService = options.service ?? createTenantService(prisma);

  // ===========================================================================
  // Tenant Configuration
  // ===========================================================================

  /**
   * GET /config
   * Get tenant configuration for the current tenant.
   * Accessible by authenticated users to check enabled features.
   */
  app.get("/config", async (req, reply) => {
    const tenantId = getTenantId(req);
    // Logic: If user is authenticated, they can see config.
    // Ideally we might filter sensitive admin settings for non-admins.
    // keeping it simple for now.
    await respondWithErrors(reply, () =>
      tenantService.getTenantConfig(tenantId as TenantId),
    );
  });

  /**
   * PATCH /config
   * Update tenant configuration.
   * Admin only.
   */
  app.patch("/config", async (req, reply) => {
    const tenantId = getTenantId(req);
    const actorId = getUserId(req);
    requireRole(req, ["admin", "superadmin"]);
    const updatesResult = z.record(z.string(), z.unknown()).safeParse(req.body);
    if (!updatesResult.success) {
      return reply.code(400).send({
        error: "Invalid tenant config payload",
        issues: updatesResult.error.issues,
      });
    }
    const updates = updatesResult.data as TenantConfigUpdate;

    await respondWithErrors(reply, () =>
      tenantService.updateTenantConfig(tenantId as TenantId, updates),
    );
    const audit = buildSensitiveOperationAuditEntry({
      actorId,
      actorTenantId: tenantId,
      targetResourceType: "tenant_config",
      targetResourceId: tenantId,
      operation: "update_tenant_config",
      decision: "ALLOW",
      reason: "Tenant configuration updated by admin",
      correlationId: req.id,
      metadata: { changedKeys: Object.keys(updates).join(",") },
    });
    app.log.info({ audit }, "Sensitive operation allowed");
  });

  // ===========================================================================
  // Domain Packs
  // ===========================================================================

  /**
   * GET /domain-packs
   * List available domain packs.
   */
  app.get("/domain-packs", async (req, reply) => {
    const tenantId = getTenantId(req);
    const queryResult = domainPackListQuerySchema.safeParse(req.query ?? {});
    if (!queryResult.success) {
      return reply.code(400).send({
        error: "Invalid domain pack query",
        issues: queryResult.error.issues,
      });
    }
    const { domain, status, search, page, limit } = queryResult.data;
    const normalizedDomain =
      domain && isSimulationDomain(domain) ? domain : undefined;
    const normalizedStatus =
      status && isPackStatus(status) ? status : undefined;

    await respondWithErrors(reply, () =>
      tenantService.listDomainPacks(tenantId, {
        page: page ?? 1,
        limit: limit ?? 20,
        ...(normalizedDomain ? { domain: normalizedDomain } : {}),
        ...(normalizedStatus ? { status: normalizedStatus } : {}),
        ...(search ? { search } : {}),
      }),
    );
  });

  /**
   * POST /domain-packs
   * Create a new domain pack.
   */
  app.post("/domain-packs", async (req, reply) => {
    const tenantId = getTenantId(req);
    requireRole(req, ["admin", "content_creator"]);
    const bodyResult = createDomainPackSchema.safeParse(req.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid domain pack payload",
        issues: bodyResult.error.issues,
      });
    }
    const body = bodyResult.data as Partial<DomainPack>;

    const pack: DomainPack = {
      id: randomUUID(),
      tenantId,
      metadata: {
        name: body.metadata?.name || "Untitled Pack",
        version: body.metadata?.version || "1.0.0",
        domain: body.metadata?.domain || "CS_DISCRETE",
        tags: body.metadata?.tags || [],
        ...(body.metadata?.description
          ? { description: body.metadata.description }
          : {}),
        ...(body.metadata?.thumbnailUrl
          ? { thumbnailUrl: body.metadata.thumbnailUrl }
          : {}),
        ...(body.metadata?.author ? { author: body.metadata.author } : {}),
        ...(body.metadata?.license ? { license: body.metadata.license } : {}),
      },
      status: body.status || "draft",
      visibility: body.visibility || "private",
      simulations: body.simulations || [],
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    await respondWithErrors(reply, () => tenantService.createDomainPack(pack));
  });

  /**
   * GET /domain-packs/:id
   */
  app.get("/domain-packs/:id", async (req, reply) => {
    const tenantId = getTenantId(req);
    const paramsResult = idParamSchema.safeParse(req.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid domain pack id",
        issues: paramsResult.error.issues,
      });
    }
    const { id } = paramsResult.data;
    const pack = await tenantService.getDomainPack(id);
    if (!pack) return reply.code(404).send({ error: "Domain Pack not found" });

    if (pack.visibility !== "public") {
      requireTenantAccess(
        pack.tenantId,
        tenantId,
        "Domain Pack is not accessible for the current tenant",
      );
    }

    return reply.send(pack);
  });

  /**
   * PATCH /domain-packs/:id
   */
  app.patch("/domain-packs/:id", async (req, reply) => {
    const tenantId = getTenantId(req);
    requireRole(req, ["admin", "content_creator"]);
    const paramsResult = idParamSchema.safeParse(req.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid domain pack id",
        issues: paramsResult.error.issues,
      });
    }
    const { id } = paramsResult.data;
    const updates = req.body as Partial<DomainPack>;

    const pack = await tenantService.getDomainPack(id);
    if (!pack) return reply.code(404).send({ error: "Domain Pack not found" });
    if (pack.tenantId !== tenantId)
      return reply.code(403).send({ error: "Forbidden" });

    await respondWithErrors(reply, () =>
      tenantService.updateDomainPack(id, updates),
    );
  });

  /**
   * DELETE /domain-packs/:id
   */
  app.delete("/domain-packs/:id", async (req, reply) => {
    const tenantId = getTenantId(req);
    const actorId = getUserId(req);
    requireRole(req, ["admin"]);
    const paramsResult = idParamSchema.safeParse(req.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid domain pack id",
        issues: paramsResult.error.issues,
      });
    }
    const { id } = paramsResult.data;

    const pack = await tenantService.getDomainPack(id);
    if (!pack) return reply.code(404).send({ error: "Domain Pack not found" });
    if (pack.tenantId !== tenantId)
      return reply.code(403).send({ error: "Forbidden" });

    await tenantService.deleteDomainPack(id);
    const audit = buildSensitiveOperationAuditEntry({
      actorId,
      actorTenantId: tenantId,
      targetResourceType: "domain_pack",
      targetResourceId: id,
      operation: "delete_domain_pack",
      decision: "ALLOW",
      reason: "Domain pack deleted by admin",
      correlationId: req.id,
      metadata: { packId: id },
    });
    app.log.info({ audit }, "Sensitive operation allowed");
    return reply.send({ success: true });
  });

  // ===========================================================================
  // SSO Identity Providers
  // ===========================================================================

  /**
   * GET /sso-providers
   * List all SSO providers for the current tenant. Admin only.
   * Masks client_secret in the response.
   */
  app.get("/sso-providers", async (req, reply) => {
    const tenantId = getTenantId(req);
    requireRole(req, ["admin", "superadmin"]);

    await respondWithErrors(reply, async () => {
      const providers = await prisma.identityProvider.findMany({
        where: { tenantId },
        orderBy: { createdAt: "asc" },
      });
      return providers.map(maskSecret);
    });
  });

  /**
   * POST /sso-providers
   * Create a new SSO provider. Admin only.
   */
  app.post("/sso-providers", async (req, reply) => {
    const tenantId = getTenantId(req);
    const actorId = getUserId(req);
    requireRole(req, ["admin", "superadmin"]);
    const body = req.body as SSOProviderCreateRequest;

    await respondWithErrors(reply, async () => {
      const provider = await prisma.identityProvider.create({
        data: {
          tenantId,
          type: body.type ?? "oidc",
          displayName: body.displayName,
          discoveryEndpoint: body.discoveryEndpoint,
          clientId: body.clientId,
          clientSecret: body.clientSecret ?? null,
          allowedDomains: body.allowedDomains
            ? JSON.stringify(body.allowedDomains)
            : "[]",
          enabled: body.enabled ?? false,
          status: "pending_verification",
          roleMapping: body.roleMapping
            ? JSON.stringify(body.roleMapping)
            : null,
        },
      });
      const audit = buildSensitiveOperationAuditEntry({
        actorId,
        actorTenantId: tenantId,
        targetResourceType: "sso_provider",
        targetResourceId: provider.id,
        operation: "create_sso_provider",
        decision: "ALLOW",
        reason: "SSO provider created by admin",
        correlationId: req.id,
        metadata: { displayName: body.displayName, type: body.type ?? "oidc" },
      });
      app.log.info({ audit }, "Sensitive operation allowed");
      reply.code(201);
      return maskSecret(provider);
    });
  });

  /**
   * GET /sso-providers/:providerId
   * Get a single SSO provider. Admin only.
   */
  app.get("/sso-providers/:providerId", async (req, reply) => {
    const tenantId = getTenantId(req);
    requireRole(req, ["admin", "superadmin"]);
    const { providerId } = req.params as { providerId: string };

    const provider = await prisma.identityProvider.findFirst({
      where: { id: providerId, tenantId },
    });
    if (!provider)
      return reply.code(404).send({ error: "SSO provider not found" });
    return reply.send(maskSecret(provider));
  });

  /**
   * PATCH /sso-providers/:providerId
   * Update SSO provider config. Admin only.
   */
  app.patch("/sso-providers/:providerId", async (req, reply) => {
    const tenantId = getTenantId(req);
    requireRole(req, ["admin", "superadmin"]);
    const { providerId } = req.params as { providerId: string };
    const body = req.body as SSOProviderUpdateRequest;

    await respondWithErrors(reply, async () => {
      const existing = await prisma.identityProvider.findFirst({
        where: { id: providerId, tenantId },
      });
      if (!existing)
        throw Object.assign(new Error("SSO provider not found"), {
          statusCode: 404,
        });

      const updated = await prisma.identityProvider.update({
        where: { id: providerId },
        data: {
          ...(body.displayName !== undefined && {
            displayName: body.displayName,
          }),
          ...(body.discoveryEndpoint !== undefined && {
            discoveryEndpoint: body.discoveryEndpoint,
            status: "pending_verification",
          }),
          ...(body.clientId !== undefined && { clientId: body.clientId }),
          ...(body.clientSecret !== undefined && {
            clientSecret: body.clientSecret,
          }),
          ...(body.allowedDomains !== undefined && {
            allowedDomains: JSON.stringify(body.allowedDomains),
          }),
          ...(body.enabled !== undefined && { enabled: body.enabled }),
          ...(body.roleMapping !== undefined && {
            roleMapping: JSON.stringify(body.roleMapping),
          }),
        },
      });
      return maskSecret(updated);
    });
  });

  /**
   * DELETE /sso-providers/:providerId
   * Remove an SSO provider. Admin only.
   */
  app.delete("/sso-providers/:providerId", async (req, reply) => {
    const tenantId = getTenantId(req);
    const actorId = getUserId(req);
    requireRole(req, ["admin", "superadmin"]);
    const { providerId } = req.params as { providerId: string };

    await respondWithErrors(reply, async () => {
      const existing = await prisma.identityProvider.findFirst({
        where: { id: providerId, tenantId },
      });
      if (!existing)
        throw Object.assign(new Error("SSO provider not found"), {
          statusCode: 404,
        });

      await prisma.identityProvider.delete({ where: { id: providerId } });
      const audit = buildSensitiveOperationAuditEntry({
        actorId,
        actorTenantId: tenantId,
        targetResourceType: "sso_provider",
        targetResourceId: providerId,
        operation: "delete_sso_provider",
        decision: "ALLOW",
        reason: "SSO provider deleted by admin",
        correlationId: req.id,
        metadata: { providerId },
      });
      app.log.info({ audit }, "Sensitive operation allowed");
      return { success: true };
    });
  });
};
