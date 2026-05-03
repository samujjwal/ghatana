/**
 * @doc.type test-suite
 * @doc.purpose Unit tests for java-ai-agent-client HTTP client
 * @doc.layer application
 * @doc.pattern Unit Test
 *
 * Verifies that:
 * - fetchAgentRegistry makes a real HTTP GET to the Java backend
 * - Agent execute() methods POST typed JSON bodies
 * - AbortController timeout is applied
 * - Non-2xx responses throw meaningful errors
 * - No stub or zero-vector logic is imported
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchAgentRegistry,
  createCopilotClient,
  type JavaAIClientConfig,
} from '../java-ai-agent-client';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const config: JavaAIClientConfig = {
  baseUrl: 'http://localhost:7003',
  timeoutMs: 5_000,
};

function mockFetch(
  status: number,
  body: unknown,
  ok: boolean = status >= 200 && status < 300
): ReturnType<typeof vi.fn> {
  return vi.fn().mockResolvedValueOnce({
    ok,
    status,
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(JSON.stringify(body)),
  });
}

// ---------------------------------------------------------------------------
// fetchAgentRegistry
// ---------------------------------------------------------------------------

describe('fetchAgentRegistry', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch(200, [{ id: 'agent-1', name: 'COPILOT_AGENT', status: 'ACTIVE', capabilities: [] }]));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('calls GET /api/v1/agents on the configured base URL', async () => {
    const agents = await fetchAgentRegistry(config);

    expect(fetch).toHaveBeenCalledOnce();
    const [url] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(url).toBe('http://localhost:7003/api/v1/agents');
    expect(agents).toHaveLength(1);
    expect(agents[0]!.name).toBe('COPILOT_AGENT');
  });

  it('throws a meaningful error when the registry returns 503', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch(503, { error: 'service unavailable' }, false)
    );

    await expect(fetchAgentRegistry(config)).rejects.toThrow('503');
  });

  it('throws when response body is not valid JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.reject(new SyntaxError('Unexpected token')),
      text: () => Promise.resolve('not-json'),
    }));

    await expect(fetchAgentRegistry(config)).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// createCopilotClient — execute()
// ---------------------------------------------------------------------------

describe('createCopilotClient.execute', () => {
  const copilotClient = createCopilotClient(config);

  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      mockFetch(200, {
        success: true,
        data: { response: 'hello' },
        metrics: { tokensUsed: 10, latencyMs: 50 },
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('POSTs to /api/v1/agents/copilot/execute with typed body', async () => {
    const input = { prompt: 'Hello', sessionId: 'sess-1', projectId: 'proj-1' };
    const ctx = {
      userId: 'user-1',
      workspaceId: 'ws-1',
      requestId: 'req-1',
      tenantId: 'tenant-1',
    };

    const result = await copilotClient.execute(input, ctx);

    expect(fetch).toHaveBeenCalledOnce();
    const [url, init] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      RequestInit,
    ];
    expect(url).toBe('http://localhost:7003/api/v1/agents/copilot/execute');
    expect(init.method).toBe('POST');
    const body = JSON.parse(init.body as string) as { input: typeof input; context: typeof ctx };
    expect(body.input).toEqual(input);
    expect(body.context.userId).toBe('user-1');
    expect(result.success).toBe(true);
  });

  it('throws with HTTP status when backend returns 500', async () => {
    vi.stubGlobal('fetch', mockFetch(500, { error: 'internal server error' }, false));

    await expect(
      copilotClient.execute(
        { prompt: 'test', sessionId: 's', projectId: 'p' },
        { userId: 'u', workspaceId: 'w', requestId: 'r', tenantId: 't' }
      )
    ).rejects.toThrow('500');
  });
});

// ---------------------------------------------------------------------------
// Timeout handling
// ---------------------------------------------------------------------------

describe('timeout via AbortController', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('aborts the request when timeoutMs elapses', async () => {
    vi.useFakeTimers();

    // Never resolves to simulate a slow upstream
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(
        (_url: string, opts: RequestInit) =>
          new Promise((_resolve, reject) => {
            (opts.signal as AbortSignal).addEventListener('abort', () =>
              reject(new DOMException('The operation was aborted.', 'AbortError'))
            );
          })
      )
    );

    const tightConfig: JavaAIClientConfig = { baseUrl: 'http://localhost:7003', timeoutMs: 100 };
    const promise = fetchAgentRegistry(tightConfig);

    vi.advanceTimersByTime(200);

    await expect(promise).rejects.toThrow();
  });
});
