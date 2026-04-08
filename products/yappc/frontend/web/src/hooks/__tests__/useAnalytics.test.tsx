/**
 * useAnalytics Hook Tests
 */

import React from 'react';
import { describe, it, expect } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAnalytics } from '../useAnalytics';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('useAnalytics', () => {
  it('should return null report while loading', () => {
    const { result } = renderHook(
      () => useAnalytics({ startDate: '2026-01-01', endDate: '2026-01-31' }),
      { wrapper: createWrapper() },
    );
    expect(result.current.report).toBeNull();
    expect(result.current.isLoading).toBe(true);
  });

  it('should fetch report for given period', async () => {
    const { result } = renderHook(
      () => useAnalytics({ startDate: '2026-01-01', endDate: '2026-01-31' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.report).not.toBeNull();
    expect(result.current.report?.period.start).toBe('2026-01-01');
    expect(result.current.report?.period.end).toBe('2026-01-31');
  });

  it('should compute bottleneck from lifecycle metrics', async () => {
    const { result } = renderHook(
      () => useAnalytics({ startDate: '2026-01-01', endDate: '2026-01-31' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Default empty report → no bottleneck
    expect(result.current.bottleneck).toBeNull();
  });

  it('should compute avgUtilisation', async () => {
    const { result } = renderHook(
      () => useAnalytics({ startDate: '2026-01-01', endDate: '2026-01-31' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.avgUtilisation).toBe(0);
  });

  it('should not fetch when disabled', () => {
    const { result } = renderHook(
      () => useAnalytics({ startDate: '2026-01-01', endDate: '2026-01-31', enabled: false }),
      { wrapper: createWrapper() },
    );
    expect(result.current.isLoading).toBe(false);
    expect(result.current.report).toBeNull();
  });
});
