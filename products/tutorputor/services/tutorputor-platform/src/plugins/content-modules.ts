import type { FastifyPluginAsync } from "fastify";
import { contentModule } from "../modules/content/index.js";
import { simulationModule } from "../modules/simulation/index.js";
import { searchModule } from "../modules/search/index.js";
import { registerKernelRegistryRoutes } from "../modules/kernel-registry/fastify-routes.js";

/**
 * Content-domain module plugin.
 * Registers content, simulation, search, and kernel registry modules.
 *
 * @doc.type module
 * @doc.purpose Register content-domain modules (studio, simulation, search, kernel registry)
 * @doc.layer platform
 * @doc.pattern Plugin
 */
export const setupContentModules: FastifyPluginAsync = async (app) => {
  // Register content module (includes studio, CMS, generation, evaluation, etc.)
  await app.register(contentModule, { prefix: "/api" });

  // Register simulation module
  await app.register(simulationModule, { prefix: "/api/v1/simulations" });

  // Register search module
  await app.register(searchModule, { prefix: "/api/v1/search" });

  // Register kernel registry routes
  await registerKernelRegistryRoutes(app);

  app.log.info("✅ Content-domain modules registered");
};
