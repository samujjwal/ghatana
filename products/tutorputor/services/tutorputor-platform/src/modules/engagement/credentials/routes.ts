import type { FastifyPluginAsync } from "fastify";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import {
  getTenantId,
  getUserId,
  requireSelfOrRole,
  requireRole,
} from "../../../core/http/requestContext.js";
import { z } from "zod";

const userIdParamsSchema = z.object({
  userId: z.string().min(1),
});

const awardBadgeBodySchema = z.object({
  userId: z.string().min(1),
  badgeId: z.string().min(1),
  reason: z.string().optional(),
});

const generateCertificateBodySchema = z.object({
  moduleId: z.string().min(1),
  completionDate: z.string().datetime().optional(),
});

/**
 * Credentials routes - badges and certificates.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for badges and certificates
 * @doc.layer product
 * @doc.pattern REST API
 */
export const credentialsRoutes: FastifyPluginAsync = async (app) => {
  const prisma = app.prisma;
  /**
   * GET /badges
   * List available badges
   */
  app.get("/badges", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;

    try {
      const badges = await app.prisma.badge.findMany({
        where: { tenantId },
        orderBy: { createdAt: "desc" },
      });
      return reply.code(200).send(badges);
    } catch (error) {
      app.log.error(error, "Failed to list badges");
      return reply.code(500).send({
        error: "Failed to list badges",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /users/:userId/badges
   * Get user's earned badges
   */
  app.get("/users/:userId/badges", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const paramsResult = userIdParamsSchema.safeParse(request.params);
    if (!paramsResult.success) {
      return reply.code(400).send({
        error: "Invalid user id",
        issues: paramsResult.error.issues,
      });
    }
    const { userId } = paramsResult.data as { userId: UserId };
    requireSelfOrRole(request, userId, ["teacher", "admin", "superadmin"]);

    try {
      const userBadges = await prisma.userBadge.findMany({
        where: { tenantId, userId },
        include: { badge: true },
        orderBy: { earnedAt: "desc" },
      });
      return reply.code(200).send(userBadges);
    } catch (error) {
      app.log.error(error, "Failed to get user badges");
      return reply.code(500).send({
        error: "Failed to get user badges",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /badges/award
   * Award a badge to a user
   */
  app.post("/badges/award", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const adminUserId = getUserId(request) as UserId;
    requireRole(request, ["teacher", "admin", "superadmin"]);
    const bodyResult = awardBadgeBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid badge award payload",
        issues: bodyResult.error.issues,
      });
    }
    const { userId, badgeId, reason } = bodyResult.data as {
      userId: UserId;
      badgeId: string;
      reason?: string;
    };

    try {
      const userBadge = await prisma.userBadge.create({
        data: {
          tenantId,
          userId,
          badgeId,
          reason,
          awardedBy: adminUserId,
        },
        include: { badge: true },
      });
      return reply.code(201).send(userBadge);
    } catch (error) {
      app.log.error(error, "Failed to award badge");
      return reply.code(500).send({
        error: "Failed to award badge",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /certificates
   * List user's certificates
   */
  app.get("/certificates", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;

    try {
      const certificates = await prisma.certificate.findMany({
        where: { tenantId, userId },
        orderBy: { issuedAt: "desc" },
      });
      return reply.code(200).send(certificates);
    } catch (error) {
      app.log.error(error, "Failed to list certificates");
      return reply.code(500).send({
        error: "Failed to list certificates",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /certificates/generate
   * Generate a certificate for module completion
   */
  app.post("/certificates/generate", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;
    const bodyResult = generateCertificateBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid certificate payload",
        issues: bodyResult.error.issues,
      });
    }
    const { moduleId, completionDate } = bodyResult.data;

    try {
      const certificate = await prisma.certificate.create({
        data: {
          tenantId,
          userId,
          moduleId,
          issuedAt: completionDate ? new Date(completionDate) : new Date(),
          certificateUrl: `/certificates/${userId}/${moduleId}`,
        },
      });
      return reply.code(201).send(certificate);
    } catch (error) {
      app.log.error(error, "Failed to generate certificate");
      return reply.code(500).send({
        error: "Failed to generate certificate",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/health", async () => ({
    status: "healthy",
    module: "credentials",
  }));

  app.log.info("Credentials routes registered");
};
