import type { FastifyPluginAsync } from "fastify";
import { collaborationRoutes } from "./routes.js";

/**
 * Collaboration module - consolidates:
 * - tutorputor-collaboration → routes.ts
 * - Discussion threads and shared notes
 *
 * @doc.type module
 * @doc.purpose Collaborative learning features (Q&A, shared notes)
 * @doc.layer product
 * @doc.pattern Modular Plugin
 */
export const collaborationModule: FastifyPluginAsync = async (app) => {
  await app.register(collaborationRoutes);

  app.log.info(
    "Collaboration module registered with discussion and notes routes",
  );
};
