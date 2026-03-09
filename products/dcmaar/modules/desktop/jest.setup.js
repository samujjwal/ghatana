// Register jest-dom matchers in Jest 30+
const matchers = require('@testing-library/jest-dom/matchers');
expect.extend(matchers);

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: jest.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: jest.fn(), // deprecated
    removeListener: jest.fn(), // deprecated
    addEventListener: jest.fn(),
    removeEventListener: jest.fn(),
    dispatchEvent: jest.fn(),
  })),
});

// Mock ResizeObserver
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

window.ResizeObserver = window.ResizeObserver || ResizeObserverStub;

// Mock IntersectionObserver
class IntersectionObserverStub {
  constructor() {}
  observe() {}
  unobserve() {}
  disconnect() {}
}

window.IntersectionObserver = window.IntersectionObserver || IntersectionObserverStub;

// Mock scrollTo
window.scrollTo = jest.fn();

// Mock localStorage
const localStorageMock = (() => {
  let store = {};
  return {
    getItem: jest.fn(key => store[key] || null),
    setItem: jest.fn((key, value) => {
      store[key] = value.toString();
    }),
    removeItem: jest.fn(key => {
      delete store[key];
    }),
    clear: jest.fn(() => {
      store = {};
    }),
  };
})();

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
});

// Mock sessionStorage
const sessionStorageMock = (() => {
  let store = {};
  return {
    getItem: jest.fn(key => store[key] || null),
    setItem: jest.fn((key, value) => {
      store[key] = value.toString();
    }),
    removeItem: jest.fn(key => {
      delete store[key];
    }),
    clear: jest.fn(() => {
      store = {};
    }),
  };
})();

Object.defineProperty(window, 'sessionStorage', {
  value: sessionStorageMock,
});

// Mock fetch
global.fetch = jest.fn(() =>
  Promise.resolve({
    ok: true,
    json: () => Promise.resolve({}),
  })
);

// Lightweight Response polyfill for tests that call `new Response(body, init)`
if (typeof global.Response === 'undefined') {
  global.Response = class {
    constructor(body, init = {}) {
      this._body = body;
      this.status = init.status ?? 200;
      this.ok = this.status >= 200 && this.status < 300;
      this.headers = init.headers || {};
    }

    async json() {
      if (typeof this._body === 'string') {
        try {
          return JSON.parse(this._body);
        } catch (_e) {
          return this._body;
        }
      }
      return this._body;
    }

    async text() {
      if (typeof this._body === 'string') return this._body;
      return JSON.stringify(this._body);
    }
  };
}

// Mock console methods
const consoleError = console.error;
const consoleWarn = console.warn;
const consoleLog = console.log;

// Suppress specific warnings in tests
const suppressedErrors = [
  /ReactDOM.render is no longer supported in React 18/,
  /Warning: An update to .* inside a test was not wrapped in act/,
];

const suppressedWarnings = [
  /Warning: ReactDOM.render is no longer supported in React 18/,
  /Warning: You are importing .* from react-i18next/,
  /Warning: componentWillReceiveProps has been renamed/,
  /Warning: componentWillMount has been renamed/,
  /Warning: componentWillUpdate has been renamed/,
];

const error = console.error;

console.error = function (message) {
  if (suppressedErrors.some(restriction => restriction.test(message))) {
    return;
  }
  error.apply(console, arguments);
};

const warn = console.warn;

console.warn = function (message) {
  if (suppressedWarnings.some(restriction => restriction.test(message))) {
    return;
  }
  warn.apply(console, arguments);
};

// Reset all mocks before each test
beforeEach(() => {
  jest.clearAllMocks();
  localStorage.clear();
  sessionStorage.clear();
});

afterEach(() => {
  // Restore console methods
  console.error = consoleError;
  console.warn = consoleWarn;
  console.log = consoleLog;
});

// Provide a default QueryClientProvider wrapper for tests that use react-query
let testQueryClient;
try {
  const rtl = require('@testing-library/react');
  const React = require('react');
  const rq = require('@tanstack/react-query');
  const { QueryClient, QueryClientProvider } = rq;

  testQueryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  // Patch the render function so test files that import { render } from
  // @testing-library/react will receive a version that automatically wraps
  // components with a QueryClientProvider using a shared test client.
  if (rtl && rtl.render) {
    const originalRender = rtl.render;
    rtl.render = (ui, options) =>
      originalRender(React.createElement(QueryClientProvider, { client: testQueryClient }, ui), options);
  }

  // Also ensure react-query's `useQueryClient` returns the test client by
  // patching the module export in the require cache. This makes components
  // that call `useQuery` (which internally calls `useQueryClient`) work
  // without an explicit provider in test files.
  if (rq && typeof rq.useQueryClient === 'function') {
    rq.useQueryClient = () => testQueryClient;
  }
} catch (e) {
  // If testing library or react-query aren't available in some contexts,
  // don't break the setup; tests that require them will fail later with a
  // clear error.
   
  console.warn('Could not configure QueryClient test wrapper:', e?.message || e);
}
