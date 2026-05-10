import fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
  projectFindManyMock,
  projectCreateMock,
  projectFindUniqueMock,
  projectUpdateMock,
  workspaceMemberFindUniqueMock,
  workspaceProjectFindManyMock,
  workspaceProjectFindUniqueMock,
  workspaceFindUniqueMock,
  lifecycleActivityLogCreateMock,
  auditLogMock,
} = vi.hoisted(() => ({
  projectFindManyMock: vi.fn(),
  projectCreateMock: vi.fn(),
  projectFindUniqueMock: vi.fn(),
  projectUpdateMock: vi.fn(),
  workspaceMemberFindUniqueMock: vi.fn(),
  workspaceProjectFindManyMock: vi.fn(),
  workspaceProjectFindUniqueMock: vi.fn(),
  workspaceFindUniqueMock: vi.fn(),
  lifecycleActivityLogCreateMock: vi.fn(),
  auditLogMock: vi.fn(),
}));

vi.mock('../../db', () => ({
  default: {
    project: {
      create: projectCreateMock,
      findMany: projectFindManyMock,
      findUnique: projectFindUniqueMock,
      update: projectUpdateMock,
    },
    workspaceMember: {
      findUnique: workspaceMemberFindUniqueMock,
    },
    workspaceProject: {
      findMany: workspaceProjectFindManyMock,
      findUnique: workspaceProjectFindUniqueMock,
    },
    workspace: {
      findUnique: workspaceFindUniqueMock,
    },
    lifecycleActivityLog: {
      create: lifecycleActivityLogCreateMock,
    },
  },
}));

vi.mock('../../middleware/rbac.middleware', () => ({
  requirePermission: () => async () => undefined,
}));

vi.mock('../../services/audit/audit.service', () => ({
  getAuditService: () => ({
    log: auditLogMock,
  }),
}));

import projectRoutes from '../projects';

