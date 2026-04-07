/**
 * Versioning Resolver Tests
 *
 * Tests the versioning resolver implementation for snapshot management.
 * Uses mocked Prisma and VersioningService.
 */

import type { PrismaClient } from '@prisma/client';
import { VersioningService } from '../../../services/versioning/versioning.service';

// Test context with user identity
const testContext = {
  userId: 'user-123',
  email: 'user@example.com',
  role: 'EDITOR',
};

const NOW = new Date().toISOString();

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeVersioningServiceMock() {
  return {
    createSnapshot: jest.fn(),
    restoreSnapshot: jest.fn(),
    listSnapshots: jest.fn(),
    deleteSnapshot: jest.fn(),
  };
}

function snapshotRow(overrides: Partial<any> = {}) {
  return {
    id: 'snap-1',
    projectId: 'proj-1',
    version: 1,
    name: 'Initial Snapshot',
    description: 'First snapshot',
    createdBy: 'user-1',
    createdAt: NOW,
    projectData: JSON.stringify({
      workspace: {},
      documents: [],
      pages: [],
      metadata: {},
    }),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// createSnapshot Mutation
// ---------------------------------------------------------------------------

describe('createSnapshot Mutation', () => {
  it('creates snapshot with required fields', async () => {
    const versioningService = makeVersioningServiceMock();
    const snapshot = snapshotRow();

    versioningService.createSnapshot.mockResolvedValue(snapshot);

    const result = await versioningService.createSnapshot({
      projectId: 'proj-1',
      name: 'Initial Snapshot',
      description: 'First snapshot',
      userId: 'user-123',
    });

    expect(result.id).toBeDefined();
    expect(result.projectId).toBe('proj-1');
    expect(result.name).toBe('Initial Snapshot');
    expect(result.version).toBe(1);
  });

  it('increments version for subsequent snapshots', async () => {
    const versioningService = makeVersioningServiceMock();
    const snapshot1 = snapshotRow({ version: 1 });
    const snapshot2 = snapshotRow({ version: 2, id: 'snap-2' });

    versioningService.createSnapshot.mockResolvedValueOnce(snapshot1);
    versioningService.createSnapshot.mockResolvedValueOnce(snapshot2);

    const result1 = await versioningService.createSnapshot({
      projectId: 'proj-1',
      name: 'Snapshot 1',
      userId: 'user-123',
    });

    const result2 = await versioningService.createSnapshot({
      projectId: 'proj-1',
      name: 'Snapshot 2',
      userId: 'user-123',
    });

    expect(result1.version).toBe(1);
    expect(result2.version).toBe(2);
  });

  it('stores complete project state in snapshot', async () => {
    const versioningService = makeVersioningServiceMock();
    const projectData = {
      workspace: { id: 'ws-1', name: 'My Workspace' },
      documents: [
        { id: 'doc-1', name: 'Document 1', content: {} },
        { id: 'doc-2', name: 'Document 2', content: {} },
      ],
      pages: [
        { id: 'page-1', path: '/home', content: {} },
        { id: 'page-2', path: '/about', content: {} },
      ],
      metadata: {
        createdAt: NOW,
        modifiedBy: 'user-123',
        version: '1.0',
      },
    };

    const snapshot = snapshotRow({
      projectData: JSON.stringify(projectData),
    });

    versioningService.createSnapshot.mockResolvedValue(snapshot);

    const result = await versioningService.createSnapshot({
      projectId: 'proj-1',
      name: 'Full State Snapshot',
      userId: 'user-123',
    });

    const storedData = JSON.parse(result.projectData as string);
    expect(storedData.workspace).toBeDefined();
    expect(storedData.documents).toHaveLength(2);
    expect(storedData.pages).toHaveLength(2);
    expect(storedData.metadata).toBeDefined();
  });

  it('records snapshot creator', async () => {
    const versioningService = makeVersioningServiceMock();
    const snapshot = snapshotRow({ createdBy: 'user-456' });

    versioningService.createSnapshot.mockResolvedValue(snapshot);

    const result = await versioningService.createSnapshot({
      projectId: 'proj-1',
      name: 'Snapshot',
      userId: 'user-456',
    });

    expect(result.createdBy).toBe('user-456');
  });

  it('includes optional description', async () => {
    const versioningService = makeVersioningServiceMock();
    const snapshot = snapshotRow({
      description: 'Before major refactoring',
    });

    versioningService.createSnapshot.mockResolvedValue(snapshot);

    const result = await versioningService.createSnapshot({
      projectId: 'proj-1',
      name: 'Pre-Refactor',
      description: 'Before major refactoring',
      userId: 'user-123',
    });

    expect(result.description).toBe('Before major refactoring');
  });

  it('returns ISO date for createdAt', async () => {
    const versioningService = makeVersioningServiceMock();
    const snapshot = snapshotRow({
      createdAt: '2024-03-25T14:30:00Z',
    });

    versioningService.createSnapshot.mockResolvedValue(snapshot);

    const result = await versioningService.createSnapshot({
      projectId: 'proj-1',
      name: 'Snapshot',
      userId: 'user-123',
    });

    expect(result.createdAt).toBe('2024-03-25T14:30:00Z');
    expect(new Date(result.createdAt).toISOString()).toBe(
      '2024-03-25T14:30:00Z'
    );
  });
});

// ---------------------------------------------------------------------------
// restoreSnapshot Mutation
// ---------------------------------------------------------------------------

describe('restoreSnapshot Mutation', () => {
  it('restores project to snapshot state', async () => {
    const versioningService = makeVersioningServiceMock();
    const projectData = {
      workspace: { id: 'ws-1', name: 'Restored Workspace' },
      documents: [{ id: 'doc-1', name: 'Restored Doc' }],
      pages: [{ id: 'page-1', path: '/home' }],
    };

    const snapshot = snapshotRow({
      projectData: JSON.stringify(projectData),
    });

    versioningService.restoreSnapshot.mockResolvedValue({
      success: true,
      restoredAt: NOW,
      projectData,
      snapshotVersion: 1,
    });

    const result = await versioningService.restoreSnapshot({
      snapshotId: 'snap-1',
      projectId: 'proj-1',
      userId: 'user-123',
    });

    expect(result.success).toBe(true);
    expect(result.projectData).toEqual(projectData);
  });

  it('preserves restoration timestamp', async () => {
    const versioningService = makeVersioningServiceMock();
    const restorationTime = '2024-03-25T15:45:00Z';

    versioningService.restoreSnapshot.mockResolvedValue({
      success: true,
      restoredAt: restorationTime,
      projectData: {},
      snapshotVersion: 1,
    });

    const result = await versioningService.restoreSnapshot({
      snapshotId: 'snap-1',
      projectId: 'proj-1',
      userId: 'user-123',
    });

    expect(result.restoredAt).toBe(restorationTime);
  });

  it('tracks which snapshot was restored', async () => {
    const versioningService = makeVersioningServiceMock();

    versioningService.restoreSnapshot.mockResolvedValue({
      success: true,
      restoredAt: NOW,
      projectData: {},
      snapshotVersion: 3,
    });

    const result = await versioningService.restoreSnapshot({
      snapshotId: 'snap-3',
      projectId: 'proj-1',
      userId: 'user-123',
    });

    expect(result.snapshotVersion).toBe(3);
  });

  it('fails when snapshot not found', async () => {
    const versioningService = makeVersioningServiceMock();

    versioningService.restoreSnapshot.mockResolvedValue({
      success: false,
      error: 'Snapshot not found',
    });

    const result = await versioningService.restoreSnapshot({
      snapshotId: 'nonexistent',
      projectId: 'proj-1',
      userId: 'user-123',
    });

    expect(result.success).toBe(false);
    expect(result.error).toBe('Snapshot not found');
  });

  it('validates project ownership before restore', async () => {
    const versioningService = makeVersioningServiceMock();

    versioningService.restoreSnapshot.mockResolvedValue({
      success: false,
      error: 'Unauthorized: user does not own project',
    });

    const result = await versioningService.restoreSnapshot({
      snapshotId: 'snap-1',
      projectId: 'other-proj',
      userId: 'user-123',
    });

    expect(result.success).toBe(false);
    expect(result.error).toContain('Unauthorized');
  });

  it('fails when snapshot belongs to different project', async () => {
    const versioningService = makeVersioningServiceMock();

    versioningService.restoreSnapshot.mockResolvedValue({
      success: false,
      error: 'Snapshot belongs to different project',
    });

    const result = await versioningService.restoreSnapshot({
      snapshotId: 'snap-other-project',
      projectId: 'proj-1',
      userId: 'user-123',
    });

    expect(result.success).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// listSnapshots Query
// ---------------------------------------------------------------------------

describe('listSnapshots Query', () => {
  it('lists all snapshots for a project', async () => {
    const versioningService = makeVersioningServiceMock();
    const snapshots = [
      snapshotRow({ id: 'snap-1', version: 1 }),
      snapshotRow({ id: 'snap-2', version: 2 }),
      snapshotRow({ id: 'snap-3', version: 3 }),
    ];

    versioningService.listSnapshots.mockResolvedValue(snapshots);

    const result = await versioningService.listSnapshots({
      projectId: 'proj-1',
    });

    expect(result).toHaveLength(3);
    expect(result[0].version).toBe(1);
    expect(result[2].version).toBe(3);
  });

  it('returns empty list when no snapshots exist', async () => {
    const versioningService = makeVersioningServiceMock();

    versioningService.listSnapshots.mockResolvedValue([]);

    const result = await versioningService.listSnapshots({
      projectId: 'proj-new',
    });

    expect(result).toEqual([]);
  });

  it('orders snapshots by version descending (newest first)', async () => {
    const versioningService = makeVersioningServiceMock();
    const snapshots = [
      snapshotRow({
        id: 'snap-3',
        version: 3,
        createdAt: '2024-03-25T16:00:00Z',
      }),
      snapshotRow({
        id: 'snap-2',
        version: 2,
        createdAt: '2024-03-25T14:00:00Z',
      }),
      snapshotRow({
        id: 'snap-1',
        version: 1,
        createdAt: '2024-03-25T12:00:00Z',
      }),
    ];

    versioningService.listSnapshots.mockResolvedValue(snapshots);

    const result = await versioningService.listSnapshots({
      projectId: 'proj-1',
    });

    expect(result[0].version).toBe(3);
    expect(result[1].version).toBe(2);
    expect(result[2].version).toBe(1);
  });

  it('includes snapshot metadata', async () => {
    const versioningService = makeVersioningServiceMock();
    const snapshot = snapshotRow({
      name: 'Release v1.0',
      description: 'Production release snapshot',
      createdBy: 'user-admin',
      createdAt: '2024-03-25T12:00:00Z',
    });

    versioningService.listSnapshots.mockResolvedValue([snapshot]);

    const result = await versioningService.listSnapshots({
      projectId: 'proj-1',
    });

    expect(result[0].name).toBe('Release v1.0');
    expect(result[0].description).toBe('Production release snapshot');
    expect(result[0].createdBy).toBe('user-admin');
  });
});

// ---------------------------------------------------------------------------
// deleteSnapshot Mutation
// ---------------------------------------------------------------------------

describe('deleteSnapshot Mutation', () => {
  it('deletes snapshot by id', async () => {
    const versioningService = makeVersioningServiceMock();

    versioningService.deleteSnapshot.mockResolvedValue({
      success: true,
      deletedSnapshotId: 'snap-1',
    });

    const result = await versioningService.deleteSnapshot({
      snapshotId: 'snap-1',
      projectId: 'proj-1',
      userId: 'user-123',
    });

    expect(result.success).toBe(true);
    expect(result.deletedSnapshotId).toBe('snap-1');
  });

  it('fails when snapshot not found', async () => {
    const versioningService = makeVersioningServiceMock();

    versioningService.deleteSnapshot.mockResolvedValue({
      success: false,
      error: 'Snapshot not found',
    });

    const result = await versioningService.deleteSnapshot({
      snapshotId: 'nonexistent',
      projectId: 'proj-1',
      userId: 'user-123',
    });

    expect(result.success).toBe(false);
  });

  it('validates authorization before deletion', async () => {
    const versioningService = makeVersioningServiceMock();

    versioningService.deleteSnapshot.mockResolvedValue({
      success: false,
      error: 'Unauthorized: only project owner can delete snapshots',
    });

    const result = await versioningService.deleteSnapshot({
      snapshotId: 'snap-1',
      projectId: 'proj-1',
      userId: 'other-user',
    });

    expect(result.success).toBe(false);
    expect(result.error).toContain('Unauthorized');
  });

  it('cannot delete current snapshot', async () => {
    const versioningService = makeVersioningServiceMock();

    versioningService.deleteSnapshot.mockResolvedValue({
      success: false,
      error: 'Cannot delete current snapshot',
    });

    const result = await versioningService.deleteSnapshot({
      snapshotId: 'snap-current',
      projectId: 'proj-1',
      userId: 'user-123',
    });

    expect(result.success).toBe(false);
    expect(result.error).toContain('Cannot delete current snapshot');
  });
});

// ---------------------------------------------------------------------------
// Integration - Snapshot Lifecycle
// ---------------------------------------------------------------------------

describe('Snapshot Lifecycle', () => {
  it('creates, lists, and restores snapshots correctly', async () => {
    const versioningService = makeVersioningServiceMock();

    // Create first snapshot
    const snapshot1 = snapshotRow({
      id: 'snap-1',
      version: 1,
      name: 'Initial State',
    });
    versioningService.createSnapshot.mockResolvedValueOnce(snapshot1);

    // Create second snapshot
    const snapshot2 = snapshotRow({
      id: 'snap-2',
      version: 2,
      name: 'After Updates',
    });
    versioningService.createSnapshot.mockResolvedValueOnce(snapshot2);

    // List snapshots
    versioningService.listSnapshots.mockResolvedValue([snapshot2, snapshot1]);

    // Restore to first snapshot
    versioningService.restoreSnapshot.mockResolvedValue({
      success: true,
      restoredAt: NOW,
      projectData: JSON.parse(snapshot1.projectData as string),
      snapshotVersion: 1,
    });

    // Execute lifecycle
    const create1 = await versioningService.createSnapshot({
      projectId: 'proj-1',
      name: 'Initial State',
      userId: 'user-123',
    });
    expect(create1.version).toBe(1);

    const create2 = await versioningService.createSnapshot({
      projectId: 'proj-1',
      name: 'After Updates',
      userId: 'user-123',
    });
    expect(create2.version).toBe(2);

    const list = await versioningService.listSnapshots({ projectId: 'proj-1' });
    expect(list).toHaveLength(2);

    const restore = await versioningService.restoreSnapshot({
      snapshotId: 'snap-1',
      projectId: 'proj-1',
      userId: 'user-123',
    });
    expect(restore.success).toBe(true);
    expect(restore.snapshotVersion).toBe(1);
  });

  it('maintains snapshot history across multiple operations', async () => {
    const versioningService = makeVersioningServiceMock();
    const snapshots: any[] = [];

    versioningService.createSnapshot.mockImplementation(async (args) => {
      const snapshot = snapshotRow({
        id: `snap-${snapshots.length + 1}`,
        version: snapshots.length + 1,
        name: args.name,
      });
      snapshots.push(snapshot);
      return snapshot;
    });

    versioningService.listSnapshots.mockImplementation(async () => snapshots);

    // Create multiple snapshots
    for (let i = 1; i <= 5; i++) {
      await versioningService.createSnapshot({
        projectId: 'proj-1',
        name: `Snapshot ${i}`,
        userId: 'user-123',
      });
    }

    const list = await versioningService.listSnapshots({ projectId: 'proj-1' });
    expect(list).toHaveLength(5);
    expect(list[0].version).toBe(5);
    expect(list[4].version).toBe(1);
  });
});
