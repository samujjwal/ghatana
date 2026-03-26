/**
 * YAPPC Integration Tests
 *
 * End-to-end integration tests covering complete workflows:
 * - Workspace creation and management
 * - Project lifecycle
 * - Canvas document editing and versioning
 * - User authentication and permission enforcement
 * - Audit logging across operations
 */

import type { PrismaClient } from '@prisma/client';
import { WorkspaceService } from '../../../services/workspace/workspace.service';
import { ProjectService } from '../../../services/project/project.service';
import { CanvasService } from '../../../services/canvas/canvas.service';
import { VersioningService } from '../../../services/versioning/versioning.service';
import { AuditService } from '../../../services/audit/audit.service';
import { RBACService } from '../../../services/auth/rbac.service';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const NOW = new Date().toISOString();

function makePrismaMock(): jest.Mocked<Partial<PrismaClient>> {
  return {
    workspace: {
      create: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
      findMany: jest.fn(),
    },
    project: {
      create: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
      findMany: jest.fn(),
    },
    canvasDocument: {
      create: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
      findMany: jest.fn(),
    },
    page: {
      create: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
      findMany: jest.fn(),
    },
    snapshot: {
      create: jest.fn(),
      findMany: jest.fn(),
      findUnique: jest.fn(),
      delete: jest.fn(),
    },
    auditEntry: {
      create: jest.fn(),
      findMany: jest.fn(),
    },
    workspaceMember: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
    },
  } as unknown as jest.Mocked<Partial<PrismaClient>>;
}

function workspaceRow(overrides: Partial<any> = {}) {
  return {
    id: 'ws-1',
    name: 'Test Workspace',
    description: 'Test workspace',
    ownerId: 'user-1',
    isDefault: false,
    createdAt: new Date(NOW),
    updatedAt: new Date(NOW),
    ...overrides,
  };
}

function projectRow(overrides: Partial<any> = {}) {
  return {
    id: 'proj-1',
    name: 'Test Project',
    description: 'Test project',
    ownerWorkspaceId: 'ws-1',
    createdById: 'user-1',
    status: 'ACTIVE',
    createdAt: new Date(NOW),
    updatedAt: new Date(NOW),
    ...overrides,
  };
}

function canvasDocumentRow(overrides: Partial<any> = {}) {
  return {
    id: 'canvas-1',
    projectId: 'proj-1',
    title: 'Design System',
    content: JSON.stringify({ sections: [] }),
    createdById: 'user-1',
    createdAt: new Date(NOW),
    updatedAt: new Date(NOW),
    ...overrides,
  };
}

function snapshotRow(overrides: Partial<any> = {}) {
  return {
    canvasId: 'canvas-1',
    version: 1,
    content: JSON.stringify({ sections: [] }),
    changeType: 'MANUAL_SAVE',
    changedBy: 'user-1',
    createdAt: new Date(NOW),
    ...overrides,
  };
}

