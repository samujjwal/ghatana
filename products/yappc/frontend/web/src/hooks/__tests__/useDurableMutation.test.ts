/**
 * useDurableMutation Hook Tests.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useDurableMutation } from '../useDurableMutation';

// ─── Wrapper ─────────────────────────────────────────────────────────────────

function makeWrapper(): React.FC<{ children: React.ReactNode }> {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return function Wrapper({ children }) {
    return React.createElement(QueryClientProvider, { client: qc }, children);
  };
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('useDurableMutation', () => {
  let onToast: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    onToast = vi.fn();
  });

  it('calls the mutation function with an injected idempotency key', async () => {
    const mutationFn = vi.fn().mockResolvedValue({ id: '1' });
    const { result } = renderHook(
      () =>
        useDurableMutation({
          mutationFn,
          actionLabel: 'Create item',
          onToast,
        }),
      { wrapper: makeWrapper() },
    );

    await act(async () => {
      result.current.mutate({ name: 'foo' } as Record<string, unknown>);
      await vi.waitFor(() => expect(result.current.isSuccess).toBe(true));
    });

    expect(mutationFn).toHaveBeenCalledOnce();
    const args = mutationFn.mock.calls[0][0] as Record<string, unknown>;
    expect(typeof args['idempotencyKey']).toBe('string');
    expect(args['idempotencyKey']).toBeTruthy();
    expect(args['name']).toBe('foo');
  });

  it('shows a success toast on mutation success', async () => {
    const mutationFn = vi.fn().mockResolvedValue({});
    const { result } = renderHook(
      () =>
        useDurableMutation({
          mutationFn,
          actionLabel: 'Save document',
          onToast,
        }),
      { wrapper: makeWrapper() },
    );

    await act(async () => {
      result.current.mutate({});
      await vi.waitFor(() => expect(result.current.isSuccess).toBe(true));
    });

    expect(onToast).toHaveBeenCalledWith({ type: 'success', message: 'Save document succeeded' });
  });

  it('uses custom successMessage when provided', async () => {
    const mutationFn = vi.fn().mockResolvedValue({});
    const { result } = renderHook(
      () =>
        useDurableMutation({
          mutationFn,
          actionLabel: 'Save',
          successMessage: 'Document saved!',
          onToast,
        }),
      { wrapper: makeWrapper() },
    );

    await act(async () => {
      result.current.mutate({});
      await vi.waitFor(() => expect(result.current.isSuccess).toBe(true));
    });

    expect(onToast).toHaveBeenCalledWith({ type: 'success', message: 'Document saved!' });
  });

  it('shows an error toast on mutation failure', async () => {
    const mutationFn = vi.fn().mockRejectedValue(new Error('Server error'));
    const { result } = renderHook(
      () =>
        useDurableMutation({
          mutationFn,
          actionLabel: 'Delete item',
          onToast,
        }),
      { wrapper: makeWrapper() },
    );

    await act(async () => {
      result.current.mutate({});
      await vi.waitFor(() => expect(result.current.isError).toBe(true));
    });

    expect(onToast).toHaveBeenCalledWith({
      type: 'error',
      message: 'Delete item failed: Server error',
    });
  });

  it('generates a new idempotency key after each successful mutation', async () => {
    const mutationFn = vi.fn().mockResolvedValue({});
    const { result } = renderHook(
      () =>
        useDurableMutation({
          mutationFn,
          actionLabel: 'Submit',
          onToast,
        }),
      { wrapper: makeWrapper() },
    );

    await act(async () => {
      result.current.mutate({});
      await vi.waitFor(() => expect(result.current.isSuccess).toBe(true));
    });

    const key1 = (mutationFn.mock.calls[0][0] as Record<string, unknown>)['idempotencyKey'];

    await act(async () => {
      result.current.mutate({});
      await vi.waitFor(() => expect(mutationFn).toHaveBeenCalledTimes(2));
    });

    const key2 = (mutationFn.mock.calls[1][0] as Record<string, unknown>)['idempotencyKey'];

    expect(key1).not.toBe(key2);
  });
});
