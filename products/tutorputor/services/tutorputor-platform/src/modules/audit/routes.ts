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
import { z } from "zod";

type AuditRoutesService = Pick<
  AuditServiceImpl,
  "queryAuditEvents" | "getAuditSummary" | "exportAuditLog"
>;

type AuditRoutesOptions = {
  service?: AuditRoutesService;
};

const DateInputSchema = z
  .string()
  .min(1)
  .refine((value) => !Number.isNaN(Date.parse(value)), {
    message: "Invalid date format",
  });

const AuditQuerySchema = z.object({
  action: z.string().min(1).optional(),
  actorId: z.string().min(1).optional(),
  resourceType: z.string().min(1).optional(),
  resourceId: z.string().min(1).optional(),
  startDate: DateInputSchema.optional(),
  endDate: DateInputSchema.optional(),
  cursor: z.string().min(1).optional(),
  limit: z.coerce.number().int().min(1).max(200).optional(),
  sortBy: z.enum(["timestamp", "actorId", "action", "resourceType"]).optional(),
  sortOrder: z.enum(["asc", "desc"]).optional(),
});

const AuditSummaryQuerySchema = z.object({
  days: z.coerce.number().int().min(1).max(365).optional(),
});

const AuditExportBodySchema = z.object({
  startDate: DateInputSchema,
  endDate: DateInputSchema,
});

function createValidationErrorResponse(error: z.ZodError) {
  const primaryIssue = error.issues[0];
  return {
    error: "Validation Error",
    message: primaryIssue?.message ?? "Invalid request payload",
  };
}

export const auditRoutes: FastifyPluginAsync<AuditRoutesOptions> = async (
  app,
  options,
) => {
  const auditService = options.service ?? new AuditServiceImpl(app.prisma);
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

    const parseResult = AuditQuerySchema.safeParse(request.query);
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }

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
    } = parseResult.data;

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

    const parseResult = AuditSummaryQuerySchema.safeParse(request.query);
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }

    const { days } = parseResult.data;

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

    const parseResult = AuditExportBodySchema.safeParse(request.body);
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }

    const { startDate, endDate } = parseResult.data;

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
