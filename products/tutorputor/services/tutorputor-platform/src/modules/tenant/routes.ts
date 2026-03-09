import type { FastifyPluginAsync } from "fastify";
import { TenantId } from "@ghatana/tutorputor-contracts/v1";
import { getTenantId, requireRole, respondWithErrors } from "../../core/http/requestContext.js";
import { createTenantService, type DomainPack } from "./service.js";
import { randomUUID } from "node:crypto";

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
    await respondWithErrors(reply, () => tenantService.getTenantConfig(tenantId as TenantId));
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

    await respondWithErrors(reply, () => tenantService.updateTenantConfig(tenantId as TenantId, updates));
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
        domain, status, search,
        page: page ? Number(page) : 1,
        limit: limit ? Number(limit) : 20
      })
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
        description: body.metadata?.description,
        thumbnailUrl: body.metadata?.thumbnailUrl,
        author: body.metadata?.author,
        license: body.metadata?.license
      },
      status: body.status || "draft",
      visibility: body.visibility || "private",
      simulations: body.simulations || [],
      createdAt: new Date(),
      updatedAt: new Date()
    };

    await respondWithErrors(reply, () => tenantService.createDomainPack(pack));
  });

  /**
   * GET /domain-packs/:id
   */
  app.get("/domain-packs/:id", async (req, reply) => {
    const { id } = req.params as { id: string };
    const pack = await tenantService.getDomainPack(id);
    if (!pack) return reply.code(404).send({ error: "Domain Pack not found" });
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
    if (pack.tenantId !== tenantId) return reply.code(403).send({ error: "Forbidden" });

    await respondWithErrors(reply, () => tenantService.updateDomainPack(id, updates));
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
    if (pack.tenantId !== tenantId) return reply.code(403).send({ error: "Forbidden" });

    await tenantService.deleteDomainPack(id);
    return reply.send({ success: true });
  });
};
