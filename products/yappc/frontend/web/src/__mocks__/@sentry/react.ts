/**
 * Manual mock for @sentry/react (optional dependency not installed in this workspace).
 * Tests use vi.mock('@sentry/react', ...) but the module must be resolvable at transform time.
 */
import { vi } from 'vitest';

export const init = vi.fn();
export const captureException = vi.fn();
export const captureMessage = vi.fn();
export const withScope = vi.fn((fn: (scope: unknown) => void) => fn({ setExtras: vi.fn(), setTag: vi.fn() }));
export const browserTracingIntegration = vi.fn(() => ({}));
export const replayIntegration = vi.fn(() => ({}));
export const setUser = vi.fn();
export const setTag = vi.fn();
export const setExtra = vi.fn();
export const addBreadcrumb = vi.fn();
