import { expect, afterEach, beforeAll, afterAll, vi } from 'vitest';
import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { configureAxe, toHaveNoViolations } from 'vitest-axe';
import { server } from '../mocks/server';

/**
 * Global test setup for CES Workflow Platform tests.
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

expect.extend(toHaveNoViolations);

/**
 * Pre-configured axe runner.
 * Disables colour-contrast (requires real CSS computed values) while
 * enforcing the WCAG2AA rule set for all other violations.
 */
export const axe = configureAxe({
  rules: [
    // Colour-contrast cannot be evaluated against jsdom's computed styles
    { id: 'color-contrast', enabled: false },
  ],
});

// ---------------------------------------------------------------------------
// MSW — API mocking
// ---------------------------------------------------------------------------

// Start before the test suite; close when done
beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));

// Reset handler overrides between tests so each test starts clean
afterEach(() => server.resetHandlers());

afterAll(() => server.close());

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
