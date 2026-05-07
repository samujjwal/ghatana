import fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { auditLogMock } = vi.hoisted(() => ({
  auditLogMock: vi.fn(),
}));

vi.mock('../../services/audit/audit.service', () => ({
  getAuditService: () => ({
    log: auditLogMock,
  }),
}));

import sourceImportRoutes from '../source-imports';

describe('sourceImportRoutes', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    vi.clearAllMocks();
    app = fastify({ logger: false });
    await app.register(sourceImportRoutes, { prefix: '/api/v1' });
  });

  afterEach(async () => {
    await app.close();
    vi.unstubAllGlobals();
  });

  it('rejects governed source import requests without tenant workspace and project headers', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifact/import-source',
      payload: {
        sourceType: 'tsx',
        source: 'https://example.com/Page.tsx',
        projectId: 'project-1',
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      success: false,
      job: {
        status: 'REJECTED',
        reason: 'missing_scope',
      },
    });
  });

  it('creates a review-required import job for trusted scoped HTTPS sources', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response('export function InboxPanel() { return <main>Inbox</main>; }', {
        status: 200,
        headers: { 'content-type': 'text/plain' },
      }),
    ));

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifact/import-source',
      headers: {
        'x-tenant-id': 'tenant-1',
        'x-workspace-id': 'workspace-1',
        'x-project-id': 'project-1',
      },
      payload: {
        sourceType: 'tsx',
        source: 'https://example.com/InboxPanel.tsx',
        projectId: 'project-1',
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      success: true,
      componentId: 'project-1/InboxPanel',
      metadata: {
        sourceType: 'tsx',
        source: 'https://example.com/InboxPanel.tsx',
        componentName: 'InboxPanel',
        fileCount: 1,
      },
      job: {
        status: 'REVIEW_REQUIRED',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
        auditRecorded: true,
      },
    });
    expect(auditLogMock).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'YAPPC_SOURCE_IMPORT',
        actor: 'system',
        actorRole: 'SYSTEM',
        tenantId: 'tenant-1',
        status: 200,
        success: true,
        metadata: expect.objectContaining({
          workspaceId: 'workspace-1',
          projectId: 'project-1',
          sourceType: 'tsx',
          outcome: 'REVIEW_REQUIRED',
          componentName: 'InboxPanel',
        }),
      }),
    );
  });

  it('rejects untrusted local source locators before fetching content', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/artifact/import-source',
      headers: {
        'x-tenant-id': 'tenant-1',
        'x-workspace-id': 'workspace-1',
        'x-project-id': 'project-1',
      },
      payload: {
        sourceType: 'tsx',
        source: '/Users/sam/Secret.tsx',
        projectId: 'project-1',
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      success: false,
      job: {
        status: 'REJECTED',
        reason: 'untrusted_source_locator',
        auditRecorded: true,
      },
    });
    expect(fetchMock).not.toHaveBeenCalled();
    expect(auditLogMock).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'YAPPC_SOURCE_IMPORT',
        status: 400,
        success: false,
        error: 'untrusted_source_locator',
        metadata: expect.objectContaining({
          projectId: 'project-1',
          sourceType: 'tsx',
          outcome: 'REJECTED',
          reason: 'untrusted_source_locator',
        }),
      }),
    );
  });
});
