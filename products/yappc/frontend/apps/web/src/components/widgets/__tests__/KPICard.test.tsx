import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { KPICard } from '../KPICard';

/**
 * Unit tests for KPICard component.
 *
 * Tests validate:
 * - Metric value display
 * - Trend indicator (up/down/neutral)
 * - Color coding by trend
 * - Percentage changes
 * - Loading states
 * - Icon rendering
 *
 * @doc.type test
 * @doc.purpose Test KPI card component
 * @doc.layer product
 * @doc.pattern Component Test
 */

describe('KPICard', () => {
  /**
   * Verifies KPI displays metric value and label.
   *
   * GIVEN: KPI with value 42 and label "Total Incidents"
   * WHEN: Component renders
   * THEN: Value and label are displayed
   */
  it('should display metric value and label', () => {
    // GIVEN: KPI data
    const kpi = {
      label: 'Total Incidents',
      value: 42,
      trend: 'neutral' as const,
    };

    // WHEN: Render KPI card
    render(<KPICard {...kpi} />);

    // THEN: Value and label are visible
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByText('Total Incidents')).toBeInTheDocument();
  });

  /**
   * Verifies trend indicator shows increase.
   *
   * GIVEN: KPI with upward trend (+15%)
   * WHEN: Component renders
   * THEN: Up arrow and percentage are displayed in green
   */
  it('should show upward trend indicator', () => {
    // GIVEN: Increasing KPI
    const kpi = {
      label: 'Deployments',
      value: 23,
      trend: 'up' as const,
      changePercent: 15,
    };

    // WHEN: Render KPI card
    render(<KPICard {...kpi} />);

    // THEN: Trend indicator is visible
    expect(screen.getByText('+15%')).toBeInTheDocument();
    expect(screen.getByTestId('trend-up-icon')).toBeInTheDocument();
    
    const trendElement = screen.getByText('+15%').parentElement;
    expect(trendElement).toHaveClass('text-green-600');
  });

  /**
   * Verifies trend indicator shows decrease.
   *
   * GIVEN: KPI with downward trend (-8%)
   * WHEN: Component renders
   * THEN: Down arrow and percentage are displayed in red
   */
  it('should show downward trend indicator', () => {
    // GIVEN: Decreasing KPI
    const kpi = {
      label: 'Open Vulnerabilities',
      value: 12,
      trend: 'down' as const,
      changePercent: -8,
    };

    // WHEN: Render KPI card
    render(<KPICard {...kpi} />);

    // THEN: Trend indicator is visible
    expect(screen.getByText('-8%')).toBeInTheDocument();
    expect(screen.getByTestId('trend-down-icon')).toBeInTheDocument();
    
    const trendElement = screen.getByText('-8%').parentElement;
    expect(trendElement).toHaveClass('text-red-600');
  });

  /**
   * Verifies neutral trend has no indicator.
   *
   * GIVEN: KPI with neutral trend
   * WHEN: Component renders
   * THEN: No trend indicator is shown
   */
  it('should not show trend indicator for neutral', () => {
    // GIVEN: Neutral KPI
    const kpi = {
      label: 'Active Users',
      value: 156,
      trend: 'neutral' as const,
    };

    // WHEN: Render KPI card
    render(<KPICard {...kpi} />);

    // THEN: No trend indicator
    expect(screen.queryByTestId('trend-up-icon')).not.toBeInTheDocument();
    expect(screen.queryByTestId('trend-down-icon')).not.toBeInTheDocument();
  });

  /**
   * Verifies loading state shows skeleton.
   *
   * GIVEN: KPI in loading state
   * WHEN: Component renders
   * THEN: Skeleton placeholder is shown
   */
  it('should display loading skeleton', () => {
    // GIVEN: Loading state
    const kpi = {
      label: 'Loading...',
      value: 0,
      isLoading: true,
    };

    // WHEN: Render KPI card
    render(<KPICard {...kpi} />);

    // THEN: Skeleton is visible
    expect(screen.getByTestId('kpi-skeleton')).toBeInTheDocument();
  });

  /**
   * Verifies custom icon rendering.
   *
   * GIVEN: KPI with custom icon
   * WHEN: Component renders
   * THEN: Icon is displayed
   */
  it('should render custom icon', () => {
    // GIVEN: KPI with icon
    const kpi = {
      label: 'Critical Alerts',
      value: 5,
      icon: 'alert-triangle',
    };

    // WHEN: Render KPI card
    render(<KPICard {...kpi} />);

    // THEN: Icon is visible
    expect(screen.getByTestId('kpi-icon-alert-triangle')).toBeInTheDocument();
  });

  /**
   * Verifies large numbers are formatted.
   *
   * GIVEN: KPI with value 1234567
   * WHEN: Component renders
   * THEN: Value is formatted as "1.2M"
   */
  it('should format large numbers', () => {
    // GIVEN: Large value
    const kpi = {
      label: 'Total Events',
      value: 1234567,
      trend: 'neutral' as const,
    };

    // WHEN: Render KPI card
    render(<KPICard {...kpi} />);

    // THEN: Formatted value is shown
    expect(screen.getByText('1.2M')).toBeInTheDocument();
  });

  /**
   * Verifies click handler is called.
   *
   * GIVEN: KPI with click handler
   * WHEN: User clicks card
   * THEN: Handler is invoked
   */
  it('should call click handler when clicked', async () => {
    // GIVEN: KPI with handler
    const handleClick = vi.fn();
    const kpi = {
      label: 'Incidents',
      value: 10,
      onClick: handleClick,
    };

    // WHEN: Render and click
    const { container } = render(<KPICard {...kpi} />);
    const card = container.firstChild as HTMLElement;
    card.click();

    // THEN: Handler is called
    expect(handleClick).toHaveBeenCalledTimes(1);
  });
});
