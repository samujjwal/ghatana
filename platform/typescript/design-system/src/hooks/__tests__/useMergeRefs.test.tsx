import { renderHook } from '@testing-library/react';
import { createRef } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { useMergeRefs } from '../useMergeRefs';

describe('useMergeRefs', () => {
  it('assigns the same value to function and object refs', () => {
    const callbackRef = vi.fn();
    const objectRef = createRef<HTMLDivElement>();
    const value = document.createElement('div');

    const { result } = renderHook(() => useMergeRefs<HTMLDivElement>(callbackRef, objectRef));

    result.current(value);

    expect(callbackRef).toHaveBeenCalledWith(value);
    expect(objectRef.current).toBe(value);
  });

  it('ignores undefined refs', () => {
    const objectRef = createRef<HTMLButtonElement>();
    const value = document.createElement('button');

    const { result } = renderHook(() => useMergeRefs<HTMLButtonElement>(undefined, objectRef));

    result.current(value);

    expect(objectRef.current).toBe(value);
  });

  it('swallows readonly ref assignment failures', () => {
    const readonlyRef = {} as { readonly current: HTMLDivElement | null };
    Object.defineProperty(readonlyRef, 'current', {
      configurable: true,
      get: () => null,
      set: () => {
        throw new Error('readonly');
      },
    });

    const callbackRef = vi.fn();
    const value = document.createElement('div');

    const { result } = renderHook(() =>
      useMergeRefs<HTMLDivElement>(readonlyRef, callbackRef)
    );

    expect(() => result.current(value)).not.toThrow();
    expect(callbackRef).toHaveBeenCalledWith(value);
  });

  it('returns a stable callback for the same ref inputs', () => {
    const callbackRef = vi.fn();
    const objectRef = createRef<HTMLDivElement>();

    const { result, rerender } = renderHook(() =>
      useMergeRefs<HTMLDivElement>(callbackRef, objectRef)
    );

    const firstCallback = result.current;
    rerender();

    expect(result.current).toBe(firstCallback);
  });
});