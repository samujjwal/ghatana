import type { FastifyPluginAsync } from "fastify";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import { GamificationService } from "./service";
import {
  getTenantId,
  getUserId,
  requireSelfOrRole,
  requireRole,
} from "../../../core/http/requestContext.js";
import { z } from "zod";

const leaderboardQuerySchema = z.object({
  limit: z.coerce.number().int().positive().max(100).optional(),
  offset: z.coerce.number().int().nonnegative().optional(),
});

const userIdParamsSchema = z.object({
  userId: z.string().min(1),
});

const awardPointsBodySchema = z.object({
  userId: z.string().min(1),
  points: z.coerce.number(),
  reason: z.string().min(1).optional(),
  sourceType: z
    .enum(["module_complete", "assessment", "streak", "daily_login", "bonus"])
    .optional(),
});

const unlockAchievementBodySchema = z.object({
  userId: z.string().min(1),
  achievementId: z.string().min(1),
});

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
   * GET /progress
   * Get current authenticated user's full gamification progress.
   * Full path: /api/v1/engagement/gamification/progress
   */
  app.get("/progress", async (request, reply) => {
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
   * GET /achievements
   * Get current authenticated user's achievements.
   * Full path: /api/v1/engagement/gamification/achievements
   */
  app.get("/achievements", async (request, reply) => {
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
   * GET /leaderboard
   * Full path: /api/v1/engagement/gamification/leaderboard
   */
  app.get("/leaderboard", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const queryResult = leaderboardQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      return reply.code(400).send({
        error: "Invalid leaderboard query",
        issues: queryResult.error.issues,
      });
    }
    const { limit } = queryResult.data;

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
    const bodyResult = awardPointsBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid award payload",
        issues: bodyResult.error.issues,
      });
    }
    const userId = bodyResult.data.userId as UserId;
    const points = bodyResult.data.points;
    const reason = bodyResult.data.reason ?? "Admin award";
    const sourceType = bodyResult.data.sourceType ?? "bonus";

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
    const queryResult = leaderboardQuerySchema.safeParse(request.query);
    if (!queryResult.success) {
      return reply.code(400).send({
        error: "Invalid leaderboard query",
        issues: queryResult.error.issues,
      });
    }
    const { limit, offset } = queryResult.data;

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
    const bodyResult = unlockAchievementBodySchema.safeParse(request.body);
    if (!bodyResult.success) {
      return reply.code(400).send({
        error: "Invalid unlock payload",
        issues: bodyResult.error.issues,
      });
    }
    const { userId, achievementId } = bodyResult.data as {
      userId: UserId;
      achievementId: string;
    };

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
