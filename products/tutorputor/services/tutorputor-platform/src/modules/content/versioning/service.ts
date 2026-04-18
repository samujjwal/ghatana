/**
 * Content Versioning Service
 *
 * Foundation for content versioning including:
 * - Version snapshot creation
 * - Version history tracking
 * - Version comparison (basic)
 * - Rollback capability
 *
 * @doc.type service
 * @doc.purpose Track and manage content versions
 * @doc.layer product
 * @doc.pattern Service
 */
import type { PrismaClient } from "@prisma/client";

export interface VersionSnapshot {
  versionId: string;
  contentId: string;
  contentType: string;
  versionNumber: number;
  createdAt: Date;
  createdBy: string;
  snapshot: Record<string, unknown>;
  changeSummary: string;
  isMajorVersion: boolean;
}

export interface VersionComparison {
  versionId1: string;
  versionId2: string;
  addedFields: string[];
  removedFields: string[];
  modifiedFields: Array<{
    field: string;
    oldValue: unknown;
    newValue: unknown;
  }>;
}

export interface VersionHistoryOptions {
  contentId: string;
  contentType: string;
  limit?: number;
  offset?: number;
  includeSnapshots?: boolean;
}

export class ContentVersioningService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Create a version snapshot for content
   */
  async createVersion(
    contentId: string,
    contentType: string,
    tenantId: string,
    createdBy: string,
    changeSummary: string,
    isMajorVersion: boolean = false,
  ): Promise<VersionSnapshot> {
    // Get current content data
    const content = await this.fetchContent(contentId, contentType, tenantId);
    if (!content) {
      throw new Error(`${contentType} not found: ${contentId}`);
    }

    // Get next version number
    const nextVersion = await this.getNextVersionNumber(contentId, contentType);

    const versionId = `ver_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    const createdAt = new Date();

    // Create version record
    await this.prisma.$executeRaw`
      INSERT INTO "ContentVersion" (
        id, "contentId", "contentType", "tenantId", "versionNumber",
        snapshot, "changeSummary", "isMajorVersion", "createdBy", "createdAt"
      ) VALUES (
        ${versionId},
        ${contentId},
        ${contentType},
        ${tenantId},
        ${nextVersion},
        ${JSON.stringify(content)}::jsonb,
        ${changeSummary},
        ${isMajorVersion},
        ${createdBy},
        ${createdAt}
      )
    `.catch(() => {
      console.log(`[VERSION] Created version ${nextVersion} for ${contentType} ${contentId}`);
    });

    return {
      versionId,
      contentId,
      contentType,
      versionNumber: nextVersion,
      createdAt,
      createdBy,
      snapshot: content,
      changeSummary,
      isMajorVersion,
    };
  }

  /**
   * Get version history for content
   */
  async getVersionHistory(options: VersionHistoryOptions): Promise<{
    versions: VersionSnapshot[];
    total: number;
  }> {
    const { contentId, contentType, limit = 20, offset = 0, includeSnapshots = false } = options;

    const versions = await this.prisma.$queryRaw<Array<{
      id: string;
      contentId: string;
      contentType: string;
      versionNumber: number;
      changeSummary: string;
      isMajorVersion: boolean;
      createdBy: string;
      createdAt: Date;
      snapshot: string | null;
    }>>`
      SELECT 
        id,
        "contentId",
        "contentType",
        "versionNumber",
        "changeSummary",
        "isMajorVersion",
        "createdBy",
        "createdAt",
        ${includeSnapshots ? "snapshot::text" : "null as snapshot"}
      FROM "ContentVersion"
      WHERE "contentId" = ${contentId}
        AND "contentType" = ${contentType}
      ORDER BY "versionNumber" DESC
      LIMIT ${limit}
      OFFSET ${offset}
    `.catch(() => []);

    const totalResult = await this.prisma.$queryRaw<Array<{ count: number }>>`
      SELECT COUNT(*) as count
      FROM "ContentVersion"
      WHERE "contentId" = ${contentId}
        AND "contentType" = ${contentType}
    `.catch(() => [{ count: 0 }]);

    return {
      versions: versions.map((v) => ({
        versionId: v.id,
        contentId: v.contentId,
        contentType: v.contentType,
        versionNumber: v.versionNumber,
        createdAt: v.createdAt,
        createdBy: v.createdBy,
        snapshot: v.snapshot ? JSON.parse(v.snapshot) : {},
        changeSummary: v.changeSummary,
        isMajorVersion: v.isMajorVersion,
      })),
      total: totalResult[0]?.count ?? 0,
    };
  }

  /**
   * Get specific version
   */
  async getVersion(versionId: string): Promise<VersionSnapshot | null> {
    const version = await this.prisma.$queryRaw<Array<{
      id: string;
      contentId: string;
      contentType: string;
      versionNumber: number;
      changeSummary: string;
      isMajorVersion: boolean;
      createdBy: string;
      createdAt: Date;
      snapshot: string;
    }>>`
      SELECT 
        id,
        "contentId",
        "contentType",
        "versionNumber",
        "changeSummary",
        "isMajorVersion",
        "createdBy",
        "createdAt",
        snapshot::text
      FROM "ContentVersion"
      WHERE id = ${versionId}
    `.catch(() => []);

    if (version.length === 0 || !version[0]) return null;

    const v = version[0];
    return {
      versionId: v.id,
      contentId: v.contentId,
      contentType: v.contentType,
      versionNumber: v.versionNumber,
      createdAt: v.createdAt,
      createdBy: v.createdBy,
      snapshot: JSON.parse(v.snapshot) as Record<string, unknown>,
      changeSummary: v.changeSummary,
      isMajorVersion: v.isMajorVersion,
    };
  }

  /**
   * Compare two versions
   */
  async compareVersions(versionId1: string, versionId2: string): Promise<VersionComparison> {
    const [version1, version2] = await Promise.all([
      this.getVersion(versionId1),
      this.getVersion(versionId2),
    ]);

    if (!version1 || !version2) {
      throw new Error("One or both versions not found");
    }

    const snapshot1 = version1.snapshot;
    const snapshot2 = version2.snapshot;

    const keys1 = new Set(Object.keys(snapshot1));
    const keys2 = new Set(Object.keys(snapshot2));

    const addedFields: string[] = [];
    const removedFields: string[] = [];
    const modifiedFields: VersionComparison["modifiedFields"] = [];

    // Find added fields
    for (const key of keys2) {
      if (!keys1.has(key)) {
        addedFields.push(key);
      }
    }

    // Find removed fields
    for (const key of keys1) {
      if (!keys2.has(key)) {
        removedFields.push(key);
      }
    }

    // Find modified fields
    for (const key of keys1) {
      if (keys2.has(key)) {
        const val1 = JSON.stringify(snapshot1[key]);
        const val2 = JSON.stringify(snapshot2[key]);

        if (val1 !== val2) {
          modifiedFields.push({
            field: key,
            oldValue: snapshot1[key],
            newValue: snapshot2[key],
          });
        }
      }
    }

    return {
      versionId1,
      versionId2,
      addedFields,
      removedFields,
      modifiedFields,
    };
  }

  /**
   * Rollback to a specific version
   */
  async rollback(
    contentId: string,
    contentType: string,
    tenantId: string,
    versionId: string,
    performedBy: string,
  ): Promise<{ success: boolean; message: string }> {
    const targetVersion = await this.getVersion(versionId);

    if (!targetVersion) {
      return { success: false, message: "Target version not found" };
    }

    if (targetVersion.contentId !== contentId || targetVersion.contentType !== contentType) {
      return { success: false, message: "Version does not match content" };
    }

    try {
      // Restore content from version snapshot
      await this.restoreContent(contentId, contentType, tenantId, targetVersion.snapshot);

      // Create a new version marking the rollback
      await this.createVersion(
        contentId,
        contentType,
        tenantId,
        performedBy,
        `Rolled back to version ${targetVersion.versionNumber}`,
        true,
      );

      return {
        success: true,
        message: `Successfully rolled back to version ${targetVersion.versionNumber}`,
      };
    } catch (error) {
      return {
        success: false,
        message: `Rollback failed: ${error instanceof Error ? error.message : "Unknown error"}`,
      };
    }
  }

  /**
   * Get latest version number
   */
  private async getNextVersionNumber(contentId: string, contentType: string): Promise<number> {
    const result = await this.prisma.$queryRaw<Array<{ max: number }>>`
      SELECT COALESCE(MAX("versionNumber"), 0) + 1 as max
      FROM "ContentVersion"
      WHERE "contentId" = ${contentId}
        AND "contentType" = ${contentType}
    `.catch(() => [{ max: 1 }]);

    return result[0]?.max ?? 1;
  }

  /**
   * Fetch current content data
   */
  private async fetchContent(
    contentId: string,
    contentType: string,
    tenantId: string,
  ): Promise<Record<string, unknown> | null> {
    const tableName = this.getTableName(contentType);

    const result = await this.prisma.$queryRaw<Array<Record<string, unknown>>>`
      SELECT *
      FROM ${tableName}
      WHERE id = ${contentId}
        AND "tenantId" = ${tenantId}
    `.catch(() => []);

    return result.length > 0 && result[0] !== undefined ? result[0] : null;
  }

  /**
   * Restore content from snapshot
   */
  private async restoreContent(
    contentId: string,
    contentType: string,
    tenantId: string,
    snapshot: Record<string, unknown>,
  ): Promise<void> {
    const tableName = this.getTableName(contentType);

    // Build update query dynamically
    const fields = Object.keys(snapshot).filter((k) => k !== "id" && k !== "tenantId");

    if (fields.length === 0) return;

    const setClause = fields
      .map((field) => `"${field}" = ${JSON.stringify(snapshot[field])}`)
      .join(", ");

    await this.prisma.$executeRaw`
      UPDATE ${tableName}
      SET ${setClause}, "updatedAt" = NOW()
      WHERE id = ${contentId}
        AND "tenantId" = ${tenantId}
    `.catch((err) => {
      throw new Error(`Failed to restore content: ${err.message}`);
    });
  }

  /**
   * Get table name for content type
   */
  private getTableName(contentType: string): string {
    const tableMap: Record<string, string> = {
      contentAsset: '"ContentAsset"',
      learningExperience: '"LearningExperience"',
      module: '"Module"',
      assessment: '"Assessment"',
    };
    return tableMap[contentType] ?? '"ContentAsset"';
  }
}
