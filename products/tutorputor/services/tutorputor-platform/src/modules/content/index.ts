import type { FastifyPluginAsync } from "fastify";
import type { PrismaClient } from "@ghatana/tutorputor-db";
import { registerContentStudioRoutes } from "./studio/routes.js";
import { createContentStudioService } from "./studio/service.js";
import { registerCMSRoutes } from "./cms/routes.js";
import { CMSServiceImpl } from "./cms/service.js";
import { registerContentRoutes } from "./routes.js";
import { ContentServiceImpl } from "./service.js";

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

  const contentService = new ContentServiceImpl(prisma, /* aiProxy */ undefined);
  const cmsService = new CMSServiceImpl(prisma, contentService, /* aiProxy */ undefined);

  // Register Content routes (ContentService)
  await registerContentRoutes(app, { contentService });

  // Register Content Studio routes
  registerContentStudioRoutes(app, {
    contentStudioService,
    prefixes: ["/content-studio"],
  });

  // Register CMS routes
  registerCMSRoutes(app, { cmsService });

  app.log.info("✅ Content module routes registered");
};
