import type { FastifyPluginAsync } from "fastify";
import { teacherRoutes } from "./teacher/routes.js";
import { adminRoutes } from "./admin/routes.js";

/**
 * User module - consolidates:
 * - tutorputor-teacher → teacher/
 * - tutorputor-institution-admin → admin/
 *
 * @doc.type module
 * @doc.purpose User management for teachers and institutional administrators
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const userModule: FastifyPluginAsync = async (app) => {
  // Register teacher routes
  await app.register(teacherRoutes, { prefix: "/teacher" });

  // Register institution admin routes
  await app.register(adminRoutes, { prefix: "/admin" });

  // Health check
  app.get("/health", async (request, reply) => {
    return {
      module: "user",
      status: "healthy",
      submodules: ["teacher", "admin"],
    };
  });

  app.log.info("User module registered with teacher and admin routes");
};
