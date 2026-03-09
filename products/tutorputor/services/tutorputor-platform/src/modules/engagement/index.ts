import type { FastifyPluginAsync } from "fastify";
import { credentialsRoutes } from "./credentials/routes.js";
import { gamificationRoutes } from "./gamification/routes.js";
import { socialRoutes } from "./social/routes.js";

/**
 * Engagement module - consolidates:
 * - tutorputor-credentials → credentials/
 * - tutorputor-gamification → gamification/
 * - tutorputor-social → social/
 *
 * @doc.type module
 * @doc.purpose Student engagement features (badges, points, social)
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const engagementModule: FastifyPluginAsync = async (app) => {
  await app.register(credentialsRoutes, { prefix: "/credentials" });
  await app.register(gamificationRoutes, { prefix: "/gamification" });
  await app.register(socialRoutes, { prefix: "/social" });

  app.get("/health", async () => ({
    module: "engagement",
    status: "healthy",
    submodules: ["credentials", "gamification", "social"],
  }));

  app.log.info(
    "Engagement module registered with credentials, gamification, and social routes",
  );
};
