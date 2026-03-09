// Vitest setup file for unit tests
// Mock browser extension APIs for tests running in jsdom

import { webcrypto } from 'crypto';

// ✅ Setup IndexedDB mocking
// Using minimal mock implementation (fake-indexeddb has export issues in ESM)
let fakeIndexedDB: any;
let IDBKeyRange: any;

// Minimal IndexedDB mock for tests
fakeIndexedDB = {
    open: () => {
      const listeners: Map<string, Function[]> = new Map();

      const request: any = {
        result: {
          objectStoreNames: { contains: () => false },
          createObjectStore: () => ({
            createIndex: () => {},
          }),
          transaction: () => ({
            objectStore: () => ({
              add: () => Promise.resolve(),
              put: () => Promise.resolve(),
              get: () => Promise.resolve(undefined),
              delete: () => Promise.resolve(),
              clear: () => Promise.resolve(),
              count: () => Promise.resolve(0),
              getAll: () => Promise.resolve([]),
              getAllKeys: () => Promise.resolve([]),
              openCursor: () => Promise.resolve(null),
              index: () => ({
                get: () => Promise.resolve(undefined),
                getAll: () => Promise.resolve([]),
                getAllKeys: () => Promise.resolve([]),
                openCursor: () => Promise.resolve(null),
                count: () => Promise.resolve(0),
              }),
            }),
            complete: Promise.resolve(),
            done: Promise.resolve(),
          }),
          close: () => {},
          version: 1,
        },
        onsuccess: null as any,
        onerror: null as any,
        onupgradeneeded: null as any,
        onblocked: null as any,
        // Add EventTarget methods
        addEventListener: (event: string, handler: Function) => {
          if (!listeners.has(event)) {
            listeners.set(event, []);
          }
          listeners.get(event)!.push(handler);
        },
        removeEventListener: (event: string, handler: Function) => {
          const handlers = listeners.get(event);
          if (handlers) {
            const index = handlers.indexOf(handler);
            if (index > -1) {
              handlers.splice(index, 1);
            }
          }
        },
        dispatchEvent: (event: any) => {
          const handlers = listeners.get(event.type) || [];
          handlers.forEach(handler => handler(event));
          return true;
        },
      };

      setTimeout(() => {
        // Trigger upgrade if needed
        if (request.onupgradeneeded) {
          request.onupgradeneeded({ target: request });
        }
        // Then trigger success
        if (request.onsuccess) {
          request.onsuccess({ target: request });
        }
        // Trigger success event listeners
        const successHandlers = listeners.get('success') || [];
        successHandlers.forEach(handler => handler({ target: request }));
      }, 0);

      return request;
    },
    deleteDatabase: () => ({
      onsuccess: null,
      onerror: null,
    }),
};

IDBKeyRange = {
  bound: (lower: any, upper: any) => ({ lower, upper }),
  only: (value: any) => ({ value }),
  lowerBound: (lower: any) => ({ lower }),
  upperBound: (upper: any) => ({ upper }),
};

// Mock chrome/browser APIs
const mockBrowser = {
  runtime: {
    id: 'test-extension-id',
    getManifest: () => ({ version: '1.0.0' }),
    sendMessage: () => Promise.resolve({}),
    onMessage: {
      addListener: () => {},
      removeListener: () => {},
    },
    onInstalled: {
      addListener: () => {},
      removeListener: () => {},
    },
    onStartup: {
      addListener: () => {},
      removeListener: () => {},
    },
  },
  storage: {
    local: {
      get: () => Promise.resolve({}),
      set: () => Promise.resolve(),
      remove: () => Promise.resolve(),
      clear: () => Promise.resolve(),
      getBytesInUse: () => Promise.resolve(0),
      QUOTA_BYTES: 5242880, // 5MB
    },
  },
  tabs: {
    onCreated: {
      addListener: () => {},
      removeListener: () => {},
    },
    onUpdated: {
      addListener: () => {},
      removeListener: () => {},
    },
    onRemoved: {
      addListener: () => {},
      removeListener: () => {},
    },
    onActivated: {
      addListener: () => {},
      removeListener: () => {},
    },
    executeScript: () => Promise.resolve([{}]),
  },
  webNavigation: {
    onCompleted: {
      addListener: () => {},
      removeListener: () => {},
    },
    onErrorOccurred: {
      addListener: () => {},
      removeListener: () => {},
    },
  },
  windows: {
    onFocusChanged: {
      addListener: () => {},
      removeListener: () => {},
    },
    WINDOW_ID_NONE: -1,
  },
};

// Mock browser globals with explicit casting
globalThis.chrome = mockBrowser as unknown as typeof globalThis.chrome;
globalThis.browser = mockBrowser as unknown as typeof globalThis.browser;

// ✅ Mock IndexedDB globals
globalThis.indexedDB = fakeIndexedDB;
globalThis.IDBKeyRange = IDBKeyRange;

// Mock additional IDB classes needed by idb library
globalThis.IDBRequest = class IDBRequest {} as any;
globalThis.IDBOpenDBRequest = class IDBOpenDBRequest extends (globalThis.IDBRequest as any) {} as any;
globalThis.IDBTransaction = class IDBTransaction {} as any;
globalThis.IDBDatabase = class IDBDatabase {} as any;
globalThis.IDBObjectStore = class IDBObjectStore {} as any;
globalThis.IDBIndex = class IDBIndex {} as any;
globalThis.IDBCursor = class IDBCursor {} as any;
globalThis.IDBCursorWithValue = class IDBCursorWithValue extends (globalThis.IDBCursor as any) {} as any;

// Ensure crypto.subtle is available from Node.js webcrypto
try {
  // Polyfilling crypto for tests with explicit cast
  globalThis.crypto = webcrypto as unknown as Crypto;
} catch {
  // If crypto is read-only (jsdom), use Object.defineProperty
  Object.defineProperty(globalThis, 'crypto', {
    value: webcrypto,
    writable: false,
    configurable: true,
  });
}
