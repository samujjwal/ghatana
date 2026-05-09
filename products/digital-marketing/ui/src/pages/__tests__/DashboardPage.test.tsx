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
import type { BudgetRecommendation } from '@/types/budget';
import type { Campaign } from '@/types/campaign';

const mockUseApprovalQueue = vi.fn();
const mockUseCampaigns = vi.fn();
const mockUseStrategy = vi.fn();
const mockUseBudgetRecommendation = vi.fn();
const mockUseConnectorHealth = vi.fn();

vi.mock('@/hooks/useApprovalQueue', () => ({
  useApprovalQueue: (...args: unknown[]) => mockUseApprovalQueue(...args),
}));

vi.mock('@/hooks/useCampaigns', () => ({
  useCampaigns: (...args: unknown[]) => mockUseCampaigns(...args),
}));

vi.mock('@/hooks/useStrategy', () => ({
  useStrategy: (...args: unknown[]) => mockUseStrategy(...args),
}));

vi.mock('@/hooks/useBudget', () => ({
  useBudgetRecommendation: (...args: unknown[]) => mockUseBudgetRecommendation(...args),
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

const APPROVED_BUDGET: BudgetRecommendation = {
  recommendationId: 'budget-1',
  workspaceId: 'ws-1',
  strategyId: 'strat-1',
  status: 'APPROVED',
  totalMonthlyCap: 1000,
  changeThresholdPct: 10,
  channelAllocations: [],
  rationale: 'Approved budget',
  assumptions: 'Steady CPC',
  modelVersion: 'v1',
  generatedAt: '2026-01-10T10:00:00Z',
  generatedBy: 'system',
  approvedAt: '2026-01-10T10:30:00Z',
  approvedBy: 'approver-1',
};

const DRAFT_BUDGET: BudgetRecommendation = {
  ...APPROVED_BUDGET,
  recommendationId: 'budget-2',
  status: 'DRAFT',
  approvedAt: null,
  approvedBy: null,
};

const LAUNCHED_CAMPAIGN: Campaign = {
  id: 'cmp-1',
  workspaceId: 'ws-1',
  name: 'Launch A',
  status: 'LAUNCHED',
  type: 'PAID_SEARCH',
  objective: 'LEADS',
  budgetCents: 60000,
  startDate: '2026-01-01',
  endDate: '2026-01-31',
  audience: 'SMB',
  landingPageUrl: null,
  createdBy: 'user-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
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
    mockUseCampaigns.mockReturnValue({
      campaigns: [],
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
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: null,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
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

  it('shows unavailable budget state when no recommendation exists', async () => {
    renderPage();
    expect(await screen.findByTestId('budget-tracking-unavailable')).toBeInTheDocument();
  });

  it('shows draft budget with hidden utilization telemetry', async () => {
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: DRAFT_BUDGET,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPage();

    expect(await screen.findByText('$1,000')).toBeInTheDocument();
    expect(screen.getByTestId('budget-utilization-unavailable')).toBeInTheDocument();
    expect(screen.getByText(/draft recommendation/i)).toBeInTheDocument();
  });

  it('shows approved budget spend and utilization', async () => {
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: APPROVED_BUDGET,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    mockUseCampaigns.mockReturnValue({
      campaigns: [LAUNCHED_CAMPAIGN],
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPage();

    expect(await screen.findByText('$600')).toBeInTheDocument();
    expect(screen.getByText('$400')).toBeInTheDocument();
    expect(screen.getByText('60.0%')).toBeInTheDocument();
  });

  it('shows overspend utilization above 100 percent', async () => {
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: APPROVED_BUDGET,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    mockUseCampaigns.mockReturnValue({
      campaigns: [
        { ...LAUNCHED_CAMPAIGN, id: 'cmp-overspend-1', budgetCents: 80000 },
        { ...LAUNCHED_CAMPAIGN, id: 'cmp-overspend-2', budgetCents: 50000 },
      ],
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPage();

    expect(await screen.findByText('$1,300')).toBeInTheDocument();
    expect(screen.getByText('$-300')).toBeInTheDocument();
    expect(screen.getByText('130.0%')).toBeInTheDocument();
  });

  it('shows budget error state when budget API fails', async () => {
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: null,
      isLoading: false,
      isError: true,
      error: new Error('budget failed'),
      refetch: vi.fn(),
    });

    renderPage();

    expect(await screen.findByText(/failed to load budget data/i)).toBeInTheDocument();
  });
});
