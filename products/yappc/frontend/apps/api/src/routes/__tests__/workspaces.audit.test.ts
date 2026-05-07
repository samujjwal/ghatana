import fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
  queryRawMock,
  workspaceFindManyMock,
  workspaceFindUniqueMock,
  workspaceMemberFindUniqueMock,
  workspaceCountMock,
  workspaceCreateMock,
  projectCreateMock,
} = vi.hoisted(() => ({
  queryRawMock: vi.fn(),
  workspaceFindManyMock: vi.fn(),
  workspaceFindUniqueMock: vi.fn(),
  workspaceMemberFindUniqueMock: vi.fn(),
  workspaceCountMock: vi.fn(),
  workspaceCreateMock: vi.fn(),
  projectCreateMock: vi.fn(),
}));

vi.mock('../../db', () => ({
  default: {
    $queryRaw: queryRawMock,
    workspace: {
      findMany: workspaceFindManyMock,
      findUnique: workspaceFindUniqueMock,
      count: workspaceCountMock,
      create: workspaceCreateMock,
    },
    workspaceMember: {
      findUnique: workspaceMemberFindUniqueMock,
    },
    project: {
      create: projectCreateMock,
    },
  },
}));

vi.mock('../../middleware/rbac.middleware', () => ({
  requirePermission: () => async () => undefined,
}));

vi.mock('../../middleware/resource-auth.middleware', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../middleware/resource-auth.middleware')>();

  return {
    ...actual,
    requireWorkspaceMember: () => async () => undefined,
    requireWorkspaceOwner: () => async () => undefined,
  };
});

import workspaceRoutes from '../workspaces';

describe('workspaceRoutes bootstrap behavior', () => {
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

    await app.register(workspaceRoutes, { prefix: '/api' });
  });

  afterEach(async () => {
    await app.close();
  });

  it('persists onboarding personas and starter project metadata during workspace creation', async () => {
    workspaceCountMock.mockResolvedValueOnce(0);
    workspaceCreateMock.mockResolvedValueOnce({
      id: 'ws-123',
      name: 'Platform Workspace',
      description: 'Workspace created from onboarding',
      ownerId: 'user-123',
      isDefault: true,
      aiTags: ['persona:founder', 'persona:designer'],
    });
    projectCreateMock.mockResolvedValueOnce({ id: 'project-123' });

    const response = await app.inject({
      method: 'POST',
      url: '/api/workspaces',
      payload: {
        name: 'Platform Workspace',
        description: 'Workspace created from onboarding',
        personaSelections: ['founder', 'designer', 'founder'],
        defaultProject: {
          name: 'Alpha Project',
          description: 'First project',
          type: 'BACKEND',
        },
      },
    });

    expect(response.statusCode).toBe(201);
    expect(workspaceCreateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          name: 'Platform Workspace',
          description: 'Workspace created from onboarding',
          ownerId: 'user-123',
          isDefault: true,
          aiTags: ['persona:founder', 'persona:designer'],
          members: {
            create: {
              userId: 'user-123',
              role: 'ADMIN',
            },
          },
        }),
      })
    );

    expect(projectCreateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          name: 'Alpha Project',
          description: 'First project',
          ownerWorkspaceId: 'ws-123',
          createdById: 'user-123',
          type: 'BACKEND',
          status: 'DRAFT',
          lifecyclePhase: 'INTENT',
          isDefault: true,
        }),
      })
    );
  });

  it('returns server-backed role and capabilities in the workspace list', async () => {
    queryRawMock.mockResolvedValueOnce([{ ok: 1 }]);
    workspaceFindManyMock.mockResolvedValueOnce([
      {
        id: 'ws-123',
        name: 'Platform Workspace',
        ownerId: 'owner-456',
        isDefault: false,
        aiTags: [],
        createdAt: new Date('2026-01-01T00:00:00.000Z'),
        updatedAt: new Date('2026-01-02T00:00:00.000Z'),
        members: [{ role: 'VIEWER' }],
        _count: { ownedProjects: 2 },
      },
    ]);

    const response = await app.inject({
      method: 'GET',
      url: '/api/workspaces',
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      workspaces: [
        {
          id: 'ws-123',
          role: 'VIEWER',
          isOwner: false,
          projectCount: 2,
          capabilities: {
            read: true,
            create: false,
            update: false,
            delete: false,
            comment: true,
          },
        },
      ],
    });
  });
});
