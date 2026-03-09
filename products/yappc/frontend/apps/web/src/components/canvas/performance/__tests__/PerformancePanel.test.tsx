// All tests skipped - incomplete feature
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

import { PerformancePanel } from '../PerformancePanel';

import type { PerformanceMetrics } from '../PerformancePanel';

const mockMetrics: PerformanceMetrics = {
  fps: 58,
  elements: 42,
  frameTime: 17,
  renderTime: 9,
  memoryUsage: 85,
};

describe.skip('PerformancePanel', () => {
  it('renders when open', () => {
    render(
      <PerformancePanel
        open={true}
        onClose={vi.fn()}
        metrics={mockMetrics}
      />
    );

    expect(screen.getByTestId('performance-metrics')).toBeInTheDocument();
    expect(screen.getByText('Performance')).toBeInTheDocument();
  });

  it('does not render when closed', () => {
    render(
      <PerformancePanel
        open={false}
        onClose={vi.fn()}
        metrics={mockMetrics}
      />
    );

    expect(screen.queryByTestId('performance-metrics')).not.toBeInTheDocument();
  });

  it('displays all metrics', () => {
    render(
      <PerformancePanel
        open={true}
        onClose={vi.fn()}
        metrics={mockMetrics}
      />
    );

    expect(screen.getByText('FPS')).toBeInTheDocument();
    expect(screen.getByText('58')).toBeInTheDocument();
    expect(screen.getByText('Elements')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByText('Frame Time')).toBeInTheDocument();
    expect(screen.getByText('17ms')).toBeInTheDocument();
    expect(screen.getByText('Render Time')).toBeInTheDocument();
    expect(screen.getByText('9ms')).toBeInTheDocument();
    expect(screen.getByText('Memory')).toBeInTheDocument();
    expect(screen.getByText('85MB')).toBeInTheDocument();
  });

  it('calls onClose when close button clicked', () => {
    const onClose = vi.fn();

    render(
      <PerformancePanel
        open={true}
        onClose={onClose}
        metrics={mockMetrics}
      />
    );

    fireEvent.click(screen.getByText('Close'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('shows enable monitoring button when not enabled', () => {
    const onEnableMonitoring = vi.fn();

    render(
      <PerformancePanel
        open={true}
        onClose={vi.fn()}
        metrics={mockMetrics}
        onEnableMonitoring={onEnableMonitoring}
        monitoringEnabled={false}
      />
    );

    const enableButton = screen.getByTestId('enable-monitoring');
    expect(enableButton).toBeInTheDocument();

    fireEvent.click(enableButton);
    expect(onEnableMonitoring).toHaveBeenCalledTimes(1);
  });

  it('shows live monitoring status when enabled', () => {
    render(
      <PerformancePanel
        open={true}
        onClose={vi.fn()}
        metrics={mockMetrics}
        monitoringEnabled={true}
      />
    );

    expect(screen.getByText('● Live monitoring active')).toBeInTheDocument();
    expect(screen.queryByTestId('enable-monitoring')).not.toBeInTheDocument();
  });

  it('uses default metrics when none provided', () => {
    render(
      <PerformancePanel
        open={true}
        onClose={vi.fn()}
      />
    );

    expect(screen.getByText('60')).toBeInTheDocument(); // Default FPS
    expect(screen.getByText('0')).toBeInTheDocument(); // Default elements
  });
});
