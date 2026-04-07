/**
 * Versioning Resolver Tests
 *
 * Tests the GraphQL resolvers for canvas document versioning.
 */

import { versioningResolvers } from '../versioning.resolver';
import type { VersioningService } from '../../../services/versioning/versioning.service';

// Mock the versioning service
vi.mock('../../../services/versioning/versioning.service', () => ({
  getVersioningService: jest.fn(),
}));

import { getVersioningService } from '../../../services/versioning/versioning.service';

const mockGetVersioningService = getVersioningService as jest.MockedFunction<
  typeof getVersioningService
>;

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const NOW = new Date().toISOString();

function makeVersioningServiceMock(): jest.Mocked<VersioningService> {
  return {
    listVersions: jest.fn(),
    getVersion: jest.fn(),
    createSnapshot: jest.fn(),
    restore: jest.fn(),
    pruneHistory: jest.fn(),
  } as unknown as jest.Mocked<VersioningService>;
}

function snapshotRow(overrides: Partial<any> = {}) {
  return {
    canvasId: 'canvas-1',
    version: 1,
    content: { text: 'Document content' },
    changeType: 'MANUAL_SAVE',
    changedBy: 'user-1',
    changeSummary: 'Initial version',
    createdAt: NOW,
    ...overrides,
  };
}

function contextWithAuth(userId = 'user-1') {
  return { userId };
}

function contextWithoutAuth() {
  return {};
}

// ---------------------------------------------------------------------------
// canvasVersions Query
// ---------------------------------------------------------------------------

describe('versioningResolvers.Query.canvasVersions', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('lists all versions for a canvas', async () => {
    const serviceMock = makeVersioningServiceMock();
    const versions = [
      snapshotRow({ version: 3 }),
      snapshotRow({ version: 2, changeSummary: 'Updated text' }),
      snapshotRow({ version: 1, changeSummary: 'Initial' }),
    ];
    serviceMock.listVersions.mockResolvedValue(versions);
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Query.canvasVersions(
      undefined,
      { canvasId: 'canvas-1' },
      contextWithAuth('user-1')
    );

    expect(serviceMock.listVersions).toHaveBeenCalledWith('canvas-1');
    expect(result).toHaveLength(3);
    expect(result[0].version).toBe(3);
    expect(result[2].version).toBe(1);
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeVersioningServiceMock();
    mockGetVersioningService.mockReturnValue(serviceMock);

    await expect(
      versioningResolvers.Query.canvasVersions(
        undefined,
        { canvasId: 'canvas-1' },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Authentication required');

    expect(serviceMock.listVersions).not.toHaveBeenCalled();
  });

  it('returns empty array if no versions exist', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.listVersions.mockResolvedValue([]);
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Query.canvasVersions(
      undefined,
      { canvasId: 'canvas-nonexistent' },
      contextWithAuth()
    );

    expect(result).toEqual([]);
  });

  it('returns versions ordered newest first', async () => {
    const serviceMock = makeVersioningServiceMock();
    const versions = [
      snapshotRow({ version: 5, createdAt: '2024-03-05T00:00:00Z' }),
      snapshotRow({ version: 4, createdAt: '2024-03-04T00:00:00Z' }),
      snapshotRow({ version: 3, createdAt: '2024-03-03T00:00:00Z' }),
    ];
    serviceMock.listVersions.mockResolvedValue(versions);
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Query.canvasVersions(
      undefined,
      { canvasId: 'canvas-1' },
      contextWithAuth()
    );

    expect(result[0].version).toBe(5);
    expect(result[result.length - 1].version).toBe(3);
  });
});

// ---------------------------------------------------------------------------
// canvasVersion Query
// ---------------------------------------------------------------------------

describe('versioningResolvers.Query.canvasVersion', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('retrieves a specific version by version number', async () => {
    const serviceMock = makeVersioningServiceMock();
    const version = snapshotRow({ version: 2, changeSummary: 'Updated' });
    serviceMock.getVersion.mockResolvedValue(version);
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Query.canvasVersion(
      undefined,
      { canvasId: 'canvas-1', version: 2 },
      contextWithAuth()
    );

    expect(serviceMock.getVersion).toHaveBeenCalledWith('canvas-1', 2);
    expect(result.version).toBe(2);
    expect(result.changeSummary).toBe('Updated');
  });

  it('returns null if version does not exist', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.getVersion.mockResolvedValue(null);
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Query.canvasVersion(
      undefined,
      { canvasId: 'canvas-1', version: 999 },
      contextWithAuth()
    );

    expect(result).toBeNull();
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeVersioningServiceMock();
    mockGetVersioningService.mockReturnValue(serviceMock);

    await expect(
      versioningResolvers.Query.canvasVersion(
        undefined,
        { canvasId: 'canvas-1', version: 1 },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Authentication required');

    expect(serviceMock.getVersion).not.toHaveBeenCalled();
  });

  it('handles version 1 specifically', async () => {
    const serviceMock = makeVersioningServiceMock();
    const version = snapshotRow({
      version: 1,
      changeSummary: 'Initial version',
    });
    serviceMock.getVersion.mockResolvedValue(version);
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Query.canvasVersion(
      undefined,
      { canvasId: 'canvas-1', version: 1 },
      contextWithAuth()
    );

    expect(result.version).toBe(1);
    expect(result.changeSummary).toBe('Initial version');
  });
});

