/**
 * Group Matching Routes
 *
 * REST endpoints for AI-powered study group suggestions and creation.
 *
 * @doc.type routes
 * @doc.purpose Study group matching and formation API endpoints
 * @doc.layer product
 * @doc.pattern REST API
 */
import type { FastifyInstance } from "fastify";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import { GroupMatchingService } from "./group-matching.js";

export function registerGroupMatchingRoutes(
  app: FastifyInstance,
  { prisma }: { prisma: TutorPrismaClient },
): void {
  const groupMatchingService = new GroupMatchingService(
    prisma as unknown as ConstructorParameters<typeof GroupMatchingService>[0],
  );

  /**
   * GET /collaboration/groups/suggested - Get suggested groups for a user
   */
  app.get<{
    Querystring: { tenantId: string; userId: string; limit?: string };
  }>("/groups/suggested", async (request, reply) => {
    const { tenantId, userId, limit } = request.query;

    try {
      const groups = await groupMatchingService.findMatchingGroups(
        tenantId,
        userId,
        limit ? parseInt(limit, 10) : undefined,
      );
      return reply.send({ groups });
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch suggested groups",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * GET /collaboration/groups/formation-suggestion - Get group formation suggestion
   */
  app.get<{
    Querystring: {
      tenantId: string;
      userId: string;
      targetSize?: string;
    };
  }>("/groups/formation-suggestion", async (request, reply) => {
    const { tenantId, userId, targetSize } = request.query;

    try {
      const suggestion = await groupMatchingService.suggestGroupFormation(
        tenantId,
        userId,
        targetSize ? parseInt(targetSize, 10) : undefined,
      );
      return reply.send({ suggestion });
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to generate group formation suggestion",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /collaboration/groups/overlap - Calculate learning overlap between users
   */
  app.post<{
    Body: {
      tenantId: string;
      userId1: string;
      userId2: string;
    };
  }>("/groups/overlap", async (request, reply) => {
    const { tenantId, userId1, userId2 } = request.body;

    try {
      const overlap = await groupMatchingService.getLearningOverlapBetweenUsers(
        tenantId,
        userId1,
        userId2,
      );
      return reply.send(overlap);
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to calculate learning overlap",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  app.get("/groups/health", async () => ({
    module: "collaboration/group-matching",
    status: "healthy",
  }));
}
