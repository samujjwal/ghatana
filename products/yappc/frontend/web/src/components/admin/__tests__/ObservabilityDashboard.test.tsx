/**
 * ObservabilityDashboard unit tests
 *
 * @doc.type test
 * @doc.purpose Verify dashboard renders metrics, statuses, links, and edge cases
 * @doc.layer product
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import {
  ObservabilityDashboard,
  HealthMetric,
  ObservabilityDashboardProps,
  ReleaseGateEvidence,
} from '../ObservabilityDashboard';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeMetric(overrides: Partial<HealthMetric> = {}): HealthMetric {
  return {
    id: 'api-latency',
    label: 'API Latency (p95)',
    value: '24ms',
    status: 'healthy',
    refreshedAt: new Date().toISOString(),
    ...overrides,
  };
}

function renderDashboard(props: Partial<ObservabilityDashboardProps> = {}) {
  return render(
    <ObservabilityDashboard
      metrics={[]}
      {...props}
    />
  );
}

function makeReleaseGate(overrides: Partial<ReleaseGateEvidence> = {}): ReleaseGateEvidence {
  return {
    id: 'product-slo-budgets',
    label: 'Product SLO budgets',
    category: 'SLO',
    status: 'healthy',
    evidenceHref: '/release-evidence/product-slo-budgets.json',
    refreshedAt: new Date().toISOString(),
    summary: 'All YAPPC SLO budgets passed',
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ObservabilityDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the dashboard heading', () => {
    renderDashboard({ metrics: [makeMetric()] });
    expect(screen.getByText('System Health')).toBeInTheDocument();
  });

  it('renders a metric card for each metric', () => {
    const metrics: HealthMetric[] = [
      makeMetric({ id: 'm1', label: 'API Latency (p95)', value: '24ms' }),
      makeMetric({ id: 'm2', label: 'Agent Success Rate', value: '98.7%', status: 'healthy' }),
      makeMetric({ id: 'm3', label: 'Active Agent Runs', value: '12', status: 'healthy' }),
    ];
    renderDashboard({ metrics });
    expect(screen.getByText('API Latency (p95)')).toBeInTheDocument();
    expect(screen.getByText('Agent Success Rate')).toBeInTheDocument();
    expect(screen.getByText('Active Agent Runs')).toBeInTheDocument();
  });

  it('displays metric values', () => {
    renderDashboard({ metrics: [makeMetric({ value: '42ms' })] });
    expect(screen.getByText('42ms')).toBeInTheDocument();
  });

  it('shows Healthy chip for healthy metrics', () => {
    renderDashboard({ metrics: [makeMetric({ status: 'healthy' })] });
    // Multiple Healthy chips may appear (overall + card)
    expect(screen.getAllByText('Healthy').length).toBeGreaterThan(0);
  });

  it('shows Degraded chip for degraded metrics', () => {
    renderDashboard({ metrics: [makeMetric({ status: 'degraded' })] });
    expect(screen.getAllByText('Degraded').length).toBeGreaterThan(0);
  });

  it('shows Down chip for down metrics', () => {
    renderDashboard({ metrics: [makeMetric({ status: 'down' })] });
    expect(screen.getAllByText('Down').length).toBeGreaterThan(0);
  });

  it('shows overall status as Down when any metric is down', () => {
    const metrics: HealthMetric[] = [
      makeMetric({ status: 'healthy' }),
      makeMetric({ id: 'm2', status: 'down' }),
    ];
    renderDashboard({ metrics });
    // Overall status chip should be Down
    expect(screen.getAllByText('Down').length).toBeGreaterThan(0);
  });

  it('renders the loading skeleton when isLoading is true', () => {
    const { container } = renderDashboard({ metrics: [], isLoading: true });
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('does not render metric cards when loading', () => {
    renderDashboard({
      metrics: [makeMetric({ label: 'API Latency (p95)' })],
      isLoading: true,
    });
    expect(screen.queryByText('API Latency (p95)')).not.toBeInTheDocument();
  });

  it('renders an error alert when error prop is set', () => {
    renderDashboard({ metrics: [], error: 'Metrics endpoint unreachable' });
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/metrics endpoint unreachable/i)).toBeInTheDocument();
  });

  it('shows empty state when no metrics and not loading', () => {
    renderDashboard({ metrics: [] });
    expect(
      screen.getByText(/no metrics available/i)
    ).toBeInTheDocument();
  });

  it('renders monitoring stack links', () => {
    renderDashboard({ metrics: [] });
    expect(screen.getByRole('link', { name: /open grafana/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /open prometheus/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /open jaeger/i })).toBeInTheDocument();
  });

  it('calls onRefresh when the Refresh button is clicked', () => {
    const onRefresh = vi.fn();
    renderDashboard({ metrics: [], onRefresh });
    fireEvent.click(screen.getByRole('button', { name: /refresh metrics/i }));
    expect(onRefresh).toHaveBeenCalledOnce();
  });

  it('disables the Refresh button while loading', () => {
    renderDashboard({ metrics: [], isLoading: true, onRefresh: vi.fn() });
    expect(screen.getByRole('button', { name: /refresh metrics/i })).toBeDisabled();
  });

  it('displays previous value when provided', () => {
    renderDashboard({
      metrics: [makeMetric({ previousValue: '18ms' })],
    });
    expect(screen.getByText(/previous: 18ms/i)).toBeInTheDocument();
  });

  it('renders release gate evidence cards', () => {
    renderDashboard({
      metrics: [makeMetric()],
      releaseGates: [
        makeReleaseGate(),
        makeReleaseGate({
          id: 'openapi-breaking-changes',
          label: 'OpenAPI breaking changes',
          category: 'API',
          status: 'degraded',
          summary: 'One route requires review',
        }),
      ],
    });

    expect(screen.getByText('Release Gate Evidence')).toBeInTheDocument();
    expect(screen.getByText('Product SLO budgets')).toBeInTheDocument();
    expect(screen.getByText('OpenAPI breaking changes')).toBeInTheDocument();
    expect(screen.getAllByText('Evidence')).toHaveLength(2);
  });

  it('renders SLO, cost, domain invariant, and OpenAPI evidence cards with categories', () => {
    renderDashboard({
      metrics: [makeMetric()],
      releaseGates: [
        makeReleaseGate({ id: 'product-slo-budgets', label: 'Product SLO budgets', category: 'SLO' }),
        makeReleaseGate({ id: 'product-cost-budgets', label: 'Product cost budgets', category: 'Cost' }),
        makeReleaseGate({ id: 'product-domain-invariants', label: 'Product domain invariants', category: 'Domain' }),
        makeReleaseGate({ id: 'openapi-breaking-changes', label: 'OpenAPI breaking changes', category: 'API' }),
      ],
    });

    expect(screen.getByTestId('release-gate-product-slo-budgets')).toHaveTextContent('SLO');
    expect(screen.getByTestId('release-gate-product-cost-budgets')).toHaveTextContent('Cost');
    expect(screen.getByTestId('release-gate-product-domain-invariants')).toHaveTextContent('Domain');
    expect(screen.getByTestId('release-gate-openapi-breaking-changes')).toHaveTextContent('API');
  });
});
