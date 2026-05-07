import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  HttpPageArtifactPersistenceAdapter,
  LocalStoragePageArtifactPersistenceAdapter,
  ResilientPageArtifactPersistenceAdapter,
  PageArtifactConflictError,
  PageArtifactPersistenceError,
  isConflictError,
} from '../pageArtifactPersistence';
import { createPageArtifactDocument } from '../pageArtifactDocument';

const buildDocument = () =>
  createPageArtifactDocument({
    artifactId: 'artifact-1',
    name: 'Landing',
    createdBy: 'tester',
  });

const scope = {
  tenantId: 'tenant-1',
  workspaceId: 'workspace-1',
  projectId: 'project-1',
};

afterEach(() => {
  vi.useRealTimers();
});

describe('pageArtifactPersistence', () => {
  it('saves and loads with local storage adapter', async () => {
    const adapter = new LocalStoragePageArtifactPersistenceAdapter('test:page-artifact:');
    const document = buildDocument();

    await adapter.save(document);
    const loaded = await adapter.load(document.artifactId);

    expect(loaded?.artifactId).toBe(document.artifactId);
    expect(loaded?.documentId).toBe(document.documentId);
  });

  it('uses HTTP adapter endpoints for save and load', async () => {
    const document = buildDocument();
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, status: 200 })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => document });

    const adapter = new HttpPageArtifactPersistenceAdapter({
      baseUrl: '/api/v1/page-artifacts',
      fetchImpl: fetchImpl as unknown as typeof fetch,
      scope,
    });

    await adapter.save(document);
    const loaded = await adapter.load(document.artifactId);

    expect(fetchImpl).toHaveBeenNthCalledWith(
      1,
      '/api/v1/page-artifacts/artifact-1/document',
      expect.objectContaining({ method: 'PUT' }),
    );
    expect(fetchImpl).toHaveBeenNthCalledWith(
      2,
      '/api/v1/page-artifacts/artifact-1/document',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(loaded?.artifactId).toBe(document.artifactId);
  });

  it('falls back to local save when primary save fails', async () => {
    const document = buildDocument();
    const primary = {
      save: vi.fn().mockRejectedValue(new Error('offline')),
      load: vi.fn(),
    };
    const fallback = {
      save: vi.fn().mockResolvedValue(undefined),
      load: vi.fn(),
    };

    const adapter = new ResilientPageArtifactPersistenceAdapter(primary, fallback);
    await adapter.save(document);

    expect(primary.save).toHaveBeenCalledWith(document);
    expect(fallback.save).toHaveBeenCalledWith(document);
  });

  it('falls back to local load when primary load fails', async () => {
    const document = buildDocument();
    const primary = {
      save: vi.fn(),
      load: vi.fn().mockRejectedValue(new Error('offline')),
    };
    const fallback = {
      save: vi.fn(),
      load: vi.fn().mockResolvedValue(document),
    };

    const adapter = new ResilientPageArtifactPersistenceAdapter(primary, fallback);
    const loaded = await adapter.load(document.artifactId);

    expect(primary.load).toHaveBeenCalledWith(document.artifactId);
    expect(fallback.load).toHaveBeenCalledWith(document.artifactId);
    expect(loaded?.artifactId).toBe(document.artifactId);
  });
});

// ---------------------------------------------------------------------------
// Conflict detection tests
// ---------------------------------------------------------------------------

