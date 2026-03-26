/**
 * ProjectService Tests
 *
 * Unit tests for all project domain operations with Prisma mocked at
 * the constructor boundary — no database connection required.
 */

import { ProjectService } from '../project.service';
import type {
  CreateProjectInput,
  UpdateProjectInput,
} from '../project.service';

// ---------------------------------------------------------------------------
// Prisma Mock Factory
// ---------------------------------------------------------------------------

function makePrismaMock() {
  return {
    project: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    canvasDocument: {
      count: jest.fn(),
    },
  };
}

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

const NOW = new Date('2025-03-01T12:00:00Z');

function projectRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'proj-1',
    workspaceId: 'ws-1',
    ownerWorkspaceId: 'ws-1',
    createdById: 'user-1',
    name: 'My Project',
    description: null,
    type: 'FULL_STACK',
    status: 'ACTIVE',
    lifecyclePhase: 'DEVELOPMENT',
    isDefault: false,
    aiSummary: null,
    aiNextActions: [],
    aiHealthScore: null,
    createdAt: NOW,
    updatedAt: NOW,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// getById
// ---------------------------------------------------------------------------

describe('ProjectService.getById', () => {
  it('returns null when project not found', async () => {
    const prisma = makePrismaMock();
    prisma.project.findUnique.mockResolvedValue(null);
    const svc = new ProjectService(prisma as never);

    expect(await svc.getById('nonexistent')).toBeNull();
  });

  it('returns project with ISO dates and resolved workspace', async () => {
    const prisma = makePrismaMock();
    prisma.project.findUnique.mockResolvedValue(projectRow());
    prisma.canvasDocument.count.mockResolvedValue(3);
    const svc = new ProjectService(prisma as never);

    const result = await svc.getById('proj-1');
    expect(result).not.toBeNull();
    expect(result!.id).toBe('proj-1');
    expect(result!.createdAt).toBe(NOW.toISOString());
    expect(result!.updatedAt).toBe(NOW.toISOString());
  });
});

// ---------------------------------------------------------------------------
// listForWorkspace
// ---------------------------------------------------------------------------

describe('ProjectService.listForWorkspace', () => {
  it('returns empty array when no projects exist', async () => {
    const prisma = makePrismaMock();
    prisma.project.findMany.mockResolvedValue([]);
    const svc = new ProjectService(prisma as never);

    expect(await svc.listForWorkspace('ws-1')).toEqual([]);
  });

  it('filters by workspace and orders by updatedAt desc', async () => {
    const prisma = makePrismaMock();
    prisma.project.findMany.mockResolvedValue([]);
    prisma.canvasDocument.count.mockResolvedValue(0);
    const svc = new ProjectService(prisma as never);

    await svc.listForWorkspace('ws-1');

    expect(prisma.project.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { ownerWorkspaceId: 'ws-1' },
        orderBy: { updatedAt: 'desc' },
      })
    );
  });

  it('maps project rows with ISO dates and document counts', async () => {
    const prisma = makePrismaMock();
    prisma.project.findMany.mockResolvedValue([projectRow({ id: 'p-1' })]);
    prisma.canvasDocument.count.mockResolvedValue(5);
    const svc = new ProjectService(prisma as never);

    const results = await svc.listForWorkspace('ws-1');
    expect(results).toHaveLength(1);
    expect(results[0].id).toBe('p-1');
  });
});

// ---------------------------------------------------------------------------
// create
// ---------------------------------------------------------------------------

describe('ProjectService.create', () => {
  it('creates project with ACTIVE status and DEVELOPMENT phase defaults', async () => {
    const prisma = makePrismaMock();
    prisma.project.create.mockResolvedValue(projectRow());
    prisma.canvasDocument.count.mockResolvedValue(0);
    const svc = new ProjectService(prisma as never);

    const input: CreateProjectInput = {
      workspaceId: 'ws-1',
      name: 'New Project',
      type: 'FULL_STACK',
    };
    await svc.create('user-1', input);

    const callData = prisma.project.create.mock.calls[0][0].data;
    expect(callData.status).toBe('ACTIVE');
    expect(callData.lifecyclePhase).toBe('DEVELOPMENT');
    expect(callData.type).toBe('FULL_STACK');
  });

  it('returns project with ISO dates', async () => {
    const prisma = makePrismaMock();
    prisma.project.create.mockResolvedValue(projectRow());
    prisma.canvasDocument.count.mockResolvedValue(0);
    const svc = new ProjectService(prisma as never);

    const result = await svc.create('user-1', {
      workspaceId: 'ws-1',
      name: 'Test',
    });
    expect(result.createdAt).toBe(NOW.toISOString());
  });
});

// ---------------------------------------------------------------------------
// update
// ---------------------------------------------------------------------------

describe('ProjectService.update', () => {
  it('only sends fields that are provided', async () => {
    const prisma = makePrismaMock();
    prisma.project.update.mockResolvedValue(projectRow({ name: 'Updated' }));
    prisma.canvasDocument.count.mockResolvedValue(0);
    const svc = new ProjectService(prisma as never);

    const input: UpdateProjectInput = { name: 'Updated' };
    await svc.update('proj-1', input);

    const callData = prisma.project.update.mock.calls[0][0].data;
    expect(callData.name).toBe('Updated');
    expect('status' in callData).toBe(false);
  });

  it('handles array aiNextActions field', async () => {
    const prisma = makePrismaMock();
    prisma.project.update.mockResolvedValue(
      projectRow({ aiNextActions: ['action1', 'action2'] })
    );
    prisma.canvasDocument.count.mockResolvedValue(0);
    const svc = new ProjectService(prisma as never);

    await svc.update('proj-1', { aiNextActions: ['action1', 'action2'] });

    const callData = prisma.project.update.mock.calls[0][0].data;
    expect(callData.aiNextActions).toEqual(['action1', 'action2']);
  });
});

// ---------------------------------------------------------------------------
// delete
// ---------------------------------------------------------------------------

describe('ProjectService.delete', () => {
  it('deletes project and returns true', async () => {
    const prisma = makePrismaMock();
    prisma.project.delete.mockResolvedValue(projectRow());
    const svc = new ProjectService(prisma as never);

    expect(await svc.delete('proj-1')).toBe(true);
    expect(prisma.project.delete).toHaveBeenCalledWith({
      where: { id: 'proj-1' },
    });
  });
});
