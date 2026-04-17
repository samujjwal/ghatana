import fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
  workspaceFindUniqueMock,
  projectFindManyMock,
} = vi.hoisted(() => ({
  workspaceFindUniqueMock: vi.fn(),
  projectFindManyMock: vi.fn(),
}));

vi.mock('../../db', () => ({
  default: {
    project: {
      findMany: projectFindManyMock,
    },
    workspace: {
      findUnique: workspaceFindUniqueMock,
    },
  },
}));

vi.mock('../../middleware/deprecation', () => ({
  markDeprecated: () => undefined,
}));

vi.mock('../../middleware/rbac.middleware', () => ({
  requirePermission: () => async () => undefined,
}));

vi.mock('../../services/audit/audit.service', () => ({
  getAuditService: () => ({
    log: vi.fn(),
  }),
}));

import projectRoutes from '../projects';

describe('project setup suggestion route', () => {
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
        workspaceId: 'ws-current',
      };
    });

    await app.register(projectRoutes, { prefix: '/api' });
  });

  afterEach(async () => {
    await app.close();
  });

  it('infers a backend project type and returns cross-workspace recommendations', async () => {
    workspaceFindUniqueMock.mockResolvedValueOnce({
      id: 'ws-current',
    });
    workspaceFindUniqueMock.mockResolvedValueOnce({
      id: 'ws-current',
      name: 'Current Workspace',
      ownedProjects: [{ name: 'Current Platform' }],
    });
    projectFindManyMock.mockResolvedValueOnce([
      {
        id: 'project-1',
        name: 'Identity API',
        type: 'BACKEND',
        ownerWorkspaceId: 'ws-other',
        ownerWorkspace: {
          id: 'ws-other',
          name: 'Shared Services',
        },
      },
    ]);

    const response = await app.inject({
      method: 'POST',
      url: '/api/projects/setup-suggestion',
      payload: {
        workspaceId: 'ws-current',
        description: 'Build an authenticated API service for mobile clients and integrations.',
        preferredType: 'FULL_STACK',
      },
    });

    expect(response.statusCode).toBe(200);
    expect(projectFindManyMock).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          ownerWorkspaceId: { not: 'ws-current' },
          type: 'BACKEND',
        }),
      })
    );

    expect(response.json()).toEqual(
      expect.objectContaining({
        inferredType: 'BACKEND',
        suggestion: expect.any(String),
        recommendations: expect.arrayContaining([
          'Define the core API contract and integration boundaries before scaffolding.',
        ]),
        relatedProjects: [
          expect.objectContaining({
            id: 'project-1',
            name: 'Identity API',
            ownerWorkspaceName: 'Shared Services',
            type: 'BACKEND',
          }),
        ],
      })
    );
  });
});