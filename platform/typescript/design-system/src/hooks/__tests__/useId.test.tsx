import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { useId } from '../useId';

describe('useId', () => {
  it('uses the provided prefix', () => {
    const { result } = renderHook(() => useId('field'));

    expect(result.current.startsWith('field')).toBe(true);
  });

  it('returns a stable id across rerenders', () => {
    const { result, rerender } = renderHook(() => useId('stable'));

    const firstId = result.current;
    rerender();

    expect(result.current).toBe(firstId);
  });

  it('returns distinct ids for distinct hook instances', () => {
    const { result: first } = renderHook(() => useId('item'));
    const { result: second } = renderHook(() => useId('item'));

    expect(first.current).not.toBe(second.current);
  });
});