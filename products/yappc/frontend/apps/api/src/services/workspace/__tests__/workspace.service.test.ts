/**
 * WorkspaceService Tests
 *
 * Unit tests for all workspace domain operations with Prisma mocked at
 * the constructor boundary — no database connection required.
 */

import { WorkspaceService } from '../workspace.service';
import type {
  CreateWorkspaceInput,
  UpdateWorkspaceInput,
  AddMemberInput,
} from '../workspace.service';

// ---------------------------------------------------------------------------
// Prisma Mock Factory
// ---------------------------------------------------------------------------

function makePrismaMock() {
  return {
    workspace: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    workspaceMember: {
      create: jest.fn(),
      deleteMany: jest.fn(),
      count: jest.fn(),
      findUnique: jest.fn(),
    },
    project: {
      create: jest.fn(),
    },
  };
}

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

const NOW = new Date('2025-01-15T10:00:00Z');

function workspaceRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'ws-1',
    name: 'My Workspace',
    description: null,
    ownerId: 'user-1',
    isDefault: false,
    aiSummary: null,
    aiTags: [],
    createdAt: NOW,
    updatedAt: NOW,
    members: [],
    ownedProjects: [],
    includedProjects: [],
    ...overrides,
  };
}

function memberRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'member-1',
    userId: 'user-2',
    workspaceId: 'ws-1',
    role: 'EDITOR',
    user: { id: 'user-2', email: 'user2@example.com', name: 'User Two' },
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// getById
// ---------------------------------------------------------------------------

describe('WorkspaceService.getById', () => {
  it('returns null when workspace not found', async () => {
    const prisma = makePrismaMock();
    prisma.workspace.findUnique.mockResolvedValue(null);
    const svc = new WorkspaceService(prisma as never);

    expect(await svc.getById('nonexistent')).toBeNull();
    expect(prisma.workspace.findUnique).toHaveBeenCalledWith(
      expect.objectContaining({ where: { id: 'nonexistent' } })
    );
  });

  it('returns workspace with ISO date strings and merged projects', async () => {
    const ownedProject = {
      id: 'proj-1',
      name: 'Owned',
      ownerWorkspaceId: 'ws-1',
    };
    const includedProject = { id: 'proj-2', name: 'Included' };
    const row = workspaceRow({
      ownedProjects: [ownedProject],
      includedProjects: [{ project: includedProject }],
    });
    const prisma = makePrismaMock();
    prisma.workspace.findUnique.mockResolvedValue(row);
    const svc = new WorkspaceService(prisma as never);

    const result = await svc.getById('ws-1');

    expect(result).not.toBeNull();
    expect(result!.createdAt).toBe(NOW.toISOString());
    expect(result!.updatedAt).toBe(NOW.toISOString());
    expect(result!.projects).toHaveLength(2);
    expect(result!.projects.map((p: { id: string }) => p.id)).toEqual([
      'proj-1',
      'proj-2',
    ]);
  });
});

// ---------------------------------------------------------------------------
// listForUser
// ---------------------------------------------------------------------------

describe('WorkspaceService.listForUser', () => {
  it('returns workspaces mapped to WorkspaceSummary shape', async () => {
    const row = {
      ...workspaceRow(),
      members: [{ userId: 'user-1' }],
      _count: { ownedProjects: 3, members: 1 },
    };
    const prisma = makePrismaMock();
    prisma.workspace.findMany.mockResolvedValue([row]);
    const svc = new WorkspaceService(prisma as never);

    const results = await svc.listForUser('user-1');

    expect(results).toHaveLength(1);
    expect(results[0].projectCount).toBe(3);
    expect(results[0].memberCount).toBe(1);
    expect(results[0].createdAt).toBe(NOW.toISOString());
  });

  it('returns empty array when user has no workspaces', async () => {
    const prisma = makePrismaMock();
    prisma.workspace.findMany.mockResolvedValue([]);
    const svc = new WorkspaceService(prisma as never);

    expect(await svc.listForUser('user-x')).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// create
// ---------------------------------------------------------------------------

describe('WorkspaceService.create', () => {
  it('creates workspace and assigns creator as ADMIN member', async () => {
    const created = workspaceRow({
      members: [{ userId: 'user-1', role: 'ADMIN', user: { id: 'user-1' } }],
    });
    const prisma = makePrismaMock();
    prisma.workspace.create.mockResolvedValue(created);
    const svc = new WorkspaceService(prisma as never);

    const input: CreateWorkspaceInput = { name: 'New Workspace' };
    const result = await svc.create('user-1', input);

    expect(prisma.workspace.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          name: 'New Workspace',
          ownerId: 'user-1',
          members: { create: { userId: 'user-1', role: 'ADMIN' } },
        }),
      })
    );
    expect(result.createdAt).toBe(NOW.toISOString());
    expect(prisma.project.create).not.toHaveBeenCalled();
  });

  it('bootstraps a default project when createDefaultProject is true', async () => {
    const created = workspaceRow({ name: 'Workspace With Project' });
    const prisma = makePrismaMock();
    prisma.workspace.create.mockResolvedValue({
      ...created,
      members: [],
    });
    prisma.project.create.mockResolvedValue({});
    const svc = new WorkspaceService(prisma as never);

    await svc.create('user-1', {
      name: 'Workspace With Project',
      createDefaultProject: true,
    });

    expect(prisma.project.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          name: 'Workspace With Project — Default Project',
          ownerWorkspaceId: 'ws-1',
          isDefault: true,
        }),
      })
    );
  });

  it('trims whitespace from name and description', async () => {
    const created = workspaceRow({ name: 'Trimmed' });
    const prisma = makePrismaMock();
    prisma.workspace.create.mockResolvedValue({ ...created, members: [] });
    const svc = new WorkspaceService(prisma as never);

    await svc.create('user-1', {
      name: '  Trimmed  ',
      description: '  desc  ',
    });

    expect(prisma.workspace.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ name: 'Trimmed', description: 'desc' }),
      })
    );
  });
});

