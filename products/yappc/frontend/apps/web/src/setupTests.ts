// Jest setup file for testing
import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';
import './__mocks__/localStorage';

// Mock HTML canvas methods
HTMLCanvasElement.prototype.getContext = vi.fn();

// Mock ResizeObserver
/**
 *
 */
class ResizeObserverMock {
  observe = vi.fn();
  unobserve = vi.fn();
  disconnect = vi.fn();
}

// Mock matchMedia
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

// Clean up after each test
afterEach(() => {
  cleanup();
  // Clear all mocks after each test
  vi.clearAllMocks();
  // Clear localStorage
  window.localStorage.clear();
});

// Mock window.scrollTo
window.scrollTo = vi.fn();

// Mock IntersectionObserver
/**
 *
 */
class IntersectionObserverMock {
  readonly root: Element | null = null;
  readonly rootMargin: string = '';
  readonly thresholds: ReadonlyArray<number> = [];

  /**
   *
   */
  constructor(
    private callback: IntersectionObserverCallback,
    _options?: IntersectionObserverInit
  ) {}

  /**
   *
   */
  observe() {
    // Trigger callback with a mock entry
    const entry: IntersectionObserverEntry = {
      boundingClientRect: {} as DOMRectReadOnly,
      intersectionRatio: 1,
      intersectionRect: {} as DOMRectReadOnly,
      isIntersecting: true,
      rootBounds: null,
      target: document.createElement('div'),
      time: 0,
    };
    this.callback([entry], this);
  }

  /**
   *
   */
  unobserve() {}
  /**
   *
   */
  disconnect() {}
  /**
   *
   */
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}

// Mock global objects
Object.defineProperty(window, 'IntersectionObserver', {
  writable: true,
  configurable: true,
  value: IntersectionObserverMock,
});

// Mock ResizeObserver
global.ResizeObserver = ResizeObserverMock;

// Mock requestIdleCallback
window.requestIdleCallback = vi.fn((callback: (deadline: unknown) => void) => {
  const start = Date.now();
  const id = setTimeout(() => {
    callback({
      didTimeout: false,
      timeRemaining: () => Math.max(0, 50 - (Date.now() - start)),
    });
  }, 0);
  return id as unknown as number;
});

window.cancelIdleCallback = (id) => {
  clearTimeout(id);
};
