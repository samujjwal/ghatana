import fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { auditLogMock } = vi.hoisted(() => ({
  auditLogMock: vi.fn(),
}));

vi.mock('../../middleware/rbac.middleware', () => ({
  requirePermission: () => async () => undefined,
}));

vi.mock('../../services/audit/audit.service', () => ({
  getAuditService: () => ({
    log: auditLogMock,
  }),
}));

import registryCandidateRoutes from '../registry-candidates';

describe('registryCandidateRoutes', () => {
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
        workspaceId: 'workspace-123',
      };
      request.correlationId = 'corr-123';
    });
    await app.register(registryCandidateRoutes, { prefix: '/api/v1' });
  });

  afterEach(async () => {
    await app.close();
  });

  it('promotes residual islands to registry candidates backed by audit evidence', async () => {
    auditLogMock.mockResolvedValueOnce({
      id: 'audit-registry-1',
      timestamp: new Date('2026-05-07T12:00:00.000Z'),
    });

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifacts/artifact-1/residual-islands/legacy-chart/registry-candidates',
      headers: {
        'x-project-id': 'project-123',
        'user-agent': 'vitest',
      },
      payload: {
        proposedContractName: 'LegacyChartCandidate',
        source: 'decompiled-import',
        notes: 'Promote decompiled island for registry review.',
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toEqual({
      candidateId: 'registry-candidate-artifact-1-legacy-chart',
      artifactId: 'artifact-1',
      residualIslandId: 'legacy-chart',
      proposedContractName: 'LegacyChartCandidate',
      status: 'NEEDS_REVIEW',
      auditRecordId: 'audit-registry-1',
      auditRecorded: true,
      createdAt: '2026-05-07T12:00:00.000Z',
    });
    expect(auditLogMock).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'YAPPC_REGISTRY_CANDIDATE_PROMOTED',
        actor: 'user-123',
        actorRole: 'ADMIN',
        resource: 'artifact/artifact-1/residual-island/legacy-chart/registry-candidate',
        severity: 'info',
        status: 201,
        tenantId: 'tenant-123',
        success: true,
        metadata: expect.objectContaining({
          workspaceId: 'workspace-123',
          projectId: 'project-123',
          artifactId: 'artifact-1',
          residualIslandId: 'legacy-chart',
          candidateId: 'registry-candidate-artifact-1-legacy-chart',
          proposedContractName: 'LegacyChartCandidate',
          source: 'decompiled-import',
          status: 'NEEDS_REVIEW',
          notes: 'Promote decompiled island for registry review.',
          correlationId: 'corr-123',
        }),
      }),
    );
  });

  it('rejects incomplete promotion requests before writing audit records', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifacts/artifact-1/residual-islands/legacy-chart/registry-candidates',
      payload: {
        source: 'decompiled-import',
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      error: 'artifactId, residualIslandId, and proposedContractName are required',
      auditRecorded: false,
    });
    expect(auditLogMock).not.toHaveBeenCalled();
  });

  it('fails closed when audit evidence cannot be persisted', async () => {
    auditLogMock.mockRejectedValueOnce(new Error('audit unavailable'));

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifacts/artifact-1/residual-islands/legacy-chart/registry-candidates',
      payload: {
        proposedContractName: 'LegacyChartCandidate',
        source: 'decompiled-import',
      },
    });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toEqual({
      error: 'Registry candidate promotion audit persistence failed',
      auditRecorded: false,
    });
  });
});
