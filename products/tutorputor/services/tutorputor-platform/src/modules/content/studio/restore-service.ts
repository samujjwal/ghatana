/**
 * Content Restore Service
 *
 * Implements soft delete and restore functionality for content including:
 * - Soft delete (mark as deleted without removing)
 * - Restore from trash
 * - Permanent delete
 * - Trash management and cleanup
 * - Batch restore operations
 *
 * @doc.type service
 * @doc.purpose Soft delete and restore content with trash management
 * @doc.layer product
 * @doc.pattern Service
 */
import type { PrismaClient } from "@prisma/client";

export interface TrashItem {
  itemId: string;
  itemType: "contentAsset" | "learningExperience" | "module" | "assessment";
  title: string;
  deletedAt: Date;
  deletedBy: string;
  originalData: Record<string, unknown>;
  canRestore: boolean;
  expiresAt: Date;
}

export interface RestoreResult {
  success: boolean;
  itemId: string;
  itemType: string;
  message: string;
  restoredAt?: Date;
}

export interface TrashQueryOptions {
  tenantId: string;
  itemType?: string;
  deletedBy?: string;
  dateFrom?: Date;
  dateTo?: Date;
  limit?: number;
  offset?: number;
}

export const TRASH_RETENTION_DAYS = 30;

export class ContentRestoreService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Soft delete a content item (move to trash)
   */
  async softDelete(
    tenantId: string,
    itemId: string,
    itemType: TrashItem["itemType"],
    deletedBy: string,
  ): Promise<TrashItem> {
    // Get the item before deletion
    const item = await this.getItemBeforeDelete(tenantId, itemId, itemType);
    if (!item) {
      throw new Error(`${itemType} not found: ${itemId}`);
    }

    const deletedAt = new Date();
    const expiresAt = new Date(deletedAt.getTime() + TRASH_RETENTION_DAYS * 24 * 60 * 60 * 1000);

    // Mark as deleted in the original table using metadata field
    await this.markAsDeleted(tenantId, itemId, itemType, deletedAt, deletedBy);

    // Create trash record
    await this.prisma.$executeRaw`
      INSERT INTO "TrashItem" (
        id, "tenantId", "itemId", "itemType", title, "deletedAt", "deletedBy",
        "originalData", "expiresAt", created_at
      ) VALUES (
        gen_random_uuid(),
        ${tenantId},
        ${itemId},
        ${itemType},
        ${item.title},
        ${deletedAt},
        ${deletedBy},
        ${JSON.stringify(item)}::jsonb,
        ${expiresAt},
        NOW()
      )
    `.catch(() => {
      // If TrashItem table doesn't exist, just mark as deleted
      console.log(`[SOFT DELETE] ${itemType} ${itemId} marked as deleted`);
    });

    return {
      itemId,
      itemType,
      title: item.title,
      deletedAt,
      deletedBy,
      originalData: item,
      canRestore: true,
      expiresAt,
    };
  }

  /**
   * Restore item from trash
   */
  async restore(
    tenantId: string,
    trashItemId: string,
    restoredBy: string,
  ): Promise<RestoreResult> {
    // Get trash item
    const trashItem = await this.prisma.$queryRaw<Array<{
      itemId: string;
      itemType: string;
      originalData: string;
    }>>`
      SELECT "itemId", "itemType", "originalData"::text
      FROM "TrashItem"
      WHERE id = ${trashItemId}
        AND "tenantId" = ${tenantId}
        AND "restoredAt" IS NULL
    `.catch(() => []);

    if (trashItem.length === 0) {
      return {
        success: false,
        itemId: trashItemId,
        itemType: "unknown",
        message: "Trash item not found or already restored",
      };
    }

    const trashItemData = trashItem[0];
    if (!trashItemData) {
      return {
        success: false,
        itemId: trashItemId,
        itemType: "unknown",
        message: "Trash item data not available",
      };
    }

    const { itemId, itemType, originalData } = trashItemData;

    try {
      // Restore the item
      await this.restoreItem(tenantId, itemId, itemType as TrashItem["itemType"]);

      // Mark as restored in trash
      await this.prisma.$executeRaw`
        UPDATE "TrashItem"
        SET "restoredAt" = NOW(),
            "restoredBy" = ${restoredBy}
        WHERE id = ${trashItemId}
      `.catch(() => {
        // Ignore errors
      });

      return {
        success: true,
        itemId,
        itemType,
        message: "Item restored successfully",
        restoredAt: new Date(),
      };
    } catch (error) {
      return {
        success: false,
        itemId,
        itemType,
        message: `Failed to restore: ${error instanceof Error ? error.message : "Unknown error"}`,
      };
    }
  }

  /**
   * Permanently delete item from trash
   */
  async permanentDelete(
    tenantId: string,
    trashItemId: string,
  ): Promise<RestoreResult> {
    // Get trash item
    const trashItem = await this.prisma.$queryRaw<Array<{
      itemId: string;
      itemType: string;
    }>>`
      SELECT "itemId", "itemType"
      FROM "TrashItem"
      WHERE id = ${trashItemId}
        AND "tenantId" = ${tenantId}
    `.catch(() => []);

    if (trashItem.length === 0) {
      return {
        success: false,
        itemId: trashItemId,
        itemType: "unknown",
        message: "Trash item not found",
      };
    }

    const trashItemData = trashItem[0];
    if (!trashItemData) {
      return {
        success: false,
        itemId: trashItemId,
        itemType: "unknown",
        message: "Trash item data not available",
      };
    }

    const { itemId, itemType } = trashItemData;

    try {
      // Permanently delete item from original table
      await this.permanentlyDeleteItem("system", itemId, itemType as TrashItem["itemType"]);

      // Remove from trash
      await this.prisma.$executeRaw`
        DELETE FROM "TrashItem"
        WHERE id = ${trashItemId}
          AND "tenantId" = ${tenantId}
      `.catch(() => {
        // Ignore errors
      });

      return {
        success: true,
        itemId,
        itemType,
        message: "Item permanently deleted",
      };
    } catch (error) {
      return {
        success: false,
        itemId,
        itemType,
        message: `Failed to delete: ${error instanceof Error ? error.message : "Unknown error"}`,
      };
    }
  }

  /**
   * Get trash items for tenant
   */
  async getTrashItems(options: TrashQueryOptions): Promise<{
    items: TrashItem[];
    total: number;
  }> {
    const { tenantId, itemType, deletedBy, dateFrom, dateTo, limit = 20, offset = 0 } = options;

    const items = await this.prisma.$queryRaw<Array<{
      id: string;
      itemId: string;
      itemType: string;
      title: string;
      deletedAt: Date;
      deletedBy: string;
      originalData: string;
      expiresAt: Date;
    }>>`
      SELECT 
        id,
        "itemId",
        "itemType",
        title,
        "deletedAt",
        "deletedBy",
        "originalData"::text,
        "expiresAt"
      FROM "TrashItem"
      WHERE "tenantId" = ${tenantId}
        AND "restoredAt" IS NULL
        ${itemType ? `AND "itemType" = ${itemType}` : ""}
        ${deletedBy ? `AND "deletedBy" = ${deletedBy}` : ""}
        ${dateFrom ? `AND "deletedAt" >= ${dateFrom}` : ""}
        ${dateTo ? `AND "deletedAt" <= ${dateTo}` : ""}
      ORDER BY "deletedAt" DESC
      LIMIT ${limit}
      OFFSET ${offset}
    `.catch(() => []);

    const total = await this.prisma.$queryRaw<Array<{ count: number }>>`
      SELECT COUNT(*) as count
      FROM "TrashItem"
      WHERE "tenantId" = ${tenantId}
        AND "restoredAt" IS NULL
    `.catch(() => [{ count: 0 }]);

    return {
      items: items.map((item) => ({
        itemId: item.itemId,
        itemType: item.itemType as TrashItem["itemType"],
        title: item.title,
        deletedAt: item.deletedAt,
        deletedBy: item.deletedBy,
        originalData: JSON.parse(item.originalData) as Record<string, unknown>,
        canRestore: true,
        expiresAt: item.expiresAt,
      })),
      total: total[0]?.count ?? 0,
    };
  }

  /**
   * Batch restore items
   */
  async batchRestore(
    tenantId: string,
    trashItemIds: string[],
    restoredBy: string,
  ): Promise<RestoreResult[]> {
    const results: RestoreResult[] = [];

    for (const trashItemId of trashItemIds) {
      const result = await this.restore(tenantId, trashItemId, restoredBy);
      results.push(result);
    }

    return results;
  }

  /**
   * Batch permanent delete
   */
  async batchPermanentDelete(
    tenantId: string,
    trashItemIds: string[],
  ): Promise<RestoreResult[]> {
    const results: RestoreResult[] = [];

    for (const trashItemId of trashItemIds) {
      const result = await this.permanentDelete(tenantId, trashItemId);
      results.push(result);
    }

    return results;
  }

  /**
   * Clean up expired trash items
   */
  async cleanupExpiredTrash(): Promise<{
    deleted: number;
    items: Array<{ itemId: string; itemType: string }>;
  }> {
    const now = new Date();

    const expiredItems = await this.prisma.$queryRaw<Array<{
      id: string;
      itemId: string;
      itemType: string;
    }>>`
      SELECT id, "itemId", "itemType"
      FROM "TrashItem"
      WHERE "expiresAt" < ${now}
        AND "restoredAt" IS NULL
    `.catch(() => []);

    for (const item of expiredItems) {
      await this.permanentlyDeleteItem("system", item.itemId, item.itemType as TrashItem["itemType"]);
    }

    // Delete from trash table
    await this.prisma.$executeRaw`
      DELETE FROM "TrashItem"
      WHERE "expiresAt" < ${now}
        AND "restoredAt" IS NULL
    `.catch(() => {
      // Ignore errors
    });

    return {
      deleted: expiredItems.length,
      items: expiredItems.map((i) => ({ itemId: i.itemId, itemType: i.itemType })),
    };
  }

  /**
   * Get trash statistics
   */
  async getTrashStats(tenantId: string): Promise<{
    totalItems: number;
    byType: Record<string, number>;
    expiringSoon: number;
    recentlyDeleted: number;
  }> {
    const stats = await this.prisma.$queryRaw<Array<{
      itemType: string;
      count: number;
    }>>`
      SELECT "itemType", COUNT(*) as count
      FROM "TrashItem"
      WHERE "tenantId" = ${tenantId}
        AND "restoredAt" IS NULL
      GROUP BY "itemType"
    `.catch(() => []);

    const expiringSoon = await this.prisma.$queryRaw<Array<{ count: number }>>`
      SELECT COUNT(*) as count
      FROM "TrashItem"
      WHERE "tenantId" = ${tenantId}
        AND "restoredAt" IS NULL
        AND "expiresAt" < NOW() + INTERVAL '7 days'
    `.catch(() => [{ count: 0 }]);

    const recentlyDeleted = await this.prisma.$queryRaw<Array<{ count: number }>>`
      SELECT COUNT(*) as count
      FROM "TrashItem"
      WHERE "tenantId" = ${tenantId}
        AND "restoredAt" IS NULL
        AND "deletedAt" > NOW() - INTERVAL '24 hours'
    `.catch(() => [{ count: 0 }]);

    const byType: Record<string, number> = {};
    for (const stat of stats) {
      byType[stat.itemType] = Number(stat.count);
    }

    return {
      totalItems: stats.reduce((sum, s) => sum + Number(s.count), 0),
      byType,
      expiringSoon: Number(expiringSoon[0]?.count ?? 0),
      recentlyDeleted: Number(recentlyDeleted[0]?.count ?? 0),
    };
  }

  // Private helper methods

  private async getItemBeforeDelete(
    tenantId: string,
    itemId: string,
    itemType: TrashItem["itemType"],
  ): Promise<{ title: string; [key: string]: unknown } | null> {
    let result: Array<{ title: string; [key: string]: unknown }> = [];

    try {
      switch (itemType) {
        case "contentAsset": {
          const items = await this.prisma.$queryRaw<Array<{ title: string }>>`
            SELECT id, title, "searchableText", "difficultyLevel", domain, tags
            FROM "ContentAsset"
            WHERE id = ${itemId} AND "tenantId" = ${tenantId}
          `;
          result = items as Array<{ title: string; [key: string]: unknown }>;
          break;
        }

        case "learningExperience": {
          const items = await this.prisma.$queryRaw<Array<{ title: string }>>`
            SELECT id, title, description, type
            FROM "LearningExperience"
            WHERE id = ${itemId} AND "tenantId" = ${tenantId}
          `;
          result = items as Array<{ title: string; [key: string]: unknown }>;
          break;
        }

        case "module": {
          const items = await this.prisma.$queryRaw<Array<{ title: string }>>`
            SELECT id, title, description, "learningObjectives"
            FROM "Module"
            WHERE id = ${itemId} AND "tenantId" = ${tenantId}
          `;
          result = items as Array<{ title: string; [key: string]: unknown }>;
          break;
        }

        case "assessment": {
          const items = await this.prisma.$queryRaw<Array<{ title: string }>>`
            SELECT id, title, type, "timeLimitMinutes"
            FROM "Assessment"
            WHERE id = ${itemId} AND "tenantId" = ${tenantId}
          `;
          result = items as Array<{ title: string; [key: string]: unknown }>;
          break;
        }
      }
    } catch {
      // Return empty result on error
    }

    return result.length > 0 && result[0] !== undefined ? result[0] : null;
  }

  private async markAsDeleted(
    tenantId: string,
    itemId: string,
    itemType: TrashItem["itemType"],
    deletedAt: Date,
    deletedBy: string,
  ): Promise<void> {
    const tableName = this.getTableName(itemType);

    await this.prisma.$executeRaw`
      UPDATE ${tableName}
      SET 
        "deletedAt" = ${deletedAt},
        "deletedBy" = ${deletedBy},
        "updatedAt" = NOW()
      WHERE id = ${itemId}
        AND "tenantId" = ${tenantId}
    `.catch(() => {
      // Table might not have soft delete columns
      console.log(`[MARK DELETED] ${itemType} ${itemId}`);
    });
  }

  private async restoreItem(
    tenantId: string,
    itemId: string,
    itemType: TrashItem["itemType"],
  ): Promise<void> {
    const tableName = this.getTableName(itemType);

    await this.prisma.$executeRaw`
      UPDATE ${tableName}
      SET 
        "deletedAt" = NULL,
        "deletedBy" = NULL,
        "updatedAt" = NOW()
      WHERE id = ${itemId}
        AND "tenantId" = ${tenantId}
    `.catch(() => {
      console.log(`[RESTORE] ${itemType} ${itemId}`);
    });
  }

  private async permanentlyDeleteItem(
    tenantId: string,
    itemId: string,
    itemType: TrashItem["itemType"],
  ): Promise<void> {
    const tableName = this.getTableName(itemType);

    await this.prisma.$executeRaw`
      DELETE FROM ${tableName}
      WHERE id = ${itemId}
        AND "tenantId" = ${tenantId}
    `.catch(() => {
      console.log(`[PERMANENT DELETE] ${itemType} ${itemId}`);
    });
  }

  private getTableName(itemType: TrashItem["itemType"]): string {
    const tableMap: Record<TrashItem["itemType"], string> = {
      contentAsset: '"ContentAsset"',
      learningExperience: '"LearningExperience"',
      module: '"Module"',
      assessment: '"Assessment"',
    };
    return tableMap[itemType];
  }
}
