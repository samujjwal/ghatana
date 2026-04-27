/**
 * Tests for HealthPanel and IncidentsPanel (observe/ components)
 */
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { HealthPanel, type HealthMetric, type SLOStatus, type ServiceHealth } from '../HealthPanel';
import { IncidentsPanel, type Incident } from '../IncidentsPanel';

// ─── Test Fixtures ────────────────────────────────────────────────────────────

const makeMetric = (overrides: Partial<HealthMetric> = {}): HealthMetric => ({
  id: 'm1',
  name: 'Response Time',
  value: 120,
  unit: 'ms',
  status: 'healthy',
  ...overrides,
});

const makeSLO = (overrides: Partial<SLOStatus> = {}): SLOStatus => ({
  id: 's1',
  name: 'Availability SLO',
  target: 99.9,
  current: 99.95,
  status: 'met',
  period: '30d',
  ...overrides,
});

const makeService = (overrides: Partial<ServiceHealth> = {}): ServiceHealth => ({
  id: 'svc1',
  name: 'API Service',
  status: 'healthy',
  uptime: '99.9%',
  lastChecked: new Date().toISOString(),
  ...overrides,
});

const makeIncident = (overrides: Partial<Incident> = {}): Incident => ({
  id: 'inc1',
  title: 'API outage',
  severity: 'high',
  status: 'open',
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  events: [],
  ...overrides,
});

// ─── HealthPanel ──────────────────────────────────────────────────────────────

