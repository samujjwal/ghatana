import type { FastifyPluginAsync } from "fastify";
import type {
  TenantId,
  UserId,
} from "@ghatana/tutorputor-contracts/v1/types";
import { GamificationService } from "./service";

/**
 * Gamification routes - points, leaderboards, and achievements.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for gamification features
 * @doc.layer product
 * @doc.pattern REST API
 */
export const gamificationRoutes: FastifyPluginAsync = async (app) => {
  const service = new GamificationService(app.prisma);

  /**
   * GET /users/:userId/points
   * Get user's points balance
   */
  app.get("/users/:userId/points", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { userId } = request.params as { userId: UserId };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const progress = await service.getUserProgress(tenantId, userId);
      return reply
        .code(200)
        .send({
          userId: progress.userId,
          totalPoints: progress.totalPoints,
          level: progress.level
        });
    } catch (error) {
      app.log.error(error, "Failed to get user points");
      return reply.code(500).send({
        error: "Failed to get points",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /points/award
   * Award points to a user
   */
  app.post("/points/award", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    // @ts-ignore
    const { userId, points, reason, sourceType } = request.body as any;

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const result = await service.awardPoints({
        tenantId,
        userId,
        points,
        reason: reason || "Admin award",
        sourceType: sourceType || "bonus"
      });
      return reply.code(200).send(result);
    } catch (error) {
      app.log.error(error, "Failed to award points");
      return reply.code(500).send({ error: "Failed to award points" });
    }
  });

  // Implemented Leaderboard and Badge routes as well

  /**
   * GET /leaderboard
   */
  app.get("/leaderboard", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { limit, offset } = request.query as { limit?: number; offset?: number };

    if (!tenantId) return reply.code(401).send({ error: "Auth required" });

    try {
      const leaderboard = await service.getLeaderboard({
        tenantId,
        limit: limit ? Number(limit) : 10,
        offset: offset ? Number(offset) : 0
      });
      return reply.send(leaderboard);
    } catch (error) {
      app.log.error(error);
      return reply.code(500).send({ error: "Failed to get leaderboard" });
    }
  });

  /**
   * GET /users/:userId/achievements
   */
  app.get("/users/:userId/achievements", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { userId } = request.params as { userId: UserId };

    if (!tenantId) return reply.code(401).send({ error: "Auth required" });

    try {
      const achievements = await service.getUserAchievements(tenantId, userId);
      return reply.send(achievements);
    } catch (error) {
      app.log.error(error);
      return reply.code(500).send({ error: "Failed to fetch achievements" });
    }
  });

  /**
   * POST /achievements/unlock
   * Unlock an achievement for a user
   */
  app.post("/achievements/unlock", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { userId, achievementId } = request.body as {
      userId: UserId;
      achievementId: string;
    };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!userId || !achievementId) {
      return reply
        .code(400)
        .send({ error: "User ID and achievement ID are required" });
    }

    try {
      const userAchievement = await app.prisma.userAchievement.create({
        data: {
          tenantId,
          userId,
          achievementId,
        },
        include: { achievement: true },
      });
      return reply.code(201).send(userAchievement);
    } catch (error) {
      app.log.error(error, "Failed to unlock achievement");
      return reply.code(500).send({
        error: "Failed to unlock achievement",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /streaks/:userId
   * Get user's learning streak
   */
  app.get("/streaks/:userId", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { userId } = request.params as { userId: UserId };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const streak = await app.prisma.learningStreak.findFirst({
        where: { tenantId, userId },
      });
      return reply
        .code(200)
        .send(streak || { userId, currentStreak: 0, longestStreak: 0 });
    } catch (error) {
      app.log.error(error, "Failed to get streak");
      return reply.code(500).send({
        error: "Failed to get streak",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  app.get("/health", async () => ({
    status: "healthy",
    module: "gamification",
  }));

  app.log.info("Gamification routes registered");
};
