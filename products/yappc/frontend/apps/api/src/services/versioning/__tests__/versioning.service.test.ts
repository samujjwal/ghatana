/**
 * VersioningService Tests
 *
 * Unit tests for version snapshot creation, retrieval, and restore logic.
 * Prisma is mocked at the constructor boundary — no database required.
 */

import { VersioningService } from '../versioning.service';
import type {
  CreateSnapshotInput,
  RestoreVersionInput,
} from '../versioning.service';

// ---------------------------------------------------------------------------
// Prisma Mock Factory
// ---------------------------------------------------------------------------

function makePrismaMock() {
  return {
    canvasVersion: {
      findMany: jest.fn(),
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      create: jest.fn(),
    },
    canvasDocument: {
      update: jest.fn(),
    },
  };
}

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

const NOW = new Date('2025-03-01T12:00:00Z');

function versionRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'ver-1',
    canvasId: 'canvas-1',
    version: 1,
    changeType: 'MANUAL_SAVE',
    changedBy: 'user-1',
    changeSummary: 'Initial save',
    content: { nodes: [] },
    createdAt: NOW,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// listVersions
// ---------------------------------------------------------------------------

describe('VersioningService.listVersions', () => {
  it('returns an empty array when no versions exist', async () => {
    const prisma = makePrismaMock();
    prisma.canvasVersion.findMany.mockResolvedValue([]);
    const svc = new VersioningService(prisma as never);

    expect(await svc.listVersions('canvas-1')).toEqual([]);
    expect(prisma.canvasVersion.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { canvasId: 'canvas-1' },
        orderBy: { version: 'desc' },
      })
    );
  });

  it('maps rows to VersionListItem with ISO createdAt', async () => {
    const prisma = makePrismaMock();
    prisma.canvasVersion.findMany.mockResolvedValue([
      versionRow(),
      versionRow({ id: 'ver-2', version: 2 }),
    ]);
    const svc = new VersioningService(prisma as never);

    const results = await svc.listVersions('canvas-1');

    expect(results).toHaveLength(2);
    expect(results[0].createdAt).toBe(NOW.toISOString());
    expect(results[0].id).toBe('ver-1');
  });

  it('does not include content field in listed versions', async () => {
    const prisma = makePrismaMock();
    prisma.canvasVersion.findMany.mockResolvedValue([versionRow()]);
    const svc = new VersioningService(prisma as never);

    const results = await svc.listVersions('canvas-1');
    // listVersions uses `select` which excludes content
    expect(results[0]).not.toHaveProperty('content');
  });
});

// ---------------------------------------------------------------------------
// getVersion
// ---------------------------------------------------------------------------

describe('VersioningService.getVersion', () => {
  it('returns null when version not found', async () => {
    const prisma = makePrismaMock();
    prisma.canvasVersion.findUnique.mockResolvedValue(null);
    const svc = new VersioningService(prisma as never);

    expect(await svc.getVersion('canvas-1', 99)).toBeNull();
    expect(prisma.canvasVersion.findUnique).toHaveBeenCalledWith({
      where: { canvasId_version: { canvasId: 'canvas-1', version: 99 } },
    });
  });

  it('returns version with ISO createdAt and content', async () => {
    const prisma = makePrismaMock();
    prisma.canvasVersion.findUnique.mockResolvedValue(versionRow());
    const svc = new VersioningService(prisma as never);

    const result = await svc.getVersion('canvas-1', 1);

    expect(result).not.toBeNull();
    expect(result!.createdAt).toBe(NOW.toISOString());
    expect(result!.content).toEqual({ nodes: [] });
  });
});

// ---------------------------------------------------------------------------
// createSnapshot
// ---------------------------------------------------------------------------

