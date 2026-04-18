/**
 * Content Restore Routes
 *
 * REST endpoints for soft delete, trash management, and content restore.
 *
 * @doc.type routes
 * @doc.purpose Content trash and restore API endpoints
 * @doc.layer product
 * @doc.pattern REST API
 */
import type { FastifyInstance } from "fastify";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import {
  ContentRestoreService,
  type TrashItem,
} from "../studio/restore-service.js";

export function registerRestoreRoutes(
  app: FastifyInstance,
  { prisma }: { prisma: TutorPrismaClient },
): void {
  const restoreService = new ContentRestoreService(prisma as unknown as ConstructorParameters<typeof ContentRestoreService>[0]);

  /**
   * POST /content/trash - Soft delete a content item
   */
  app.post<{
    Body: {
      tenantId: string;
      itemId: string;
      itemType: TrashItem["itemType"];
      deletedBy: string;
    };
  }>("/trash", async (request, reply) => {
    const { tenantId, itemId, itemType, deletedBy } = request.body;

    try {
      const trashItem = await restoreService.softDelete(
        tenantId,
        itemId,
        itemType,
        deletedBy,
      );
      return reply.code(200).send(trashItem);
    } catch (error) {
      return reply.code(500).send({
        error: "Soft delete failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * GET /content/trash - List items in trash
   */
  app.get<{
    Querystring: {
      tenantId: string;
      itemType?: string;
      deletedBy?: string;
      limit?: string;
      offset?: string;
    };
  }>("/trash", async (request, reply) => {
    const { tenantId, itemType, deletedBy, limit, offset } = request.query;

    try {
      const opts = {
        tenantId,
        ...(itemType !== undefined && { itemType }),
        ...(deletedBy !== undefined && { deletedBy }),
        ...(limit !== undefined && { limit: parseInt(limit, 10) }),
        ...(offset !== undefined && { offset: parseInt(offset, 10) }),
      };
      const result = await restoreService.getTrashItems(opts);
      return reply.send(result);
    } catch (error) {
      return reply.code(500).send({
        error: "Failed to fetch trash items",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /content/trash/:trashItemId/restore - Restore a trash item
   */
  app.post<{
    Params: { trashItemId: string };
    Body: { tenantId: string; restoredBy: string };
  }>("/trash/:trashItemId/restore", async (request, reply) => {
    const { trashItemId } = request.params;
    const { tenantId, restoredBy } = request.body;

    try {
      const result = await restoreService.restore(
        tenantId,
        trashItemId,
        restoredBy,
      );
      return reply.send(result);
    } catch (error) {
      return reply.code(500).send({
        error: "Restore failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * DELETE /content/trash/:trashItemId - Permanently delete a trash item
   */
  app.delete<{
    Params: { trashItemId: string };
    Querystring: { tenantId: string; deletedBy: string };
  }>("/trash/:trashItemId", async (request, reply) => {
    const { trashItemId } = request.params;
    const { tenantId, deletedBy } = request.query;

    try {
      const result = await restoreService.permanentDelete(
        tenantId,
        trashItemId,
      );
      return reply.send(result);
    } catch (error) {
      return reply.code(500).send({
        error: "Permanent delete failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /content/trash/restore-batch - Restore multiple trash items
   */
  app.post<{
    Body: {
      tenantId: string;
      trashItemIds: string[];
      restoredBy: string;
    };
  }>("/trash/restore-batch", async (request, reply) => {
    const { tenantId, trashItemIds, restoredBy } = request.body;

    try {
      const results = await restoreService.batchRestore(
        tenantId,
        trashItemIds,
        restoredBy,
      );
      return reply.send({ results });
    } catch (error) {
      return reply.code(500).send({
        error: "Batch restore failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  /**
   * POST /content/trash/purge-expired - Purge expired trash items
   */
  app.post<{
    Body: { tenantId: string };
  }>("/trash/purge-expired", async (request, reply) => {
    const { tenantId } = request.body;

    try {
      const result = await restoreService.cleanupExpiredTrash();
      return reply.send({ purgedCount: result.deleted, items: result.items });
    } catch (error) {
      return reply.code(500).send({
        error: "Purge failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });
}
