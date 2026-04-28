/**
 * useGeneratedCodeQualityGate hook tests (F-Y016 / AI-Y5)
 */

import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

import { useGeneratedCodeQualityGate } from '../useGeneratedCodeQualityGate';

// ── Mocks ──────────────────────────────────────────────────────────────────────

vi.mock('@/lib/http', () => ({ parseJsonResponse: vi.fn((r) => Promise.resolve(r)) }));

const mockFetch = vi.hoisted(() => vi.fn());
vi.stubGlobal('fetch', mockFetch);

// ── Helpers ────────────────────────────────────────────────────────────────────

function makeWrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('useGeneratedCodeQualityGate', () => {
  beforeEach(() => vi.clearAllMocks());

  it('returns canAccept=false while loading', () => {
    mockFetch.mockReturnValue(new Promise(() => undefined));
    const { result } = renderHook(
      () => useGeneratedCodeQualityGate({ artifactId: 'art-1' }),
      { wrapper: makeWrapper() }
    );
    expect(result.current.canAccept).toBe(false);
    expect(result.current.isLoading).toBe(true);
  });

  it('returns canAccept=true when all checks pass', async () => {
    mockFetch.mockResolvedValue({
      artifactId: 'art-1',
      compile: { status: 'PASSED' },
      lint: { status: 'PASSED' },
      test: { status: 'PASSED' },
      allPassed: true,
    });
    const { result } = renderHook(
      () => useGeneratedCodeQualityGate({ artifactId: 'art-1' }),
      { wrapper: makeWrapper() }
    );
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.canAccept).toBe(true);
  });

  it('returns canAccept=false when any check fails', async () => {
    mockFetch.mockResolvedValue({
      artifactId: 'art-1',
      compile: { status: 'PASSED' },
      lint: { status: 'FAILED', detail: 'error' },
      test: { status: 'PASSED' },
      allPassed: false,
    });
    const { result } = renderHook(
      () => useGeneratedCodeQualityGate({ artifactId: 'art-1' }),
      { wrapper: makeWrapper() }
    );
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.canAccept).toBe(false);
  });

  it('does not fetch when enabled=false', () => {
    const { result } = renderHook(
      () => useGeneratedCodeQualityGate({ artifactId: 'art-1', enabled: false }),
      { wrapper: makeWrapper() }
    );
    expect(mockFetch).not.toHaveBeenCalled();
    expect(result.current.canAccept).toBe(false);
  });
});
