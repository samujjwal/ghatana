import { render, screen, waitFor } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ChartWidget } from '../ChartWidget';

/**
 * Unit tests for ChartWidget component.
 *
 * Tests validate:
 * - Chart rendering (line, bar, area)
 * - Data visualization
 * - Tooltip interactions
 * - Legend display
 * - Responsive sizing
 * - Empty state handling
 * - Loading states
 *
 * @doc.type test
 * @doc.purpose Test chart widget component
 * @doc.layer product
 * @doc.pattern Component Test
 */

describe('ChartWidget', () => {
  const user = userEvent.setup();

  const mockLineChartData = [
    { date: '2024-01-01', incidents: 12, vulnerabilities: 8 },
    { date: '2024-01-02', incidents: 15, vulnerabilities: 10 },
    { date: '2024-01-03', incidents: 10, vulnerabilities: 5 },
  ];

  const mockBarChartData = [
    { category: 'Critical', count: 5 },
    { category: 'High', count: 12 },
    { category: 'Medium', count: 23 },
    { category: 'Low', count: 8 },
  ];

  /**
   * Verifies line chart renders with data.
   *
   * GIVEN: Line chart data with 3 data points
   * WHEN: Component renders
   * THEN: Chart is displayed with correct series
   */
  it('should render line chart with data', async () => {
    // GIVEN: Line chart config
    const config = {
      type: 'line' as const,
      title: 'Incidents Over Time',
      data: mockLineChartData,
      xKey: 'date',
      yKeys: ['incidents', 'vulnerabilities'],
    };

    // WHEN: Render chart
    render(<ChartWidget {...config} />);

    // THEN: Chart is visible
    await waitFor(() => {
      expect(screen.getByText('Incidents Over Time')).toBeInTheDocument();
      expect(screen.getByTestId('recharts-line-chart')).toBeInTheDocument();
    });
  });

  /**
   * Verifies bar chart renders with categories.
   *
   * GIVEN: Bar chart data with 4 categories
   * WHEN: Component renders
   * THEN: Bars are displayed for each category
   */
  it('should render bar chart with categories', async () => {
    // GIVEN: Bar chart config
    const config = {
      type: 'bar' as const,
      title: 'Vulnerabilities by Severity',
      data: mockBarChartData,
      xKey: 'category',
      yKeys: ['count'],
    };

    // WHEN: Render chart
    render(<ChartWidget {...config} />);

    // THEN: Chart is visible
    await waitFor(() => {
      expect(screen.getByText('Vulnerabilities by Severity')).toBeInTheDocument();
      expect(screen.getByTestId('recharts-bar-chart')).toBeInTheDocument();
    });
  });

  /**
   * Verifies tooltip shows on hover.
   *
   * GIVEN: Chart with data
   * WHEN: User hovers over data point
   * THEN: Tooltip displays values
   */
  it('should show tooltip on hover', async () => {
    // GIVEN: Line chart
    const config = {
      type: 'line' as const,
      title: 'Incidents',
      data: mockLineChartData,
      xKey: 'date',
      yKeys: ['incidents'],
    };

    // WHEN: Render and hover
    render(<ChartWidget {...config} />);

    const dataPoint = screen.getByTestId('chart-data-point-0');
    await user.hover(dataPoint);

    // THEN: Tooltip appears
    await waitFor(() => {
      expect(screen.getByTestId('recharts-tooltip')).toBeInTheDocument();
      expect(screen.getByText('12')).toBeInTheDocument();
    });
  });

  /**
   * Verifies legend is displayed.
   *
   * GIVEN: Chart with multiple series
   * WHEN: Component renders
   * THEN: Legend shows all series labels
   */
  it('should display legend for multiple series', () => {
    // GIVEN: Multi-series chart
    const config = {
      type: 'line' as const,
      title: 'Security Metrics',
      data: mockLineChartData,
      xKey: 'date',
      yKeys: ['incidents', 'vulnerabilities'],
      showLegend: true,
    };

    // WHEN: Render chart
    render(<ChartWidget {...config} />);

    // THEN: Legend is visible
    expect(screen.getByText('incidents')).toBeInTheDocument();
    expect(screen.getByText('vulnerabilities')).toBeInTheDocument();
  });

  /**
   * Verifies empty state when no data.
   *
   * GIVEN: Chart with empty data array
   * WHEN: Component renders
   * THEN: Empty state message is shown
   */
  it('should show empty state when no data', () => {
    // GIVEN: Empty data
    const config = {
      type: 'line' as const,
      title: 'No Data Chart',
      data: [],
      xKey: 'date',
      yKeys: ['value'],
    };

    // WHEN: Render chart
    render(<ChartWidget {...config} />);

    // THEN: Empty state is visible
    expect(screen.getByText(/no data available/i)).toBeInTheDocument();
  });

  /**
   * Verifies loading skeleton.
   *
   * GIVEN: Chart in loading state
   * WHEN: Component renders
   * THEN: Skeleton placeholder is shown
   */
  it('should display loading skeleton', () => {
    // GIVEN: Loading state
    const config = {
      type: 'line' as const,
      title: 'Loading Chart',
      data: [],
      xKey: 'date',
      yKeys: ['value'],
      isLoading: true,
    };

    // WHEN: Render chart
    render(<ChartWidget {...config} />);

    // THEN: Skeleton is visible
    expect(screen.getByTestId('chart-skeleton')).toBeInTheDocument();
  });

  /**
   * Verifies responsive sizing.
   *
   * GIVEN: Chart with custom dimensions
   * WHEN: Component renders
   * THEN: Chart respects size constraints
   */
  it('should respect custom dimensions', () => {
    // GIVEN: Chart with size
    const config = {
      type: 'line' as const,
      title: 'Sized Chart',
      data: mockLineChartData,
      xKey: 'date',
      yKeys: ['incidents'],
      width: 600,
      height: 400,
    };

    // WHEN: Render chart
    const { container } = render(<ChartWidget {...config} />);

    // THEN: Chart has correct size
    const chartContainer = container.querySelector('.recharts-responsive-container');
    expect(chartContainer).toHaveStyle({ width: '600px', height: '400px' });
  });

  /**
   * Verifies area chart variant.
   *
   * GIVEN: Area chart config
   * WHEN: Component renders
   * THEN: Area chart is displayed
   */
  it('should render area chart variant', async () => {
    // GIVEN: Area chart
    const config = {
      type: 'area' as const,
      title: 'Deployment Frequency',
      data: mockLineChartData,
      xKey: 'date',
      yKeys: ['incidents'],
    };

    // WHEN: Render chart
    render(<ChartWidget {...config} />);

    // THEN: Area chart is visible
    await waitFor(() => {
      expect(screen.getByTestId('recharts-area-chart')).toBeInTheDocument();
    });
  });

  /**
   * Verifies custom colors.
   *
   * GIVEN: Chart with custom color scheme
   * WHEN: Component renders
   * THEN: Series use specified colors
   */
  it('should apply custom colors to series', () => {
    // GIVEN: Chart with colors
    const config = {
      type: 'line' as const,
      title: 'Custom Colors',
      data: mockLineChartData,
      xKey: 'date',
      yKeys: ['incidents', 'vulnerabilities'],
      colors: ['#FF0000', '#00FF00'],
    };

    // WHEN: Render chart
    const { container } = render(<ChartWidget {...config} />);

    // THEN: Colors are applied
    const lines = container.querySelectorAll('.recharts-line-curve');
    expect(lines[0]).toHaveAttribute('stroke', '#FF0000');
    expect(lines[1]).toHaveAttribute('stroke', '#00FF00');
  });
});
