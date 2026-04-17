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
import { z } from "zod";

type ComplianceRoutesService = Pick<
  ComplianceServiceImpl,
  "requestUserExport" | "createDeletionVerification" | "verifyAndProcessDeletion"
>;

type ComplianceRoutesOptions = {
  service?: ComplianceRoutesService;
};

const ExportBodySchema = z.object({
  userId: z.string().min(1).optional(),
});

const DeletionVerifyBodySchema = z.object({
  token: z.string().min(1),
});

function createValidationErrorResponse(error: z.ZodError) {
  const primaryIssue = error.issues[0];
  return {
    error: "Validation Error",
    message: primaryIssue?.message ?? "Invalid request payload",
  };
}

export const complianceRoutes: FastifyPluginAsync<ComplianceRoutesOptions> = async (
  app,
  options,
) => {
  const complianceService = options.service ?? new ComplianceServiceImpl(app.prisma);
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
    const parseResult = ExportBodySchema.safeParse(request.body ?? {});
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }

    const targetUserId =
      (parseResult.data.userId as UserId | undefined) ?? requesterId;

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
      const parseResult = DeletionVerifyBodySchema.safeParse(request.body);
      if (!parseResult.success) {
        return reply
          .code(400)
          .send(createValidationErrorResponse(parseResult.error));
      }
      const { token } = parseResult.data;

      try {
        const result = await complianceService.verifyAndProcessDeletion({
          userId,
          tenantId,
          token,
        });
        return reply.send(result);
      } catch (e: unknown) {
        return reply.code(400).send({ error: e instanceof Error ? e.message : String(e) });
      }
    },
  );
};
