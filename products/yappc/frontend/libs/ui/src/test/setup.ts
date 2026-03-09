/**
 * Test Setup Configuration
 *
 * This file configures the testing environment for the YAPPC UI library.
 * It sets up global mocks, environment variables, and cleanup functions.
 */

import { cleanup } from '@testing-library/react';
import { afterEach, afterAll, beforeAll, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';

// Let React know the environment supports `act`
if (
  typeof globalThis !== 'undefined' &&
  typeof (globalThis as unknown).IS_REACT_ACT_ENVIRONMENT === 'undefined'
) {
  (globalThis as unknown).IS_REACT_ACT_ENVIRONMENT = true;
}

// Provide a richer `jest` shim for suites that still reference Jest helpers
if (typeof (globalThis as unknown).jest === 'undefined') {
  (globalThis as unknown).jest = {
    fn: (...args: unknown[]) => vi.fn(...args),
    spyOn: (obj: unknown, method: string) => vi.spyOn(obj, method),
    advanceTimersByTime: (ms: number) => vi.advanceTimersByTime(ms),
    runAllTimers: () => vi.runAllTimers(),
    clearAllTimers: () => vi.clearAllTimers(),
    useFakeTimers: () => vi.useFakeTimers(),
    useRealTimers: () => vi.useRealTimers(),
    setSystemTime: (time: Date | number) => vi.setSystemTime(time as unknown),
    getRealSystemTime: () => vi.getRealSystemTime(),
  };
}

// Mock browser APIs
global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

global.IntersectionObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
  takeRecords: vi.fn(),
}));

global.matchMedia = vi.fn().mockImplementation((query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
}));

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value.toString();
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
  };
})();

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
});

// Set environment variables for testing
process.env.VITEST = 'true';
process.env.TEST_ENV = 'test';
// API Gateway URL (single entry point)
process.env.API_URL = 'http://localhost:7002/api';
process.env.MOCK_API = 'true';

// Configure console to avoid test noise
const originalConsoleError = console.error;
const originalConsoleWarn = console.warn;

console.error = (...args: unknown[]) => {
  if (
    /Warning.*not wrapped in act/i.test(args[0]) ||
    /Warning.*cannot update a component/i.test(args[0]) ||
    /Warning.*received `true` for a non-boolean attribute/i.test(args[0])
  ) {
    return;
  }
  originalConsoleError(...args);
};

console.warn = (...args: unknown[]) => {
  if (
    /Warning.*cannot update a component/i.test(args[0]) ||
    /Warning.*received `true` for a non-boolean attribute/i.test(args[0])
  ) {
    return;
  }
  originalConsoleWarn(...args);
};

// Clean up after each test
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.resetModules();
  localStorage.clear();
  document.body.innerHTML = '';
});

// Global setup before all tests
beforeAll(() => {
  // Set up global test environment
  vi.useFakeTimers();
});

// Global teardown after all tests
afterAll(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  console.error = originalConsoleError;
  console.warn = originalConsoleWarn;
});
