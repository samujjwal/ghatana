/**
 * useProjectData Hook Tests
 */

import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useProjectPhases, useProjectProgress } from '../useProjectData';

function createWrapper(): React.ComponentType<{ children: React.ReactNode }> {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  };
}

describe('useProjectPhases', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns loading state initially', () => {
    const { result } = renderHook(() => useProjectPhases('proj-1'), {
      wrapper: createWrapper(),
    });

    expect(result.current.isLoading).toBe(true);
    expect(result.current.data).toBeUndefined();
  });

  it('does not fetch when projectId is empty', () => {
    const { result } = renderHook(() => useProjectPhases(''), {
      wrapper: createWrapper(),
    });

    // enabled: false means no loading
    expect(result.current.isLoading).toBe(false);
    expect(result.current.isFetching).toBe(false);
  });

  it('resolves to phase array after fetch', async () => {
    const { result } = renderHook(() => useProjectPhases('proj-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(
      () => expect(result.current.isSuccess).toBe(true),
      { timeout: 3000 }
    );

    expect(Array.isArray(result.current.data)).toBe(true);
    expect((result.current.data?.length ?? 0)).toBeGreaterThan(0);
  });

  it('each phase has id, name, status, and tasks', async () => {
    const { result } = renderHook(() => useProjectPhases('proj-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true), {
      timeout: 3000,
    });

    const phase = result.current.data?.[0];
    expect(phase).toHaveProperty('id');
    expect(phase).toHaveProperty('name');
    expect(phase).toHaveProperty('status');
    expect(phase).toHaveProperty('tasks');
  });
});

describe('useProjectProgress', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns loading state initially', () => {
    const { result } = renderHook(() => useProjectProgress('proj-1'), {
      wrapper: createWrapper(),
    });

    expect(result.current.isLoading).toBe(true);
  });

  it('does not fetch when projectId is empty', () => {
    const { result } = renderHook(() => useProjectProgress(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.isLoading).toBe(false);
    expect(result.current.isFetching).toBe(false);
  });

  it('resolves to progress object with overallProgress and phaseProgress', async () => {
    const { result } = renderHook(() => useProjectProgress('proj-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true), {
      timeout: 3000,
    });

    const data = result.current.data;
    expect(data).toHaveProperty('overallProgress');
    expect(data).toHaveProperty('phaseProgress');
    expect(typeof data?.overallProgress).toBe('number');
  });

  it('overallProgress is between 0 and 100', async () => {
    const { result } = renderHook(() => useProjectProgress('proj-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true), {
      timeout: 3000,
    });

    const progress = result.current.data?.overallProgress ?? -1;
    expect(progress).toBeGreaterThanOrEqual(0);
    expect(progress).toBeLessThanOrEqual(100);
  });
});
