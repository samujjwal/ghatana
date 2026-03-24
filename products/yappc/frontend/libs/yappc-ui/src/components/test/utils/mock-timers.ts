/**
 * Mock timer and animation utilities for testing
 * @module test/utils/mock-timers
 */

import { vi } from 'vitest';

/**
 * Creates mock timer functions (setTimeout, setInterval, etc.)
 *
 * @returns Object with timer functions and controls
 *
 * @example
 * ```typescript
 * const timers = createMockTimer();
 * const timeout = timers.setTimeout(() => {}, 100);
 * timers.clearTimeout(timeout);
 * ```
 */
export function createMockTimer() {
  return {
    setTimeout: vi.fn((callback: TimerHandler, ms?: number) => {
      setTimeout(callback, ms);
      return 1;
    }),
    setInterval: vi.fn((callback: TimerHandler, ms?: number) => {
      setInterval(callback, ms);
      return 1;
    }),
    setImmediate: vi.fn((callback: () => void) => {
      setImmediate(callback);
      return 1;
    }),
    clearTimeout: vi.fn(),
    clearInterval: vi.fn(),
    clearImmediate: vi.fn(),
  };
}

/**
 * Creates a mock requestAnimationFrame function
 *
 * @returns Mocked requestAnimationFrame
 *
 * @example
 * ```typescript
 * const rafMock = createMockRAF();
 * const id = rafMock(() => {});
 * expect(rafMock).toHaveBeenCalled();
 * ```
 */
export function createMockRAF() {
  return vi.fn((callback: FrameRequestCallback) => {
    callback(performance.now());
    return 1;
  });
}

/**
 * Creates a mock cancelAnimationFrame function
 *
 * @returns Mocked cancelAnimationFrame
 *
 * @example
 * ```typescript
 * const cancelMock = createMockCancelRAF();
 * cancelMock(1);
 * expect(cancelMock).toHaveBeenCalledWith(1);
 * ```
 */
export function createMockCancelRAF() {
  return vi.fn();
}

/**
 * Creates mock performance.now() function
 *
 * @param startTime - Initial timestamp
 * @returns Mocked performance.now
 *
 * @example
 * ```typescript
 * const nowMock = createMockPerformanceNow(0);
 * expect(nowMock()).toBe(0);
 * ```
 */
export function createMockPerformanceNow(startTime = 0) {
  let time = startTime;
  return vi.fn(() => {
    time += 1;
    return time;
  });
}