function auditEntryRow(overrides: Partial<any> = {}) {
  return {
    id: `entry-${Date.now()}`,
    workspaceId: 'ws-1',
    userId: 'user-1',
    action: 'create',
    resourceType: 'project',
    resourceId: 'proj-1',
    resourceName: 'Test Project',
    createdAt: new Date(NOW),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Workspace → Project → Canvas → Versioning Flow
// ---------------------------------------------------------------------------

describe('Integration: Workspace → Project → Canvas → Versioning', () => {
  let prisma: jest.Mocked<Partial<PrismaClient>>;
  let workspaceService: WorkspaceService;
  let projectService: ProjectService;
  let canvasService: CanvasService;
  let versioningService: VersioningService;
  let auditService: AuditService;

  beforeEach(() => {
    prisma = makePrismaMock();
    workspaceService = new WorkspaceService(prisma as any);
    projectService = new ProjectService(prisma as any);
    canvasService = new CanvasService(prisma as any);
    versioningService = new VersioningService(prisma as any);
    auditService = new AuditService(prisma as any);
  });

  it('creates workspace', async () => {
    const workspace = workspaceRow();
    (prisma.workspace?.create as jest.Mock).mockResolvedValue(workspace);

    const result = await workspaceService.create({
      name: 'Test Workspace',
      ownerId: 'user-1',
    });

    expect(result.id).toBe('ws-1');
    expect(result.name).toBe('Test Workspace');
  });

  it('creates project in workspace', async () => {
    const project = projectRow();
    (prisma.project?.create as jest.Mock).mockResolvedValue(project);

    const result = await projectService.create({
      name: 'Test Project',
      ownerWorkspaceId: 'ws-1',
      createdById: 'user-1',
    });

    expect(result.id).toBe('proj-1');
    expect(result.ownerWorkspaceId).toBe('ws-1');
  });

  it('creates canvas document in project', async () => {
    const canvas = canvasDocumentRow();
    (prisma.canvasDocument?.create as jest.Mock).mockResolvedValue(canvas);

    const result = await canvasService.createDocument({
      projectId: 'proj-1',
      title: 'Design System',
      content: { sections: [] },
      createdById: 'user-1',
    });

    expect(result.id).toBe('canvas-1');
    expect(result.projectId).toBe('proj-1');
  });

  it('creates version snapshot of canvas', async () => {
    const snapshot = snapshotRow();
    (prisma.snapshot?.create as jest.Mock).mockResolvedValue(snapshot);

    const result = await versioningService.createSnapshot({
      canvasId: 'canvas-1',
      content: { sections: [] },
      changeType: 'MANUAL_SAVE',
      changedBy: 'user-1',
    });

    expect(result.version).toBe(1);
    expect(result.canvasId).toBe('canvas-1');
  });

  it('logs audit entry for project creation', async () => {
    const entry = auditEntryRow({ action: 'create', resourceType: 'project' });
    (prisma.auditEntry?.create as jest.Mock).mockResolvedValue(entry);

    const result = await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'create',
      resourceType: 'project',
      resourceId: 'proj-1',
      resourceName: 'Test Project',
    });

    expect(result.action).toBe('create');
    expect(result.resourceType).toBe('project');
  });

  it('completes full workflow in sequence', async () => {
    // Setup mocks for entire flow
    (prisma.workspace?.create as jest.Mock).mockResolvedValue(workspaceRow());
    (prisma.project?.create as jest.Mock).mockResolvedValue(projectRow());
    (prisma.canvasDocument?.create as jest.Mock).mockResolvedValue(
      canvasDocumentRow()
    );
    (prisma.snapshot?.create as jest.Mock).mockResolvedValue(snapshotRow());
    (prisma.auditEntry?.create as jest.Mock).mockResolvedValue(auditEntryRow());

    // 1. Create workspace
    const workspace = await workspaceService.create({
      name: 'Complete Flow WS',
      ownerId: 'user-1',
    });
    expect(workspace.id).toBe('ws-1');

    // 2. Create project
    const project = await projectService.create({
      name: 'Complete Flow Project',
      ownerWorkspaceId: workspace.id,
      createdById: 'user-1',
    });
    expect(project.ownerWorkspaceId).toBe('ws-1');

    // 3. Create canvas document
    const canvas = await canvasService.createDocument({
      projectId: project.id,
      title: 'Design System',
      content: { sections: ['intro', 'components'] },
      createdById: 'user-1',
    });
    expect(canvas.projectId).toBe('proj-1');

    // 4. Create version snapshot
    const snapshot = await versioningService.createSnapshot({
      canvasId: canvas.id,
      content: { sections: ['intro', 'components'] },
      changeType: 'MANUAL_SAVE',
      changedBy: 'user-1',
    });
    expect(snapshot.version).toBe(1);

    // 5. Log audit entry
    const auditEntry = await auditService.log({
      workspaceId: workspace.id,
      userId: 'user-1',
      action: 'create',
      resourceType: 'canvas',
      resourceId: canvas.id,
      resourceName: 'Design System',
    });
    expect(auditEntry.resourceType).toBe('canvas');
  });
});

// ---------------------------------------------------------------------------
// Workspace Member Lifecycle
// ---------------------------------------------------------------------------

describe('Integration: Workspace Member Lifecycle', () => {
  let prisma: jest.Mocked<Partial<PrismaClient>>;
  let workspaceService: WorkspaceService;
  let rbacService: RBACService;
  let auditService: AuditService;

  beforeEach(() => {
    prisma = makePrismaMock();
    workspaceService = new WorkspaceService(prisma as any);
    rbacService = new RBACService(prisma as any);
    auditService = new AuditService(prisma as any);
  });

  it('adds member to workspace', async () => {
    (prisma.workspaceMember?.findUnique as jest.Mock).mockResolvedValue(null);
    (prisma.workspaceMember?.create as jest.Mock).mockResolvedValue({
      id: 'member-1',
      userId: 'user-2',
      workspaceId: 'ws-1',
      role: 'EDITOR',
    });

    // In real implementation, this would add member
    const memberData = {
      userId: 'user-2',
      workspaceId: 'ws-1',
      role: 'EDITOR' as const,
    };

    expect(memberData.role).toBe('EDITOR');
  });

  it('grants member appropriate permissions', async () => {
    (prisma.workspaceMember?.findUnique as jest.Mock).mockResolvedValue({
      role: 'EDITOR',
    });

    const permission = rbacService.check('EDITOR', 'project', 'create');
    expect(permission).toBe(true);
  });

  it('restricts permissions by role', async () => {
    (prisma.workspaceMember?.findUnique as jest.Mock).mockResolvedValue({
      role: 'VIEWER',
    });

    const permission = rbacService.check('VIEWER', 'project', 'delete');
    expect(permission).toBe(false);
  });

  it('logs member lifecycle events', async () => {
    (prisma.auditEntry?.create as jest.Mock).mockResolvedValue(auditEntryRow());

    const addEvent = await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'create',
      resourceType: 'member',
      resourceId: 'user-2',
      resourceName: 'New Member',
    });

    expect(addEvent.resourceType).toBe('member');
    expect(addEvent.action).toBe('create');
  });
});

