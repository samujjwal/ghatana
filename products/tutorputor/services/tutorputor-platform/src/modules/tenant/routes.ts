import type { FastifyPluginAsync } from "fastify";
import { TenantId } from "@tutorputor/contracts/v1";
import {
  getTenantId,
  requireRole,
  requireTenantAccess,
  respondWithErrors,
} from "../../core/http/requestContext.js";
import { createTenantService, type DomainPack } from "./service.js";
import { randomUUID } from "node:crypto";

/** Mask the clientSecret field so it never leaks in API responses. */
function maskSecret(provider: any) {
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
export const tenantRoutes: FastifyPluginAsync = async (app) => {
  const prisma = app.prisma as any;
  const tenantService = createTenantService(prisma);

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
    requireRole(req, ["admin", "superadmin"]);
    const updates = req.body as any; // Validation ideally via Zod body schema

    await respondWithErrors(reply, () =>
      tenantService.updateTenantConfig(tenantId as TenantId, updates),
    );
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
    const { domain, status, search, page, limit } = (req.query ?? {}) as any;

    await respondWithErrors(reply, () =>
      tenantService.listDomainPacks(tenantId, {
        domain,
        status,
        search,
        page: page ? Number(page) : 1,
        limit: limit ? Number(limit) : 20,
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
    const body = req.body as Partial<DomainPack>;

    const pack: DomainPack = {
      id: randomUUID(),
      tenantId,
      metadata: {
        name: body.metadata?.name || "Untitled Pack",
        version: body.metadata?.version || "1.0.0",
        domain: body.metadata?.domain || "CS_DISCRETE",
        tags: body.metadata?.tags || [],
        ...(body.metadata?.description ? { description: body.metadata.description } : {}),
        ...(body.metadata?.thumbnailUrl ? { thumbnailUrl: body.metadata.thumbnailUrl } : {}),
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
    const { id } = req.params as { id: string };
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
    const { id } = req.params as { id: string };
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
    requireRole(req, ["admin"]);
    const { id } = req.params as { id: string };

    const pack = await tenantService.getDomainPack(id);
    if (!pack) return reply.code(404).send({ error: "Domain Pack not found" });
    if (pack.tenantId !== tenantId)
      return reply.code(403).send({ error: "Forbidden" });

    await tenantService.deleteDomainPack(id);
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
    requireRole(req, ["admin", "superadmin"]);
    const body = req.body as any;

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
    const body = req.body as any;

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
      return { success: true };
    });
  });
};
