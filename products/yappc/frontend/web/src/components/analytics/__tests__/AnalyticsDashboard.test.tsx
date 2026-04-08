/**
 * AnalyticsDashboard Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AnalyticsDashboard, type AnalyticsDashboardProps } from '../AnalyticsDashboard';
import type { AnalyticsReport } from '../../../services/analytics/AnalyticsService';

function createReport(overrides: Partial<AnalyticsReport> = {}): AnalyticsReport {
  return {
    period: { start: '2026-01-01', end: '2026-01-31' },
    summary: {
      totalProjects: { current: 12, previous: 10, change: 20, trend: 'up' },
      totalTasks: { current: 87, previous: 90, change: -3.3, trend: 'down' },
      avgHealthScore: { current: 74, previous: 74, change: 0, trend: 'flat' },
      teamUtilisation: { current: 68, previous: 65, change: 4.6, trend: 'up' },
    },
    projectMetrics: [
      { projectId: 'p1', name: 'Alpha', completionRate: 0.85, taskCount: 20, openIssues: 2, avgCycleTime: 3, healthScore: 82 },
      { projectId: 'p2', name: 'Beta', completionRate: 0.4, taskCount: 15, openIssues: 8, avgCycleTime: 7, healthScore: 35 },
    ],
    teamMetrics: [
      { memberId: 'm1', name: 'Alice', tasksCompleted: 14, avgResponseTime: 2, utilisation: 0.75 },
      { memberId: 'm2', name: 'Bob', tasksCompleted: 10, avgResponseTime: 4, utilisation: 0.9 },
    ],
    lifecycleMetrics: [],
    timeSeries: { tasksCompleted: [], healthScore: [] },
    ...overrides,
  };
}

describe('AnalyticsDashboard', () => {
  it('should render header', () => {
    render(<AnalyticsDashboard report={createReport()} bottleneck={null} />);
    expect(screen.getByText('Analytics')).toBeDefined();
  });

  it('should render summary metric cards', () => {
    render(<AnalyticsDashboard report={createReport()} bottleneck={null} />);
    expect(screen.getByText('Projects')).toBeDefined();
    expect(screen.getByText('Tasks')).toBeDefined();
    expect(screen.getByText('Health')).toBeDefined();
    expect(screen.getByText('Utilisation')).toBeDefined();
  });

  it('should render summary values', () => {
    render(<AnalyticsDashboard report={createReport()} bottleneck={null} />);
    expect(screen.getByText('12')).toBeDefined();
    expect(screen.getByText('87')).toBeDefined();
  });

  it('should render change percentages', () => {
    render(<AnalyticsDashboard report={createReport()} bottleneck={null} />);
    expect(screen.getByText('+20%')).toBeDefined();
    expect(screen.getByText('-3.3%')).toBeDefined();
  });

  it('should render bottleneck alert when present', () => {
    render(<AnalyticsDashboard report={createReport()} bottleneck="Generate" />);
    expect(screen.getByText(/Bottleneck detected/)).toBeDefined();
    expect(screen.getByText('Generate')).toBeDefined();
  });

  it('should not render bottleneck alert when null', () => {
    render(<AnalyticsDashboard report={createReport()} bottleneck={null} />);
    expect(screen.queryByText(/Bottleneck detected/)).toBeNull();
  });

  it('should render project rows', () => {
    render(<AnalyticsDashboard report={createReport()} bottleneck={null} />);
    expect(screen.getByText('Alpha')).toBeDefined();
    expect(screen.getByText('Beta')).toBeDefined();
    expect(screen.getByText(/Projects \(2\)/)).toBeDefined();
  });

  it('should call onProjectClick when project row clicked', () => {
    const onClick = vi.fn();
    render(<AnalyticsDashboard report={createReport()} bottleneck={null} onProjectClick={onClick} />);
    fireEvent.click(screen.getByText('Alpha'));
    expect(onClick).toHaveBeenCalledWith('p1');
  });

  it('should render team rows', () => {
    render(<AnalyticsDashboard report={createReport()} bottleneck={null} />);
    expect(screen.getByText('Alice')).toBeDefined();
    expect(screen.getByText('Bob')).toBeDefined();
    expect(screen.getByText(/Team \(2\)/)).toBeDefined();
  });

  it('should render refresh button and call onRefresh', () => {
    const onRefresh = vi.fn();
    render(<AnalyticsDashboard report={createReport()} bottleneck={null} onRefresh={onRefresh} />);
    fireEvent.click(screen.getByText('Refresh'));
    expect(onRefresh).toHaveBeenCalled();
  });

  it('should accept className prop', () => {
    const { container } = render(
      <AnalyticsDashboard report={createReport()} bottleneck={null} className="test-cls" />,
    );
    expect(container.firstElementChild?.classList.contains('test-cls')).toBe(true);
  });

  it('should hide project section when no projects', () => {
    const report = createReport({ projectMetrics: [] });
    render(<AnalyticsDashboard report={report} bottleneck={null} />);
    expect(screen.queryByText(/Projects \(/)).toBeNull();
  });

  it('should hide team section when no members', () => {
    const report = createReport({ teamMetrics: [] });
    render(<AnalyticsDashboard report={report} bottleneck={null} />);
    expect(screen.queryByText(/Team \(/)).toBeNull();
  });
});
