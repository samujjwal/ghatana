import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';

Object.defineProperty(window, 'SpeechRecognition', {
  configurable: true,
  value: vi.fn(),
  writable: true,
});

Object.defineProperty(window, 'webkitSpeechRecognition', {
  configurable: true,
  value: vi.fn(),
  writable: true,
});

const storage = new Map<string, string>();

const localStorageMock: Storage = {
  get length(): number {
    return storage.size;
  },
  clear: vi.fn((): void => {
    storage.clear();
  }),
  getItem: vi.fn((key: string): string | null => storage.get(key) ?? null),
  key: vi.fn((index: number): string | null => Array.from(storage.keys())[index] ?? null),
  removeItem: vi.fn((key: string): void => {
    storage.delete(key);
  }),
  setItem: vi.fn((key: string, value: string): void => {
    storage.set(key, value);
  }),
};

Object.defineProperty(window, 'localStorage', {
  configurable: true,
  value: localStorageMock,
});

globalThis.fetch = vi.fn();
