import type { FastifyPluginAsync } from "fastify";
import type { PrismaClient } from "@tutorputor/core/db";
import { registerContentStudioRoutes } from "./studio/routes.js";
import { createContentStudioService } from "./studio/service.js";
import { registerCMSRoutes } from "./cms/routes.js";
import { CMSServiceImpl } from "./cms/service.js";
import { registerContentRoutes } from "./routes.js";
import { ContentServiceImpl } from "./service.js";
import { createAnimationContentIntegration } from "./animation-integration.js";
import { registerContentAssetRoutes } from "./asset/routes.js";
import { registerSemanticRoutes } from "./semantic/routes.js";
import { registerRecommendationRoutes } from "./recommendation/routes.js";
import { registerGenerationRoutes } from "./generation/routes.js";
import { registerEvaluationRoutes } from "./evaluation/routes.js";
import { registerReviewRoutes } from "./review/routes.js";
import { registerTelemetryRoutes } from "./telemetry/routes.js";
import { registerCandidateRoutes } from "./candidates/routes.js";
import { registerPublishRoutes } from "./publish/routes.js";

/**
 * Content module - consolidates:
 * - CMS APIs
 * - Content APIs
 * - Content studio APIs
 * - Search integrations (platform search module)
 * - Content worker pipelines
 * - Domain loader integration
 */
export const contentModule: FastifyPluginAsync = async (app) => {
  app.log.info("Initializing content module...");

  // Get shared Prisma client from app context
  const prisma = app.prisma as PrismaClient;

  // Initialize Services
  const contentStudioService = createContentStudioService(prisma, {
    openaiApiKey: process.env.OPENAI_API_KEY || "",
    model: process.env.OPENAI_MODEL || "gpt-4",
  });

  const contentService = new ContentServiceImpl(
    prisma,
    /* aiProxy */ undefined,
  );
  const cmsService = new CMSServiceImpl(
    prisma,
    contentService,
    /* aiProxy */ undefined,
  );

  // Initialize Animation Content Integration with Prisma for persistence
  const animationIntegration = createAnimationContentIntegration(prisma);

  // Register Content routes (ContentService)
  await registerContentRoutes(app, { contentService });

  // Register Content Studio routes
  registerContentStudioRoutes(app, {
    contentStudioService,
    animationIntegration,
    prefixes: ["/content-studio"],
  });

  // Register CMS routes
  registerCMSRoutes(app, { cmsService });

  // Register Content Asset read routes
  registerContentAssetRoutes(app, { prisma });

  // Register Semantic Indexing routes
  registerSemanticRoutes(app, { prisma });

  // Register Recommendation routes
  registerRecommendationRoutes(app, { prisma });

  // Register Generation Planner routes
  registerGenerationRoutes(app, { prisma });

  // Register Evaluation & Guardrail Scorecard routes (P3.3)
  registerEvaluationRoutes(app, { prisma });

  // Register Admin Review & Regeneration Console routes (P3.4)
  registerReviewRoutes(app, { prisma });

  // Register Explorer Telemetry routes (P4.1)
  registerTelemetryRoutes(app, { prisma });

  // Register Regeneration Candidate routes (P4.3)
  registerCandidateRoutes(app, { prisma });

  // Register Publish & Reindex routes (P4.4)
  registerPublishRoutes(app, { prisma });

  app.log.info("✅ Content module routes registered");
};
