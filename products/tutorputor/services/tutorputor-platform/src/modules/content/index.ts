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
import { registerModalityConversionRoutes } from "./modality-conversion/routes.js";
import { registerQualityMLRoutes } from "./quality-ml/routes.js";
import { registerABTestingRoutes } from "./experiments/ab-testing/routes.js";
import { registerRestoreRoutes } from "./restore/routes.js";
import { registerVersioningRoutes } from "./versioning/routes.js";
import { IndependentGeneratedContentValidator } from "./evaluation/independent-validator-service.js";
import { KnowledgeBaseServiceImpl } from "../knowledge-base/service.js";

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
    independentValidator: new IndependentGeneratedContentValidator(
      prisma,
      new KnowledgeBaseServiceImpl(prisma),
    ),
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
  // F-023: Serve under both the legacy /api/content-studio/* prefix (deprecated)
  // and the canonical /api/v1/content-studio/* prefix.  The content module is mounted at
  // /api so "/content-studio" → /api/content-studio and "/v1/content-studio" → /api/v1/content-studio.
  // Legacy prefix is deprecated and will be removed in a future release.
  
  // Add deprecation warning for legacy prefix
  app.addHook('onRequest', async (request, reply) => {
    if (request.url.startsWith('/content-studio/')) {
      reply.header('Deprecation', '110');
      reply.header('Link', '</api/v1/content-studio>; rel="alternate"');
    }
  });

  registerContentStudioRoutes(app, {
    contentStudioService,
    animationIntegration,
    prefixes: ["/content-studio", "/v1/content-studio"],
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
  registerGenerationRoutes(app, { prisma, redis: app.redis });

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

  // Register cross-modal conversion routes
  registerModalityConversionRoutes(app, { prisma });

  // Register heuristic quality prediction routes
  registerQualityMLRoutes(app, { prisma });

  // Register experimentation routes
  registerABTestingRoutes(app, { prisma });

  // Register soft delete / restore routes
  registerRestoreRoutes(app, { prisma });

  // Register content versioning routes
  registerVersioningRoutes(app, { prisma });

  app.log.info("✅ Content module routes registered");
};
