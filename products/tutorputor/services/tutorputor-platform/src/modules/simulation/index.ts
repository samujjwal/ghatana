/**
 * Simulation Module
 *
 * Consolidates simulation authoring, runtime management,
 * and AI-powered manifest generation endpoints.
 *
 * @doc.type module
 * @doc.purpose Simulation-related HTTP endpoints
 * @doc.layer platform
 * @doc.pattern Module
 */
import type { FastifyPluginAsync } from "fastify";
import { simulationAuthoringRoutes } from "./authoring-routes.js";

export const simulationModule: FastifyPluginAsync = async (app) => {
  app.log.info("Initializing Simulation module...");

  await app.register(simulationAuthoringRoutes);

  app.log.info("✅ Simulation module routes registered");
};
