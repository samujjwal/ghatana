/**
 * Runtime Truth Evidence — API Client (DC-P1-438)
 *
 * "Add runtime truth evidence to every critical action response or diagnosable
 * path where applicable."
 *
 * Operators MUST be able to correlate a browser-visible failure back to backend
 * traces. This requires:
 *
 * 1. Every outbound request carries a unique X-Correlation-ID request header.
 * 2. Every error response captures the server-echoed X-Correlation-ID from the
 *    response headers.
 * 3. The captured correlationId is exposed on the ApiError for operator access.
 */

import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';

// ─── Minimal fetch-mock helpers ───────────────────────────────────────────────

function makeMockResponse(opts: {
  status: number;
  body: unknown;
  correlationId?: string;
  contentType?: string;
}): Response {
  const headers = new Headers({
    'content-type': opts.contentType ?? 'application/json',
    ...(opts.correlationId ? { 'X-Correlation-ID': opts.correlationId } : {}),
  });
  return {
    ok: opts.status >= 200 && opts.status < 300,
    status: opts.status,
    statusText: opts.status === 401 ? 'Unauthorized' : opts.status === 403 ? 'Forbidden' : 'Error',
    headers,
    json: async () => opts.body,
    text: async () => String(opts.body),
    blob: async () => new Blob(),
  } as unknown as Response;
}

// ─── ApiError type used for assertion ────────────────────────────────────────

interface ApiError {
  code: string;
  message: string;
  status?: number;
  correlationId?: string;
  details?: Record<string, unknown>;
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('ApiClient runtime truth evidence (DC-P1-438)', () => {
  let ApiClient: typeof import('../../lib/api/client').ApiClient;
  let createApiClient: () => InstanceType<typeof ApiClient>;

  beforeEach(async () => {
    vi.stubGlobal('window', {
      setTimeout: globalThis.setTimeout,
      clearTimeout: globalThis.clearTimeout,
      setInterval: globalThis.setInterval,
      clearInterval: globalThis.clearInterval,
      location: { origin: 'http://localhost:3000' },
    });
    vi.stubGlobal('crypto', {
      randomUUID: () => 'test-uuid-1234-5678-9012-abcdef012345',
    });
    const module = await import('../../lib/api/client');
    ApiClient = module.ApiClient;
    createApiClient = () =>
      new ApiClient({ baseUrl: '/api', timeout: 5000 });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  describe('outbound correlation ID', () => {
    it('sends X-Correlation-ID header on every request', async () => {
      const capturedHeaders: Record<string, string> = {};
      vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
        for (const [k, v] of (init?.headers as Headers).entries()) {
          capturedHeaders[k] = v;
        }
        return makeMockResponse({ status: 200, body: {} });
      });

      const client = createApiClient();
      await client.get('/test');

      expect(capturedHeaders['x-correlation-id']).toBeDefined();
      expect(capturedHeaders['x-correlation-id'].length).toBeGreaterThan(0);
    });

    it('generates a unique correlation ID per request', async () => {
      const ids: string[] = [];
      let callCount = 0;
      vi.stubGlobal('crypto', {
        randomUUID: () => `uuid-${++callCount}`,
      });
      vi.stubGlobal('fetch', async (_url: string, init?: RequestInit) => {
        const correlationId = (init?.headers as Headers).get('X-Correlation-ID');
        if (correlationId) ids.push(correlationId);
        return makeMockResponse({ status: 200, body: {} });
      });

      const client = createApiClient();
      await client.get('/a');
      await client.post('/b', {});

      expect(ids.length).toBe(2);
      expect(ids[0]).not.toBe(ids[1]);
    });
  });

  describe('correlationId captured from error responses', () => {
    it('includes correlationId on 401 error when server echoes the header', async () => {
      vi.stubGlobal('fetch', async () =>
        makeMockResponse({
          status: 401,
          body: { code: 'AUTH_REQUIRED', message: 'Unauthorized' },
          correlationId: 'server-echo-abc-123',
        })
      );

      const client = createApiClient();
      let caught: ApiError | null = null;
      try {
        await client.get('/protected');
      } catch (e) {
        caught = e as ApiError;
      }

      expect(caught).not.toBeNull();
      expect(caught?.code).toBe('AUTH_REQUIRED');
      expect(caught?.correlationId).toBe('server-echo-abc-123');
    });

    it('includes correlationId on 403 error', async () => {
      vi.stubGlobal('fetch', async () =>
        makeMockResponse({
          status: 403,
          body: { code: 'ACCESS_DENIED', message: 'Forbidden' },
          correlationId: 'server-403-corr-xyz',
        })
      );

      const client = createApiClient();
      let caught: ApiError | null = null;
      try {
        await client.post('/admin/action', {});
      } catch (e) {
        caught = e as ApiError;
      }

      expect(caught?.code).toBe('ACCESS_DENIED');
      expect(caught?.correlationId).toBe('server-403-corr-xyz');
    });

    it('includes correlationId on 500 error', async () => {
      vi.stubGlobal('fetch', async () =>
        makeMockResponse({
          status: 500,
          body: { message: 'Internal Server Error' },
          correlationId: 'server-500-corr-999',
        })
      );

      const client = createApiClient();
      let caught: ApiError | null = null;
      try {
        await client.post('/data/operation', {});
      } catch (e) {
        caught = e as ApiError;
      }

      expect(caught?.status).toBe(500);
      expect(caught?.correlationId).toBe('server-500-corr-999');
    });

    it('correlationId is undefined when server does not echo the header', async () => {
      vi.stubGlobal('fetch', async () =>
        makeMockResponse({
          status: 404,
          body: { code: 'NOT_FOUND', message: 'Not found' },
          // No correlationId in response
        })
      );

      const client = createApiClient();
      let caught: ApiError | null = null;
      try {
        await client.get('/missing');
      } catch (e) {
        caught = e as ApiError;
      }

      expect(caught?.status).toBe(404);
      expect(caught?.correlationId).toBeUndefined();
    });
  });

  describe('ApiError interface has required runtime truth fields', () => {
    it('ApiError type includes correlationId field for operator diagnosis', async () => {
      // This test is a compile-time / interface guard.
      // It verifies that ApiError has a correlationId field that can be accessed
      // without TypeScript errors — confirming runtime truth evidence is part of
      // the error contract and not just a hidden implementation detail.
      vi.stubGlobal('fetch', async () =>
        makeMockResponse({
          status: 500,
          body: {},
          correlationId: 'operator-trace-id-001',
        })
      );

      const client = createApiClient();
      let caught: ApiError | null = null;
      try {
        await client.get('/fail');
      } catch (e) {
        caught = e as ApiError;
      }

      // operator-visible fields:
      const operatorInfo = {
        id: caught?.correlationId,  // for backend trace lookup
        status: caught?.status,
        code: caught?.code,
      };

      expect(operatorInfo.id).toBe('operator-trace-id-001');
      expect(typeof operatorInfo.id).toBe('string');
    });
  });
});
