import '@testing-library/jest-dom/vitest';
import * as matchers from '@testing-library/jest-dom/matchers';
import { cleanup } from '@testing-library/react';
import * as React from 'react';
import { afterEach, expect, vi } from 'vitest';

// Extend Vitest's expect with jest-dom matchers
expect.extend(matchers);

// If the test-utils package exports a helper to mock @dnd-kit/core, invoke
// it here so the mock is registered before any test modules import the
// real `@dnd-kit/core`. This ensures consistent mocking regardless of
// import order in individual test files.
try {
  // use require to avoid ESM import timing issues in the setup file
   
  const maybeMock = require('./index');
  if (
    maybeMock &&
    typeof maybeMock.mockUseDraggableWithPayload === 'function'
  ) {
    try {
      maybeMock.mockUseDraggableWithPayload();
    } catch (_) {}
  }
} catch (_) {}

// Mock browser globals
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// Mock global APIs
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  clear: vi.fn(),
  removeItem: vi.fn(),
};

global.localStorage = localStorageMock as unknown as Storage;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// Intercept React element creation to capture any DndContext's onDragEnd
// prop and register it on the module-global test bridge. This makes the
// DnD mock resilient to import-order differences between tests and the
// mocked module.
try {
  const origCreateElement = React.createElement as unknown;
  (React as unknown).createElement = function (
    type: unknown,
    props: unknown,
    ...children: unknown[]
  ) {
    try {
      const isDndContext =
        type &&
        (type.displayName === 'DndContext' || type.name === 'DndContext');
      if (isDndContext && props && typeof props.onDragEnd === 'function') {
        try {
          // Ensure global bridge exists
          (globalThis as unknown).__TEST_DND_BRIDGE__ =
            (globalThis as unknown).__TEST_DND_BRIDGE__ || {};
          (globalThis as unknown).__TEST_DND_BRIDGE__.onDragEnd = props.onDragEnd;
          // Also register an alternate global handler that the mock will call
          // directly. This covers import-order cases where the module mock
          // can't inject the original DndContext instance.
          (globalThis as unknown).__TEST_DND_ONDRAGEND__ = props.onDragEnd;
          // Provide a debug accessor for recorded calls (used by the mock)
          (globalThis as unknown).__TEST_LAST_DND_CALLS__ =
            (globalThis as unknown).__TEST_LAST_DND_CALLS__ || [];
        } catch (_) {}
      }
    } catch (_) {}

    return origCreateElement(type, props, ...children);
  };
} catch (_) {}

// Expose a small helper to read recorded DnD calls from tests or debug harnesses
try {
  (globalThis as unknown).__getTestDndCalls = () => {
    return (globalThis as unknown).__TEST_LAST_DND_CALLS__ || [];
  };
} catch (_) {}

// Optional debug output: when running tests with DEBUG_TEST_DND=1 (or
// VITEST_DEBUG_DND=1) we'll print the recorded DnD call history after each
// test. This is guarded so it only affects local debugging and doesn't noisy
// the normal CI test output.
try {
  const enabled =
    process &&
    process.env &&
    (process.env.DEBUG_TEST_DND === '1' ||
      process.env.VITEST_DEBUG_DND === '1');
  if (enabled) {
    afterEach(() => {
      try {
        const calls = (globalThis as unknown).__getTestDndCalls?.() || [];
        if (calls && calls.length) {
          // Keep log concise but machine-readable for easy copy/paste when
          // investigating test failures. Also write to /tmp so CI or a
          // separate process can inspect recorded calls even if stdout is
          // filtered by the test runner.
          try {
            // eslint-disable-next-line no-console
            console.log(
              '\n[DEBUG] Recorded __TEST_LAST_DND_CALLS__:',
              JSON.stringify(calls, null, 2)
            );
          } catch (_) {}
          try {
            const fs = require('fs');
            const path = `/tmp/test-dnd-calls-${process.pid}.json`;
            fs.writeFileSync(path, JSON.stringify(calls, null, 2), 'utf8');
          } catch (_) {}
        }
        // Clear recorded calls between tests to avoid cross-test bleed.
        (globalThis as unknown).__TEST_LAST_DND_CALLS__ = [];
      } catch (_) {}
    });
  }
} catch (_) {}

// Minimal canvas getContext shim for jsdom in app-level tests
if (typeof window !== 'undefined' && typeof HTMLCanvasElement !== 'undefined') {
  // Always override jsdom's getContext which throws "Not implemented".
  // Use defineProperty to replace any existing implementation (may be non-writable).
  // @ts-ignore - test shim
  try {
    Object.defineProperty(HTMLCanvasElement.prototype, 'getContext', {
      configurable: true,
      writable: true,
      value (type: string) {
        if (type !== '2d') return null;
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
          // Additional commonly-used methods used by Canvas/minimap code
          strokeRect: () => {},
          rect: () => {},
          arc: () => {},
          lineTo: () => {},
          moveTo: () => {},
          closePath: () => {},
          clip: () => {},
          setLineDash: () => {},
          getLineDash: () => [],
          createLinearGradient: () => ({ addColorStop: () => {} }),
          createRadialGradient: () => ({ addColorStop: () => {} }),
          // basic stroke/fill style properties
          strokeStyle: '#000',
          fillStyle: '#000',
        } as unknown as CanvasRenderingContext2D;
        return ctx;
      },
    });
  } catch (e) {
    // ignore if defineProperty fails in some environments
  }
}
