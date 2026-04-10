import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { AIQualityDashboard } from '../AIQualityDashboard';

vi.mock('../../../hooks/useAIQuality', () => ({
  useAIQuality: () => ({
    summary: {
      period: { start: Date.now() - 86400000, end: Date.now() },
      totalRequests: 100,
      successfulRequests: 95,
      failedRequests: 5,
      averageConfidence: 0.87,
      averageLatencyMs: 1200,
      cacheHitRate: 0.25,
      fallbackUsageRate: 0.05,
      errorBreakdown: { TIMEOUT: 3, RATE_LIMIT: 2, INVALID_REQUEST: 0, AUTHENTICATION: 0, SERVER_ERROR: 0, NETWORK_ERROR: 0, UNKNOWN: 0 },
      providerDistribution: { openai: 60, anthropic: 30, azure: 5, local: 5 },
    },
    getProviderHealth: vi.fn().mockReturnValue({ healthy: true, score: 0.95 }),
    resetMetrics: vi.fn(),
  }),
}));

describe('AIQualityDashboard simple mock test', () => {
  it('should render Confidence when mock is applied', () => {
    render(<AIQualityDashboard />);
    expect(screen.getByText('Confidence')).toBeDefined();
  });
});
