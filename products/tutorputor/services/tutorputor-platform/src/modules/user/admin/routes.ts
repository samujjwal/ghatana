/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for institution admin operations
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyPluginAsync } from "fastify";
import { InstitutionAdminServiceImpl } from "./service";
import type { InstitutionAdminService, TenantId, UserId } from "@tutorputor/contracts";
import {
  getTenantId,
  getUserId,
  requireRole,
} from "../../../core/http/requestContext.js";
import { z } from "zod";

type AdminRoutesService = Pick<
  InstitutionAdminService,
  | "getTenantSummary"
  | "listTenantUsers"
  | "getTenantUsage"
  | "bulkImportUsers"
  | "updateUserRole"
  | "assignPathToClassroom"
>;

type AdminRoutesOptions = {
  service?: AdminRoutesService;
};

const TenantUsersQuerySchema = z.object({
  role: z.string().min(1).optional(),
  searchQuery: z.string().min(1).optional(),
  cursor: z.string().min(1).optional(),
  limit: z.coerce.number().int().min(1).max(200).optional(),
  sortBy: z.enum(["displayName", "email", "role", "createdAt"]).optional(),
  sortOrder: z.enum(["asc", "desc"]).optional(),
});

const DateInputSchema = z
  .string()
  .min(1)
  .refine((value) => !Number.isNaN(Date.parse(value)), {
    message: "Invalid date format",
  });

const TenantUsageQuerySchema = z.object({
  startDate: DateInputSchema.optional(),
  endDate: DateInputSchema.optional(),
});

const ImportUsersBodySchema = z.object({
  users: z
    .array(
      z.object({
        email: z.string().email(),
        role: z.string().min(1),
        displayName: z.string().min(1).optional(),
        classroomIds: z.array(z.string().min(1)).optional(),
      }),
    )
    .min(1),
  sendInvites: z.boolean().optional(),
});

const UpdateUserRoleParamsSchema = z.object({
  userId: z.string().min(1),
});

const UpdateUserRoleBodySchema = z.object({
  newRole: z.string().min(1),
});

const AssignPathParamsSchema = z.object({
  classroomId: z.string().min(1),
});

const AssignPathBodySchema = z.object({
  pathwayId: z.string().min(1),
});

function createValidationErrorResponse(error: z.ZodError) {
  const primaryIssue = error.issues[0];
  return {
    error: "Validation Error",
    message: primaryIssue?.message ?? "Invalid request payload",
  };
}

export const adminRoutes: FastifyPluginAsync<AdminRoutesOptions> = async (
  app,
  options,
) => {
  const adminService = options.service ?? new InstitutionAdminServiceImpl(app.prisma);
  const adminRoles = ["admin", "superadmin"];

  /**
   * GET /admin/tenant/summary
   * Get tenant summary (stats)
   */
  app.get("/tenant/summary", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, adminRoles);

    try {
      const summary = await adminService.getTenantSummary({ tenantId });
      return reply.send(summary);
    } catch (error) {
      app.log.error(error, "Failed to get tenant summary");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /admin/tenant/users
   * List users with pagination and filtering
   */
  app.get<{
    Querystring: {
      role?: string;
      searchQuery?: string;
      cursor?: string;
      limit?: number;
      sortBy?: string;
      sortOrder?: "asc" | "desc";
    };
  }>("/tenant/users", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, adminRoles);

    const parseResult = TenantUsersQuerySchema.safeParse(request.query);
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }

    const { role, searchQuery, cursor, limit, sortBy, sortOrder } =
      parseResult.data;

    try {
      const result = await adminService.listTenantUsers({
        tenantId,
        ...(role ? { role } : {}),
        ...(searchQuery ? { searchQuery } : {}),
        pagination: {
          ...(cursor ? { cursor } : {}),
          ...(typeof limit === "number" ? { limit: Number(limit) } : {}),
          ...(sortBy ? { sortBy } : {}),
          ...(sortOrder ? { sortOrder } : {}),
        },
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to list users");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /admin/tenant/usage
   * Get usage metrics for a date range
   */
  app.get<{
    Querystring: {
      startDate: string;
      endDate: string;
    };
  }>("/tenant/usage", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, adminRoles);

    const parseResult = TenantUsageQuerySchema.safeParse(request.query);
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }

    // Default to last 30 days if not provided
    const end = parseResult.data.endDate
      ? new Date(parseResult.data.endDate)
      : new Date();
    const start = parseResult.data.startDate
      ? new Date(parseResult.data.startDate)
      : new Date(end.getTime() - 30 * 24 * 60 * 60 * 1000);

    try {
      const metrics = await adminService.getTenantUsage({
        tenantId,
        dateRange: {
          start: start.toISOString(),
          end: end.toISOString(),
        },
      });
      return reply.send(metrics);
    } catch (error) {
      app.log.error(error, "Failed to get usage metrics");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /admin/tenant/users/import
   * Bulk import users
   */
  app.post<{
    Body: {
      users: Array<{
        email: string;
        role: string;
        displayName?: string;
        classroomIds?: string[];
      }>;
      sendInvites?: boolean;
    };
  }>("/tenant/users/import", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const adminId = getUserId(request) as UserId;
    requireRole(request, adminRoles);

    const parseResult = ImportUsersBodySchema.safeParse(request.body);
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }

    try {
      const result = await adminService.bulkImportUsers({
        tenantId,
        importedBy: adminId,
        users: parseResult.data.users,
        ...(typeof parseResult.data.sendInvites === "boolean"
          ? { sendInvites: parseResult.data.sendInvites }
          : {}),
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to import users");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * PUT /admin/tenant/users/:userId/role
   * Update user role
   */
  app.put<{
    Params: { userId: string };
    Body: { newRole: string };
  }>("/tenant/users/:userId/role", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const adminId = getUserId(request) as UserId;
    requireRole(request, adminRoles);

    const paramsParseResult = UpdateUserRoleParamsSchema.safeParse(request.params);
    if (!paramsParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(paramsParseResult.error));
    }

    const bodyParseResult = UpdateUserRoleBodySchema.safeParse(request.body);
    if (!bodyParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(bodyParseResult.error));
    }

    const { userId } = paramsParseResult.data;
    const { newRole } = bodyParseResult.data;

    try {
      const user = await adminService.updateUserRole({
        tenantId,
        userId: userId as UserId,
        newRole,
        updatedBy: adminId,
      });
      return reply.send(user);
    } catch (error) {
      app.log.error(error, `Failed to update role for user ${userId}`);
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /admin/classrooms/:classroomId/assign-path
   * Assign learning path to classroom
   */
  app.post<{
    Params: { classroomId: string };
    Body: { pathwayId: string };
  }>("/classrooms/:classroomId/assign-path", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const adminId = getUserId(request) as UserId;
    requireRole(request, adminRoles);

    const paramsParseResult = AssignPathParamsSchema.safeParse(request.params);
    if (!paramsParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(paramsParseResult.error));
    }

    const bodyParseResult = AssignPathBodySchema.safeParse(request.body);
    if (!bodyParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(bodyParseResult.error));
    }

    const { classroomId } = paramsParseResult.data;
    const { pathwayId } = bodyParseResult.data;

    try {
      const result = await adminService.assignPathToClassroom({
        tenantId,
        classroomId,
        pathwayId,
        assignedBy: adminId,
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to assign pathway");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });
};
