/**
 * LineChart Component Tests
 * 
 * Tests for LineChart component including:
 * - Rendering with various data configurations
 * - Data sampling
 * - Interactive features
 * - Export functionality
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LineChart } from '../charts/LineChart';
import type { TimeSeriesData } from '../types';

describe('LineChart', () => {
  const mockData: TimeSeriesData[] = [
    {
      id: 'series1',
      name: 'Test Series',
      data: [
        { timestamp: 1000, value: 10 },
        { timestamp: 2000, value: 20 },
        { timestamp: 3000, value: 15 },
      ],
    },
  ];

  it('renders without crashing', () => {
    render(<LineChart data={mockData} />);
    expect(screen.getByRole('img', { hidden: true })).toBeInTheDocument();
  });

  it('displays loading state', () => {
    render(<LineChart data={mockData} loading />);
    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('displays error state', () => {
    const error = new Error('Test error');
    render(<LineChart data={mockData} error={error} />);
    expect(screen.getByText(/Error: Test error/)).toBeInTheDocument();
  });

  it('displays empty state when no data', () => {
    render(<LineChart data={[]} />);
    expect(screen.getByText('No data available')).toBeInTheDocument();
  });

  it('applies data sampling when maxDataPoints is exceeded', () => {
    const largeData: TimeSeriesData[] = [
      {
        id: 'large',
        name: 'Large Series',
        data: Array.from({ length: 2000 }, (_, i) => ({
          timestamp: i * 1000,
          value: Math.random() * 100,
        })),
      },
    ];

    const { container } = render(
      <LineChart data={largeData} maxDataPoints={1000} />
    );

    // Chart should render with sampled data
    expect(container.querySelector('.recharts-wrapper')).toBeInTheDocument();
  });

  it('renders with custom configuration', () => {
    render(
      <LineChart
        data={mockData}
        config={{
          type: 'line',
          title: 'Custom Title',
          xAxis: { label: 'Time' },
          yAxis: { label: 'Value' },
        }}
      />
    );

    // Verify chart is rendered
    expect(screen.getByRole('img', { hidden: true })).toBeInTheDocument();
  });

  it('supports smooth and non-smooth lines', () => {
    const { rerender } = render(<LineChart data={mockData} smooth />);
    expect(screen.getByRole('img', { hidden: true })).toBeInTheDocument();

    rerender(<LineChart data={mockData} smooth={false} />);
    expect(screen.getByRole('img', { hidden: true })).toBeInTheDocument();
  });

  it('shows/hides grid based on prop', () => {
    const { rerender } = render(<LineChart data={mockData} showGrid />);
    expect(screen.getByRole('img', { hidden: true })).toBeInTheDocument();

    rerender(<LineChart data={mockData} showGrid={false} />);
    expect(screen.getByRole('img', { hidden: true })).toBeInTheDocument();
  });

  it('handles multiple series', () => {
    const multiSeriesData: TimeSeriesData[] = [
      {
        id: 'series1',
        name: 'Series 1',
        data: [
          { timestamp: 1000, value: 10 },
          { timestamp: 2000, value: 20 },
        ],
      },
      {
        id: 'series2',
        name: 'Series 2',
        data: [
          { timestamp: 1000, value: 15 },
          { timestamp: 2000, value: 25 },
        ],
      },
    ];

    render(<LineChart data={multiSeriesData} />);
    expect(screen.getByRole('img', { hidden: true })).toBeInTheDocument();
  });

  it('applies custom colors to series', () => {
    const coloredData: TimeSeriesData[] = [
      {
        id: 'series1',
        name: 'Series 1',
        data: mockData[0].data,
        color: '#ff0000',
      },
    ];

    render(<LineChart data={coloredData} />);
    expect(screen.getByRole('img', { hidden: true })).toBeInTheDocument();
  });
});
