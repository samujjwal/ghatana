/**
 * P1-01: Fail-closed tests for analytics AI feature gate.
 *
 * These tests are isolated from analytics.service.test.ts to avoid
 * the pre-existing zod resolution chain triggered by TestWrapper / design-system imports.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ANALYTICS_AI_DISABLED_BOUNDARY_MESSAGE } from '../../lib/runtime-boundaries';
import { UnsupportedRuntimeBoundaryError } from '../../lib/runtime-boundaries';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('../../lib/api/client', () => ({
  apiClient: mockApiClient,
}));

import {
  fetchAnalyticsQuerySuggestions,
  evaluateQueryPolicy,
} from '../../api/analytics.service';
import SessionBootstrap from '../../lib/auth/session';

describe('analytics AI feature gate (fail-closed)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    SessionBootstrap.setTenantId('tenant-a');
    vi.stubEnv('VITE_FEATURE_ANALYTICS_AI', 'false');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('fetchAnalyticsQuerySuggestions throws UnsupportedRuntimeBoundaryError when gate is off', async () => {
    await expect(fetchAnalyticsQuerySuggestions('revenue trends')).rejects.toThrow(
      UnsupportedRuntimeBoundaryError,
    );
    await expect(fetchAnalyticsQuerySuggestions('revenue trends')).rejects.toThrow(
      ANALYTICS_AI_DISABLED_BOUNDARY_MESSAGE,
    );
    expect(mockApiClient.post).not.toHaveBeenCalled();
  });

  it('evaluateQueryPolicy throws UnsupportedRuntimeBoundaryError when gate is off', async () => {
    await expect(evaluateQueryPolicy('SELECT * FROM events')).rejects.toThrow(
      UnsupportedRuntimeBoundaryError,
    );
    await expect(evaluateQueryPolicy('SELECT * FROM events')).rejects.toThrow(
      ANALYTICS_AI_DISABLED_BOUNDARY_MESSAGE,
    );
    expect(mockApiClient.post).not.toHaveBeenCalled();
  });
});
