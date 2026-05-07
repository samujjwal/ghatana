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
  requirePermission,
} from "../../core/http/requestContext.js";
import { z } from "zod";

type ComplianceRoutesService = Pick<
  ComplianceServiceImpl,
  | "requestUserExport"
  | "getExportStatus"
  | "downloadExport"
  | "requestUserDeletion"
  | "cancelDeletionRequest"
  | "getDeletionStatus"
  | "createDeletionVerification"
  | "verifyAndProcessDeletion"
  | "getPrivacyDataAccessSummary"
  | "revokeConsent"
  | "deleteTelemetryForUser"
  | "processDeletionNow"
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

const DeletionRequestBodySchema = z.object({
  reason: z.string().min(1).optional(),
});

const ConsentRevocationBodySchema = z.object({
  consentType: z.enum(["ai_tutor", "learning_telemetry", "personalization", "voice_image", "social"]),
});

const TelemetryDeletionBodySchema = z.object({
  anonymize: z.boolean().optional(),
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
      requirePermission(request, "admin.export");
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
   * GET /compliance/privacy-center
   * Product-facing summary for privacy center state.
   */
  app.get("/privacy-center", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;

    const summary = await complianceService.getPrivacyDataAccessSummary({
      userId,
      tenantId,
    });
    return reply.send(summary);
  });

  /**
   * GET /compliance/export/:requestId
   * Export request status.
   */
  app.get<{ Params: { requestId: string } }>("/export/:requestId", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const result = await complianceService.getExportStatus({
      requestId: request.params.requestId,
      tenantId,
    });
    return reply.send(result);
  });

  /**
   * GET /compliance/export/:requestId/download
   * Download URL/evidence for completed export.
   */
  app.get<{ Params: { requestId: string } }>("/export/:requestId/download", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    try {
      const result = await complianceService.downloadExport({
        requestId: request.params.requestId,
        tenantId,
      });
      return reply.send(result);
    } catch (error) {
      return reply.code(404).send({ error: error instanceof Error ? error.message : "Export not found" });
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
   * POST /compliance/deletion/request
   * Product-facing deletion request that creates a retention-window workflow.
   */
  app.post<{ Body: { reason?: string } }>("/deletion/request", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const parseResult = DeletionRequestBodySchema.safeParse(request.body ?? {});
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }

    const result = await complianceService.requestUserDeletion({
      userId,
      tenantId,
      requestedBy: userId,
      reason: parseResult.data.reason,
    });
    return reply.code(202).send(result);
  });

  app.get<{ Params: { requestId: string } }>("/deletion/:requestId", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const result = await complianceService.getDeletionStatus({
      requestId: request.params.requestId,
      tenantId,
    });
    return reply.send(result);
  });

  app.delete<{ Params: { requestId: string } }>("/deletion/:requestId", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const result = await complianceService.cancelDeletionRequest({
      requestId: request.params.requestId,
      tenantId,
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

  /**
   * POST /compliance/consent/revoke
   * Revoke a product consent and make the result immediately visible.
   */
  app.post<{ Body: { consentType: "ai_tutor" | "learning_telemetry" | "personalization" | "voice_image" | "social" } }>(
    "/consent/revoke",
    async (request, reply) => {
      const tenantId = getTenantId(request) as TenantId;
      const userId = getUserId(request) as UserId;
      const parseResult = ConsentRevocationBodySchema.safeParse(request.body);
      if (!parseResult.success) {
        return reply.code(400).send(createValidationErrorResponse(parseResult.error));
      }
      const result = await complianceService.revokeConsent({
        userId,
        tenantId,
        consentType: parseResult.data.consentType,
        ipAddress: request.ip,
        userAgent: request.headers["user-agent"],
      });
      return reply.send(result);
    },
  );

  /**
   * POST /compliance/telemetry/delete
   * Delete or anonymize learning telemetry for the authenticated user.
   */
  app.post<{ Body: { anonymize?: boolean } }>("/telemetry/delete", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const parseResult = TelemetryDeletionBodySchema.safeParse(request.body ?? {});
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }
    const result = await complianceService.deleteTelemetryForUser({
      userId,
      tenantId,
      anonymize: parseResult.data.anonymize,
    });
    return reply.send(result);
  });

  /**
   * POST /compliance/deletion/process-now
   * Admin-only operational endpoint used by product support and test evidence.
   */
  app.post<{ Body: { userId: string } }>("/deletion/process-now", async (request, reply) => {
    requirePermission(request, "privacy.delete.process");
    const tenantId = getTenantId(request) as TenantId;
    const targetUserId = request.body.userId as UserId;
    const result = await complianceService.processDeletionNow({
      userId: targetUserId,
      tenantId,
    });
    return reply.send(result);
  });
};
