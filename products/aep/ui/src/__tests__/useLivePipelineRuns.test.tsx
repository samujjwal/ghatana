/**
 * useLivePipelineRuns — TanStack Query + SSE hook tests.
 *
 * Verifies:
 *   1. REST load populates runs on mount
 *   2. `run_started` SSE event prepends a new run and trims to limit
 *   3. `run_completed` SSE event merges into an existing run in-place
 *   4. Unknown SSE event types are ignored
 *   5. Subscription is closed on unmount
 *
 * Note: SSE-update assertions check the TanStack Query cache directly via
 * `client.getQueryData()` after calling the captured onMessage handler.
 * `setQueryData` is synchronous so cache reads are reliable without
 * waiting for a React re-render.
 *
 * @doc.type test
 * @doc.purpose Verify live pipeline runs hook REST + SSE integration
 * @doc.layer frontend
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';
import * as aepApi from '@/api/aep.api';
import * as sseModule from '@/api/sse';
import type { SseHandler, SseErrorHandler } from '@/api/sse';
import { useLivePipelineRuns, PIPELINE_RUNS_QUERY_KEY } from '@/hooks/usePipelineRuns';

// ── API mocks ─────────────────────────────────────────────────────────────────

vi.mock('@/api/aep.api');
vi.mock('@/api/sse');

// ── Fixtures ──────────────────────────────────────────────────────────────────

const makeRun = (id: string, status: aepApi.PipelineRun['status'] = 'RUNNING'): aepApi.PipelineRun => ({
  id,
  pipelineId: 'pipe-001',
  pipelineName: 'My Pipeline',
  status,
  startedAt: new Date().toISOString(),
  eventsProcessed: 0,
  errorsCount: 0,
});

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeWrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Wrapper({ children }: React.PropsWithChildren) {
    return (
      <JotaiProvider>
        {/* @ts-expect-error: @types/react version mismatch in pnpm workspace */}
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      </JotaiProvider>
    );
  }
  return { client, Wrapper };
}

/** Reads the pipeline-runs cache using the key format the hook uses. */
function getCacheRuns(client: QueryClient, tenantId = 'default', limit = 20) {
  return client.getQueryData<aepApi.PipelineRun[]>([PIPELINE_RUNS_QUERY_KEY, tenantId, limit]);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('useLivePipelineRuns', () => {
  let capturedOnMessage: SseHandler;
  let capturedOnError: SseErrorHandler | undefined;
  let mockClose: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockClose = vi.fn();
    vi.mocked(sseModule.subscribeToAepStream).mockImplementation(
      (_tenantId, onMessage, onError) => {
        capturedOnMessage = onMessage;
        capturedOnError = onError;
        return { close: mockClose, get connected() { return true; } };
      },
    );
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('loads initial runs from REST on mount', async () => {
    const runs = [makeRun('run-001'), makeRun('run-002', 'SUCCEEDED')];
    vi.mocked(aepApi.listPipelineRuns).mockResolvedValue(runs);

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useLivePipelineRuns(20), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(2);
  });

  it('prepends a new run when run_started SSE event arrives', async () => {
    const initialRun = makeRun('run-001');
    vi.mocked(aepApi.listPipelineRuns).mockResolvedValue([initialRun]);

    const { client, Wrapper } = makeWrapper();
    const { result } = renderHook(() => useLivePipelineRuns(20), { wrapper: Wrapper });

    // Wait for initial data and SSE subscription to be ready
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await waitFor(() => expect(vi.mocked(sseModule.subscribeToAepStream)).toHaveBeenCalled());

    // Trigger SSE event — setQueryData is synchronous so cache updates immediately
    capturedOnMessage({ type: 'run_started', data: makeRun('run-new') });

    const cached = getCacheRuns(client);
    expect(cached).toHaveLength(2);
    expect(cached![0].id).toBe('run-new');
    expect(cached![1].id).toBe('run-001');
  });

  it('merges run_completed event into existing run without duplicating it', async () => {
    const initial = [makeRun('run-001', 'RUNNING')];
    vi.mocked(aepApi.listPipelineRuns).mockResolvedValue(initial);

    const { client, Wrapper } = makeWrapper();
    const { result } = renderHook(() => useLivePipelineRuns(20), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await waitFor(() => expect(vi.mocked(sseModule.subscribeToAepStream)).toHaveBeenCalled());

    capturedOnMessage({
      type: 'run_completed',
      data: { ...makeRun('run-001', 'SUCCEEDED'), eventsProcessed: 500 },
    });

    const cached = getCacheRuns(client);
    expect(cached).toHaveLength(1);
    expect(cached![0].status).toBe('SUCCEEDED');
    expect(cached![0].eventsProcessed).toBe(500);
  });

  it('ignores SSE event types that are not run updates', async () => {
    vi.mocked(aepApi.listPipelineRuns).mockResolvedValue([makeRun('run-001')]);

    const { client, Wrapper } = makeWrapper();
    const { result } = renderHook(() => useLivePipelineRuns(20), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await waitFor(() => expect(vi.mocked(sseModule.subscribeToAepStream)).toHaveBeenCalled());

    capturedOnMessage({ type: 'hitl.new', data: { reviewId: 'rev-999' } });

    // Cache should remain unchanged
    const cached = getCacheRuns(client);
    expect(cached).toHaveLength(1);
    expect(cached![0].id).toBe('run-001');
  });

  it('trims the run list to the configured limit when a new run is prepended', async () => {
    const initial = Array.from({ length: 3 }, (_, i) => makeRun(`run-0${i}`));
    vi.mocked(aepApi.listPipelineRuns).mockResolvedValue(initial);

    const limit = 3;
    const { client, Wrapper } = makeWrapper();
    const { result } = renderHook(() => useLivePipelineRuns(limit), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await waitFor(() => expect(vi.mocked(sseModule.subscribeToAepStream)).toHaveBeenCalled());

    capturedOnMessage({ type: 'run_started', data: makeRun('run-new') });

    const cached = client.getQueryData<aepApi.PipelineRun[]>([PIPELINE_RUNS_QUERY_KEY, 'default', limit]);
    expect(cached![0].id).toBe('run-new');
    expect(cached).toHaveLength(limit); // Must not exceed limit
  });

  it('calls close() on the SSE subscription when the hook unmounts', async () => {
    vi.mocked(aepApi.listPipelineRuns).mockResolvedValue([]);

    const { Wrapper } = makeWrapper();
    const { unmount } = renderHook(() => useLivePipelineRuns(), { wrapper: Wrapper });

    await waitFor(() => expect(sseModule.subscribeToAepStream).toHaveBeenCalled());

    unmount();

    expect(mockClose).toHaveBeenCalledOnce();
  });
});
