/**
 * AIQualityDashboard Component Tests
 */

import React from 'react';
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AIQualityDashboard } from '../AIQualityDashboard';

// Mock the useAIQuality hook
const mockResetMetrics = jest.fn();
const mockGetProviderHealth = jest.fn();

jest.mock('../../../hooks/useAIQuality', () => ({
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
      errorBreakdown: {
        TIMEOUT: 3,
        RATE_LIMIT: 2,
        INVALID_REQUEST: 0,
        AUTHENTICATION: 0,
        SERVER_ERROR: 0,
        NETWORK_ERROR: 0,
        UNKNOWN: 0,
      },
      providerDistribution: {
        openai: 60,
        anthropic: 30,
        azure: 5,
        local: 5,
      },
    },
    getProviderHealth: mockGetProviderHealth,
    resetMetrics: mockResetMetrics,
  }),
}));

describe('AIQualityDashboard', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetProviderHealth.mockReturnValue({ healthy: true, score: 0.95 });
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
    expect(mockResetMetrics).toHaveBeenCalled();
  });

  it('should accept className prop', () => {
    const { container } = render(<AIQualityDashboard className="custom-class" />);
    expect(container.firstElementChild?.classList.contains('custom-class')).toBe(true);
  });
});

describe('AIQualityDashboard - Empty State', () => {
  beforeEach(() => {
    jest.resetModules();
  });

  it('should show empty state when no summary', () => {
    // Override mock for this test
    jest.doMock('../../../hooks/useAIQuality', () => ({
      useAIQuality: () => ({
        summary: null,
        getProviderHealth: jest.fn(),
        resetMetrics: jest.fn(),
      }),
    }));

    const { AIQualityDashboard: EmptyDashboard } = require('../AIQualityDashboard');
    render(<EmptyDashboard />);
    expect(screen.getByText(/No AI quality data available/)).toBeDefined();
  });
});
