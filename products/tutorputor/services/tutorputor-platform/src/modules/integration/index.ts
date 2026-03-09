import type { FastifyPluginAsync } from "fastify";
import { ltiRoutes } from "./lti/routes.js";
import { marketplaceRoutes } from "./marketplace/routes.js";
import { billingRoutes } from "./billing/routes.js";

/**
 * Integration module - consolidates:
 * - tutorputor-lti → lti/
 * - tutorputor-marketplace → marketplace/
 * - tutorputor-billing → billing/
 *
 * @doc.type module
 * @doc.purpose External integrations (LTI, marketplace, billing)
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const integrationModule: FastifyPluginAsync = async (app) => {
  await app.register(ltiRoutes, { prefix: "/lti" });
  await app.register(marketplaceRoutes, { prefix: "/marketplace" });
  await app.register(billingRoutes, { prefix: "/billing" });

  app.get("/health", async () => ({
    module: "integration",
    status: "healthy",
    submodules: ["lti", "marketplace", "billing"],
  }));

  app.log.info(
    "Integration module registered with LTI, marketplace, and billing routes",
  );
};
