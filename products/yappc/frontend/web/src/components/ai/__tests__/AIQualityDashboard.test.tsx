/**
 * AIQualityDashboard Component Tests
 */

import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';

const mockFns = vi.hoisted(() => ({
  resetMetrics: vi.fn(),
  getProviderHealth: vi.fn().mockReturnValue({ healthy: true, score: 0.95 }),
}));

vi.mock('../../../hooks/useAIQuality', () => ({
  useAIQuality: vi.fn(() => ({
    summary: {
      period: { start: Date.now() - 86400000, end: Date.now() },
      totalRequests: 100,
      successfulRequests: 95,
      failedRequests: 5,
      averageConfidence: 0.87,
      averageLatencyMs: 1200,
      cacheHitRate: 0.25,
      fallbackUsageRate: 0.05,
      errorBreakdown: {
        TIMEOUT: 3, RATE_LIMIT: 2, INVALID_REQUEST: 0, AUTHENTICATION: 0,
        SERVER_ERROR: 0, NETWORK_ERROR: 0, UNKNOWN: 0,
      },
      providerDistribution: { openai: 60, anthropic: 30, azure: 5, local: 5 },
    },
    getProviderHealth: mockFns.getProviderHealth,
    resetMetrics: mockFns.resetMetrics,
  })),
}));

import { AIQualityDashboard } from '../AIQualityDashboard';
import { useAIQuality } from '../../../hooks/useAIQuality';

describe('AIQualityDashboard', () => {
  beforeEach(() => {
    mockFns.resetMetrics.mockClear();
    mockFns.getProviderHealth.mockClear();
    mockFns.getProviderHealth.mockReturnValue({ healthy: true, score: 0.95 });
  });

  it('should render header', () => {
    render(<AIQualityDashboard />);
    expect(screen.getByText('AI Quality')).toBeDefined();
  });

  it('should render summary metric cards', () => {
    render(<AIQualityDashboard />);
    expect(screen.getByText('Confidence')).toBeDefined();
    expect(screen.getByText('Avg Latency')).toBeDefined();
    expect(screen.getByText('Cache Hit Rate')).toBeDefined();
    expect(screen.getByText('Fallback Usage')).toBeDefined();
  });

  it('should display metric values', () => {
    render(<AIQualityDashboard />);
    expect(screen.getByText('87%')).toBeDefined();
    expect(screen.getByText('1200ms')).toBeDefined();
    expect(screen.getByText('25%')).toBeDefined();
    expect(screen.getByText('5%')).toBeDefined();
  });

  it('should render provider health section', () => {
    render(<AIQualityDashboard />);
    expect(screen.getByText('Provider Health (Last Hour)')).toBeDefined();
    expect(screen.getByText('OpenAI')).toBeDefined();
    expect(screen.getByText('Anthropic')).toBeDefined();
  });

  it('should show provider request counts', () => {
    render(<AIQualityDashboard />);
    expect(screen.getByText('60 reqs')).toBeDefined();
    expect(screen.getByText('30 reqs')).toBeDefined();
  });

  it('should render error breakdown when there are failed requests', () => {
    render(<AIQualityDashboard />);
    expect(screen.getByText('Error Breakdown (Last 24 Hours)')).toBeDefined();
    expect(screen.getByText('TIMEOUT')).toBeDefined();
    expect(screen.getByText('3')).toBeDefined();
    expect(screen.getByText('RATE_LIMIT')).toBeDefined();
    expect(screen.getByText('2')).toBeDefined();
  });

  it('should call resetMetrics when refresh clicked', () => {
    render(<AIQualityDashboard />);
    const refreshBtn = screen.getAllByRole('button')[0];
    fireEvent.click(refreshBtn);
    expect(mockFns.resetMetrics).toHaveBeenCalled();
  });

  it('should accept className prop', () => {
    const { container } = render(<AIQualityDashboard className="custom-class" />);
    expect(container.firstElementChild?.classList.contains('custom-class')).toBe(true);
  });
});

describe('AIQualityDashboard - Empty State', () => {
  it('should show empty state when no summary', () => {
    vi.mocked(useAIQuality).mockReturnValueOnce({
      summary: null,
      getProviderHealth: vi.fn(),
      resetMetrics: vi.fn(),
    });
    render(<AIQualityDashboard />);
    expect(screen.getByText(/No AI quality data available/)).toBeDefined();
  });
});
