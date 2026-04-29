/**
 * Compliance Evidence Package Module
 *
 * Generate downloadable audit reports for GDPR/CCPA/pedagogical platform certifications.
 *
 * @doc.type module
 * @doc.purpose Compliance evidence package module for audit certifications
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import { ComplianceEvidenceService } from "./ComplianceEvidenceService.js";
import { z } from "zod";

const generateEvidenceSchema = z.object({
  reportType: z.enum(["GDPR", "CCPA", "PEDAGOGICAL", "SOC2", "ISO27001"]),
  periodStart: z.string().transform((s) => new Date(s)),
  periodEnd: z.string().transform((s) => new Date(s)),
});

export const complianceEvidenceModule: FastifyPluginAsync = async (app) => {
  const complianceService = new ComplianceEvidenceService(app.prisma as any);
  app.decorate("complianceEvidenceService", complianceService);

  // POST /api/v1/compliance/evidence/generate - Generate compliance evidence package
  app.post("/api/v1/compliance/evidence/generate", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const body = generateEvidenceSchema.parse(request.body);
    const evidence = await complianceService.generateEvidencePackage(
      tenantId,
      body.reportType,
      body.periodStart,
      body.periodEnd,
    );

    return reply.send(evidence);
  });

  // GET /api/v1/compliance/evidence/:reportId - Get evidence package by ID
  app.get("/api/v1/compliance/evidence/:reportId", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { reportId } = request.params as { reportId: string };
    const evidence = await complianceService.getEvidencePackage(reportId);

    if (!evidence) {
      return reply.code(404).send({ error: "Evidence package not found" });
    }

    return reply.send(evidence);
  });

  // GET /api/v1/compliance/evidence - Get tenant evidence packages
  app.get("/api/v1/compliance/evidence", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const query = request.query as { limit?: string };
    const limit = query.limit ? parseInt(query.limit, 10) : 50;
    const packages = await complianceService.getTenantEvidencePackages(tenantId, limit);

    return reply.send({ packages });
  });

  // GET /api/v1/compliance/evidence/:reportId/download - Download evidence package as JSON
  app.get("/api/v1/compliance/evidence/:reportId/download", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const { reportId } = request.params as { reportId: string };
    const evidence = await complianceService.getEvidencePackage(reportId);

    if (!evidence) {
      return reply.code(404).send({ error: "Evidence package not found" });
    }

    const json = await complianceService.downloadEvidencePackage(reportId);

    reply.header("Content-Type", "application/json");
    reply.header("Content-Disposition", `attachment; filename="${evidence.reportType}-${evidence.reportId}.json"`);
    return reply.send(json);
  });

  // GET /api/v1/compliance/status - Get compliance status summary
  app.get("/api/v1/compliance/status", async (request, reply) => {
    const tenantId = (request as any).tenantId;
    const user = (request as any).user;

    if (!tenantId || !user) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (user.role !== "admin" && user.role !== "superadmin") {
      return reply.code(403).send({ error: "Insufficient permissions" });
    }

    const status = await complianceService.getComplianceStatus(tenantId);

    return reply.send(status);
  });

  app.log.info("✅ Compliance evidence module registered");
};
