/**
 * Test setup file for @ghatana/dcmaar-browser-extension-core
 */

import { vi } from "vitest";

// Mock webextension-polyfill
const mockBrowser = {
  storage: {
    local: {
      get: vi.fn(),
      set: vi.fn(),
      remove: vi.fn(),
      clear: vi.fn(),
      getBytesInUse: vi.fn(),
    },
    sync: {
      get: vi.fn(),
      set: vi.fn(),
      remove: vi.fn(),
      clear: vi.fn(),
      getBytesInUse: vi.fn(),
    },
    onChanged: {
      addListener: vi.fn(),
      removeListener: vi.fn(),
    },
  },
  runtime: {
    sendMessage: vi.fn(),
    onMessage: {
      addListener: vi.fn(),
      removeListener: vi.fn(),
    },
    connect: vi.fn(),
    onConnect: {
      addListener: vi.fn(),
    },
  },
  tabs: {
    sendMessage: vi.fn(),
    query: vi.fn(),
    get: vi.fn(),
    onCreated: {
      addListener: vi.fn(),
      removeListener: vi.fn(),
    },
    onUpdated: {
      addListener: vi.fn(),
      removeListener: vi.fn(),
    },
    onRemoved: {
      addListener: vi.fn(),
      removeListener: vi.fn(),
    },
    onActivated: {
      addListener: vi.fn(),
      removeListener: vi.fn(),
    },
  },
  webNavigation: {
    onCommitted: {
      addListener: vi.fn(),
      removeListener: vi.fn(),
    },
    onCompleted: {
      addListener: vi.fn(),
      removeListener: vi.fn(),
    },
  },
  extension: {
    getViews: vi.fn(() => []),
  },
};

vi.mock("webextension-polyfill", () => ({
  default: mockBrowser,
}));

// Make browser available globally for tests
(global as any).browser = mockBrowser;
(global as any).chrome = mockBrowser;

// Mock Performance API
(global as any).performance = {
  now: () => Date.now(),
  getEntriesByType: () => [],
  getEntriesByName: () => [],
  mark: vi.fn(),
  measure: vi.fn(),
  clearMarks: vi.fn(),
  clearMeasures: vi.fn(),
};

// Mock PerformanceObserver
(global as any).PerformanceObserver = class MockPerformanceObserver {
  observe = vi.fn();
  disconnect = vi.fn();
  takeRecords = vi.fn(() => []);
};
