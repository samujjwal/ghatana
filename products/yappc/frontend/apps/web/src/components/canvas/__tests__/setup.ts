/**
 * Test Setup Configuration
 * Global setup for canvas refactoring tests
 */

import '@testing-library/jest-dom';

// Mock ResizeObserver
global.ResizeObserver = jest.fn().mockImplementation(() => ({
  observe: jest.fn(),
  unobserve: jest.fn(),
  disconnect: jest.fn(),
}));

// Mock IntersectionObserver
global.IntersectionObserver = jest.fn().mockImplementation(() => ({
  observe: jest.fn(),
  unobserve: jest.fn(),
  disconnect: jest.fn(),
}));

// Mock performance API
Object.defineProperty(window, 'performance', {
  value: {
    now: jest.fn(() => Date.now()),
    mark: jest.fn(),
    measure: jest.fn(),
  },
});

// Mock requestAnimationFrame
global.requestAnimationFrame = jest.fn((cb) => setTimeout(cb, 16));
global.cancelAnimationFrame = jest.fn((id) => clearTimeout(id));

// Mock matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: jest.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: jest.fn(),
    removeListener: jest.fn(),
    addEventListener: jest.fn(),
    removeEventListener: jest.fn(),
    dispatchEvent: jest.fn(),
  })),
});

// Mock canvas context
HTMLCanvasElement.prototype.getContext = jest.fn(() => ({
  fillRect: jest.fn(),
  clearRect: jest.fn(),
  getImageData: jest.fn(() => ({ data: new Array(4) })),
  putImageData: jest.fn(),
  createImageData: jest.fn(() => ({ data: new Array(4) })),
  setTransform: jest.fn(),
  drawImage: jest.fn(),
  save: jest.fn(),
  fillText: jest.fn(),
  restore: jest.fn(),
  beginPath: jest.fn(),
  moveTo: jest.fn(),
  lineTo: jest.fn(),
  closePath: jest.fn(),
  stroke: jest.fn(),
  translate: jest.fn(),
  scale: jest.fn(),
  rotate: jest.fn(),
  arc: jest.fn(),
  fill: jest.fn(),
  measureText: jest.fn(() => ({ width: 0 })),
  transform: jest.fn(),
  rect: jest.fn(),
  clip: jest.fn(),
})) as unknown;

// Extend expect matchers for canvas testing
expect.extend({
  toHavePerformantRender(received: number, expected: number) {
    const pass = received <= expected;
    if (pass) {
      return {
        message: () =>
          `expected render time ${received}ms not to be less than or equal to ${expected}ms`,
        pass: true,
      };
    } else {
      return {
        message: () =>
          `expected render time ${received}ms to be less than or equal to ${expected}ms`,
        pass: false,
      };
    }
  },

  toMaintainConsistency(received: unknown[], property: string) {
    const values = received.map((item) => item[property]);
    const uniqueValues = new Set(values);
    const pass = uniqueValues.size === 1 || uniqueValues.size === 0;

    if (pass) {
      return {
        message: () =>
          `expected property ${property} not to be consistent across items`,
        pass: true,
      };
    } else {
      return {
        message: () =>
          `expected property ${property} to be consistent across items, but found: ${Array.from(uniqueValues).join(', ')}`,
        pass: false,
      };
    }
  },
});

// Silence console warnings in tests
const originalConsoleWarn = console.warn;
const originalConsoleError = console.error;

beforeEach(() => {
  console.warn = jest.fn();
  console.error = jest.fn();
});

afterEach(() => {
  console.warn = originalConsoleWarn;
  console.error = originalConsoleError;
  jest.clearAllMocks();
});

// Global test timeout
jest.setTimeout(10000);
