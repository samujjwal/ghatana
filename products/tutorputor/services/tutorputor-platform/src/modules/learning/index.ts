import fp from "fastify-plugin";
import { createLearningService } from "./service";
import { createPathwaysService } from "./pathways-service";
import { createAssessmentService } from "./assessment-service";
import { createAnalyticsService } from "./analytics-service";
import { createLearnerProfileService } from "./learner-profile-service";
import { SessionAdaptationEngine } from "../adaptation/session-engine.js";
import { ContentAssetReadService } from "../content/asset/read-service.js";
import { ContentVariationService } from "../content/variation/service.js";
import learningRoutes from "./routes";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type { Redis } from "ioredis";

/**
 * Learning Module Plugin
 *
 * Registers the Learning service and routes.
 * Consolidates:
 * - tutorputor-learning (Done)
 * - tutorputor-pathways (Done)
 * - tutorputor-assessment (Done)
 * - tutorputor-analytics (Done)
 */
export const learningModule = fp(
  async (fastify) => {
    const prisma = (fastify as any).prisma as TutorPrismaClient;
    const redis = (fastify as any).redis as Redis;

    if (!prisma) {
      throw new Error(
        "Prisma client not found on Fastify instance. Ensure database plugin is registered.",
      );
    }

    // 1. Create Core Services
    const learningService = createLearningService(prisma);
    const pathwaysService = createPathwaysService(prisma);
    const assessmentService = createAssessmentService(prisma);
    const analyticsService = createAnalyticsService(prisma, redis);
    const learnerProfileService = createLearnerProfileService(prisma);
    const assetReadService = new ContentAssetReadService(prisma);
    const contentVariationService = new ContentVariationService(assetReadService);
    const sessionAdaptationEngine = new SessionAdaptationEngine(
      learnerProfileService,
      contentVariationService,
      redis,
    );

    // 2. Register Routes
    // Mounted relative to where this module is registered.
    await learningRoutes(
      fastify as unknown as Parameters<typeof learningRoutes>[0],
      {
        learningService,
        pathwaysService,
        assessmentService,
        analyticsService,
        learnerProfileService,
        contentVariationService,
        sessionAdaptationEngine,
      },
    );

    // 3. Decorate for cross-module usage
    fastify.decorate("learningService", learningService);
    fastify.decorate("pathwaysService", pathwaysService);
    fastify.decorate("assessmentService", assessmentService);
    fastify.decorate("analyticsService", analyticsService);
    fastify.decorate("learnerProfileService", learnerProfileService);
    fastify.decorate("contentVariationService", contentVariationService);
    fastify.decorate("sessionAdaptationEngine", sessionAdaptationEngine);
  },
  {
    name: "learning-module",
    dependencies: ["database-plugin"],
  },
);
