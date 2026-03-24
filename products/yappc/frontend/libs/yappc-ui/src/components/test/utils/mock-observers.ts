/**
 * Mock Observer utilities for testing
 * @module test/utils/mock-observers
 */

import { vi } from 'vitest';

/**
 * Creates a mock ResizeObserver for testing resize events
 *
 * @returns Mock ResizeObserver with observe, unobserve, disconnect methods
 *
 * @example
 * ```typescript
 * vi.stubGlobal('ResizeObserver', createMockResizeObserver());
 * ```
 */
export function createMockResizeObserver() {
  return {
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
  };
}

/**
 * Creates a mock IntersectionObserver for testing visibility
 *
 * @param isIntersecting - Whether elements are considered intersecting
 * @returns Mock IntersectionObserver with all methods and properties
 *
 * @example
 * ```typescript
 * const observer = createMockIntersectionObserver(true);
 * vi.stubGlobal('IntersectionObserver', observer);
 * ```
 */
export function createMockIntersectionObserver(isIntersecting = true) {
  return {
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
    takeRecords: vi.fn(),
    root: null,
    rootMargin: '0px',
    thresholds: [0],
    entries: [
      {
        isIntersecting,
        target: document.createElement('div'),
        intersectionRatio: isIntersecting ? 1 : 0,
        boundingClientRect: {} as DOMRectReadOnly,
        intersectionRect: {} as DOMRectReadOnly,
        rootBounds: null,
        time: Date.now(),
      },
    ],
  };
}

/**
 * Creates a mock MediaQueryList for testing media queries
 *
 * @param matches - Whether the media query matches
 * @returns Mock MediaQueryList with event listeners
 *
 * @example
 * ```typescript
 * const darkMode = createMockMediaQueryList(true);
 * vi.stubGlobal('matchMedia', () => darkMode);
 * ```
 */
export function createMockMediaQueryList(matches = false) {
  return {
    matches,
    media: '',
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  };
}

/**
 * Creates a mock MutationObserver for testing DOM changes
 *
 * @returns Mock MutationObserver with observe, disconnect, takeRecords
 *
 * @example
 * ```typescript
 * vi.stubGlobal('MutationObserver', createMockMutationObserver());
 * ```
 */
export function createMockMutationObserver() {
  return {
    observe: vi.fn(),
    disconnect: vi.fn(),
    takeRecords: vi.fn(),
  };
}

/**
 * Creates a mock PerformanceObserver for testing performance metrics
 *
 * @returns Mock PerformanceObserver with observe, disconnect, takeRecords
 *
 * @example
 * ```typescript
 * vi.stubGlobal('PerformanceObserver', createMockPerformanceObserver());
 * ```
 */
export function createMockPerformanceObserver() {
  return {
    observe: vi.fn(),
    disconnect: vi.fn(),
    takeRecords: vi.fn(),
  };
}

/**
 * Creates a mock generic observer (base pattern)
 *
 * @returns Mock observer with common interface
 *
 * @internal
 */
export function createMockObserver() {
  return {
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
  };
}
