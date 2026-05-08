/**
 * Manual mock for @sentry/react (optional dependency not installed in this workspace).
 * Tests use vi.mock('@sentry/react', ...) but the module must be resolvable at transform time.
 */
import { vi } from 'vitest';

type MockCallback = (...args: unknown[]) => unknown;
type SentryIntegration = Record<string, never>;

interface SentryScopeMock {
  setExtras: MockCallback;
  setTag: MockCallback;
}

const createMockCallback = (): MockCallback => vi.fn() as MockCallback;

export const init: MockCallback = createMockCallback();
export const captureException: MockCallback = createMockCallback();
export const captureMessage: MockCallback = createMockCallback();
export const withScope = (fn: (scope: SentryScopeMock) => void): void =>
  fn({ setExtras: createMockCallback(), setTag: createMockCallback() });
export const browserTracingIntegration = (): SentryIntegration => ({});
export const replayIntegration = (): SentryIntegration => ({});
export const setUser: MockCallback = createMockCallback();
export const setTag: MockCallback = createMockCallback();
export const setExtra: MockCallback = createMockCallback();
export const addBreadcrumb: MockCallback = createMockCallback();