describe('VersioningService.createSnapshot', () => {
  it('auto-increments version from the latest snapshot (1 → 2)', async () => {
    const prisma = makePrismaMock();
    prisma.canvasVersion.findFirst.mockResolvedValue({ version: 1 });
    const created = versionRow({ version: 2 });
    prisma.canvasVersion.create.mockResolvedValue(created);
    const svc = new VersioningService(prisma as never);

    const input: CreateSnapshotInput = {
      canvasId: 'canvas-1',
      content: { nodes: [{ id: 'node-1' }] },
    };
    const result = await svc.createSnapshot(input);

    expect(prisma.canvasVersion.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          version: 2,
          changeType: 'MANUAL_SAVE',
        }),
      })
    );
    expect(result.version).toBe(2);
    expect(result.createdAt).toBe(NOW.toISOString());
  });

  it('starts at version 1 when no previous snapshot exists', async () => {
    const prisma = makePrismaMock();
    prisma.canvasVersion.findFirst.mockResolvedValue(null);
    prisma.canvasVersion.create.mockResolvedValue(versionRow({ version: 1 }));
    const svc = new VersioningService(prisma as never);

    await svc.createSnapshot({ canvasId: 'canvas-1', content: {} });

    expect(prisma.canvasVersion.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ version: 1 }),
      })
    );
  });

  it('uses the caller-supplied changeType when provided', async () => {
    const prisma = makePrismaMock();
    prisma.canvasVersion.findFirst.mockResolvedValue(null);
    prisma.canvasVersion.create.mockResolvedValue(
      versionRow({ changeType: 'AUTO_SAVE' })
    );
    const svc = new VersioningService(prisma as never);

    await svc.createSnapshot({
      canvasId: 'canvas-1',
      content: {},
      changeType: 'AUTO_SAVE',
    });

    expect(prisma.canvasVersion.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ changeType: 'AUTO_SAVE' }),
      })
    );
  });
});

// ---------------------------------------------------------------------------
// restore
// ---------------------------------------------------------------------------

describe('VersioningService.restore', () => {
  it('throws when target version does not exist', async () => {
    const prisma = makePrismaMock();
    prisma.canvasVersion.findUnique.mockResolvedValue(null);
    const svc = new VersioningService(prisma as never);

    const input: RestoreVersionInput = {
      canvasId: 'canvas-1',
      targetVersion: 5,
    };
    await expect(svc.restore(input)).rejects.toThrow(
      'Version 5 not found for canvas canvas-1'
    );
  });

  it('updates the live canvas document with the target content', async () => {
    const targetContent = { nodes: [{ id: 'restored-node' }] };
    const targetRow = versionRow({ version: 3, content: targetContent });
    const prisma = makePrismaMock();
    prisma.canvasVersion.findUnique.mockResolvedValue(targetRow);
    prisma.canvasDocument.update.mockResolvedValue({});
    // For the new RESTORE snapshot
    prisma.canvasVersion.findFirst.mockResolvedValue({ version: 4 });
    prisma.canvasVersion.create.mockResolvedValue(
      versionRow({ version: 5, changeType: 'RESTORE' })
    );
    const svc = new VersioningService(prisma as never);

    await svc.restore({ canvasId: 'canvas-1', targetVersion: 3 });

    expect(prisma.canvasDocument.update).toHaveBeenCalledWith({
      where: { id: 'canvas-1' },
      data: { content: targetContent },
    });
  });

  it('records a RESTORE snapshot so history is preserved', async () => {
    const targetRow = versionRow({ version: 2, content: {} });
    const prisma = makePrismaMock();
    prisma.canvasVersion.findUnique.mockResolvedValue(targetRow);
    prisma.canvasDocument.update.mockResolvedValue({});
    prisma.canvasVersion.findFirst.mockResolvedValue({ version: 3 });
    const restoreRow = versionRow({ version: 4, changeType: 'RESTORE' });
    prisma.canvasVersion.create.mockResolvedValue(restoreRow);
    const svc = new VersioningService(prisma as never);

    const result = await svc.restore({
      canvasId: 'canvas-1',
      targetVersion: 2,
      restoredBy: 'user-1',
    });

    expect(prisma.canvasVersion.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          changeType: 'RESTORE',
          changeSummary: 'Restored to version 2',
          changedBy: 'user-1',
        }),
      })
    );
    expect(result.changeType).toBe('RESTORE');
  });
});
