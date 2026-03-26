/**
 * Versioning Service
 *
 * Manages canvas document version history: creating snapshots, listing
 * versions, restoring to a previous version, and comparing versions.
 * Reuses the existing CanvasVersion Prisma model.
 *
 * @doc.type class
 * @doc.purpose Canvas document version-control service
 * @doc.layer product
 * @doc.pattern Service
 */

import { getPrismaClient, type PrismaClient } from '../../database/client';

// ============================================================================
// Types
// ============================================================================

export type ChangeType = 'MANUAL_SAVE' | 'AUTO_SAVE' | 'RESTORE' | 'MERGE';

export interface CreateSnapshotInput {
  canvasId: string;
  content: unknown;
  changeType?: ChangeType;
  changedBy?: string;
  changeSummary?: string;
}

export interface RestoreVersionInput {
  canvasId: string;
  targetVersion: number;
  restoredBy?: string;
}

export interface VersionListItem {
  id: string;
  canvasId: string;
  version: number;
  changeType: string;
  changedBy: string | null;
  changeSummary: string | null;
  createdAt: string;
}

// ============================================================================
// Service
// ============================================================================

/**
 * Service for managing canvas document versions.
 *
 * @doc.type class
 * @doc.purpose Canvas version snapshot, restore, and history
 * @doc.layer product
 * @doc.pattern Service
 */
export class VersioningService {
  private prisma: PrismaClient;

  constructor(prisma?: PrismaClient) {
    this.prisma = prisma ?? getPrismaClient();
  }

  // --------------------------------------------------------------------------
  // Queries
  // --------------------------------------------------------------------------

  /**
   * List all versions for a canvas document, newest first.
   */
  async listVersions(canvasId: string): Promise<VersionListItem[]> {
    const versions = await this.prisma.canvasVersion.findMany({
      where: { canvasId },
      orderBy: { version: 'desc' },
      select: {
        id: true,
        canvasId: true,
        version: true,
        changeType: true,
        changedBy: true,
        changeSummary: true,
        createdAt: true,
      },
    });

    return versions.map((v) => ({
      ...v,
      createdAt: v.createdAt.toISOString(),
    }));
  }

  /**
   * Get a specific version by canvas ID and version number.
   */
  async getVersion(canvasId: string, version: number) {
    const v = await this.prisma.canvasVersion.findUnique({
      where: { canvasId_version: { canvasId, version } },
    });

    if (!v) return null;

    return {
      ...v,
      content: v.content,
      createdAt: v.createdAt.toISOString(),
    };
  }

  // --------------------------------------------------------------------------
  // Mutations
  // --------------------------------------------------------------------------

  /**
   * Create a new snapshot of a canvas document.
   * The version number is automatically derived as MAX(version) + 1.
   */
  async createSnapshot(input: CreateSnapshotInput) {
    // Determine the next version number
    const latest = await this.prisma.canvasVersion.findFirst({
      where: { canvasId: input.canvasId },
      orderBy: { version: 'desc' },
      select: { version: true },
    });

    const nextVersion = (latest?.version ?? 0) + 1;

    const snapshot = await this.prisma.canvasVersion.create({
      data: {
        canvasId: input.canvasId,
        version: nextVersion,
        content: input.content as never,
        changeType: input.changeType ?? 'MANUAL_SAVE',
        changedBy: input.changedBy,
        changeSummary: input.changeSummary,
      },
    });

    return {
      ...snapshot,
      createdAt: snapshot.createdAt.toISOString(),
    };
  }

  /**
   * Restore a canvas document to a previously saved version.
   * A new RESTORE snapshot is created so the history is never lost.
   */
  async restore(input: RestoreVersionInput) {
    const target = await this.prisma.canvasVersion.findUnique({
      where: {
        canvasId_version: {
          canvasId: input.canvasId,
          version: input.targetVersion,
        },
      },
    });

    if (!target) {
      throw new Error(
        `Version ${input.targetVersion} not found for canvas ${input.canvasId}`
      );
    }

    // Apply the restored content to the live canvas document
    await this.prisma.canvasDocument.update({
      where: { id: input.canvasId },
      data: { content: target.content as never },
    });

    // Record the restore as a new snapshot
    return this.createSnapshot({
      canvasId: input.canvasId,
      content: target.content,
      changeType: 'RESTORE',
      changedBy: input.restoredBy,
      changeSummary: `Restored to version ${input.targetVersion}`,
    });
  }

  /**
   * Delete all versions beyond the most recent N for a given canvas.
   * Useful for garbage-collection of old history.
   */
  async pruneHistory(canvasId: string, keepCount: number): Promise<number> {
    const versions = await this.prisma.canvasVersion.findMany({
      where: { canvasId },
      orderBy: { version: 'desc' },
      select: { id: true },
    });

    const toDelete = versions.slice(keepCount);

    if (toDelete.length === 0) return 0;

    const { count } = await this.prisma.canvasVersion.deleteMany({
      where: { id: { in: toDelete.map((v) => v.id) } },
    });

    return count;
  }
}

// Lazy singleton
let _instance: VersioningService | null = null;

/**
 * Returns the singleton VersioningService instance.
 *
 * @doc.type function
 * @doc.purpose Lazy-initialise VersioningService singleton
 * @doc.layer product
 * @doc.pattern Factory
 */
export function getVersioningService(): VersioningService {
  if (!_instance) {
    _instance = new VersioningService();
  }
  return _instance;
}
