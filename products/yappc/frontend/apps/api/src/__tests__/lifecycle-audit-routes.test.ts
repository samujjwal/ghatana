import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
  projectFindUniqueMock,
  workspaceMemberFindManyMock,
  lifecycleArtifactFindManyMock,
  lifecycleArtifactCreateMock,
  lifecycleArtifactUpdateMock,
  lifecycleArtifactDeleteMock,
  auditLogMock,
} = vi.hoisted(() => ({
  projectFindUniqueMock: vi.fn(),
  workspaceMemberFindManyMock: vi.fn(),
  lifecycleArtifactFindManyMock: vi.fn(),
  lifecycleArtifactCreateMock: vi.fn(),
  lifecycleArtifactUpdateMock: vi.fn(),
  lifecycleArtifactDeleteMock: vi.fn(),
  auditLogMock: vi.fn(),
}));

vi.mock('../database/client.js', () => ({
  getPrismaClient: () => ({
    project: {
      findUnique: projectFindUniqueMock,
    },
    workspaceMember: {
      findMany: workspaceMemberFindManyMock,
    },
    lifecycleArtifact: {
      findMany: lifecycleArtifactFindManyMock,
      create: lifecycleArtifactCreateMock,
      update: lifecycleArtifactUpdateMock,
      delete: lifecycleArtifactDeleteMock,
    },
  }),
}));

vi.mock('../middleware/rbac.middleware', () => ({
  requirePermission: () => async () => undefined,
  requireRole: () => async () => undefined,
}));

vi.mock('../services/audit/audit.service', () => ({
  getAuditService: () => ({
    log: auditLogMock,
  }),
}));

import lifecycleRoutes from '../routes/lifecycle';

function buildHeaders(): Record<string, string> {
  return {
    'x-correlation-id': 'corr-lifecycle-123',
    'user-agent': 'vitest',
  };
}

describe('lifecycle route audit logging', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    vi.clearAllMocks();
    app = Fastify({ logger: false });
    app.addHook('onRequest', async (request) => {
      request.user = {
        userId: 'user-123',
        email: 'user@yappc.local',
        role: 'ADMIN',
        tenantId: 'tenant-123',
        workspaceId: 'workspace-123',
      };
      request.correlationId = 'corr-lifecycle-123';
    });
    await app.register(lifecycleRoutes, { prefix: '/api' });
  });

  afterEach(async () => {
    await app.close();
  });

  it('records audit metadata for gate validation outcomes', async () => {
    lifecycleArtifactFindManyMock.mockResolvedValueOnce([
      { type: 'Problem Statement' },
    ]);
    auditLogMock.mockResolvedValueOnce({ id: 'audit-gate-1' });

    const response = await app.inject({
      method: 'POST',
      url: '/api/gates/validate',
      headers: buildHeaders(),
      payload: {
        projectId: 'project-123',
        phase: 'INTENT',
        gate: 'problem-defined',
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      projectId: 'project-123',
      phase: 'INTENT',
      gate: 'problem-defined',
      passed: true,
      readiness: 100,
      auditRecorded: true,
    });
    expect(auditLogMock).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'YAPPC_LIFECYCLE_GATE_VALIDATED',
        actor: 'user-123',
        actorRole: 'ADMIN',
        resource: '/lifecycle/projects/project-123/gates/problem-defined/validate',
        status: 200,
        tenantId: 'tenant-123',
        success: true,
        metadata: expect.objectContaining({
          workspaceId: 'workspace-123',
          projectId: 'project-123',
          phase: 'INTENT',
          gate: 'problem-defined',
          outcome: 'SUCCESS',
          correlationId: 'corr-lifecycle-123',
          passed: true,
          readiness: 100,
          completedArtifacts: ['Problem Statement'],
          missingArtifacts: [],
        }),
      }),
    );
  });

  it('records audit metadata for lifecycle artifact creation without changing the response body shape', async () => {
    projectFindUniqueMock.mockResolvedValueOnce({
      id: 'project-123',
      ownerWorkspaceId: 'workspace-123',
      ownerWorkspace: { id: 'workspace-123' },
      workspaceProjects: [],
    });
    workspaceMemberFindManyMock.mockResolvedValueOnce([
      { workspaceId: 'workspace-123' },
    ]);
    lifecycleArtifactCreateMock.mockResolvedValueOnce({
      id: 'artifact-123',
      projectId: 'project-123',
      title: 'Problem Statement',
      type: 'Problem Statement',
      status: 'draft',
      phase: 'INTENT',
      flowStage: 0,
      createdBy: 'user-123',
    });
    auditLogMock.mockResolvedValueOnce({ id: 'audit-artifact-create' });

    const response = await app.inject({
      method: 'POST',
      url: '/api/artifacts',
      headers: buildHeaders(),
      payload: {
        projectId: 'project-123',
        title: 'Problem Statement',
        type: 'Problem Statement',
        phase: 'INTENT',
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.headers['x-audit-recorded']).toBe('true');
    expect(response.json()).toMatchObject({
      id: 'artifact-123',
      projectId: 'project-123',
      title: 'Problem Statement',
    });
    expect(response.json()).not.toHaveProperty('auditRecorded');
    expect(auditLogMock).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'YAPPC_LIFECYCLE_ARTIFACT_CREATED',
        resource: '/lifecycle/artifacts/artifact-123',
        status: 201,
        success: true,
        metadata: expect.objectContaining({
          projectId: 'project-123',
          artifactId: 'artifact-123',
          title: 'Problem Statement',
          type: 'Problem Statement',
          artifactStatus: 'draft',
        }),
      }),
    );
  });

  it('records audit headers for artifact update and delete mutations', async () => {
    lifecycleArtifactUpdateMock.mockResolvedValueOnce({
      id: 'artifact-123',
      projectId: 'project-123',
      title: 'Updated Problem Statement',
      type: 'Problem Statement',
      status: 'approved',
      phase: 'INTENT',
    });
    lifecycleArtifactDeleteMock.mockResolvedValueOnce({
      id: 'artifact-123',
      projectId: 'project-123',
      type: 'Problem Statement',
      status: 'approved',
      phase: 'INTENT',
    });
    auditLogMock
      .mockResolvedValueOnce({ id: 'audit-artifact-update' })
      .mockResolvedValueOnce({ id: 'audit-artifact-delete' });

    const updateResponse = await app.inject({
      method: 'PATCH',
      url: '/api/artifacts/artifact-123',
      headers: buildHeaders(),
      payload: {
        title: 'Updated Problem Statement',
        status: 'approved',
      },
    });
    const deleteResponse = await app.inject({
      method: 'DELETE',
      url: '/api/artifacts/artifact-123',
      headers: buildHeaders(),
    });

    expect(updateResponse.statusCode).toBe(200);
    expect(updateResponse.headers['x-audit-recorded']).toBe('true');
    expect(deleteResponse.statusCode).toBe(204);
    expect(deleteResponse.headers['x-audit-recorded']).toBe('true');
    expect(auditLogMock).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        action: 'YAPPC_LIFECYCLE_ARTIFACT_UPDATED',
        metadata: expect.objectContaining({
          artifactId: 'artifact-123',
          changedFields: ['title', 'status'],
          artifactStatus: 'approved',
        }),
      }),
    );
    expect(auditLogMock).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        action: 'YAPPC_LIFECYCLE_ARTIFACT_DELETED',
        status: 204,
        metadata: expect.objectContaining({
          artifactId: 'artifact-123',
          type: 'Problem Statement',
          artifactStatus: 'approved',
        }),
      }),
    );
  });
});
