import type { FastifyPluginAsync } from "fastify";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import { GamificationService } from "./service";
import {
  getTenantId,
  getUserId,
  requireSelfOrRole,
  requireRole,
} from "../../../core/http/requestContext.js";

/**
 * Gamification routes - points, leaderboards, and achievements.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for gamification features
 * @doc.layer product
 * @doc.pattern REST API
 */
export const gamificationRoutes: FastifyPluginAsync = async (app) => {
  type GamificationServicePrisma = ConstructorParameters<
    typeof GamificationService
  >[0];
  type GamificationRoutePrisma = typeof app.prisma & {
    badge: GamificationServicePrisma["badge"];
    badgeEarned: GamificationServicePrisma["badgeEarned"];
    userPoints: GamificationServicePrisma["userPoints"];
    userAchievement: {
      create(args: unknown): Promise<unknown>;
    };
    learningStreak: {
      findFirst(args: unknown): Promise<unknown>;
    };
  };

  const prisma = app.prisma as GamificationRoutePrisma;
  const service = new GamificationService(prisma);

  /**
   * GET /gamification/progress
   * Get current authenticated user's full gamification progress.
   * Alias consistent with frontend GET /api/v1/gamification/progress.
   */
  app.get("/gamification/progress", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;

    try {
      const progress = await service.getUserProgress(tenantId, userId);
      return reply.code(200).send(progress);
    } catch (error) {
      app.log.error(error, "Failed to get gamification progress");
      return reply.code(500).send({ error: "Failed to get progress" });
    }
  });

  /**
   * GET /gamification/achievements
   * Get current authenticated user's achievements.
   */
  app.get("/gamification/achievements", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const userId = getUserId(request) as UserId;

    try {
      const achievements = await service.getUserAchievements(tenantId, userId);
      return reply.send(achievements);
    } catch (error) {
      app.log.error(error, "Failed to get achievements");
      return reply.code(500).send({ error: "Failed to fetch achievements" });
    }
  });

  /**
   * GET /gamification/leaderboard
   * Alias consistent with frontend GET /api/v1/gamification/leaderboard.
   */
  app.get("/gamification/leaderboard", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const { limit } = request.query as { limit?: number };

    try {
      const leaderboard = await service.getLeaderboard({
        tenantId,
        limit: limit ? Number(limit) : 10,
        offset: 0,
      });
      return reply.send(leaderboard);
    } catch (error) {
      app.log.error(error);
      return reply.code(500).send({ error: "Failed to get leaderboard" });
    }
  });

  /**
   * GET /users/:userId/points
   * Get user's points balance
   */
  app.get("/users/:userId/points", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const { userId } = request.params as { userId: UserId };
    requireSelfOrRole(request, userId, ["teacher", "admin", "superadmin"]);

    try {
      const progress = await service.getUserProgress(tenantId, userId);
      return reply.code(200).send({
        userId: progress.userId,
        totalPoints: progress.totalPoints,
        level: progress.level,
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
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, ["teacher", "admin", "superadmin"]);
    const body = request.body as Record<string, unknown>;
    const userId = typeof body["userId"] === "string" ? (body["userId"] as UserId) : undefined;
    const points = typeof body["points"] === "number" ? body["points"] : Number(body["points"]);
    const reason = typeof body["reason"] === "string" ? body["reason"] : "Admin award";
    const rawSourceType = typeof body["sourceType"] === "string" ? body["sourceType"] : "bonus";
    const sourceType =
      rawSourceType === "module_complete" ||
      rawSourceType === "assessment" ||
      rawSourceType === "streak" ||
      rawSourceType === "daily_login" ||
      rawSourceType === "bonus"
        ? rawSourceType
        : "bonus";

    if (!userId || Number.isNaN(points)) {
      return reply.code(400).send({ error: "Valid userId and points are required" });
    }

    try {
      const result = await service.awardPoints({
        tenantId,
        userId,
        points,
        reason,
        sourceType,
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
    const tenantId = getTenantId(request) as TenantId;
    const { limit, offset } = request.query as {
      limit?: number;
      offset?: number;
    };

    try {
      const leaderboard = await service.getLeaderboard({
        tenantId,
        limit: limit ? Number(limit) : 10,
        offset: offset ? Number(offset) : 0,
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
    const tenantId = getTenantId(request) as TenantId;
    const { userId } = request.params as { userId: UserId };
    requireSelfOrRole(request, userId, ["teacher", "admin", "superadmin"]);

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
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, ["teacher", "admin", "superadmin"]);
    const { userId, achievementId } = request.body as {
      userId: UserId;
      achievementId: string;
    };

    if (!userId || !achievementId) {
      return reply
        .code(400)
        .send({ error: "User ID and achievement ID are required" });
    }

    try {
      const userAchievement = await prisma.userAchievement.create({
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
    const tenantId = getTenantId(request) as TenantId;
    const { userId } = request.params as { userId: UserId };
    requireSelfOrRole(request, userId, ["teacher", "admin", "superadmin"]);

    try {
      const streak = await prisma.learningStreak.findFirst({
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
