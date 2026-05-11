/**
 * Dashboard Page tests (F1-024).
 *
 * @doc.type test
 * @doc.purpose Unit tests for DashboardPage behavior
 * @doc.layer frontend
 */
import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { DashboardPage } from '@/pages/DashboardPage';
import type { ApprovalRecordResponse } from '@/types/approval';
import type { DashboardSummary } from '@/types/dashboard';

const mockUseApprovalQueue = vi.fn();
const mockUseStrategy = vi.fn();
const mockUseConnectorHealth = vi.fn();
const mockUseDashboardSummary = vi.fn();

vi.mock('@/hooks/useApprovalQueue', () => ({
  useApprovalQueue: (...args: unknown[]) => mockUseApprovalQueue(...args),
}));

vi.mock('@/hooks/useStrategy', () => ({
  useStrategy: (...args: unknown[]) => mockUseStrategy(...args),
}));

vi.mock('@/hooks/useDashboardSummary', () => ({
  useDashboardSummary: (...args: unknown[]) => mockUseDashboardSummary(...args),
}));

vi.mock('@/hooks/useConnectorHealth', () => ({
  useConnectorHealth: (...args: unknown[]) => mockUseConnectorHealth(...args),
}));

vi.mock('@/hooks/useAiActionLog', () => ({
  useAiActionLog: () => ({
    entries: [],
    isLoading: false,
    isError: false,
    error: null,
  }),
}));

vi.mock('@/lib/feature-flags', () => ({
  isFeatureEnabled: () => false,
  FEATURE_FLAGS: {
    DASHBOARD_GROWTH_METRICS: 'dmos.dashboard_growth_metrics',
  },
}));

const PENDING: ApprovalRecordResponse = {
  requestId: 'req-5',
  tenantId: 'tenant-1',
  workspaceId: 'ws-1',
  subjectId: 'c-1',
  requestedBy: 'user-1',
  action: 'campaign-launch',
  targetType: 'CAMPAIGN_LAUNCH',
  targetId: 'c-1',
  description: null,
  riskLevel: 2,
  requiredApproverRole: 'brand-manager',
  status: 'PENDING',
  submittedAt: '2026-01-10T10:00:00Z',
  submittedBy: 'user-1',
  requestedAt: '2026-01-10T10:00:00Z',
  expiresAt: null,
  decidedAt: null,
  decidedBy: null,
  reviewerId: null,
  reviewerNotes: null,
  comment: null,
  snapshotSummary: null,
  validationResultId: null,
  snapshotAt: null,
};

const DASHBOARD_SUMMARY: DashboardSummary = {
  workspaceId: 'ws-1',
  campaignMetrics: {
    totalCampaigns: 3,
    activeCampaigns: 1,
    pausedCampaigns: 1,
    completedCampaigns: 0,
    archivedCampaigns: 0,
  },
  approvalMetrics: {
    pendingApprovals: 0,
    overdueApprovals: 0,
    approvalsToday: 0,
    approvalsThisWeek: 0,
  },
  budgetMetrics: {
    totalBudget: 1000,
    spentBudget: 600,
    remainingBudget: 400,
    pacingPercentage: 0.6,
    onTrack: true,
  },
  leadMetrics: {
    totalLeads: 0,
    qualifiedLeads: 0,
    conversionRate: 0,
    leadsToday: 0,
    leadsThisWeek: 0,
  },
  freshness: {
    lastUpdated: '2026-01-10T12:00:00Z',
    stalenessSeconds: 10,
    status: 'FRESH',
  },
  confidence: 'HIGH',
  metricSource: 'DMOS_BACKEND_SUMMARY',
  formulaVersion: 'dashboard-summary.v1',
  authorizationScope: 'tenant_workspace',
  partialData: false,
};

function buildQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderPage(token: string | null = 'test-token'): void {
  render(
    <QueryClientProvider client={buildQueryClient()}>
      <AuthProvider
        initialToken={token}
        initialWorkspaceId="ws-1"
        initialTenantId="tenant-1"
        initialPrincipalId="user-1"
        initialSessionId="session-1"
        initialRoles={[]}
      >
        <MemoryRouter initialEntries={['/workspaces/ws-1/dashboard']}>
          <Routes>
            <Route path="/login" element={<div data-testid="login-page" />} />
            <Route
              path="/workspaces/:workspaceId/dashboard"
              element={<DashboardPage />}
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseApprovalQueue.mockReturnValue({
      approvals: [],
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    mockUseStrategy.mockReturnValue({
      strategy: null,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    mockUseDashboardSummary.mockReturnValue({
      summary: DASHBOARD_SUMMARY,
      isLoading: false,
      isError: false,
      error: null,
    });
    mockUseConnectorHealth.mockReturnValue({
      connectors: [],
      isLoading: false,
      isError: false,
      error: null,
      source: 'DMOS Health API /health',
      lastUpdated: null,
      unavailableReason: 'No connector bridge health signals are currently available for this workspace.',
    });
  });

  it('redirects unauthenticated user to /login', () => {
    renderPage(null);
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('renders all dashboard widgets', async () => {
    renderPage();
    expect(await screen.findByTestId('approval-widget')).toBeInTheDocument();
    expect(screen.getByTestId('workflow-status-widget')).toBeInTheDocument();
    expect(screen.getByTestId('growth-goal-widget')).toBeInTheDocument();
    expect(screen.getByTestId('risk-compliance-widget')).toBeInTheDocument();
    expect(screen.getByTestId('ai-action-log-widget')).toBeInTheDocument();
  });

  it('shows pending approval alert when there are pending approvals', async () => {
    mockUseApprovalQueue.mockReturnValue({
      approvals: [PENDING],
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    const alert = await screen.findByTestId('dashboard-approval-alert');
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent('1');
  });

  it('does not show alert when no pending approvals', async () => {
    renderPage();
    await screen.findByTestId('approval-widget');
    expect(
      screen.queryByTestId('dashboard-approval-alert'),
    ).not.toBeInTheDocument();
  });

  it('approval widget shows link to queue', async () => {
    renderPage();
    const link = await screen.findByTestId('approval-widget-link');
    expect(link).toHaveAttribute('href', '/workspaces/ws-1/approvals');
  });

  it('shows loading state in approval widget while fetching', () => {
    mockUseApprovalQueue.mockReturnValue({
      approvals: [],
      isLoading: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(
      screen.getByTestId('approval-widget-loading'),
    ).toBeInTheDocument();
  });

  it('shows error in approval widget on failure', async () => {
    mockUseApprovalQueue.mockReturnValue({
      approvals: [],
      isLoading: false,
      isError: true,
      error: new Error('fetch failed'),
      refetch: vi.fn(),
    });
    renderPage();
    expect(
      await screen.findByTestId('approval-widget-error'),
    ).toBeInTheDocument();
  });

  it('dashboard page title is present', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: /dashboard/i })).toBeInTheDocument();
  });

  it('shows connector unavailable message when no connector health signals exist', async () => {
    renderPage();
    expect(await screen.findByTestId('connector-health-unavailable')).toBeInTheDocument();
  });

  it('renders unhealthy connector from health endpoint data', async () => {
    mockUseConnectorHealth.mockReturnValue({
      connectors: [
        {
          name: 'Google Ads',
          status: 'unhealthy',
          detail: 'token expired',
          lastSync: '2026-01-10T12:00:00Z',
        },
      ],
      isLoading: false,
      isError: false,
      error: null,
      source: 'DMOS Health API /health',
      lastUpdated: '2026-01-10T12:00:00Z',
      unavailableReason: undefined,
    });

    renderPage();

    expect(await screen.findByText(/google ads/i)).toBeInTheDocument();
    expect(screen.getByText(/unhealthy/i)).toBeInTheDocument();
    expect(screen.getByText(/token expired/i)).toBeInTheDocument();
  });

  it('renders degraded connector reasons for rate-limit and kill-switch signals', async () => {
    mockUseConnectorHealth.mockReturnValue({
      connectors: [
        {
          name: 'Google Ads',
          status: 'degraded',
          detail: 'rate-limited by provider',
          lastSync: '2026-01-10T12:00:00Z',
        },
        {
          name: 'Google Ads Mutation',
          status: 'degraded',
          detail: 'kill-switch active for launch commands',
          lastSync: '2026-01-10T12:00:00Z',
        },
      ],
      isLoading: false,
      isError: false,
      error: null,
      source: 'DMOS Health API /health',
      lastUpdated: '2026-01-10T12:00:00Z',
      unavailableReason: undefined,
    });

    renderPage();

    expect(await screen.findByText(/rate-limited by provider/i)).toBeInTheDocument();
    expect(screen.getByText(/kill-switch active/i)).toBeInTheDocument();
  });

  it('renders budget values from the backend dashboard summary', async () => {
    renderPage();

    expect(await screen.findByText('$1,000')).toBeInTheDocument();
    expect(screen.getByText('$600')).toBeInTheDocument();
    expect(screen.getByText('$400')).toBeInTheDocument();
    expect(screen.getByText('60.0%')).toBeInTheDocument();
    expect(screen.getAllByText(/source: dmos_backend_summary/i).length).toBeGreaterThan(0);
  });

  it('renders campaign values from the backend dashboard summary', async () => {
    renderPage();

    expect(await screen.findByTestId('campaign-status-widget')).toHaveTextContent('Active');
    expect(screen.getByTestId('campaign-status-widget')).toHaveTextContent('1');
    expect(screen.getByTestId('campaign-status-widget')).toHaveTextContent('Paused');
    expect(screen.getByTestId('campaign-status-widget')).toHaveTextContent('Pending');
  });

  it('shows unavailable campaign and budget states when dashboard summary is absent', async () => {
    mockUseDashboardSummary.mockReturnValue({
      summary: null,
      isLoading: false,
      isError: false,
      error: null,
    });

    renderPage();

    expect(await screen.findByTestId('campaign-status-unavailable')).toBeInTheDocument();
    expect(screen.getByTestId('budget-tracking-unavailable')).toBeInTheDocument();
  });

  it('shows dashboard summary error state when the summary API fails', async () => {
    mockUseDashboardSummary.mockReturnValue({
      summary: null,
      isLoading: false,
      isError: true,
      error: new Error('summary failed'),
    });

    renderPage();

    expect(await screen.findByText(/failed to load campaign status/i)).toBeInTheDocument();
    expect(screen.getByText(/failed to load budget data/i)).toBeInTheDocument();
  });

  it('shows partial dashboard summary state with confidence and authorization scope', async () => {
    mockUseDashboardSummary.mockReturnValue({
      summary: {
        ...DASHBOARD_SUMMARY,
        confidence: 'MEDIUM',
        partialData: true,
      },
      isLoading: false,
      isError: false,
      error: null,
    });

    renderPage();

    expect(await screen.findByTestId('campaign-status-state')).toHaveTextContent(/partial/i);
    expect(screen.getByTestId('budget-tracking-state')).toHaveTextContent(/partial/i);
    expect(screen.getAllByText(/confidence: medium/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/scope: tenant_workspace/i).length).toBeGreaterThan(0);
  });

  it('shows stale dashboard summary state', async () => {
    mockUseDashboardSummary.mockReturnValue({
      summary: {
        ...DASHBOARD_SUMMARY,
        freshness: {
          lastUpdated: '2026-01-10T10:00:00Z',
          stalenessSeconds: 3600,
          status: 'STALE',
        },
      },
      isLoading: false,
      isError: false,
      error: null,
    });

    renderPage();

    expect(await screen.findByTestId('campaign-status-state')).toHaveTextContent(/stale/i);
    expect(screen.getByTestId('budget-tracking-state')).toHaveTextContent(/stale/i);
    expect(screen.getAllByText(/stale data/i).length).toBeGreaterThan(0);
  });
});