describe('pageArtifactPersistence — conflict detection', () => {
  it('HTTP adapter throws PageArtifactConflictError on 409', async () => {
    const document = buildDocument();
    const responseHeaders = new Map([['X-Current-Version', 'doc-remote']]);
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      headers: { get: (h: string) => responseHeaders.get(h) ?? null },
    });

    const adapter = new HttpPageArtifactPersistenceAdapter({
      baseUrl: '/api/v1/page-artifacts',
      fetchImpl: fetchImpl as unknown as typeof fetch,
      scope,
    });

    await expect(adapter.save(document)).rejects.toThrow(PageArtifactConflictError);
    await expect(adapter.save(document)).rejects.toMatchObject({
      kind: 'conflict',
      remoteVersion: 'doc-remote',
    });
  });

  it('HTTP adapter sends If-Match header with documentId', async () => {
    const document = buildDocument();
    const fetchImpl = vi.fn().mockResolvedValue({ ok: true, status: 200 });

    const adapter = new HttpPageArtifactPersistenceAdapter({
      baseUrl: '/api/v1/page-artifacts',
      fetchImpl: fetchImpl as unknown as typeof fetch,
      scope,
    });

    await adapter.save(document);

    expect(fetchImpl).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({
          'If-Match': document.documentId,
          'X-Tenant-ID': scope.tenantId,
          'X-Workspace-ID': scope.workspaceId,
          'X-Project-ID': scope.projectId,
        }),
        credentials: 'include',
      }),
    );
  });

  it('HTTP adapter reads current tenant workspace and project scope from scope provider', async () => {
    const document = buildDocument();
    const fetchImpl = vi.fn().mockResolvedValue({ ok: true, status: 200 });
    const dynamicScope = {
      tenantId: 'tenant-dynamic',
      workspaceId: 'workspace-dynamic',
      projectId: 'project-dynamic',
    };

    const adapter = new HttpPageArtifactPersistenceAdapter({
      baseUrl: '/api/v1/page-artifacts',
      fetchImpl: fetchImpl as unknown as typeof fetch,
      scopeProvider: () => dynamicScope,
    });

    await adapter.save(document);

    expect(fetchImpl).toHaveBeenCalledWith(
      '/api/v1/page-artifacts/artifact-1/document',
      expect.objectContaining({
        method: 'PUT',
        credentials: 'include',
        headers: expect.objectContaining({
          'X-Tenant-ID': dynamicScope.tenantId,
          'X-Workspace-ID': dynamicScope.workspaceId,
          'X-Project-ID': dynamicScope.projectId,
          'If-Match': document.documentId,
        }),
      }),
    );
  });

  it('HTTP adapter ingests artifact graph snapshots after document save', async () => {
    const document = {
      ...buildDocument(),
      artifactGraph: {
        graphId: 'artifact-1:graph',
        projectId: scope.projectId,
        sourceType: 'tsx',
        source: 'src/pages/Landing.tsx',
        importedAt: '2026-05-07T00:00:00.000Z',
        nodes: [
          {
            id: 'artifact-1:page',
            kind: 'page',
            label: 'Landing',
          },
          {
            id: 'artifact-1:source',
            kind: 'source',
            label: 'src/pages/Landing.tsx',
            sourceLocation: {
              filePath: 'src/pages/Landing.tsx',
              startLine: 1,
              startColumn: 1,
              endLine: 20,
              endColumn: 1,
            },
            metadata: {
              sourceType: 'tsx',
            },
          },
        ],
        edges: [
          {
            id: 'artifact-1:page-derived-from-source',
            from: 'artifact-1:page',
            to: 'artifact-1:source',
            kind: 'derived-from',
          },
        ],
        provenance: {
          createdBy: 'tester',
          compiler: 'yappc-artifact-compiler',
          confidence: 0.91,
          residualIslandIds: [],
        },
      },
    };
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, status: 200 })
      .mockResolvedValueOnce({ ok: true, status: 200 });

    const adapter = new HttpPageArtifactPersistenceAdapter({
      baseUrl: '/api/v1/page-artifacts',
      artifactGraphBaseUrl: '/api/v1/yappc/artifact/graph',
      fetchImpl: fetchImpl as unknown as typeof fetch,
      scope,
    });

    await adapter.save(document);

    expect(fetchImpl).toHaveBeenNthCalledWith(
      2,
      '/api/v1/yappc/artifact/graph/ingest',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
          'X-Tenant-ID': scope.tenantId,
          'X-Workspace-ID': scope.workspaceId,
          'X-Project-ID': scope.projectId,
        }),
      }),
    );

    const graphRequest = JSON.parse(String(fetchImpl.mock.calls[1]?.[1]?.body));
    expect(graphRequest).toMatchObject({
      productId: 'artifact-1:graph',
      tenantId: scope.tenantId,
      nodes: expect.arrayContaining([
        expect.objectContaining({
          id: 'artifact-1:page',
          type: 'page',
          projectId: scope.projectId,
          tenantId: scope.tenantId,
        }),
      ]),
      edges: expect.arrayContaining([
        expect.objectContaining({
          sourceNodeId: 'artifact-1:page',
          targetNodeId: 'artifact-1:source',
          relationshipType: 'derived-from',
        }),
      ]),
    });
    expect(graphRequest.nodes[1].properties).toMatchObject({
      artifactId: document.artifactId,
      graphId: 'artifact-1:graph',
      sourceLocationFilePath: 'src/pages/Landing.tsx',
      confidence: 0.91,
    });
  });

  it('HTTP adapter surfaces artifact graph ingest failures without local fallback masking', async () => {
    const document = {
      ...buildDocument(),
      artifactGraph: {
        graphId: 'artifact-1:graph',
        projectId: scope.projectId,
        sourceType: 'semantic-model',
        source: 'semantic-model',
        importedAt: '2026-05-07T00:00:00.000Z',
        nodes: [{ id: 'artifact-1:page', kind: 'page' as const, label: 'Landing' }],
        edges: [],
        provenance: {
          createdBy: 'tester',
          compiler: 'yappc-artifact-compiler' as const,
          confidence: 0.8,
          residualIslandIds: [],
        },
      },
    };
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, status: 200 })
      .mockResolvedValueOnce({
        ok: false,
        status: 503,
        text: async () => 'graph store unavailable',
      });
    const fallback = {
      save: vi.fn().mockResolvedValue(undefined),
      load: vi.fn(),
    };

    const adapter = new ResilientPageArtifactPersistenceAdapter(
      new HttpPageArtifactPersistenceAdapter({
        fetchImpl: fetchImpl as unknown as typeof fetch,
        scope,
      }),
      fallback,
    );

    await expect(adapter.save(document)).rejects.toMatchObject({
      kind: 'artifact-graph',
      status: 503,
    });
    expect(fallback.save).not.toHaveBeenCalled();
  });

  it('HTTP adapter fails actionably when scope is missing', async () => {
    const document = buildDocument();
    const fetchImpl = vi.fn();
    const adapter = new HttpPageArtifactPersistenceAdapter({
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    await expect(adapter.save(document)).rejects.toMatchObject({
      kind: 'missing-scope',
      status: 422,
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('HTTP adapter maps 401 and 403 to distinct errors', async () => {
    const document = buildDocument();
    const unauthorizedFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      headers: { get: () => null },
      text: async () => '',
    });
    const forbiddenFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
      headers: { get: () => null },
      text: async () => '',
    });

    await expect(
      new HttpPageArtifactPersistenceAdapter({
        fetchImpl: unauthorizedFetch as unknown as typeof fetch,
        scope,
      }).save(document),
    ).rejects.toMatchObject({ kind: 'unauthenticated', status: 401 });

    await expect(
      new HttpPageArtifactPersistenceAdapter({
        fetchImpl: forbiddenFetch as unknown as typeof fetch,
        scope,
      }).save(document),
    ).rejects.toMatchObject({ kind: 'forbidden', status: 403 });
  });

  it('HTTP adapter maps load validation failures to actionable 422 errors', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: false,
      status: 422,
      headers: { get: () => null },
      text: async () => 'workspaceId is required',
    });

    const adapter = new HttpPageArtifactPersistenceAdapter({
      fetchImpl: fetchImpl as unknown as typeof fetch,
      scope,
    });

    await expect(adapter.load('artifact-1')).rejects.toMatchObject({
      kind: 'validation',
      status: 422,
      message: expect.stringContaining('workspaceId is required'),
    });
  });

  it('local drafts reject sensitive artifacts by policy', async () => {
    const document = {
      ...buildDocument(),
      dataClassification: 'SENSITIVE' as never,
    };
    const adapter = new LocalStoragePageArtifactPersistenceAdapter('test:page-artifact:');

    await expect(adapter.save(document)).rejects.toThrow(PageArtifactPersistenceError);
    expect(localStorage.getItem(`test:page-artifact:${document.artifactId}`)).toBeNull();
  });

  it('local drafts store explicit expiry metadata', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-05-07T12:00:00.000Z'));

    const document = buildDocument();
    const adapter = new LocalStoragePageArtifactPersistenceAdapter(
      'test:page-artifact:',
      { allowClassifications: new Set(['INTERNAL']), ttlMs: 60_000 },
    );

    await adapter.save(document);

    const raw = localStorage.getItem(`test:page-artifact:${document.artifactId}`);
    expect(raw).not.toBeNull();
    const envelope = JSON.parse(String(raw)) as Record<string, unknown>;
    expect(envelope).toMatchObject({
      schemaVersion: 1,
      savedAt: '2026-05-07T12:00:00.000Z',
      expiresAt: '2026-05-07T12:01:00.000Z',
      document: expect.objectContaining({
        artifactId: document.artifactId,
        dataClassification: 'INTERNAL',
      }),
    });

    await expect(adapter.load(document.artifactId)).resolves.toMatchObject({
      artifactId: document.artifactId,
    });
  });

  it('local drafts remove expired envelope entries on load', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-05-07T12:00:00.000Z'));

    const document = buildDocument();
    const adapter = new LocalStoragePageArtifactPersistenceAdapter(
      'test:page-artifact:',
      { allowClassifications: new Set(['INTERNAL']), ttlMs: 1_000 },
    );

    await adapter.save(document);
    vi.setSystemTime(new Date('2026-05-07T12:00:01.001Z'));

    await expect(adapter.load(document.artifactId)).resolves.toBeNull();
    expect(localStorage.getItem(`test:page-artifact:${document.artifactId}`)).toBeNull();
  });

  it('local drafts keep compatible legacy raw documents but purge restricted legacy drafts', async () => {
    const document = buildDocument();
    const restrictedDocument = {
      ...document,
      artifactId: 'artifact-sensitive',
      dataClassification: 'SENSITIVE' as never,
    };
    const adapter = new LocalStoragePageArtifactPersistenceAdapter('test:page-artifact:');

    localStorage.setItem(`test:page-artifact:${document.artifactId}`, JSON.stringify(document));
    localStorage.setItem(
      `test:page-artifact:${restrictedDocument.artifactId}`,
      JSON.stringify(restrictedDocument),
    );

    await expect(adapter.load(document.artifactId)).resolves.toMatchObject({
      artifactId: document.artifactId,
    });
    await expect(adapter.load(restrictedDocument.artifactId)).resolves.toBeNull();
    expect(localStorage.getItem(`test:page-artifact:${restrictedDocument.artifactId}`)).toBeNull();
  });

  it('isConflictError correctly identifies conflict errors', () => {
    const conflict = new PageArtifactConflictError('art-1', 'v2');
    expect(isConflictError(conflict)).toBe(true);
    expect(isConflictError(new Error('other'))).toBe(false);
    expect(isConflictError('string')).toBe(false);
  });

  it('ResilientAdapter re-throws conflict errors without falling back to local', async () => {
    const document = buildDocument();
    const conflictError = new PageArtifactConflictError(document.artifactId, 'v-remote');

    const primary = {
      save: vi.fn().mockRejectedValue(conflictError),
      load: vi.fn(),
    };
    const fallback = {
      save: vi.fn().mockResolvedValue(undefined),
      load: vi.fn(),
    };

    const adapter = new ResilientPageArtifactPersistenceAdapter(primary, fallback);

    await expect(adapter.save(document)).rejects.toThrow(PageArtifactConflictError);
    // Fallback must NOT be called when a conflict is detected
    expect(fallback.save).not.toHaveBeenCalled();
  });

  it('ResilientAdapter still uses fallback for non-conflict errors', async () => {
    const document = buildDocument();
    const primary = {
      save: vi.fn().mockRejectedValue(new Error('network timeout')),
      load: vi.fn(),
    };
    const fallback = {
      save: vi.fn().mockResolvedValue(undefined),
      load: vi.fn(),
    };

    const adapter = new ResilientPageArtifactPersistenceAdapter(primary, fallback);
    await adapter.save(document);

    expect(fallback.save).toHaveBeenCalledWith(document);
  });

  it('ResilientAdapter does not write local fallback for authorization or validation failures', async () => {
    const document = buildDocument();
    const primary = {
      save: vi.fn().mockRejectedValue(
        new PageArtifactPersistenceError('forbidden', 'Not allowed', 403),
      ),
      load: vi.fn(),
    };
    const fallback = {
      save: vi.fn().mockResolvedValue(undefined),
      load: vi.fn(),
    };

    const adapter = new ResilientPageArtifactPersistenceAdapter(primary, fallback);

    await expect(adapter.save(document)).rejects.toMatchObject({ kind: 'forbidden' });
    expect(fallback.save).not.toHaveBeenCalled();
  });
});
