/**
 * Export Resolver Tests
 *
 * Covers: exportArtifacts query, exportArtifact query, createExport mutation
 * (dev-mode immediate READY, production PENDING), authentication guard.
 */

// ---------------------------------------------------------------------------
// Prisma mock — hoisted so it is in scope before any module under test loads
// ---------------------------------------------------------------------------

const mockPrisma = vi.hoisted(() => ({
  exportArtifact: {
    findMany: vi.fn(),
    findUnique: vi.fn(),
    create: vi.fn(),
  },
}));

// Must be a regular function (not arrow) so it can be used with `new`.
// Returning an object from a constructor replaces `this` with that object.
vi.mock('@prisma/client', () => ({
  PrismaClient: function PrismaClientMock() {
    return mockPrisma;
  },
}));

// ---------------------------------------------------------------------------
// Import subject under test AFTER mocks are established
// ---------------------------------------------------------------------------

import { exportResolvers } from '../export.resolver';
import type { ResolverContext } from '../../types';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeContext(overrides?: Partial<ResolverContext>): ResolverContext {
  return {
    userId: 'user-1',
    ...overrides,
  } as ResolverContext;
}

function makeArtifact(overrides: Record<string, unknown> = {}) {
  return {
    id: 'artifact-1',
    projectId: 'proj-1',
    createdById: 'user-1',
    format: 'JSON',
    status: 'READY',
    includeRequirements: true,
    includeDiagrams: true,
    includeCode: false,
    downloadUrl: '/api/exports/dev-placeholder/json',
    createdAt: new Date('2026-04-01T10:00:00Z'),
    completedAt: new Date('2026-04-01T10:01:00Z'),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Query: exportArtifacts
// ---------------------------------------------------------------------------

describe('exportResolvers.Query.exportArtifacts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns artifacts ordered by createdAt desc', async () => {
    const artifacts = [makeArtifact({ id: 'a2' }), makeArtifact({ id: 'a1' })];
    mockPrisma.exportArtifact.findMany.mockResolvedValueOnce(artifacts);

    const result = await exportResolvers.Query.exportArtifacts(
      undefined,
      { projectId: 'proj-1' },
      makeContext()
    );

    expect(result).toEqual(artifacts);
    expect(mockPrisma.exportArtifact.findMany).toHaveBeenCalledWith({
      where: { projectId: 'proj-1' },
      orderBy: { createdAt: 'desc' },
    });
  });

  it('throws when unauthenticated', async () => {
    await expect(
      exportResolvers.Query.exportArtifacts(
        undefined,
        { projectId: 'proj-1' },
        makeContext({ userId: undefined as unknown as string })
      )
    ).rejects.toThrow('Authentication required');

    expect(mockPrisma.exportArtifact.findMany).not.toHaveBeenCalled();
  });

  it('returns empty array when no artifacts exist', async () => {
    mockPrisma.exportArtifact.findMany.mockResolvedValueOnce([]);

    const result = await exportResolvers.Query.exportArtifacts(
      undefined,
      { projectId: 'proj-empty' },
      makeContext()
    );

    expect(result).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// Query: exportArtifact
// ---------------------------------------------------------------------------

describe('exportResolvers.Query.exportArtifact', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns a single artifact by id', async () => {
    const artifact = makeArtifact();
    mockPrisma.exportArtifact.findUnique.mockResolvedValueOnce(artifact);

    const result = await exportResolvers.Query.exportArtifact(
      undefined,
      { id: 'artifact-1' },
      makeContext()
    );

    expect(result).toEqual(artifact);
    expect(mockPrisma.exportArtifact.findUnique).toHaveBeenCalledWith({
      where: { id: 'artifact-1' },
    });
  });

  it('returns null when artifact does not exist', async () => {
    mockPrisma.exportArtifact.findUnique.mockResolvedValueOnce(null);

    const result = await exportResolvers.Query.exportArtifact(
      undefined,
      { id: 'nonexistent' },
      makeContext()
    );

    expect(result).toBeNull();
  });

  it('throws when unauthenticated', async () => {
    await expect(
      exportResolvers.Query.exportArtifact(
        undefined,
        { id: 'artifact-1' },
        makeContext({ userId: undefined as unknown as string })
      )
    ).rejects.toThrow('Authentication required');
  });
});

// ---------------------------------------------------------------------------
// Mutation: createExport
// ---------------------------------------------------------------------------

describe('exportResolvers.Mutation.createExport', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('creates artifact with READY status in development', async () => {
    const originalEnv = process.env['NODE_ENV'];
    process.env['NODE_ENV'] = 'development';

    const artifact = makeArtifact({ format: 'MARKDOWN', status: 'READY' });
    mockPrisma.exportArtifact.create.mockResolvedValueOnce(artifact);

    const result = await exportResolvers.Mutation.createExport(
      undefined,
      {
        projectId: 'proj-1',
        format: 'MARKDOWN',
        includeRequirements: true,
        includeDiagrams: false,
        includeCode: true,
      },
      makeContext()
    );

    expect(result).toEqual(artifact);
    const createCall = mockPrisma.exportArtifact.create.mock.calls[0]?.[0];
    expect(createCall).toBeDefined();
    expect(createCall?.data.status).toBe('READY');
    expect(createCall?.data.downloadUrl).toMatch(/dev-placeholder/);
    expect(createCall?.data.projectId).toBe('proj-1');
    expect(createCall?.data.createdById).toBe('user-1');

    process.env['NODE_ENV'] = originalEnv;
  });

  it('creates artifact with PENDING status in production', async () => {
    const originalEnv = process.env['NODE_ENV'];
    process.env['NODE_ENV'] = 'production';

    const artifact = makeArtifact({ status: 'PENDING', downloadUrl: null });
    mockPrisma.exportArtifact.create.mockResolvedValueOnce(artifact);

    await exportResolvers.Mutation.createExport(
      undefined,
      { projectId: 'proj-1', format: 'PDF' },
      makeContext()
    );

    const createCall = mockPrisma.exportArtifact.create.mock.calls[0]?.[0];
    expect(createCall?.data.status).toBe('PENDING');
    expect(createCall?.data.downloadUrl).toBeNull();

    process.env['NODE_ENV'] = originalEnv;
  });

  it('defaults optional fields when not provided', async () => {
    const artifact = makeArtifact();
    mockPrisma.exportArtifact.create.mockResolvedValueOnce(artifact);

    await exportResolvers.Mutation.createExport(
      undefined,
      { projectId: 'proj-1', format: 'ZIP' },
      makeContext()
    );

    const createCall = mockPrisma.exportArtifact.create.mock.calls[0]?.[0];
    expect(createCall?.data.includeRequirements).toBe(true);
    expect(createCall?.data.includeDiagrams).toBe(true);
    expect(createCall?.data.includeCode).toBe(false);
  });

  it('throws when unauthenticated', async () => {
    await expect(
      exportResolvers.Mutation.createExport(
        undefined,
        { projectId: 'proj-1', format: 'JSON' },
        makeContext({ userId: undefined as unknown as string })
      )
    ).rejects.toThrow('Authentication required');

    expect(mockPrisma.exportArtifact.create).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// Field resolver: ExportArtifact
// ---------------------------------------------------------------------------

describe('exportResolvers.ExportArtifact field resolvers', () => {
  it('serializes Date createdAt to ISO string', () => {
    const date = new Date('2026-04-01T10:00:00Z');
    const result = exportResolvers.ExportArtifact.createdAt({ createdAt: date });
    expect(result).toBe('2026-04-01T10:00:00.000Z');
  });

  it('passes through string createdAt unchanged', () => {
    const isoStr = '2026-04-01T10:00:00.000Z';
    const result = exportResolvers.ExportArtifact.createdAt({ createdAt: isoStr });
    expect(result).toBe(isoStr);
  });

  it('serializes Date completedAt to ISO string', () => {
    const date = new Date('2026-04-01T10:01:00Z');
    const result = exportResolvers.ExportArtifact.completedAt({ completedAt: date });
    expect(result).toBe('2026-04-01T10:01:00.000Z');
  });

  it('returns null for null completedAt', () => {
    const result = exportResolvers.ExportArtifact.completedAt({ completedAt: null });
    expect(result).toBeNull();
  });

  it('returns null for undefined completedAt', () => {
    const result = exportResolvers.ExportArtifact.completedAt({});
    expect(result).toBeNull();
  });
});
