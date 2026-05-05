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

describe('P2-023: GrowthGoalWidget', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    // Reset environment before each test
    vi.resetModules();
  });

  afterEach(() => {
    // Restore environment after each test
    process.env = originalEnv;
  });

  it('should render "Coming soon" placeholder when feature flag is disabled', () => {
    // Arrange: Disable the feature flag
    process.env.DMOS_DASHBOARD_GROWTH_METRICS_ENABLED = 'false';

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    const widget = screen.getByTestId('growth-goal-widget');
    expect(widget).toBeInTheDocument();
    expect(widget).toHaveClass('opacity-60');
    expect(screen.getByText('Coming soon')).toBeInTheDocument();
    expect(screen.getByText('Growth Goals')).toBeInTheDocument();
  });

  it('should render "Metrics loading" placeholder when feature flag is enabled', () => {
    // Arrange: Enable the feature flag
    process.env.DMOS_DASHBOARD_GROWTH_METRICS_ENABLED = 'true';

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    const widget = screen.getByTestId('growth-goal-widget');
    expect(widget).toBeInTheDocument();
    expect(widget).not.toHaveClass('opacity-60');
    expect(screen.getByText('Metrics loading…')).toBeInTheDocument();
    expect(screen.getByText('Growth Goals')).toBeInTheDocument();
  });

  it('should default to disabled state when feature flag is not set', () => {
    // Arrange: Feature flag not set (default behavior)
    delete process.env.DMOS_DASHBOARD_GROWTH_METRICS_ENABLED;

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    expect(screen.getByText('Coming soon')).toBeInTheDocument();
  });

  it('should have proper accessibility attributes', () => {
    // Arrange
    process.env.DMOS_DASHBOARD_GROWTH_METRICS_ENABLED = 'false';

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    const widget = screen.getByTestId('growth-goal-widget');
    expect(widget).toHaveAttribute('aria-labelledby', 'growth-goal-title');

    const title = screen.getByRole('heading', { level: 2 });
    expect(title).toHaveAttribute('id', 'growth-goal-title');
  });

  it('should apply correct CSS classes for disabled state', () => {
    // Arrange
    process.env.DMOS_DASHBOARD_GROWTH_METRICS_ENABLED = 'false';

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    const widget = screen.getByTestId('growth-goal-widget');
    expect(widget).toHaveClass('border', 'rounded-lg', 'p-4', 'opacity-60');
  });

  it('should apply correct CSS classes for enabled state', () => {
    // Arrange
    process.env.DMOS_DASHBOARD_GROWTH_METRICS_ENABLED = 'true';

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    const widget = screen.getByTestId('growth-goal-widget');
    expect(widget).toHaveClass('border', 'rounded-lg', 'p-4');
    expect(widget).not.toHaveClass('opacity-60');
  });

  it('should handle "1" as true for feature flag', () => {
    // Arrange
    process.env.DMOS_DASHBOARD_GROWTH_METRICS_ENABLED = '1';

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    expect(screen.getByText('Metrics loading…')).toBeInTheDocument();
  });

  it('should handle "0" as false for feature flag', () => {
    // Arrange
    process.env.DMOS_DASHBOARD_GROWTH_METRICS_ENABLED = '0';

    // Act
    render(<GrowthGoalWidget />);

    // Assert
    expect(screen.getByText('Coming soon')).toBeInTheDocument();
  });
});
