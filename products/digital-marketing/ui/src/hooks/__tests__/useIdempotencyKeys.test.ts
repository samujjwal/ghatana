/**
 * Idempotency key hook tests.
 *
 * @doc.type test
 * @doc.purpose Prove write retries reuse the same key for the same semantic user intent
 * @doc.layer frontend
 */

import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useIdempotencyKeys } from '@/hooks/useIdempotencyKeys';

describe('useIdempotencyKeys', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('reuses one key for the same semantic intent', () => {
    const randomUUID = vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000001')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000002');
    const { result } = renderHook(() => useIdempotencyKeys('campaign:launch'));

    const first = result.current.getIdempotencyKey(['ws-1', 'campaign-1']);
    const second = result.current.getIdempotencyKey(['ws-1', 'campaign-1']);

    expect(second).toBe(first);
    expect(randomUUID).toHaveBeenCalledTimes(1);
  });

  it('canonicalizes object key order when identifying an intent', () => {
    vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000001')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000002');
    const { result } = renderHook(() => useIdempotencyKeys('campaign:create'));

    const first = result.current.getIdempotencyKey(['ws-1', { name: 'Launch', budget: 500 }]);
    const second = result.current.getIdempotencyKey(['ws-1', { budget: 500, name: 'Launch' }]);

    expect(second).toBe(first);
  });

  it('clears successful intents so the next user action gets a new key', () => {
    vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000001')
      .mockReturnValueOnce('00000000-0000-4000-8000-000000000002');
    const { result } = renderHook(() => useIdempotencyKeys('budget:approve'));
    const intent = ['ws-1', 'budget-1'];

    const first = result.current.getIdempotencyKey(intent);
    act(() => result.current.clearIdempotencyKey(intent));
    const second = result.current.getIdempotencyKey(intent);

    expect(second).not.toBe(first);
  });
});