// ---------------------------------------------------------------------------
// saveCanvasVersion Mutation
// ---------------------------------------------------------------------------

describe('versioningResolvers.Mutation.saveCanvasVersion', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('saves a new version with content', async () => {
    const serviceMock = makeVersioningServiceMock();
    const snapshot = snapshotRow({ version: 4, changeType: 'MANUAL_SAVE' });
    serviceMock.createSnapshot.mockResolvedValue(snapshot);
    mockGetVersioningService.mockReturnValue(serviceMock);

    const content = { text: 'New content', sections: [] };
    const result = await versioningResolvers.Mutation.saveCanvasVersion(
      undefined,
      { canvasId: 'canvas-1', content, changeSummary: 'Updated text' },
      contextWithAuth('user-1')
    );

    expect(serviceMock.createSnapshot).toHaveBeenCalledWith({
      canvasId: 'canvas-1',
      content,
      changeType: 'MANUAL_SAVE',
      changedBy: 'user-1',
      changeSummary: 'Updated text',
    });
    expect(result.changeType).toBe('MANUAL_SAVE');
  });

  it('includes userId from context', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.createSnapshot.mockResolvedValue(
      snapshotRow({ changedBy: 'specific-user-id' })
    );
    mockGetVersioningService.mockReturnValue(serviceMock);

    await versioningResolvers.Mutation.saveCanvasVersion(
      undefined,
      { canvasId: 'canvas-1', content: {} },
      contextWithAuth('specific-user-id')
    );

    expect(serviceMock.createSnapshot).toHaveBeenCalledWith(
      expect.objectContaining({ changedBy: 'specific-user-id' })
    );
  });

  it('saves without changeSummary if not provided', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.createSnapshot.mockResolvedValue(
      snapshotRow({ changeSummary: undefined })
    );
    mockGetVersioningService.mockReturnValue(serviceMock);

    await versioningResolvers.Mutation.saveCanvasVersion(
      undefined,
      { canvasId: 'canvas-1', content: { data: 'test' } },
      contextWithAuth()
    );

    expect(serviceMock.createSnapshot).toHaveBeenCalledWith(
      expect.objectContaining({ changeSummary: undefined })
    );
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeVersioningServiceMock();
    mockGetVersioningService.mockReturnValue(serviceMock);

    await expect(
      versioningResolvers.Mutation.saveCanvasVersion(
        undefined,
        { canvasId: 'canvas-1', content: {} },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Authentication required');

    expect(serviceMock.createSnapshot).not.toHaveBeenCalled();
  });

  it('handles complex content structures', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.createSnapshot.mockResolvedValue(snapshotRow());
    mockGetVersioningService.mockReturnValue(serviceMock);

    const complexContent = {
      title: 'Document',
      sections: [
        { id: '1', text: 'Section 1', metadata: { color: 'blue' } },
        { id: '2', text: 'Section 2', nested: { deep: { value: true } } },
      ],
      tags: ['important', 'draft'],
    };

    await versioningResolvers.Mutation.saveCanvasVersion(
      undefined,
      { canvasId: 'canvas-1', content: complexContent },
      contextWithAuth()
    );

    expect(serviceMock.createSnapshot).toHaveBeenCalledWith(
      expect.objectContaining({ content: complexContent })
    );
  });
});

// ---------------------------------------------------------------------------
// restoreCanvasVersion Mutation
// ---------------------------------------------------------------------------

describe('versioningResolvers.Mutation.restoreCanvasVersion', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('restores canvas to a specific version', async () => {
    const serviceMock = makeVersioningServiceMock();
    const restored = snapshotRow({ version: 2, changeType: 'RESTORE' });
    serviceMock.restore.mockResolvedValue(restored);
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Mutation.restoreCanvasVersion(
      undefined,
      { canvasId: 'canvas-1', version: 2 },
      contextWithAuth('user-1')
    );

    expect(serviceMock.restore).toHaveBeenCalledWith({
      canvasId: 'canvas-1',
      targetVersion: 2,
      restoredBy: 'user-1',
    });
    expect(result.changeType).toBe('RESTORE');
  });

  it('includes userId from context as restoredBy', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.restore.mockResolvedValue(snapshotRow());
    mockGetVersioningService.mockReturnValue(serviceMock);

    await versioningResolvers.Mutation.restoreCanvasVersion(
      undefined,
      { canvasId: 'canvas-1', version: 1 },
      contextWithAuth('admin-user')
    );

    expect(serviceMock.restore).toHaveBeenCalledWith(
      expect.objectContaining({ restoredBy: 'admin-user' })
    );
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeVersioningServiceMock();
    mockGetVersioningService.mockReturnValue(serviceMock);

    await expect(
      versioningResolvers.Mutation.restoreCanvasVersion(
        undefined,
        { canvasId: 'canvas-1', version: 1 },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Authentication required');

    expect(serviceMock.restore).not.toHaveBeenCalled();
  });

  it('can restore to any version number', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.restore.mockResolvedValue(snapshotRow({ version: 50 }));
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Mutation.restoreCanvasVersion(
      undefined,
      { canvasId: 'canvas-1', version: 50 },
      contextWithAuth()
    );

    expect(serviceMock.restore).toHaveBeenCalledWith(
      expect.objectContaining({ targetVersion: 50 })
    );
    expect(result.version).toBe(50);
  });
});

