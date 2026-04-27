import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { RiskAlerts } from '../RiskAlerts';
import type { RiskAlert } from '../../../clients/dashboard';

const makeAlert = (
  id: string,
  overrides: Partial<RiskAlert> = {}
): RiskAlert => ({
  id,
  title: `Risk ${id}`,
  description: `Risk description for ${id}`,
  severity: 'high',
  category: 'security',
  status: 'open',
  projectId: 'proj-1',
  affectedComponents: ['auth-service'],
  createdAt: '2026-04-01T10:00:00Z',
  updatedAt: '2026-04-01T10:00:00Z',
  ...overrides,
});

describe('RiskAlerts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders heading', () => {
    render(<RiskAlerts alerts={[]} />);
    expect(screen.getByText('Risk Alerts')).toBeInTheDocument();
  });

  it('renders empty state when no active alerts', () => {
    render(<RiskAlerts alerts={[]} />);
    expect(screen.getByText(/No active risk alerts/i)).toBeInTheDocument();
  });

  it('renders active alerts', () => {
    const alerts = [makeAlert('r1'), makeAlert('r2')];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('Risk r1')).toBeInTheDocument();
    expect(screen.getByText('Risk r2')).toBeInTheDocument();
  });

  it('shows count chip with active alert count', () => {
    const alerts = [makeAlert('r1'), makeAlert('r2')];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('filters out mitigated alerts', () => {
    const alerts = [
      makeAlert('r1', { status: 'mitigated' }),
      makeAlert('r2', { status: 'open' }),
    ];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('Risk r2')).toBeInTheDocument();
    expect(screen.queryByText('Risk r1')).not.toBeInTheDocument();
  });

  it('filters out ignored alerts', () => {
    const alerts = [
      makeAlert('r1', { status: 'ignored' }),
      makeAlert('r2', { status: 'open' }),
    ];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('Risk r2')).toBeInTheDocument();
    expect(screen.queryByText('Risk r1')).not.toBeInTheDocument();
  });

  it('filters by projectId when provided', () => {
    const alerts = [
      makeAlert('r1', { projectId: 'proj-1' }),
      makeAlert('r2', { projectId: 'proj-2' }),
    ];
    render(<RiskAlerts alerts={alerts} projectId="proj-1" />);
    expect(screen.getByText('Risk r1')).toBeInTheDocument();
    expect(screen.queryByText('Risk r2')).not.toBeInTheDocument();
  });

  it('calls onViewAll when View all button clicked', () => {
    const onViewAll = vi.fn();
    const alerts = [makeAlert('r1')];
    render(<RiskAlerts alerts={alerts} onViewAll={onViewAll} />);
    const buttons = screen.getAllByText(/View all/i);
    fireEvent.click(buttons[0]);
    expect(onViewAll).toHaveBeenCalledTimes(1);
  });

  it('shows checkbox when actions provided', () => {
    const alerts = [makeAlert('r1')];
    render(<RiskAlerts alerts={alerts} onEscalate={vi.fn()} />);
    expect(screen.getByRole('checkbox')).toBeInTheDocument();
  });

  it('does not show checkbox when no actions', () => {
    const alerts = [makeAlert('r1')];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('toggles checkbox selection', () => {
    const alerts = [makeAlert('r1')];
    render(<RiskAlerts alerts={alerts} onEscalate={vi.fn()} />);
    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).not.toBeChecked();
    fireEvent.click(checkbox);
    expect(checkbox).toBeChecked();
  });

  it('renders alert description', () => {
    const alerts = [makeAlert('r1')];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('Risk description for r1')).toBeInTheDocument();
  });

  it('renders severity badge in uppercase', () => {
    const alerts = [makeAlert('r1', { severity: 'critical' })];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
  });

  it('renders status badge for open alert', () => {
    const alerts = [makeAlert('r1', { status: 'open' })];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('Open')).toBeInTheDocument();
  });

  it('renders status badge for escalated alert', () => {
    const alerts = [makeAlert('r1', { status: 'escalated' })];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('Escalated')).toBeInTheDocument();
  });

  it('renders multiple severity types in uppercase', () => {
    const alerts = [
      makeAlert('r1', { severity: 'low' }),
      makeAlert('r2', { severity: 'medium' }),
    ];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('LOW')).toBeInTheDocument();
    expect(screen.getByText('MEDIUM')).toBeInTheDocument();
  });

  it('shows escalate button when onEscalate provided', () => {
    const alerts = [makeAlert('r1')];
    render(<RiskAlerts alerts={alerts} onEscalate={vi.fn()} />);
    expect(screen.getByTitle('Escalate')).toBeInTheDocument();
  });

  it('calls onEscalate when escalate button clicked', async () => {
    const onEscalate = vi.fn().mockResolvedValue(undefined);
    const alerts = [makeAlert('r1')];
    render(<RiskAlerts alerts={alerts} onEscalate={onEscalate} />);
    fireEvent.click(screen.getByTitle('Escalate'));
    expect(onEscalate).toHaveBeenCalledWith('r1', 'security-team', 'Escalating for review');
  });

  it('renders dueDate when present', () => {
    const alerts = [makeAlert('r1', { dueDate: '2026-05-01T00:00:00Z' })];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText(/Due:/i)).toBeInTheDocument();
  });

  it('renders affected component count', () => {
    const alerts = [makeAlert('r1', { affectedComponents: ['auth-service', 'api-gateway'] })];
    render(<RiskAlerts alerts={alerts} />);
    expect(screen.getByText('2 components affected')).toBeInTheDocument();
  });
});
