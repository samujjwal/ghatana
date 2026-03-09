import '@testing-library/jest-dom/vitest';
import { server } from '@ghatana/yappc-mocks/node';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';

// Establish API mocking before all tests
beforeAll(() => {
  // Enable the MSW Node server
  server.listen({ onUnhandledRequest: 'bypass' });
});

// Reset any request handlers that we may add during the tests,
// so they don't affect other tests
afterEach(() => {
  server.resetHandlers();
});

// Clean up after the tests are finished
afterAll(() => {
  server.close();
});

// Provide a small `jest` shim for tests that still use `jest.fn` / `jest.spyOn`
// (the repo uses Vitest but some tests still reference `jest` globals).
// Keep this lightweight and mapped to Vitest's `vi`.
// The following augmentations are for the test runtime only and may rely
// on types that are not visible to the linter; use @ts-expect-error with a
// short justification so the rule enforces correctness if the code becomes valid.

// augment global jest shim for Vitest runtime (test-only)
if (typeof (globalThis as any).jest === 'undefined') {
  // assign jest shim to globalThis for tests (test-only)
  (globalThis as any).jest = {
    fn: (...args: any[]) => vi.fn(...args),
    spyOn: (obj: any, method: string) => vi.spyOn(obj, method),
    // Timer helpers mapped to Vitest's `vi`
    useFakeTimers: () => vi.useFakeTimers(),
    useRealTimers: () => vi.useRealTimers(),
    advanceTimersByTime: (ms: number) => vi.advanceTimersByTime(ms),
    runAllTimers: () => vi.runAllTimers(),
    runOnlyPendingTimers: () => vi.runOnlyPendingTimers(),
    // add any other helpers used in tests as needed
  };
}

// Minimal matchMedia polyfill for tests that read prefers-color-scheme
if (typeof window !== 'undefined' && !('matchMedia' in window)) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

// requestIdleCallback polyfill (some components and tests rely on it)
if (typeof window !== 'undefined' && !('requestIdleCallback' in window)) {
  // provide simple requestIdleCallback polyfill for tests (test-only)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (window as any).requestIdleCallback = (cb: (arg: any) => void) => {
    return setTimeout(
      () => cb({ didTimeout: false, timeRemaining: () => 50 }),
      0
    ) as unknown as number;
  };
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (window as any).cancelIdleCallback = (id: number) => clearTimeout(id);
}

// Minimal canvas getContext shim for jsdom in tests.
// Some components (minimap, konva wrappers) call canvas.getContext('2d')
// which jsdom does not implement by default. Provide a tiny fake 2D
// context that implements the methods commonly used by our code.
if (typeof window !== 'undefined' && typeof HTMLCanvasElement !== 'undefined') {
  // Only add shim if not implemented
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore
  if (!HTMLCanvasElement.prototype.getContext) {
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    HTMLCanvasElement.prototype.getContext = function (type: string) {
      if (type !== '2d') return null;
      // provide a minimal 2D context stub used by tests
      const ctx = {
        fillRect: () => {},
        clearRect: () => {},
        getImageData: (_x: number, _y: number, w: number, h: number) => ({
          data: new Uint8ClampedArray(w * h * 4),
        }),
        putImageData: () => {},
        createImageData: (_w: number, _h: number) => ({
          data: new Uint8ClampedArray(_w * _h * 4),
        }),
        setTransform: () => {},
        drawImage: () => {},
        save: () => {},
        restore: () => {},
        beginPath: () => {},
        stroke: () => {},
        fill: () => {},
        translate: () => {},
        scale: () => {},
        rotate: () => {},
        measureText: (_t: string) => ({ width: 0 }),
        fillText: () => {},
        strokeText: () => {},
      } as unknown as CanvasRenderingContext2D;
      return ctx;
    };
  }
}
