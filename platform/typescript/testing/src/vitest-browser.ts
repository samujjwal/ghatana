import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';

export interface MemoryStorage extends Storage {
  readonly __store: Readonly<Record<string, string>>;
}

export function createMemoryStorage(
  initialState: Record<string, string> = {},
): MemoryStorage {
  let store = { ...initialState };

  return {
    get __store() {
      return { ...store };
    },
    get length() {
      return Object.keys(store).length;
    },
    clear() {
      store = {};
    },
    getItem(key: string) {
      return store[key] ?? null;
    },
    key(index: number) {
      return Object.keys(store)[index] ?? null;
    },
    removeItem(key: string) {
      delete store[key];
    },
    setItem(key: string, value: string) {
      store[key] = value;
    },
  };
}

export function installMockStorage(
  target: Window,
  property: 'localStorage' | 'sessionStorage' = 'localStorage',
  initialState?: Record<string, string>,
): MemoryStorage {
  const storage = createMemoryStorage(initialState);
  Object.defineProperty(target, property, {
    configurable: true,
    writable: true,
    value: storage,
  });
  return storage;
}

export function installMockFetch(
  implementation: typeof fetch = vi.fn(),
): typeof fetch {
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    writable: true,
    value: implementation,
  });
  return implementation;
}

export function installMockMatchMedia(target: Window): void {
  Object.defineProperty(target, 'matchMedia', {
    configurable: true,
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
}

export function installMockResizeObserver(target: typeof globalThis): void {
  if ('ResizeObserver' in target) {
    return;
  }

  class MockResizeObserver implements ResizeObserver {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  }

  Object.defineProperty(target, 'ResizeObserver', {
    configurable: true,
    writable: true,
    value: MockResizeObserver,
  });
}