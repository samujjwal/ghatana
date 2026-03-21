import { expect, afterEach, beforeAll, afterAll, vi } from 'vitest';
import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { configureAxe } from 'vitest-axe';
import 'vitest-axe/extend-expect';
import { server } from '../mocks/server';

/**
 * Global test setup for Data Cloud Platform tests.
 *
 * <p><b>Purpose</b><br>
 * Configures test environment, mocks, and cleanup.
 * Includes:
 * - MSW (Mock Service Worker) — intercepts HTTP calls in Vitest/Node
 * - axe-core — runs accessibility audit after every render
 *
 * @doc.type config
 * @doc.purpose Test environment setup
 */

// ---------------------------------------------------------------------------
// axe-core — Accessibility
// ---------------------------------------------------------------------------

/**
 * Pre-configured axe runner.
 * Disables colour-contrast (requires real CSS computed values) while
 * enforcing the WCAG2AA rule set for all other violations.
 */
export const axe = configureAxe({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rules: [
    // Colour-contrast cannot be evaluated against jsdom's computed styles
    { id: 'color-contrast', enabled: false },
  ] as any,
});

// ---------------------------------------------------------------------------
// MSW — API mocking
// ---------------------------------------------------------------------------

// Start before the test suite; close when done
beforeAll(() => server.listen({
  onUnhandledRequest(request, print) {
    // Only warn for unhandled API requests — ignore asset/font/favicon requests
    const url = new URL(request.url);
    if (url.pathname.startsWith('/api/')) {
      print.warning();
    }
  },
}));

// Reset handler overrides between tests so each test starts clean
afterEach(() => server.resetHandlers());

afterAll(() => server.close());

// ---------------------------------------------------------------------------
// React act() environment — suppress false-positive act() warnings (P7-3c)
// ---------------------------------------------------------------------------
// @ts-expect-error — globalThis.IS_REACT_ACT_ENVIRONMENT is not in TS types
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

// ---------------------------------------------------------------------------
// Render cleanup
// ---------------------------------------------------------------------------

// Cleanup after each test
afterEach(() => {
  cleanup();
});

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
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

// ---------------------------------------------------------------------------
// localStorage — provide a complete in-memory implementation
// jsdom's localStorage may not expose all Storage methods as own functions,
// which causes `localStorage.clear is not a function` failures in some Vitest
// versions. This override ensures the full interface is always available.
// ---------------------------------------------------------------------------
const _createLocalStorage = () => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string): string | null => store[key] ?? null,
    setItem: (key: string, value: string): void => { store[key] = String(value); },
    removeItem: (key: string): void => { delete store[key]; },
    clear: (): void => { store = {}; },
    get length(): number { return Object.keys(store).length; },
    key: (index: number): string | null => Object.keys(store)[index] ?? null,
  } as Storage;
};

Object.defineProperty(global, 'localStorage', {
  value: _createLocalStorage(),
  writable: true,
  configurable: true,
});

// Reset localStorage before each test so tests don't bleed state into each other
beforeEach(() => {
  (global.localStorage as ReturnType<typeof _createLocalStorage>).clear();
});

// Mock IntersectionObserver
global.IntersectionObserver = class IntersectionObserver {
  constructor() {}
  disconnect() {}
  observe() {}
  takeRecords() {
    return [];
  }
  unobserve() {}
} as any;

// Mock ResizeObserver
global.ResizeObserver = class ResizeObserver {
  constructor() {}
  disconnect() {}
  observe() {}
  unobserve() {}
} as any;

// ---------------------------------------------------------------------------
// EventSource — SSE stub required by AlertsPage (not provided by jsdom)
// ---------------------------------------------------------------------------
global.EventSource = class EventSource {
  readonly url: string;
  onopen: ((this: EventSource, ev: Event) => unknown) | null = null;
  onmessage: ((this: EventSource, ev: MessageEvent) => unknown) | null = null;
  onerror: ((this: EventSource, ev: Event) => unknown) | null = null;
  readyState = 0;
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  readonly CONNECTING = 0;
  readonly OPEN = 1;
  readonly CLOSED = 2;
  constructor(url: string) { this.url = url; }
  close() { this.readyState = 2; }
  addEventListener() {}
  removeEventListener() {}
  dispatchEvent() { return false; }
} as any;

// Suppress console errors in tests (optional)
const originalError = console.error;
beforeAll(() => {
  console.error = (...args: any[]) => {
    if (
      typeof args[0] === 'string' &&
      args[0].includes('Warning: ReactDOM.render')
    ) {
      return;
    }
    originalError.call(console, ...args);
  };
});

afterAll(() => {
  console.error = originalError;
});
