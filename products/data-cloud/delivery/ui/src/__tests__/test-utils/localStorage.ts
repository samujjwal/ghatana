// Test helper for temporarily swapping global.localStorage during tests.

export type MockStorage = {
  getItem?: (key: string) => string | null;
  setItem?: (key: string, value: string) => void;
  removeItem?: (key: string) => void;
  clear?: () => void;
};

/**
 * Temporarily replace global.localStorage with the provided mock for the duration of fn.
 * Restores the original localStorage even if fn throws.
 */
export function withMockLocalStorage(mock: MockStorage, fn: () => void) {
  // Keep the reference to the original global localStorage
  const original = (global as any).localStorage;

  // Define a minimal storage object implementing the required functions
  const storage = {
    getItem: mock.getItem ?? (() => null),
    setItem: mock.setItem ?? (() => undefined),
    removeItem: mock.removeItem ?? (() => undefined),
    clear: mock.clear ?? (() => undefined),
  } as Storage;

  Object.defineProperty(global, 'localStorage', {
    value: storage,
    writable: true,
    configurable: true,
  });

  try {
    fn();
  } finally {
    Object.defineProperty(global, 'localStorage', {
      value: original,
      writable: true,
      configurable: true,
    });
  }
}

