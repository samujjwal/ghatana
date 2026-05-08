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

import importReviewRoutes from '../import-review';

describe('importReviewRoutes', () => {
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
    await app.register(importReviewRoutes, { prefix: '/api/v1' });
  });

  afterEach(async () => {
    await app.close();
  });

  it('persists residual island review decisions with audit evidence', async () => {
    auditLogMock.mockResolvedValueOnce({
      id: 'audit-residual-1',
      timestamp: new Date('2026-05-07T12:00:00.000Z'),
    });

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifacts/artifact-1/residual-islands/legacy-chart/review',
      headers: {
        'x-project-id': 'project-123',
        'user-agent': 'vitest',
      },
      payload: {
        decision: 'REJECTED',
        notes: 'Needs canonical mapping.',
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      artifactId: 'artifact-1',
      residualIslandId: 'legacy-chart',
      decision: 'REJECTED',
      auditRecordId: 'audit-residual-1',
      auditRecorded: true,
      reviewedAt: '2026-05-07T12:00:00.000Z',
    });
    expect(auditLogMock).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'YAPPC_RESIDUAL_ISLAND_REVIEWED',
        actor: 'user-123',
        actorRole: 'ADMIN',
        resource: 'artifact/artifact-1/residual-island/legacy-chart/review',
        status: 200,
        tenantId: 'tenant-123',
        success: true,
        metadata: expect.objectContaining({
          workspaceId: 'workspace-123',
          projectId: 'project-123',
          artifactId: 'artifact-1',
          residualIslandId: 'legacy-chart',
          decision: 'REJECTED',
          notes: 'Needs canonical mapping.',
          correlationId: 'corr-123',
        }),
      }),
    );
  });

  it('persists loss-point import review decisions with audit evidence', async () => {
    auditLogMock.mockResolvedValueOnce({
      id: 'audit-import-review-1',
      timestamp: '2026-05-07T12:00:01.000Z',
    });

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifacts/artifact-1/import-review-decisions',
      headers: {
        'x-project-id': 'project-123',
      },
      payload: {
        reviewItemId: 'loss-0-style-header',
        kind: 'loss-point',
        decision: 'skipped',
        label: 'style at Header.tsx',
        details: 'CSS module could not be decompiled.',
        notes: 'Reviewed by operator.',
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toEqual({
      artifactId: 'artifact-1',
      reviewItemId: 'loss-0-style-header',
      kind: 'loss-point',
      decision: 'skipped',
      auditRecordId: 'audit-import-review-1',
      auditRecorded: true,
      reviewedAt: '2026-05-07T12:00:01.000Z',
    });
    expect(auditLogMock).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'YAPPC_IMPORT_REVIEW_DECISION_RECORDED',
        resource: 'artifact/artifact-1/import-review/loss-0-style-header',
        status: 201,
        metadata: expect.objectContaining({
          reviewItemId: 'loss-0-style-header',
          decisionId: 'import-review-artifact-1-loss-0-style-header',
          kind: 'loss-point',
          decision: 'skipped',
          label: 'style at Header.tsx',
          details: 'CSS module could not be decompiled.',
          notes: 'Reviewed by operator.',
        }),
      }),
    );
  });

  it('rejects invalid import review requests before writing audit records', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifacts/artifact-1/import-review-decisions',
      payload: {
        decision: 'skipped',
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toEqual({
      error: 'artifactId, reviewItemId, kind, and decision are required',
      auditRecorded: false,
    });
    expect(auditLogMock).not.toHaveBeenCalled();
  });

  it('fails closed when residual review audit persistence fails', async () => {
    auditLogMock.mockRejectedValueOnce(new Error('audit unavailable'));

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifacts/artifact-1/residual-islands/legacy-chart/review',
      payload: {
        decision: 'ACCEPTED',
      },
    });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toEqual({
      error: 'Residual island review audit persistence failed',
      auditRecorded: false,
    });
  });
});
