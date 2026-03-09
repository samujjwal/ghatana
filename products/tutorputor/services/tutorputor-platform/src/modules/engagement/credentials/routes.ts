import type { FastifyPluginAsync } from "fastify";
import type { TenantId, UserId } from "@ghatana/tutorputor-contracts/v1/types";

/**
 * Credentials routes - badges and certificates.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for badges and certificates
 * @doc.layer product
 * @doc.pattern REST API
 */
export const credentialsRoutes: FastifyPluginAsync = async (app) => {
  /**
   * GET /badges
   * List available badges
   */
  app.get("/badges", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

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
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { userId } = request.params as { userId: UserId };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const userBadges = await app.prisma.userBadge.findMany({
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
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const adminUserId = request.headers["x-user-id"] as UserId;
    const { userId, badgeId, reason } = request.body as {
      userId: UserId;
      badgeId: string;
      reason?: string;
    };

    if (!tenantId || !adminUserId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!userId || !badgeId) {
      return reply
        .code(400)
        .send({ error: "User ID and badge ID are required" });
    }

    try {
      const userBadge = await app.prisma.userBadge.create({
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
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const certificates = await app.prisma.certificate.findMany({
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
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const userId = request.headers["x-user-id"] as UserId;
    const { moduleId, completionDate } = request.body as {
      moduleId: string;
      completionDate?: string;
    };

    if (!tenantId || !userId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!moduleId) {
      return reply.code(400).send({ error: "Module ID is required" });
    }

    try {
      const certificate = await app.prisma.certificate.create({
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