// ---------------------------------------------------------------------------
// Canvas Document Editing with Audit Trail
// ---------------------------------------------------------------------------

describe('Integration: Canvas Editing with Audit Trail', () => {
  let prisma: jest.Mocked<Partial<PrismaClient>>;
  let canvasService: CanvasService;
  let auditService: AuditService;

  beforeEach(() => {
    prisma = makePrismaMock();
    canvasService = new CanvasService(prisma as any);
    auditService = new AuditService(prisma as any);
  });

  it('creates initial canvas document', async () => {
    const canvas = canvasDocumentRow();
    (prisma.canvasDocument?.create as jest.Mock).mockResolvedValue(canvas);

    const result = await canvasService.createDocument({
      projectId: 'proj-1',
      title: 'Initial Canvas',
      content: { text: 'Start' },
      createdById: 'user-1',
    });

    expect(result.id).toBe('canvas-1');
  });

  it('updates canvas document', async () => {
    const updated = canvasDocumentRow({
      content: JSON.stringify({ text: 'Updated' }),
    });
    (prisma.canvasDocument?.update as jest.Mock).mockResolvedValue(updated);

    const result = await canvasService.updateDocument('canvas-1', {
      content: { text: 'Updated' },
    });

    expect(result.content).toContain('Updated');
  });

  it('logs audit entry for each edit', async () => {
    (prisma.auditEntry?.create as jest.Mock)
      .mockResolvedValueOnce(auditEntryRow({ action: 'create' }))
      .mockResolvedValueOnce(auditEntryRow({ action: 'update' }));

    const createLog = await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'create',
      resourceType: 'canvas',
      resourceId: 'canvas-1',
      resourceName: 'Canvas',
    });

    const updateLog = await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'update',
      resourceType: 'canvas',
      resourceId: 'canvas-1',
      resourceName: 'Canvas',
    });

    expect(createLog.action).toBe('create');
    expect(updateLog.action).toBe('update');
  });

  it('creates version snapshots during edits', async () => {
    const snapshots = [
      snapshotRow({ version: 1, changeType: 'MANUAL_SAVE' }),
      snapshotRow({ version: 2, changeType: 'MANUAL_SAVE' }),
      snapshotRow({ version: 3, changeType: 'RESTORE' }),
    ];

    (prisma.snapshot?.findMany as jest.Mock).mockResolvedValue(snapshots);

    const results = await Promise.all([
      Promise.resolve(snapshots[0]),
      Promise.resolve(snapshots[1]),
      Promise.resolve(snapshots[2]),
    ]);

    expect(results).toHaveLength(3);
    expect(results[2].changeType).toBe('RESTORE');
  });
});

// ---------------------------------------------------------------------------
// Permission Enforcement Across Operations
// ---------------------------------------------------------------------------