describe('projectRoutes audit logging', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    vi.clearAllMocks();
    app = fastify({ logger: false });

    app.addHook('onRequest', async (request) => {
      request.user = {
        userId: 'user-123',
        email: 'user@yappc.local',
        role: 'ADMIN',
        tenantId: 'tenant-123',
        workspaceId: 'ws-123',
      };
    });

    await app.register(projectRoutes, { prefix: '/api' });
  });

  afterEach(async () => {
    await app.close();
  });

  it('emits a project creation audit event with project and workspace metadata', async () => {
    workspaceFindUniqueMock.mockResolvedValueOnce({
      id: 'ws-123',
      name: 'Platform Workspace',
    });
    projectCreateMock.mockResolvedValueOnce({
      id: 'project-123',
      name: 'Alpha Project',
      description: 'New platform build',
      type: 'FULL_STACK',
      status: 'DRAFT',
      ownerWorkspaceId: 'ws-123',
      createdById: 'user-123',
      isDefault: false,
      aiNextActions: ['Define project requirements'],
    });
    projectFindUniqueMock.mockResolvedValueOnce({
      id: 'project-123',
      description: 'New platform build',
      status: 'DRAFT',
      updatedAt: new Date(),
      documents: [],
      pages: [],
    });
    projectUpdateMock.mockResolvedValueOnce({
      id: 'project-123',
      aiHealthScore: 60,
    });
    workspaceMemberFindUniqueMock.mockResolvedValueOnce({ role: 'ADMIN' });

    const response = await app.inject({
      method: 'POST',
      url: '/api/projects',
      payload: {
        name: 'Alpha Project',
        description: 'New platform build',
        type: 'FULL_STACK',
        workspaceId: 'ws-123',
      },
      headers: {
        'user-agent': 'vitest',
      },
    });

    expect(response.statusCode).toBe(201);
    expect(auditLogMock).toHaveBeenCalledWith({
      action: 'PROJECT_CREATED',
      actor: 'user-123',
      actorRole: 'ADMIN',
      resource: '/projects/project-123',
      severity: 'info',
      details: 'Project Alpha Project created in workspace Platform Workspace',
      ipAddress: expect.any(String),
      userAgent: 'vitest',
      method: 'POST',
      status: 201,
      tenantId: 'tenant-123',
      success: true,
      metadata: {
        workspaceId: 'ws-123',
        workspaceName: 'Platform Workspace',
        projectId: 'project-123',
        projectName: 'Alpha Project',
        projectType: 'FULL_STACK',
      },
    });
  });

  it('returns project role and read-only capabilities from the backend list response', async () => {
    workspaceMemberFindUniqueMock.mockResolvedValueOnce({ role: 'VIEWER' });
    projectFindManyMock.mockResolvedValueOnce([
      {
        id: 'owned-1',
        name: 'Owned Project',
        ownerWorkspaceId: 'ws-123',
        type: 'FULL_STACK',
        status: 'ACTIVE',
      },
    ]);
    workspaceProjectFindManyMock.mockResolvedValueOnce([
      {
        addedAt: new Date('2026-01-01T00:00:00.000Z'),
        project: {
          id: 'included-1',
          name: 'Included Project',
          ownerWorkspaceId: 'other-ws',
          type: 'UI',
          status: 'ACTIVE',
        },
      },
    ]);

    const response = await app.inject({
      method: 'GET',
      url: '/api/projects?workspaceId=ws-123',
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      owned: [
        {
          id: 'owned-1',
          role: 'VIEWER',
          isOwned: true,
          isIncluded: false,
          readOnly: true,
          capabilities: { read: true, update: false, delete: false },
        },
      ],
      included: [
        {
          id: 'included-1',
          role: 'VIEWER',
          isOwned: false,
          isIncluded: true,
          readOnly: true,
          capabilities: { read: true, update: false, delete: false },
        },
      ],
    });
  });

  it('returns dedicated dashboard blocker, review, and safe continuation actions', async () => {
    projectFindManyMock.mockResolvedValueOnce([
      {
        id: 'project-blocked',
        name: 'Blocked Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'RUN',
        aiNextActions: ['Resolve critical security blocker'],
        updatedAt: new Date('2026-05-01T00:00:00.000Z'),
      },
      {
        id: 'project-review',
        name: 'Review Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'GENERATE',
        aiNextActions: ['Review generated diff'],
        updatedAt: new Date('2026-05-02T00:00:00.000Z'),
      },
      {
        id: 'project-safe',
        name: 'Safe Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'SHAPE',
        aiNextActions: [],
        updatedAt: new Date('2026-05-03T00:00:00.000Z'),
      },
    ]);
    workspaceProjectFindManyMock.mockResolvedValueOnce([]);

    const response = await app.inject({
      method: 'GET',
      url: '/api/projects/dashboard-actions?workspaceId=ws-123',
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      workspaceId: 'ws-123',
      blockedWork: [
        {
          projectId: 'project-blocked',
          projectName: 'Blocked Project',
          routePhase: 'run',
          kind: 'blocker',
          severity: 'critical',
          requiresReview: true,
          safeToRun: false,
        },
      ],
      reviewRequired: [
        {
          projectId: 'project-review',
          routePhase: 'generate',
          kind: 'review',
          requiresReview: true,
          safeToRun: false,
        },
      ],
      safeToContinue: [
        {
          projectId: 'project-safe',
          routePhase: 'shape',
          kind: 'safe-to-continue',
          source: 'project.lifecyclePhase',
          requiresReview: false,
          safeToRun: true,
        },
      ],
    });
  });

  it('normalizes legacy lifecycle aliases to mounted dashboard route phases', async () => {
    projectFindManyMock.mockResolvedValueOnce([
      {
        id: 'project-context',
        name: 'Context Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'CONTEXT',
        aiNextActions: [],
        updatedAt: new Date('2026-05-01T00:00:00.000Z'),
      },
      {
        id: 'project-plan',
        name: 'Plan Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'PLAN',
        aiNextActions: [],
        updatedAt: new Date('2026-05-02T00:00:00.000Z'),
      },
      {
        id: 'project-execute',
        name: 'Execute Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'EXECUTE',
        aiNextActions: [],
        updatedAt: new Date('2026-05-03T00:00:00.000Z'),
      },
      {
        id: 'project-verify',
        name: 'Verify Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'VERIFY',
        aiNextActions: [],
        updatedAt: new Date('2026-05-04T00:00:00.000Z'),
      },
      {
        id: 'project-institutionalize',
        name: 'Institutionalize Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'INSTITUTIONALIZE',
        aiNextActions: [],
        updatedAt: new Date('2026-05-05T00:00:00.000Z'),
      },
    ]);
    workspaceProjectFindManyMock.mockResolvedValueOnce([]);

    const response = await app.inject({
      method: 'GET',
      url: '/api/projects/dashboard-actions?workspaceId=ws-123',
    });

    expect(response.statusCode).toBe(200);
    expect(
      response.json().safeToContinue.map((action: { projectId: string; routePhase: string }) => [
        action.projectId,
        action.routePhase,
      ])
    ).toEqual([
      ['project-context', 'shape'],
      ['project-plan', 'validate'],
      ['project-execute', 'generate'],
      ['project-verify', 'run'],
      ['project-institutionalize', 'evolve'],
    ]);
  });

  it('executes only safe dashboard actions and records the backend decision', async () => {
    projectFindUniqueMock.mockResolvedValueOnce({
      id: 'project-safe',
      name: 'Safe Project',
      ownerWorkspaceId: 'ws-123',
      lifecyclePhase: 'SHAPE',
      aiNextActions: [],
      updatedAt: new Date('2026-05-03T00:00:00.000Z'),
    });
    lifecycleActivityLogCreateMock.mockResolvedValueOnce({
      id: 'activity-1',
    });

    const response = await app.inject({
      method: 'POST',
      url: '/api/projects/project-safe/dashboard-actions/execute',
      payload: {
        workspaceId: 'ws-123',
        actionId: 'project-safe-safe-to-continue-0',
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      projectId: 'project-safe',
      actionId: 'project-safe-safe-to-continue-0',
      outcome: 'opened-phase-cockpit',
      targetPhase: 'shape',
      targetPath: '/p/project-safe/shape',
      auditRecorded: true,
    });
    expect(lifecycleActivityLogCreateMock).toHaveBeenCalledWith({
      data: expect.objectContaining({
        projectId: 'project-safe',
        userId: 'user-123',
        action: 'DASHBOARD_ACTION_EXECUTED',
        metadata: expect.objectContaining({
          workspaceId: 'ws-123',
          actionId: 'project-safe-safe-to-continue-0',
          targetPhase: 'shape',
        }),
      }),
    });
  });

  it('blocks review-required dashboard actions from one-click execution', async () => {
    projectFindUniqueMock.mockResolvedValueOnce({
      id: 'project-review',
      name: 'Review Project',
      ownerWorkspaceId: 'ws-123',
      lifecyclePhase: 'GENERATE',
      aiNextActions: ['Review generated diff'],
      updatedAt: new Date('2026-05-03T00:00:00.000Z'),
    });

    const response = await app.inject({
      method: 'POST',
      url: '/api/projects/project-review/dashboard-actions/execute',
      payload: {
        workspaceId: 'ws-123',
        actionId: 'project-review-review-0',
      },
    });

    expect(response.statusCode).toBe(409);
    expect(response.json()).toMatchObject({
      error: 'Dashboard action requires review before execution',
      action: {
        projectId: 'project-review',
        safeToRun: false,
        requiresReview: true,
      },
    });
    expect(lifecycleActivityLogCreateMock).not.toHaveBeenCalled();
  });

  it('blocks dashboard action execution for included read-only projects', async () => {
    projectFindUniqueMock.mockResolvedValueOnce({
      id: 'project-included',
      name: 'Included Project',
      ownerWorkspaceId: 'owner-ws',
      lifecyclePhase: 'SHAPE',
      aiNextActions: [],
      updatedAt: new Date('2026-05-03T00:00:00.000Z'),
    });
    workspaceProjectFindUniqueMock.mockResolvedValueOnce({
      workspaceId: 'ws-123',
      projectId: 'project-included',
      addedAt: new Date('2026-05-04T00:00:00.000Z'),
    });

    const response = await app.inject({
      method: 'POST',
      url: '/api/projects/project-included/dashboard-actions/execute',
      payload: {
        workspaceId: 'ws-123',
        actionId: 'project-included-safe-to-continue-0',
      },
    });

    expect(response.statusCode).toBe(403);
    expect(response.json()).toMatchObject({
      error: 'Included projects are read-only in this workspace',
      reason: 'included_project_read_only',
      projectId: 'project-included',
    });
    expect(lifecycleActivityLogCreateMock).not.toHaveBeenCalled();
  });

  // TODO-014: Test dashboard safe-to-run action execution
  describe('safe-to-run action execution', () => {
    it('executes safe actions through backend', async () => {
      projectFindUniqueMock.mockResolvedValueOnce({
        id: 'project-safe',
        name: 'Safe Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'SHAPE',
        aiNextActions: [],
        updatedAt: new Date('2026-05-03T00:00:00.000Z'),
      });
      lifecycleActivityLogCreateMock.mockResolvedValueOnce({
        id: 'activity-1',
        projectId: 'project-safe',
        action: 'PHASE_TRANSITION_REQUESTED',
        summary: 'Dashboard action executed',
        timestamp: new Date(),
        actor: 'user-1',
        severity: 'info',
        success: true,
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/projects/project-safe/dashboard-actions/execute',
        payload: {
          workspaceId: 'ws-123',
          actionId: 'project-safe-safe-to-continue-0',
        },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({
        projectId: 'project-safe',
        actionId: 'project-safe-safe-to-continue-0',
        outcome: 'opened-phase-cockpit',
        targetPath: '/p/project-safe/shape',
        auditRecorded: true,
      });
      expect(lifecycleActivityLogCreateMock).toHaveBeenCalledWith(
        expect.objectContaining({
          projectId: 'project-safe',
          action: 'PHASE_TRANSITION_REQUESTED',
          summary: expect.stringContaining('dashboard action'),
        })
      );
    });

    it('prevents review-required actions from executing directly', async () => {
      projectFindUniqueMock.mockResolvedValueOnce({
        id: 'project-review',
        name: 'Review Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'GENERATE',
        aiNextActions: ['Review generated diff'],
        updatedAt: new Date('2026-05-02T00:00:00.000Z'),
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/projects/project-review/dashboard-actions/execute',
        payload: {
          workspaceId: 'ws-123',
          actionId: 'project-review-review-0',
        },
      });

      expect(response.statusCode).toBe(409);
      expect(response.json()).toMatchObject({
        error: 'Dashboard action requires review before execution',
        action: {
          projectId: 'project-review',
          safeToRun: false,
          requiresReview: true,
        },
      });
      expect(lifecycleActivityLogCreateMock).not.toHaveBeenCalled();
    });

    it('prevents unsafe blocker actions from executing directly', async () => {
      projectFindUniqueMock.mockResolvedValueOnce({
        id: 'project-blocked',
        name: 'Blocked Project',
        ownerWorkspaceId: 'ws-123',
        lifecyclePhase: 'RUN',
        aiNextActions: ['Resolve critical security blocker'],
        updatedAt: new Date('2026-05-01T00:00:00.000Z'),
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/projects/project-blocked/dashboard-actions/execute',
        payload: {
          workspaceId: 'ws-123',
          actionId: 'project-blocked-blocker-0',
        },
      });

      expect(response.statusCode).toBe(409);
      expect(response.json()).toMatchObject({
        error: 'Dashboard action requires review before execution',
        action: {
          projectId: 'project-blocked',
          safeToRun: false,
          requiresReview: true,
        },
      });
      expect(lifecycleActivityLogCreateMock).not.toHaveBeenCalled();
    });
  });
});
