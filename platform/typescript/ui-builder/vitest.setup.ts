// Setup for @ghatana/ui-builder tests.
import { enableMapSet } from 'immer';
import '@testing-library/jest-dom';
enableMapSet();

// Mock localStorage for persistence tests
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string): string | null => store[key] ?? null,
    setItem: (key: string, value: string): void => { store[key] = value; },
    removeItem: (key: string): void => { delete store[key]; },
    clear: (): void => { store = {}; },
  };
})();

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
  writable: true,
});
