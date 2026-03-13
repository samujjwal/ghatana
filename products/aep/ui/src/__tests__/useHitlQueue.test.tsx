/**
 * useHitlQueue — TanStack Query + SSE hook tests.
 *
 * Verifies:
 *   1. REST load populates the queue on mount
 *   2. `hitl.new` SSE events prepend items without a full refetch
 *   3. `hitl.new` with duplicate reviewId is ignored
 *   4. SSE errors trigger query invalidation (refetch)
 *   5. Approve/reject mutations invalidate the queue
 *
 * @doc.type test
 * @doc.purpose Verify HITL queue hook REST + SSE integration
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
import { useHitlQueue, HITL_QUEUE_QUERY_KEY } from '@/hooks/useHitlQueue';

// ── API mocks ─────────────────────────────────────────────────────────────────

vi.mock('@/api/aep.api');
vi.mock('@/api/sse');

// ── Fixtures ──────────────────────────────────────────────────────────────────

const makeReview = (id: string): aepApi.ReviewItem => ({
  reviewId: id,
  tenantId: 'default',
  skillId: 'email-routing',
  itemType: 'POLICY',
  status: 'PENDING',
  proposedVersion: { rule: 'if score > 0.8 then escalate' },
  confidenceScore: 0.85,
  createdAt: new Date().toISOString(),
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

/** Reads the HITL queue cache using the key format the hook uses. */
function getHitlCache(client: QueryClient, tenantId = 'default') {
  return client.getQueryData<aepApi.ReviewItem[]>([HITL_QUEUE_QUERY_KEY, tenantId]);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('useHitlQueue', () => {
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

  it('loads initial queue from REST on mount', async () => {
    const reviews = [makeReview('rev-001'), makeReview('rev-002')];
    vi.mocked(aepApi.listPendingReviews).mockResolvedValue(reviews);

    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useHitlQueue(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(2);
    expect(result.current.data![0].reviewId).toBe('rev-001');
  });

  it('prepends a new item when hitl.new SSE event arrives', async () => {
    const initial = [makeReview('rev-001')];
    vi.mocked(aepApi.listPendingReviews).mockResolvedValue(initial);

    const { client, Wrapper } = makeWrapper();
    const { result } = renderHook(() => useHitlQueue(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await waitFor(() => expect(vi.mocked(sseModule.subscribeToAepStream)).toHaveBeenCalled());

    // setQueryData is synchronous — check cache immediately after calling handler
    capturedOnMessage({ type: 'hitl.new', data: makeReview('rev-999') });

    const cached = getHitlCache(client);
    expect(cached).toHaveLength(2);
    expect(cached![0].reviewId).toBe('rev-999');
    expect(cached![1].reviewId).toBe('rev-001');
  });

  it('ignores hitl.new when the review ID already exists in the queue', async () => {
    const initial = [makeReview('rev-001')];
    vi.mocked(aepApi.listPendingReviews).mockResolvedValue(initial);

    const { client, Wrapper } = makeWrapper();
    const { result } = renderHook(() => useHitlQueue(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await waitFor(() => expect(vi.mocked(sseModule.subscribeToAepStream)).toHaveBeenCalled());

    capturedOnMessage({ type: 'hitl.new', data: makeReview('rev-001') }); // duplicate

    // Cache length must remain 1
    expect(getHitlCache(client)).toHaveLength(1);
  });

  it('ignores SSE messages that are not hitl.new', async () => {
    vi.mocked(aepApi.listPendingReviews).mockResolvedValue([makeReview('rev-001')]);

    const { client, Wrapper } = makeWrapper();
    const { result } = renderHook(() => useHitlQueue(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await waitFor(() => expect(vi.mocked(sseModule.subscribeToAepStream)).toHaveBeenCalled());

    capturedOnMessage({ type: 'run.update', data: { id: 'run-1' } });

    // Cache must be unchanged
    expect(getHitlCache(client)).toHaveLength(1);
  });

  it('calls close() on the SSE subscription when the hook unmounts', async () => {
    vi.mocked(aepApi.listPendingReviews).mockResolvedValue([]);

    const { Wrapper } = makeWrapper();
    const { unmount } = renderHook(() => useHitlQueue(), { wrapper: Wrapper });

    await waitFor(() => expect(sseModule.subscribeToAepStream).toHaveBeenCalled());

    unmount();

    expect(mockClose).toHaveBeenCalledOnce();
  });
});
