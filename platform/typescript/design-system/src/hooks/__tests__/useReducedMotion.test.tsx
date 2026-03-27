import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { usePrefersReducedMotion } from '../usePrefersReducedMotion';
import { useAnimationClass, useReducedMotion, useSafeMotion } from '../useReducedMotion';

type MockMediaQuery = MediaQueryList & {
  triggerChange: (matches: boolean) => void;
};

function installMatchMedia(initialMatches: boolean): MockMediaQuery {
  let listener: ((event: MediaQueryListEvent) => void) | undefined;

  const mediaQuery = {
    matches: initialMatches,
    media: '(prefers-reduced-motion: reduce)',
    onchange: null,
    addEventListener: vi.fn((_event: string, handler: (event: MediaQueryListEvent) => void) => {
      listener = handler;
    }),
    removeEventListener: vi.fn(),
    addListener: vi.fn((_handler: (event: MediaQueryListEvent) => void) => {
      listener = _handler;
    }),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
    triggerChange(matches: boolean) {
      this.matches = matches;
      listener?.({ matches } as MediaQueryListEvent);
    },
  } as unknown as MockMediaQuery;

  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockReturnValue(mediaQuery),
  });

  return mediaQuery;
}

describe('motion preference hooks', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('reads and updates prefers-reduced-motion state from matchMedia', () => {
    const mediaQuery = installMatchMedia(false);
    const { result } = renderHook(() => usePrefersReducedMotion());

    expect(result.current).toBe(false);

    act(() => {
      mediaQuery.triggerChange(true);
    });

    expect(result.current).toBe(true);
  });

  it('proxies reduced motion through useReducedMotion', () => {
    installMatchMedia(true);
    const { result } = renderHook(() => useReducedMotion());

    expect(result.current).toBe(true);
  });

  it('returns animation classes only when motion is allowed', () => {
    const mediaQuery = installMatchMedia(false);
    const { result } = renderHook(() => useAnimationClass('animate-fadeIn'));

    expect(result.current).toBe('animate-fadeIn');

    act(() => {
      mediaQuery.triggerChange(true);
    });

    expect(result.current).toBe('');
  });

  it('returns the safe-motion fallback when reduced motion is preferred', () => {
    installMatchMedia(true);
    const { result } = renderHook(() => useSafeMotion(300, 0));

    expect(result.current).toBe(0);
  });
});