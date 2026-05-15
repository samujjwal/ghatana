import { vi } from 'vitest';

Object.defineProperty(globalThis, 'jest', {
  value: vi,
  configurable: true,
});
