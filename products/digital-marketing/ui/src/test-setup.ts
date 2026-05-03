import '@testing-library/jest-dom';

class InMemoryStorage implements Storage {
  private readonly store = new Map<string, string>();

  get length(): number {
    return this.store.size;
  }

  clear(): void {
    this.store.clear();
  }

  getItem(key: string): string | null {
    return this.store.get(key) ?? null;
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value));
  }
}

function ensureStorage(storageKey: 'localStorage' | 'sessionStorage'): void {
  const candidate = globalThis[storageKey];
  if (
    candidate &&
    typeof candidate.clear === 'function' &&
    typeof candidate.getItem === 'function' &&
    typeof candidate.setItem === 'function' &&
    typeof candidate.removeItem === 'function' &&
    typeof candidate.key === 'function'
  ) {
    return;
  }
  Object.defineProperty(globalThis, storageKey, {
    configurable: true,
    enumerable: true,
    value: new InMemoryStorage(),
    writable: true,
  });
}

ensureStorage('localStorage');
ensureStorage('sessionStorage');

if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof globalThis.ResizeObserver;
}
