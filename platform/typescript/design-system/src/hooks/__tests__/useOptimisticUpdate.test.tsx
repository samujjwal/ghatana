import { renderHook, act, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useOptimisticList, useOptimisticUpdate } from '../useOptimisticUpdate';
import type { DesignSystemDiagnosticEvent } from '../../diagnostics';

describe('useOptimisticUpdate', () => {
  const diagnostics: DesignSystemDiagnosticEvent[] = [];
  const diagnosticListener = (event: Event) => {
    diagnostics.push((event as CustomEvent<DesignSystemDiagnosticEvent>).detail);
  };

  beforeEach(() => {
    diagnostics.length = 0;
    globalThis.addEventListener('design-system-diagnostic', diagnosticListener);
  });

  afterEach(() => {
    globalThis.removeEventListener('design-system-diagnostic', diagnosticListener);
    vi.clearAllMocks();
  });

  it('applies optimistic update and commits server response on success', async () => {
    const onSuccess = vi.fn();
    const { result } = renderHook(() =>
      useOptimisticUpdate({
        data: { id: '1', title: 'old' },
        updateFn: async (data) => ({ ...data, title: `${data.title}-saved` }),
        onSuccess,
      })
    );

    await act(async () => {
      await result.current.update({ id: '1', title: 'new' });
    });

    expect(result.current.data).toEqual({ id: '1', title: 'new-saved' });
    expect(result.current.error).toBeNull();
    expect(result.current.isLoading).toBe(false);
    expect(onSuccess).toHaveBeenCalledWith({ id: '1', title: 'new-saved' });
    expect(diagnostics).toContainEqual(
      expect.objectContaining({
        source: 'useOptimisticUpdate',
        level: 'info',
        message: 'Toast success',
      })
    );
  });

  it('rolls back optimistic update and surfaces error on failure', async () => {
    const failure = new Error('save failed');
    const onError = vi.fn();
    const { result } = renderHook(() =>
      useOptimisticUpdate({
        data: { id: '1', title: 'old' },
        updateFn: async () => {
          throw failure;
        },
        onError,
      })
    );

    await act(async () => {
      await result.current.update({ id: '1', title: 'new' });
    });

    expect(result.current.data).toEqual({ id: '1', title: 'old' });
    expect(result.current.error).toBe(failure);
    expect(result.current.isLoading).toBe(false);
    expect(onError).toHaveBeenCalledWith(failure);
    expect(diagnostics).toContainEqual(
      expect.objectContaining({
        source: 'useOptimisticUpdate',
        level: 'warn',
        message: 'Toast error',
      })
    );
  });

  it('resets to the latest initial data', async () => {
    const { result, rerender } = renderHook(
      ({ title }) =>
        useOptimisticUpdate({
          data: { id: '1', title },
          updateFn: async (data) => data,
          showToast: false,
        }),
      { initialProps: { title: 'old' } }
    );

    await act(async () => {
      await result.current.update({ id: '1', title: 'edited' });
    });

    rerender({ title: 'fresh' });
    act(() => {
      result.current.reset();
    });

    expect(result.current.data).toEqual({ id: '1', title: 'fresh' });
    expect(result.current.error).toBeNull();
  });
});

describe('useOptimisticList', () => {
  it('adds items optimistically and commits the server version', async () => {
    const { result } = renderHook(() =>
      useOptimisticList({
        items: [{ id: '1', title: 'first' }],
        addFn: async (item: { id: string; title: string }) => ({ ...item, title: `${item.title}-saved` }),
        getItemId: (item: { id: string; title: string }) => item.id,
        showToast: false,
      })
    );

    await act(async () => {
      await result.current.add({ id: '2', title: 'second' });
    });

    expect(result.current.items).toEqual([
      { id: '1', title: 'first' },
      { id: '2', title: 'second-saved' },
    ]);
  });

  it('updates items optimistically and rolls back on failure', async () => {
    const { result } = renderHook(() =>
      useOptimisticList({
        items: [{ id: '1', title: 'first' }],
        updateFn: async () => {
          throw new Error('update failed');
        },
        getItemId: (item: { id: string; title: string }) => item.id,
        showToast: false,
      })
    );

    await act(async () => {
      await result.current.update({ id: '1', title: 'changed' });
    });

    expect(result.current.items).toEqual([{ id: '1', title: 'first' }]);
    expect(result.current.isLoading).toBe(false);
  });

  it('removes items optimistically and keeps removal on success', async () => {
    const { result } = renderHook(() =>
      useOptimisticList({
        items: [
          { id: '1', title: 'first' },
          { id: '2', title: 'second' },
        ],
        removeFn: async () => undefined,
        getItemId: (item: { id: string; title: string }) => item.id,
        showToast: false,
      })
    );

    await act(async () => {
      await result.current.remove('1');
    });

    await waitFor(() => {
      expect(result.current.items).toEqual([{ id: '2', title: 'second' }]);
    });
  });
});
