/**
 * Content Versioning Routes
 *
 * REST endpoints for content version history, comparison, and rollback.
 *
 * @doc.type routes
 * @doc.purpose Content versioning API endpoints
 * @doc.layer product
 * @doc.pattern REST API
 */
import type { FastifyInstance } from "fastify";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import { ContentVersioningService } from "./service.js";

export function registerVersioningRoutes(
  app: FastifyInstance,
  { prisma }: { prisma: TutorPrismaClient },
): void {
  const versioningService = new ContentVersioningService(
    prisma as unknown as ConstructorParameters<typeof ContentVersioningService>[0],
  );

  /**
   * POST /content/versions - Create a version snapshot
   */
  app.post<{
    Body: {
      contentId: string;
      contentType: string;
      tenantId: string;
      createdBy: string;
      changeSummary: string;
      isMajorVersion?: boolean;
    };
  }>("/versions", async (request, reply) => {
    const { contentId, contentType, tenantId, createdBy, changeSummary, isMajorVersion } = request.body;

    try {
      const snapshot = await versioningService.createVersion(
        contentId,
        contentType,
        tenantId,
        createdBy,
        changeSummary,
        isMajorVersion ?? false,
      );
      return reply.code(201).send(snapshot);
    } catch (error) {
      return reply.code(500).send({
        error: "Version creation failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * GET /content/versions - Get version history
   */
  app.get<{
    Querystring: {
      contentId: string;
      contentType: string;
      limit?: string;
      offset?: string;
    };
  }>("/versions", async (request, reply) => {
    const { contentId, contentType, limit, offset } = request.query;

    try {
      const opts = {
        contentId,
        contentType,
        ...(limit !== undefined && { limit: parseInt(limit, 10) }),
        ...(offset !== undefined && { offset: parseInt(offset, 10) }),
      };
      const result = await versioningService.getVersionHistory(opts);
      return reply.send(result);
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch version history",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * GET /content/versions/:versionId - Get specific version
   */
  app.get<{
    Params: { versionId: string };
  }>("/versions/:versionId", async (request, reply) => {
    const { versionId } = request.params;

    try {
      const version = await versioningService.getVersion(versionId);
      if (!version) {
        return reply.code(404).send({ error: "Version not found" });
      }
      return reply.send(version);
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch version",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /content/versions/compare - Compare two versions
   */
  app.post<{
    Body: { versionId1: string; versionId2: string };
  }>("/versions/compare", async (request, reply) => {
    const { versionId1, versionId2 } = request.body;

    try {
      const comparison = await versioningService.compareVersions(
        versionId1,
        versionId2,
      );
      return reply.send(comparison);
    } catch (error) {
      return reply.code(500).send({
        error: "Version comparison failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /content/versions/:versionId/rollback - Rollback to version
   */
  app.post<{
    Params: { versionId: string };
    Body: {
      contentId: string;
      contentType: string;
      tenantId: string;
      rolledBackBy: string;
    };
  }>("/versions/:versionId/rollback", async (request, reply) => {
    const { versionId } = request.params;
    const { contentId, contentType, tenantId, rolledBackBy } = request.body;

    try {
      const snapshot = await versioningService.rollback(
        contentId,
        contentType,
        tenantId,
        versionId,
        rolledBackBy,
      );
      return reply.send(snapshot);
    } catch (error) {
      return reply.code(500).send({
        error: "Rollback failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });
}