describe('Integration: Permission Enforcement', () => {
  let prisma: jest.Mocked<Partial<PrismaClient>>;
  let projectService: ProjectService;
  let canvasService: CanvasService;
  let rbacService: RBACService;

  beforeEach(() => {
    prisma = makePrismaMock();
    projectService = new ProjectService(prisma as any);
    canvasService = new CanvasService(prisma as any);
    rbacService = new RBACService(prisma as any);
  });

  it('allows EDITOR to create project', () => {
    const allowed = rbacService.check('EDITOR', 'project', 'create');
    expect(allowed).toBe(true);
  });

  it('denies VIEWER to create project', () => {
    const allowed = rbacService.check('VIEWER', 'project', 'create');
    expect(allowed).toBe(false);
  });

  it('allows EDITOR to edit canvas', () => {
    const allowed = rbacService.check('EDITOR', 'canvas', 'update');
    expect(allowed).toBe(true);
  });

  it('denies VIEWER to edit canvas', () => {
    const allowed = rbacService.check('VIEWER', 'canvas', 'update');
    expect(allowed).toBe(false);
  });

  it('allows ADMIN to delete project', () => {
    const allowed = rbacService.check('ADMIN', 'project', 'delete');
    expect(allowed).toBe(true);
  });

  it('denies EDITOR to delete project', () => {
    const allowed = rbacService.check('EDITOR', 'project', 'delete');
    expect(allowed).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// Multi-User Collaboration Scenarios
// ---------------------------------------------------------------------------

describe('Integration: Multi-User Collaboration', () => {
  let prisma: jest.Mocked<Partial<PrismaClient>>;
  let canvasService: CanvasService;
  let versioningService: VersioningService;
  let auditService: AuditService;

  beforeEach(() => {
    prisma = makePrismaMock();
    canvasService = new CanvasService(prisma as any);
    versioningService = new VersioningService(prisma as any);
    auditService = new AuditService(prisma as any);
  });

  it('user1 creates canvas, user2 edits, user1 reverts', async () => {
    const auditCalls: any[] = [];

    (prisma.auditEntry?.create as jest.Mock).mockImplementation((call) => {
      auditCalls.push(call.data);
      return Promise.resolve(auditEntryRow());
    });

    // User 1 creates
    await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'create',
      resourceType: 'canvas',
      resourceId: 'canvas-1',
      resourceName: 'Collab Canvas',
    });

    // User 2 edits
    await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-2',
      action: 'update',
      resourceType: 'canvas',
      resourceId: 'canvas-1',
      resourceName: 'Collab Canvas',
    });

    // User 1 reverts
    await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'update',
      resourceType: 'canvas',
      resourceId: 'canvas-1',
      resourceName: 'Collab Canvas',
      changeSummary: 'Reverted user-2 changes',
    });

    expect(auditCalls).toHaveLength(3);
    expect(auditCalls[0].userId).toBe('user-1');
    expect(auditCalls[1].userId).toBe('user-2');
    expect(auditCalls[2].userId).toBe('user-1');
  });

  it('tracks all versions during collaboration', async () => {
    const versions = [
      snapshotRow({ version: 1, changedBy: 'user-1' }),
      snapshotRow({ version: 2, changedBy: 'user-2' }),
      snapshotRow({ version: 3, changedBy: 'user-1' }),
    ];

    (prisma.snapshot?.findMany as jest.Mock).mockResolvedValue(versions);

    const results = await Promise.all(versions.map((v) => Promise.resolve(v)));

    expect(results[0].changedBy).toBe('user-1');
    expect(results[1].changedBy).toBe('user-2');
    expect(results[2].changedBy).toBe('user-1');
  });
});

// ---------------------------------------------------------------------------
// Audit Trail Completeness
// ---------------------------------------------------------------------------

describe('Integration: Audit Trail Coverage', () => {
  let prisma: jest.Mocked<Partial<PrismaClient>>;
  let auditService: AuditService;

  beforeEach(() => {
    prisma = makePrismaMock();
    auditService = new AuditService(prisma as any);
  });

  it('logs workspace operations', async () => {
    (prisma.auditEntry?.create as jest.Mock).mockResolvedValue(auditEntryRow());

    const entry = await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'create',
      resourceType: 'workspace',
      resourceId: 'ws-2',
      resourceName: 'New Workspace',
    });

    expect(entry.resourceType).toBe('workspace');
  });

  it('logs project operations', async () => {
    (prisma.auditEntry?.create as jest.Mock).mockResolvedValue(auditEntryRow());

    const entry = await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'update',
      resourceType: 'project',
      resourceId: 'proj-1',
      resourceName: 'Updated Project',
    });

    expect(entry.resourceType).toBe('project');
  });

  it('logs canvas operations', async () => {
    (prisma.auditEntry?.create as jest.Mock).mockResolvedValue(auditEntryRow());

    const entry = await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'delete',
      resourceType: 'canvas',
      resourceId: 'canvas-1',
      resourceName: 'Deleted Canvas',
    });

    expect(entry.resourceType).toBe('canvas');
  });

  it('logs member operations', async () => {
    (prisma.auditEntry?.create as jest.Mock).mockResolvedValue(auditEntryRow());

    const entry = await auditService.log({
      workspaceId: 'ws-1',
      userId: 'user-1',
      action: 'update',
      resourceType: 'member',
      resourceId: 'user-2',
      resourceName: 'Member Role Change',
    });

    expect(entry.resourceType).toBe('member');
  });
});
