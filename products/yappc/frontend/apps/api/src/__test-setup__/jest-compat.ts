/**
 * Jest compatibility shim for vitest.
 * Exposes `jest` as an alias for `vi` so existing tests using
 * jest.fn(), jest.clearAllMocks(), etc. work without modification.
 */
import { vi } from 'vitest';

(globalThis as Record<string, unknown>).jest = vi;
