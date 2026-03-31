/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for audit log operations
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyPluginAsync } from "fastify";
import { AuditServiceImpl } from "./service";
import type { TenantId, UserId } from "@tutorputor/contracts";
import { getTenantId, requireRole } from "../../core/http/requestContext.js";

export const auditRoutes: FastifyPluginAsync = async (app) => {
  const auditService = new AuditServiceImpl(app.prisma);
  const auditRoles = ["admin", "superadmin"];

  /**
   * GET /audit
   * Query audit logs
   */
  app.get<{
    Querystring: {
      action?: string;
      actorId?: string;
      resourceType?: string;
      resourceId?: string;
      startDate?: string;
      endDate?: string;
      cursor?: string;
      limit?: number;
      sortBy?: string;
      sortOrder?: "asc" | "desc";
    };
  }>("/", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, auditRoles);

    const {
      action,
      actorId,
      resourceType,
      resourceId,
      startDate,
      endDate,
      cursor,
      limit,
      sortBy,
      sortOrder,
    } = request.query;

    try {
      const result = await auditService.queryAuditEvents({
        tenantId,
        ...(actorId ? { actorId: actorId as UserId } : {}),
        ...(action ? { action } : {}),
        ...(resourceType ? { resourceType } : {}),
        ...(resourceId ? { resourceId } : {}),
        ...(startDate ? { startDate } : {}),
        ...(endDate ? { endDate } : {}),
        pagination: {
          ...(cursor ? { cursor } : {}),
          ...(typeof limit === "number" ? { limit: Number(limit) } : {}),
          ...(sortBy ? { sortBy } : {}),
          ...(sortOrder ? { sortOrder } : {}),
        },
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to query audit logs");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /audit/summary
   * Get audit log summary
   */
  app.get<{
    Querystring: {
      days?: number;
    };
  }>("/summary", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, auditRoles);

    const { days } = request.query;

    try {
      const summary = await auditService.getAuditSummary({
        tenantId,
        ...(typeof days === "number" ? { days: Number(days) } : {}),
      });
      return reply.send(summary);
    } catch (error) {
      app.log.error(error, "Failed to get audit summary");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /audit/export
   * Request audit log export
   */
  app.post<{
    Body: {
      startDate: string;
      endDate: string;
    };
  }>("/export", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, auditRoles);

    const { startDate, endDate } = request.body;

    try {
      const result = await auditService.exportAuditLog({
        tenantId,
        startDate,
        endDate,
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to export audit logs");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });
};
