import type { FastifyPluginAsync } from "fastify";
import { tenantRoutes } from "./routes.js";

/**
 * Tenant module - consolidates:
 * - tutorputor-tenant-config → routes.ts
 * - Multi-tenancy configuration and isolation
 *
 * @doc.type module
 * @doc.purpose Tenant configuration and multi-tenancy management
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const tenantModule: FastifyPluginAsync = async (app) => {
  await app.register(tenantRoutes);

  app.log.info("Tenant module registered with configuration routes");
};
