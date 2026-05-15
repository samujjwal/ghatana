import '@testing-library/jest-dom';

const createStorage = () => {
  let store: Record<string, string> = {};

  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = String(value);
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
    key: (index: number) => Object.keys(store)[index] ?? null,
    get length() {
      return Object.keys(store).length;
    },
  } satisfies Storage;
};

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: createStorage(),
});

Object.defineProperty(globalThis, 'sessionStorage', {
  configurable: true,
  value: createStorage(),
});
