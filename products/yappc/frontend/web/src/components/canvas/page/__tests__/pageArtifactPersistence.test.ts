import { describe, expect, it, vi } from 'vitest';

import {
  HttpPageArtifactPersistenceAdapter,
  LocalStoragePageArtifactPersistenceAdapter,
  ResilientPageArtifactPersistenceAdapter,
  PageArtifactConflictError,
  isConflictError,
} from '../pageArtifactPersistence';
import { createPageArtifactDocument } from '../pageArtifactDocument';

const buildDocument = () =>
  createPageArtifactDocument({
    artifactId: 'artifact-1',
    name: 'Landing',
    createdBy: 'tester',
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
    });

    await adapter.save(document);

    expect(fetchImpl).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({
          'If-Match': document.documentId,
        }),
      }),
    );
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
});
