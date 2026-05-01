import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import { MemoryRouter } from 'react-router';
import { SecurityDashboard } from '../SecurityDashboard';
import {
  vulnerabilitiesAtom,
  securityScoreAtom,
  securityAlertsAtom,
} from '../../../state/atoms';

// =============================================================================
// Types (matching component internals)
// =============================================================================

interface SecurityAlert {
  id: string;
  title?: string;
  message?: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  resolvedAt?: string | null;
}

interface Vulnerability {
  id: string;
  title: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  status: 'open' | 'fixed' | 'mitigated' | 'in-progress' | 'resolved';
}

interface SecurityScore {
  overall: number;
  categories: { vulnerabilities: number; compliance: number; access: number };
}

// =============================================================================
// Helpers
// =============================================================================

function makeVuln(overrides: Partial<Vulnerability> = {}): Vulnerability {
  return {
    id: 'vuln-1',
    title: 'SQL Injection',
    severity: 'critical',
    status: 'open',
    ...overrides,
  };
}

function makeAlert(overrides: Partial<SecurityAlert> = {}): SecurityAlert {
  return {
    id: 'alert-1',
    title: 'Unauthenticated endpoint exposed',
    severity: 'high',
    resolvedAt: null,
    ...overrides,
  };
}

function makeScore(overall = 78): SecurityScore {
  return {
    overall,
    categories: { vulnerabilities: 80, compliance: 75, access: 79 },
  };
}

function renderDashboard(
  {
    vulnerabilities = [],
    score = null,
    alerts = [],
    projectId = 'proj-1',
  }: {
    vulnerabilities?: Vulnerability[];
    score?: SecurityScore | null;
    alerts?: SecurityAlert[];
    projectId?: string;
  } = {}
) {
  const store = createStore();
  store.set(vulnerabilitiesAtom, vulnerabilities);
  store.set(securityScoreAtom, score);
  store.set(securityAlertsAtom as never, alerts);

  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[`/project/${projectId}/overview`]}>
        <SecurityDashboard projectId={projectId} />
      </MemoryRouter>
    </Provider>
  );
}

// =============================================================================
// Tests
// =============================================================================

describe('SecurityDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ---------------------------------------------------------------------------
  // Header
  // ---------------------------------------------------------------------------

  it('renders the Security Overview header', () => {
    renderDashboard();
    expect(screen.getByText('Security Overview')).toBeDefined();
  });

  it('renders the "View all" link', () => {
    renderDashboard();
    expect(screen.getByText('View all')).toBeDefined();
  });

  // ---------------------------------------------------------------------------
  // Security score ring
  // ---------------------------------------------------------------------------

  it('displays the security score value', () => {
    renderDashboard({ score: makeScore(85) });
    expect(screen.getByText('85')).toBeDefined();
  });

  it('displays score 0 when securityScore is null', () => {
    renderDashboard({ score: null });
    // Score ring and all severity cells render "0" when empty
    expect(screen.getAllByText('0').length).toBeGreaterThanOrEqual(1);
  });

  it('shows "Security Score" label', () => {
    renderDashboard();
    expect(screen.getByText('Security Score')).toBeDefined();
  });

  // ---------------------------------------------------------------------------
  // Vulnerability severity breakdown
  // ---------------------------------------------------------------------------

  it('shows all four severity sections in the breakdown', () => {
    renderDashboard();
    expect(screen.getByText('critical')).toBeDefined();
    expect(screen.getByText('high')).toBeDefined();
    expect(screen.getByText('medium')).toBeDefined();
    expect(screen.getByText('low')).toBeDefined();
  });

  it('shows count 0 for each severity when no vulnerabilities', () => {
    renderDashboard({ vulnerabilities: [] });
    // At least 4 zeros: one per severity cell (score ring may also render 0)
    const zeros = screen.getAllByText('0');
    expect(zeros.length).toBeGreaterThanOrEqual(4);
  });

  it('shows correct count for critical open vulnerabilities', () => {
    const vulns = [
      makeVuln({ id: 'v1', severity: 'critical', status: 'open' }),
      makeVuln({ id: 'v2', severity: 'critical', status: 'open' }),
    ];
    renderDashboard({ vulnerabilities: vulns });
    // Find the "2" count in the critical cell — there should be at least one
    const twos = screen.getAllByText('2');
    expect(twos.length).toBeGreaterThan(0);
  });

  it('does not count fixed vulnerabilities in severity breakdown', () => {
    const vulns = [makeVuln({ id: 'v1', severity: 'high', status: 'fixed' })];
    renderDashboard({ vulnerabilities: vulns });
    // No cell should show "1" since the vulnerability is fixed (not open)
    expect(screen.queryByText('1')).toBeNull();
    // All four severity cells should still show 0
    const zeros = screen.getAllByText('0');
    expect(zeros.length).toBeGreaterThanOrEqual(4);
  });

  // ---------------------------------------------------------------------------
  // Open Alerts
  // ---------------------------------------------------------------------------

  it('renders the "Open Alerts" section label', () => {
    renderDashboard();
    expect(screen.getByText('Open Alerts')).toBeDefined();
  });

  it('shows "No open alerts" when there are no unresolved alerts', () => {
    renderDashboard({ alerts: [] });
    expect(screen.getByText('No open alerts')).toBeDefined();
  });

  it('renders alert title when an unresolved alert exists', () => {
    const alerts = [makeAlert({ title: 'Exposed admin endpoint' })];
    renderDashboard({ alerts });
    expect(screen.getByText('Exposed admin endpoint')).toBeDefined();
  });

  it('falls back to alert message when title is missing', () => {
    const alerts = [makeAlert({ title: undefined, message: 'Weak TLS config' })];
    renderDashboard({ alerts });
    expect(screen.getByText('Weak TLS config')).toBeDefined();
  });

  it('falls back to "Security alert" when neither title nor message is present', () => {
    const alerts = [makeAlert({ title: undefined, message: undefined })];
    renderDashboard({ alerts });
    expect(screen.getByText('Security alert')).toBeDefined();
  });

  it('does not render resolved alerts', () => {
    const alerts = [
      makeAlert({ id: 'a1', title: 'Already resolved alert', resolvedAt: '2026-05-01T00:00:00Z' }),
    ];
    renderDashboard({ alerts });
    expect(screen.queryByText('Already resolved alert')).toBeNull();
    expect(screen.getByText('No open alerts')).toBeDefined();
  });

  it('shows at most 4 alerts', () => {
    const alerts = Array.from({ length: 6 }, (_, i) =>
      makeAlert({ id: `a${i}`, title: `Alert ${i}` })
    );
    renderDashboard({ alerts });
    // Only first 4 should be rendered
    expect(screen.getByText('Alert 0')).toBeDefined();
    expect(screen.getByText('Alert 3')).toBeDefined();
    expect(screen.queryByText('Alert 4')).toBeNull();
  });
});