describe('HealthPanel', () => {
  const defaultProps = {
    metrics: [makeMetric()],
    slos: [makeSLO()],
    services: [makeService()],
    onRefresh: vi.fn().mockResolvedValue(undefined),
  };

  it('renders "System Health" header', () => {
    render(<HealthPanel {...defaultProps} />);
    expect(screen.getByText('System Health')).toBeTruthy();
  });

  it('shows services up count', () => {
    render(<HealthPanel {...defaultProps} />);
    // '1/1' appears for both services and SLOs — just check one exists
    const stats = screen.getAllByText('1/1');
    expect(stats.length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Services Up')).toBeTruthy();
  });

  it('shows SLOs Met count', () => {
    render(<HealthPanel {...defaultProps} />);
    expect(screen.getByText('SLOs Met')).toBeTruthy();
  });

  it('shows Critical Alerts count', () => {
    render(<HealthPanel {...defaultProps} />);
    expect(screen.getByText('Critical Alerts')).toBeTruthy();
    // No critical metrics → shows '0'
    expect(screen.getByText('0')).toBeTruthy();
  });

  it('shows healthy overall status when all services healthy', () => {
    render(<HealthPanel {...defaultProps} />);
    expect(screen.getByText('healthy')).toBeTruthy();
  });

  it('shows critical overall status when any service is down', () => {
    render(
      <HealthPanel
        {...defaultProps}
        services={[makeService({ status: 'down' })]}
      />
    );
    expect(screen.getByText('critical')).toBeTruthy();
  });

  it('shows warning overall status when a service is degraded', () => {
    render(
      <HealthPanel
        {...defaultProps}
        services={[makeService({ status: 'degraded' })]}
      />
    );
    expect(screen.getByText('warning')).toBeTruthy();
  });

  it('shows lastUpdated time when provided', () => {
    const isoTime = new Date(2024, 0, 1, 14, 30, 0).toISOString();
    render(<HealthPanel {...defaultProps} lastUpdated={isoTime} />);
    expect(screen.getByText(/Updated/)).toBeTruthy();
  });

  it('shows tab buttons for navigation', () => {
    render(<HealthPanel {...defaultProps} />);
    expect(screen.getByText('Metrics')).toBeTruthy();
    // SLOs tab includes count — use getAllByText since SLOs also appear in stats label
    expect(screen.getAllByText(/SLOs/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/Services/).length).toBeGreaterThanOrEqual(1);
  });

  it('calls onRefresh when refresh button clicked', async () => {
    const onRefresh = vi.fn().mockResolvedValue(undefined);
    render(<HealthPanel {...defaultProps} onRefresh={onRefresh} />);
    // Refresh button is the first button in the header
    const buttons = screen.getAllByRole('button');
    fireEvent.click(buttons[0]);
    await waitFor(() => {
      expect(onRefresh).toHaveBeenCalledOnce();
    });
  });

  it('renders metric name in overview tab', () => {
    render(
      <HealthPanel
        {...defaultProps}
        metrics={[makeMetric({ name: 'Latency P95' })]}
      />
    );
    expect(screen.getByText('Latency P95')).toBeTruthy();
  });

  it('renders multiple services up count correctly', () => {
    render(
      <HealthPanel
        {...defaultProps}
        services={[
          makeService({ id: '1', status: 'healthy' }),
          makeService({ id: '2', status: 'degraded' }),
          makeService({ id: '3', status: 'healthy' }),
        ]}
      />
    );
    expect(screen.getByText('2/3')).toBeTruthy();
  });
});

// ─── IncidentsPanel ───────────────────────────────────────────────────────────

describe('IncidentsPanel', () => {
  const defaultProps = {
    incidents: [makeIncident()],
  };

  it('renders "Incidents" heading', () => {
    render(<IncidentsPanel {...defaultProps} />);
    expect(screen.getByText('Incidents')).toBeTruthy();
  });

  it('shows open incident count', () => {
    render(<IncidentsPanel incidents={[makeIncident({ status: 'open' })]} />);
    expect(screen.getByText(/1 open/)).toBeTruthy();
  });

  it('shows "0 open" when all incidents resolved', () => {
    render(
      <IncidentsPanel
        incidents={[makeIncident({ status: 'resolved' })]}
      />
    );
    expect(screen.getByText(/0 open/)).toBeTruthy();
  });

  it('shows critical count when critical open incidents exist', () => {
    render(
      <IncidentsPanel
        incidents={[makeIncident({ severity: 'critical', status: 'open' })]}
      />
    );
    expect(screen.getByText(/1 critical/)).toBeTruthy();
  });

  it('renders New Incident button when onCreateIncident provided', () => {
    const onCreateIncident = vi.fn();
    render(
      <IncidentsPanel
        incidents={[]}
        onCreateIncident={onCreateIncident}
      />
    );
    expect(screen.getByText('New Incident')).toBeTruthy();
  });

  it('calls onCreateIncident when New Incident button clicked', () => {
    const onCreateIncident = vi.fn();
    render(
      <IncidentsPanel
        incidents={[]}
        onCreateIncident={onCreateIncident}
      />
    );
    fireEvent.click(screen.getByText('New Incident'));
    expect(onCreateIncident).toHaveBeenCalledOnce();
  });

  it('does not render New Incident button without onCreateIncident', () => {
    render(<IncidentsPanel incidents={[]} />);
    expect(screen.queryByText('New Incident')).toBeNull();
  });

  it('renders incident title in list', () => {
    render(
      <IncidentsPanel
        incidents={[makeIncident({ title: 'Database crash' })]}
      />
    );
    expect(screen.getByText('Database crash')).toBeTruthy();
  });

  it('renders filter buttons (All, Open, Resolved)', () => {
    render(<IncidentsPanel incidents={[]} />);
    expect(screen.getByText('All')).toBeTruthy();
    // 'Open' appears in the filter button text (no badge when 0 open)
    const openButtons = screen.getAllByText('Open');
    expect(openButtons.length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Resolved')).toBeTruthy();
  });

  it('filters to show only resolved incidents when Resolved clicked', () => {
    render(
      <IncidentsPanel
        incidents={[
          makeIncident({ id: 'i1', title: 'Open Incident', status: 'open' }),
          makeIncident({ id: 'i2', title: 'Resolved Incident', status: 'resolved' }),
        ]}
      />
    );
    // 'Resolved' appears in the filter button AND in the incident status badge
    // Click the first occurrence which is the filter button
    fireEvent.click(screen.getAllByText('Resolved')[0]);
    expect(screen.queryByText('Open Incident')).toBeNull();
    expect(screen.getByText('Resolved Incident')).toBeTruthy();
  });

  it('filters to show only open incidents when Open clicked', () => {
    render(
      <IncidentsPanel
        incidents={[
          makeIncident({ id: 'i1', title: 'Open Incident', status: 'open' }),
          makeIncident({ id: 'i2', title: 'Resolved Incident', status: 'resolved' }),
        ]}
      />
    );
    // Open filter is first button that says Open
    const openFilterBtn = screen.getAllByText('Open')[0];
    fireEvent.click(openFilterBtn);
    expect(screen.queryByText('Resolved Incident')).toBeNull();
    expect(screen.getByText('Open Incident')).toBeTruthy();
  });

  it('accepts isLoading prop without crashing', () => {
    // isLoading prop is declared but visual state unchanged — should not crash
    render(<IncidentsPanel incidents={[]} isLoading />);
    expect(screen.getByText('Incidents')).toBeTruthy();
  });

  it('shows empty state when no incidents in filter', () => {
    render(<IncidentsPanel incidents={[]} />);
    expect(screen.getByText('No incidents to show')).toBeTruthy();
  });
});
