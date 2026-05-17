/**
 * P2-023: Unit tests for GrowthGoalWidget component.
 * 
 * Tests the growth goal widget's behavior based on feature flags
 * and renders appropriate placeholder states.
 *
 * @doc.type test
 * @doc.purpose Unit tests for GrowthGoalWidget (P2-023)
 * @doc.layer test
 */

import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { GrowthGoalWidget } from '../GrowthGoalWidget';

const mockIsFeatureEnabled = vi.fn();

vi.mock('@/lib/feature-flags', () => ({
  FEATURE_FLAGS: {
    DASHBOARD_GROWTH_METRICS: 'dmos.dashboard_growth_metrics',
  },
  isFeatureEnabled: (flag: string): boolean => mockIsFeatureEnabled(flag),
}));

describe('P2-023: GrowthGoalWidget', () => {
  beforeEach(() => {
    mockIsFeatureEnabled.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('should render "Currently unavailable" placeholder when feature flag is disabled', () => {
    mockIsFeatureEnabled.mockReturnValue(false);

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    const widget = screen.getByTestId('growth-goal-widget');
    expect(widget).toBeInTheDocument();
    expect(screen.getByText('Currently unavailable')).toBeInTheDocument();
    expect(screen.getByText('Growth Goals')).toBeInTheDocument();
  });

  it('should render "Metrics loading" placeholder when feature flag is enabled', () => {
    mockIsFeatureEnabled.mockReturnValue(true);

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    const widget = screen.getByTestId('growth-goal-widget');
    expect(widget).toBeInTheDocument();
    expect(screen.getByText('Metrics loading…')).toBeInTheDocument();
    expect(screen.getByText('Growth Goals')).toBeInTheDocument();
  });

  it('should default to disabled state when feature flag is not set', () => {
    mockIsFeatureEnabled.mockReturnValue(false);

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    expect(screen.getByText('Currently unavailable')).toBeInTheDocument();
  });

  it('should expose unavailable state test id when feature is disabled', () => {
    mockIsFeatureEnabled.mockReturnValue(false);

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    expect(screen.getByTestId('growth-goal-unavailable')).toBeInTheDocument();
  });

  it('should render base card classes for disabled state', () => {
    mockIsFeatureEnabled.mockReturnValue(false);

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    const widget = screen.getByTestId('growth-goal-widget');
    expect(widget).toHaveClass('border', 'rounded-lg', 'p-4');
  });

  it('should apply correct CSS classes for enabled state', () => {
    mockIsFeatureEnabled.mockReturnValue(true);

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    const widget = screen.getByTestId('growth-goal-widget');
    expect(widget).toHaveClass('border', 'rounded-lg', 'p-4');
    expect(widget).not.toHaveClass('opacity-60');
  });

  it('should render enabled state when feature flag resolves true', () => {
    mockIsFeatureEnabled.mockReturnValue(true);

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    expect(screen.getByText('Metrics loading…')).toBeInTheDocument();
  });

  it('should render disabled state when feature flag resolves false', () => {
    mockIsFeatureEnabled.mockReturnValue(false);

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    expect(screen.getByText('Currently unavailable')).toBeInTheDocument();
  });
});
