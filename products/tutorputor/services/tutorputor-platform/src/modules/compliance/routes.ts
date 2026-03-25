/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for compliance operations
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyPluginAsync } from "fastify";
import { ComplianceServiceImpl } from "./service";
import type { TenantId, UserId } from "@tutorputor/contracts";
import {
  getTenantId,
  getUserId,
  requireRole,
} from "../../core/http/requestContext.js";

export const complianceRoutes: FastifyPluginAsync = async (app) => {
  const complianceService = new ComplianceServiceImpl(app.prisma);
  const adminRoles = ["admin", "superadmin"];

  /**
   * POST /compliance/export
   * Request user data export
   */
  app.post<{
    Body: {
      userId?: string; // Admin can request for user
    };
  }>("/export", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const requesterId = getUserId(request) as UserId;
    const targetUserId =
      (request.body?.userId as UserId | undefined) ?? requesterId;

    if (targetUserId !== requesterId) {
      requireRole(request, adminRoles);
    }

    try {
      const user = await app.prisma.user.findFirst({
        where: { id: targetUserId, tenantId },
      });
      if (!user) return reply.code(404).send({ error: "User not found" });

      const result = await complianceService.requestUserExport({
        userId: targetUserId,
        tenantId,
        requestedBy: requesterId,
      });
      return reply.send(result);
    } catch (error) {
      app.log.error(error, "Failed to request export");
      return reply.code(500).send({
        error: "Internal Server Error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /compliance/deletion/request-token
   */
  app.post("/deletion/request-token", async (request, reply) => {
    const userId = getUserId(request) as UserId;

    const user = await app.prisma.user.findUnique({ where: { id: userId } });
    if (!user) return reply.code(404).send({ error: "User not found" });

    const result = await complianceService.createDeletionVerification({
      userId,
      userEmail: user.email,
    });
    return reply.send(result);
  });

  /**
   * POST /compliance/deletion/verify
   */
  app.post<{ Body: { token: string } }>(
    "/deletion/verify",
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const { token } = request.body;

      try {
        const result = await complianceService.verifyAndProcessDeletion({
          userId,
          tenantId,
          token,
        });
        return reply.send(result);
      } catch (e: any) {
        return reply.code(400).send({ error: e.message });
      }
    },
  );
};