// ---------------------------------------------------------------------------
// pruneCanvasVersionHistory Mutation
// ---------------------------------------------------------------------------

describe('versioningResolvers.Mutation.pruneCanvasVersionHistory', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('prunes version history keeping the specified count', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.pruneHistory.mockResolvedValue({
      prunedCount: 5,
      keptCount: 10,
    });
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Mutation.pruneCanvasVersionHistory(
      undefined,
      { canvasId: 'canvas-1', keepCount: 10 },
      contextWithAuth()
    );

    expect(serviceMock.pruneHistory).toHaveBeenCalledWith('canvas-1', 10);
    expect(result).toEqual({ prunedCount: 5, keptCount: 10 });
  });

  it('handles keepCount = 1 (keep only latest)', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.pruneHistory.mockResolvedValue({
      prunedCount: 20,
      keptCount: 1,
    });
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Mutation.pruneCanvasVersionHistory(
      undefined,
      { canvasId: 'canvas-1', keepCount: 1 },
      contextWithAuth()
    );

    expect(serviceMock.pruneHistory).toHaveBeenCalledWith('canvas-1', 1);
    expect(result.keptCount).toBe(1);
  });

  it('handles pruning with large keepCount', async () => {
    const serviceMock = makeVersioningServiceMock();
    serviceMock.pruneHistory.mockResolvedValue({
      prunedCount: 0,
      keptCount: 100,
    });
    mockGetVersioningService.mockReturnValue(serviceMock);

    const result = await versioningResolvers.Mutation.pruneCanvasVersionHistory(
      undefined,
      { canvasId: 'canvas-1', keepCount: 100 },
      contextWithAuth()
    );

    expect(result.prunedCount).toBe(0);
  });

  it('throws if not authenticated', async () => {
    const serviceMock = makeVersioningServiceMock();
    mockGetVersioningService.mockReturnValue(serviceMock);

    await expect(
      versioningResolvers.Mutation.pruneCanvasVersionHistory(
        undefined,
        { canvasId: 'canvas-1', keepCount: 10 },
        contextWithoutAuth()
      )
    ).rejects.toThrow('Authentication required');

    expect(serviceMock.pruneHistory).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// Integration Tests — Multiple Resolvers Together
// ---------------------------------------------------------------------------

describe('Versioning Resolvers - Integration', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('lists versions, gets specific version, and restores', async () => {
    const serviceMock = makeVersioningServiceMock();

    const versions = [
      snapshotRow({ version: 3 }),
      snapshotRow({ version: 2 }),
      snapshotRow({ version: 1 }),
    ];

    serviceMock.listVersions.mockResolvedValue(versions);
    serviceMock.getVersion.mockResolvedValue(snapshotRow({ version: 2 }));
    serviceMock.restore.mockResolvedValue(
      snapshotRow({ version: 2, changeType: 'RESTORE' })
    );
    mockGetVersioningService.mockReturnValue(serviceMock);

    const context = contextWithAuth('user-1');

    // List versions
    const listResult = await versioningResolvers.Query.canvasVersions(
      undefined,
      { canvasId: 'canvas-1' },
      context
    );
    expect(listResult).toHaveLength(3);

    // Get specific version
    const getResult = await versioningResolvers.Query.canvasVersion(
      undefined,
      { canvasId: 'canvas-1', version: 2 },
      context
    );
    expect(getResult.version).toBe(2);

    // Restore
    const restoreResult =
      await versioningResolvers.Mutation.restoreCanvasVersion(
        undefined,
        { canvasId: 'canvas-1', version: 2 },
        context
      );
    expect(restoreResult.changeType).toBe('RESTORE');
  });

  it('save and then list new version', async () => {
    const serviceMock = makeVersioningServiceMock();

    serviceMock.createSnapshot.mockResolvedValue(
      snapshotRow({ version: 4, changeSummary: 'New save' })
    );
    serviceMock.listVersions.mockResolvedValue([
      snapshotRow({ version: 4, changeSummary: 'New save' }),
      snapshotRow({ version: 3 }),
    ]);
    mockGetVersioningService.mockReturnValue(serviceMock);

    const context = contextWithAuth('user-1');

    // Save new version
    const saveResult = await versioningResolvers.Mutation.saveCanvasVersion(
      undefined,
      { canvasId: 'canvas-1', content: { text: 'New' } },
      context
    );
    expect(saveResult.version).toBe(4);

    // List to verify
    const listResult = await versioningResolvers.Query.canvasVersions(
      undefined,
      { canvasId: 'canvas-1' },
      context
    );
    expect(listResult[0].version).toBe(4);
  });
});