// ---------------------------------------------------------------------------
// update
// ---------------------------------------------------------------------------

describe('WorkspaceService.update', () => {
  it('updates only provided fields', async () => {
    const updated = workspaceRow({ name: 'Updated Name', ownedProjects: [] });
    const prisma = makePrismaMock();
    prisma.workspace.update.mockResolvedValue(updated);
    const svc = new WorkspaceService(prisma as never);

    const input: UpdateWorkspaceInput = { name: 'Updated Name' };
    const result = await svc.update('ws-1', input);

    expect(prisma.workspace.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: 'ws-1' },
        data: { name: 'Updated Name' },
      })
    );
    expect(result.updatedAt).toBe(NOW.toISOString());
  });
});

// ---------------------------------------------------------------------------
// delete
// ---------------------------------------------------------------------------

describe('WorkspaceService.delete', () => {
  it('calls prisma delete and returns true', async () => {
    const prisma = makePrismaMock();
    prisma.workspace.delete.mockResolvedValue({});
    const svc = new WorkspaceService(prisma as never);

    const result = await svc.delete('ws-1');

    expect(prisma.workspace.delete).toHaveBeenCalledWith({
      where: { id: 'ws-1' },
    });
    expect(result).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// addMember
// ---------------------------------------------------------------------------

describe('WorkspaceService.addMember', () => {
  it('adds member with default EDITOR role when no role supplied', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.create.mockResolvedValue(memberRow());
    const svc = new WorkspaceService(prisma as never);

    const input: AddMemberInput = { workspaceId: 'ws-1', userId: 'user-2' };
    await svc.addMember(input);

    expect(prisma.workspaceMember.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ role: 'EDITOR' }),
      })
    );
  });

  it('adds member with specified role', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.create.mockResolvedValue(
      memberRow({ role: 'ADMIN' })
    );
    const svc = new WorkspaceService(prisma as never);

    await svc.addMember({
      workspaceId: 'ws-1',
      userId: 'user-2',
      role: 'ADMIN',
    });

    expect(prisma.workspaceMember.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ role: 'ADMIN' }),
      })
    );
  });
});

// ---------------------------------------------------------------------------
// removeMember
// ---------------------------------------------------------------------------

describe('WorkspaceService.removeMember', () => {
  it('deletes member record and returns true', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.deleteMany.mockResolvedValue({ count: 1 });
    const svc = new WorkspaceService(prisma as never);

    const result = await svc.removeMember('ws-1', 'user-2');

    expect(prisma.workspaceMember.deleteMany).toHaveBeenCalledWith({
      where: { workspaceId: 'ws-1', userId: 'user-2' },
    });
    expect(result).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// isMember / getMemberRole
// ---------------------------------------------------------------------------

describe('WorkspaceService.isMember', () => {
  it('returns true when member count > 0', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.count.mockResolvedValue(1);
    const svc = new WorkspaceService(prisma as never);

    expect(await svc.isMember('ws-1', 'user-1')).toBe(true);
  });

  it('returns false when member count is 0', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.count.mockResolvedValue(0);
    const svc = new WorkspaceService(prisma as never);

    expect(await svc.isMember('ws-1', 'user-x')).toBe(false);
  });
});

describe('WorkspaceService.getMemberRole', () => {
  it('returns the member role string', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.findUnique.mockResolvedValue({ role: 'ADMIN' });
    const svc = new WorkspaceService(prisma as never);

    expect(await svc.getMemberRole('ws-1', 'user-1')).toBe('ADMIN');
  });

  it('returns null when user is not a member', async () => {
    const prisma = makePrismaMock();
    prisma.workspaceMember.findUnique.mockResolvedValue(null);
    const svc = new WorkspaceService(prisma as never);

    expect(await svc.getMemberRole('ws-1', 'user-x')).toBeNull();
  });
});
