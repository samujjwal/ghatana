import type { FastifyPluginAsync } from "fastify";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import { collaborationRoutes } from "./routes.js";
import { registerGroupMatchingRoutes } from "./group-matching-routes.js";

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
  const prisma = app.prisma as TutorPrismaClient;

  await app.register(collaborationRoutes);

  registerGroupMatchingRoutes(app, { prisma });

  app.log.info(
    "Collaboration module registered with discussion, notes, and group matching routes",
  );
};
