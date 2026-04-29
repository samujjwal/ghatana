/**
 * Data Residency Module
 *
 * Enforce tenant data residency policies in runtime routing and storage.
 *
 * @doc.type module
 * @doc.purpose Data residency module for multi-region compliance
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import { DataResidencyService, type TenantResidencyConfig } from "./DataResidencyService.js";
import { z } from "zod";

const setResidencyConfigSchema = z.object({
  primaryRegion: z.enum(["US", "EU", "APAC", "GLOBAL"]).optional(),
  allowedRegions: z.array(z.enum(["US", "EU", "APAC", "GLOBAL"])).optional(),
  dataRetentionDays: z.number().int().min(1).optional(),
  crossBorderTransferAllowed: z.boolean().optional(),
  complianceFrameworks: z.array(z.string()).optional(),
});

const validateAccessSchema = z.object({
  requestRegion: z.enum(["US", "EU", "APAC", "GLOBAL"]),
  operation: z.enum(["read", "write", "delete"]),
});

export const dataResidencyModule: FastifyPluginAsync = async (app) => {
  const residencyService = new DataResidencyService(app.prisma as any);
  app.decorate("dataResidencyService", residencyService);

  // GET /api/v1/data-residency/config - Get tenant residency configuration
  app.get("/api/v1/data-residency/config", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const config = await residencyService.getTenantResidencyConfig(tenantId);

    return reply.send(config);
  });

  // PUT /api/v1/data-residency/config - Set tenant residency configuration
  app.put("/api/v1/data-residency/config", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = setResidencyConfigSchema.parse(request.body);
    const configToSet: Partial<TenantResidencyConfig> = { tenantId };
    if (body.primaryRegion !== undefined) {
      configToSet.primaryRegion = body.primaryRegion;
    }
    if (body.allowedRegions !== undefined) {
      configToSet.allowedRegions = body.allowedRegions;
    }
    if (body.dataRetentionDays !== undefined) {
      configToSet.dataRetentionDays = body.dataRetentionDays;
    }
    if (body.crossBorderTransferAllowed !== undefined) {
      configToSet.crossBorderTransferAllowed = body.crossBorderTransferAllowed;
    }
    if (body.complianceFrameworks !== undefined) {
      configToSet.complianceFrameworks = body.complianceFrameworks;
    }
    await residencyService.setTenantResidencyConfig(configToSet);

    const config = await residencyService.getTenantResidencyConfig(tenantId);

    return reply.send(config);
  });

  // POST /api/v1/data-residency/validate-access - Validate data access request
  app.post("/api/v1/data-residency/validate-access", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const body = validateAccessSchema.parse(request.body);
    const result = await residencyService.validateDataAccess(tenantId, body.requestRegion, body.operation);

    return reply.send(result);
  });

  // GET /api/v1/data-residency/retention-policy - Get data retention policy
  app.get("/api/v1/data-residency/retention-policy", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const policy = await residencyService.getDataRetentionPolicy(tenantId);

    return reply.send(policy);
  });

  // GET /api/v1/data-residency/compliance-frameworks - Get compliance frameworks
  app.get("/api/v1/data-residency/compliance-frameworks", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const frameworks = await residencyService.getComplianceFrameworks(tenantId);

    return reply.send({ frameworks });
  });

  // POST /api/v1/data-residency/validate-compliance - Validate compliance with framework
  app.post("/api/v1/data-residency/validate-compliance", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = request.body as { framework: string };
    const result = await residencyService.validateCompliance(tenantId, body.framework);

    return reply.send(result);
  });

  app.log.info("✅ Data residency module registered");
};
