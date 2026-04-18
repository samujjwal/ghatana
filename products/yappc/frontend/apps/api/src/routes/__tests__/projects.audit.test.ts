import fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
  projectCreateMock,
  projectFindUniqueMock,
  projectUpdateMock,
  workspaceFindUniqueMock,
  auditLogMock,
} = vi.hoisted(() => ({
  projectCreateMock: vi.fn(),
  projectFindUniqueMock: vi.fn(),
  projectUpdateMock: vi.fn(),
  workspaceFindUniqueMock: vi.fn(),
  auditLogMock: vi.fn(),
}));

vi.mock('../../db', () => ({
  default: {
    project: {
      create: projectCreateMock,
      findUnique: projectFindUniqueMock,
      update: projectUpdateMock,
    },
    workspace: {
      findUnique: workspaceFindUniqueMock,
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
});