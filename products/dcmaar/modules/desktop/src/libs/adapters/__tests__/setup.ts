/**
 * Vitest setup file for adapter tests.
 * Provides polyfills and mocks for browser APIs in Node environment.
 */

import { vi } from 'vitest';
import 'fake-indexeddb/auto';

// Mock Tauri runtime for tests
if (typeof window !== 'undefined') {
  (window as any).__TAURI__ = {
    invoke: vi.fn().mockResolvedValue({}),
  };
}
