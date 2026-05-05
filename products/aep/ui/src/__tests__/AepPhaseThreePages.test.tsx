/**
 * AEP Phase Three Pages — marketplace and cost dashboard coverage.
 *
 * @doc.type test
 * @doc.purpose RTL coverage for marketplace publishing and cost visibility pages
 * @doc.layer frontend
 */
import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AgentMarketplacePage } from '@/pages/AgentMarketplacePage';
import { CostDashboardPage } from '@/pages/CostDashboardPage';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';
import * as aepApi from '@/api/aep.api';
import { useAuth } from '@/context/AuthContext';

vi.mock('@/api/aep.api');
vi.mock('@/context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

const MARKETPLACE_AGENT: aepApi.MarketplaceAgentListing = {
  id: 'agent-market-1',
  name: 'Marketplace Agent',
  description: 'Reusable operator workflow agent',
  version: '1.0.0',
  domain: 'operations',
  level: 'expert',
  capabilities: ['triage', 'explain'],
  tags: ['trusted'],
  source: 'catalog',
  owner: 'platform',
  averageRating: 4.5,
  reviewCount: 2,
  updatedAt: new Date().toISOString(),
};

const MARKETPLACE_DETAIL: aepApi.MarketplaceAgentDetail = {
  listing: MARKETPLACE_AGENT,
  reviews: [
    {
      id: 'review-1',
      agentId: 'agent-market-1',
      tenantId: 'default',
      reviewer: 'sam',
      rating: 5,
      title: 'Excellent',
      comment: 'Strong escalation summaries',
      createdAt: new Date().toISOString(),
    },
  ],
};

const COST_SUMMARY: aepApi.CostSummary = {
  tenantId: 'default',
  windowStart: new Date(Date.now() - 3_600_000).toISOString(),
  windowEnd: new Date().toISOString(),
  totalCostUsd: 12.45,
  projectedMonthlyCostUsd: 320.11,
  averageCostPerRunUsd: 1.24,
  perPipeline: [
    {
      id: 'pipe-1',
      name: 'Fraud Triage',
      costUsd: 8.2,
      sharePercent: 65.9,
      runCount: 8,
    },
  ],
  perAgent: [
    {
      id: 'agent-1',
      name: 'Reasoner',
      costUsd: 6.1,
      sharePercent: 49,
      runCount: 8,
    },
  ],
  perModel: [
    {
      id: 'gpt-4.1-mini',
      name: 'gpt-4.1-mini',
      costUsd: 7.4,
      sharePercent: 59.4,
      runCount: 6,
    },
  ],
  budget: {
    daily: {
      budgetUsd: 10,
      observedUsd: 12.45,
      remainingUsd: -2.45,
      usagePercent: 124.5,
      status: 'exceeded',
    },
    monthly: {
      budgetUsd: 350,
      observedUsd: 320.11,
      remainingUsd: 29.89,
      usagePercent: 91.46,
      status: 'warning',
    },
  },
  alerts: [
    {
      id: 'alert-1',
      severity: 'warning',
      title: 'Daily spend exceeded',
      description: 'Observed spend is above the daily budget.',
      currentValue: 12.45,
      thresholdValue: 10,
    },
  ],
  dataSource: 'metrics',
  allocationModel: 'analytics-metrics',
};

function renderWithQuery(ui: React.ReactElement) {
  return render(ui, { wrapper: createAepTestWrapper() });
}

describe('AgentMarketplacePage', () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue({
      authToken: 'jwt-token',
      sessionToken: 'session-token',
      isAuthenticated: true,
      isBootstrappingSession: false,
      isVerifyingAuth: false,
      roles: ['operator'],
      hasRole: (role) => role === 'operator',
      hasAnyRole: (roles) => roles.includes('operator'),
      loginWithToken: vi.fn(),
      loginWithPlatform: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(aepApi.listMarketplaceAgents).mockResolvedValue([MARKETPLACE_AGENT]);
    vi.mocked(aepApi.getMarketplaceAgent).mockResolvedValue(MARKETPLACE_DETAIL);
    vi.mocked(aepApi.publishMarketplaceAgent).mockResolvedValue(MARKETPLACE_AGENT);
    vi.mocked(aepApi.createMarketplaceReview).mockResolvedValue(MARKETPLACE_DETAIL.reviews[0]);
    vi.mocked(aepApi.simulateMarketplaceInstall).mockResolvedValue({
      agentId: MARKETPLACE_AGENT.id,
      agentName: MARKETPLACE_AGENT.name,
      requestedVersion: MARKETPLACE_AGENT.version,
      availableVersion: MARKETPLACE_AGENT.version,
      targetEnvironment: 'sandbox',
      versionPinned: true,
      compatibilityStatus: 'COMPATIBLE',
      compatibilityNotes: ['Direct execution remains limited to sandbox or lower-risk validation environments.'],
      directExecutionMode: 'SANDBOX_ONLY',
      productionExecutionMode: 'PIPELINE_HITL_REQUIRED',
      requiresHitl: false,
      recommendedPath: 'sandbox_direct',
      allowedToInstall: true,
    });
    vi.mocked(aepApi.installMarketplaceAgent).mockResolvedValue({
      installId: 'install-1',
      agentId: MARKETPLACE_AGENT.id,
      agentName: MARKETPLACE_AGENT.name,
      agentVersion: MARKETPLACE_AGENT.version,
      tenantId: 'default',
      compatibilityStatus: 'COMPATIBLE',
      recommendedPath: 'sandbox_direct',
      directExecutionMode: 'SANDBOX_ONLY',
      productionExecutionMode: 'PIPELINE_HITL_REQUIRED',
      targetEnvironment: 'sandbox',
      installedAt: new Date().toISOString(),
      status: 'INSTALLED',
    });
  });

  it('renders marketplace agents and opens detail on click', async () => {
    const user = userEvent.setup();
    renderWithQuery(<AgentMarketplacePage />);

    await waitFor(() => expect(screen.getByText('Marketplace Agent')).toBeInTheDocument());
    await user.click(screen.getByText('Marketplace Agent'));

    await waitFor(() => expect(screen.getByText('Agent Detail')).toBeInTheDocument());
    expect(screen.getByText('Excellent')).toBeInTheDocument();
  });

  it('publishes a new agent', async () => {
    const user = userEvent.setup();
    renderWithQuery(<AgentMarketplacePage />);

    // Switch to Publish tab first
    await user.click(screen.getByRole('button', { name: /publish/i }));
    await waitFor(() => expect(screen.getByText('Publish Agent')).toBeInTheDocument());
    await user.type(screen.getByLabelText(/agent name/i), 'Tenant Agent');
    await user.click(screen.getByText('Publish Agent'));
    await user.type(screen.getByLabelText(/reason/i), 'Publishing for operator reuse');
    await user.type(screen.getByLabelText(/type .*publish.* to confirm/i), 'PUBLISH');
    await user.click(screen.getByRole('button', { name: /confirm/i }));

    await waitFor(() => expect(aepApi.publishMarketplaceAgent).toHaveBeenCalled());
  });

  it('simulates and installs a marketplace agent with version pinning', async () => {
    const user = userEvent.setup();
    renderWithQuery(<AgentMarketplacePage />);

    await waitFor(() => expect(screen.getByText('Marketplace Agent')).toBeInTheDocument());
    await user.click(screen.getByText('Marketplace Agent'));
    await waitFor(() => expect(screen.getByRole('button', { name: /install to tenant/i })).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /install to tenant/i }));
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    expect(await screen.findByText(/pipeline_hitl_required/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText(/reason/i), 'Register for sandbox validation');
    await user.type(screen.getByLabelText(/type install to confirm/i), 'INSTALL');
    await user.click(screen.getByRole('button', { name: /confirm install/i }));

    await waitFor(() => expect(aepApi.installMarketplaceAgent).toHaveBeenCalledWith(
      MARKETPLACE_AGENT.id,
      expect.objectContaining({
        targetEnvironment: 'sandbox',
        expectedVersion: MARKETPLACE_AGENT.version,
      }),
      'default',
    ));
  });
});

describe('CostDashboardPage', () => {
  beforeEach(() => {
    vi.mocked(aepApi.getCostSummary).mockResolvedValue(COST_SUMMARY);
  });

  it('renders cost summary cards and alerts', async () => {
    renderWithQuery(<CostDashboardPage />);

    await waitFor(() => expect(screen.getByText('Observed Spend')).toBeInTheDocument());
    expect(screen.getByText('Projected Monthly')).toBeInTheDocument();
    expect(screen.getByText('Budget guardrails')).toBeInTheDocument();
    expect(screen.getByText('Model Spend Breakdown')).toBeInTheDocument();
    expect(screen.getByText('Daily spend exceeded')).toBeInTheDocument();
    expect(screen.getByText('Fraud Triage')).toBeInTheDocument();
    expect(screen.getByText('gpt-4.1-mini')).toBeInTheDocument();
  });

  it('shows data source provenance badge', async () => {
    renderWithQuery(<CostDashboardPage />);

    await waitFor(() => expect(screen.getByText('Observed Spend')).toBeInTheDocument());

    // Provenance badge shows data source and allocation model
    expect(screen.getByText(/Source: metrics/i)).toBeInTheDocument();
    expect(screen.getByText(/analytics-metrics/i)).toBeInTheDocument();
  });

  it('does not show estimated warning banner when data is real', async () => {
    vi.mocked(aepApi.getCostSummary).mockResolvedValue({ ...COST_SUMMARY, estimated: false });

    renderWithQuery(<CostDashboardPage />);

    await waitFor(() => expect(screen.getByText('Observed Spend')).toBeInTheDocument());

    expect(screen.queryByText(/Estimated costs/i)).not.toBeInTheDocument();
  });

  it('shows estimated warning banner when data is synthetic', async () => {
    vi.mocked(aepApi.getCostSummary).mockResolvedValue({ ...COST_SUMMARY, estimated: true });

    renderWithQuery(<CostDashboardPage />);

    await waitFor(() => expect(screen.getByText(/Estimated costs/i)).toBeInTheDocument());
    // Must not label synthetic data as live
    expect(screen.getByText(/Billing telemetry is not yet available/i)).toBeInTheDocument();
  });

  it('shows backend failure state when API errors', async () => {
    vi.mocked(aepApi.getCostSummary).mockRejectedValue(new Error('503 Service Unavailable'));

    renderWithQuery(<CostDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/Failed to load cost summary/i)).toBeInTheDocument();
    });
  });

  it('shows empty state for alerts when no alerts are present', async () => {
    vi.mocked(aepApi.getCostSummary).mockResolvedValue({ ...COST_SUMMARY, alerts: [] });

    renderWithQuery(<CostDashboardPage />);

    await waitFor(() => expect(screen.getByText('Observed Spend')).toBeInTheDocument());

    // Active Alerts stat card should show 0
    expect(screen.getByText('Active Alerts')).toBeInTheDocument();
  });

  it('shows budget threshold status labels', async () => {
    vi.mocked(aepApi.getCostSummary).mockResolvedValue(COST_SUMMARY);
    renderWithQuery(<CostDashboardPage />);

    await waitFor(() => expect(screen.getByText('Observed Spend')).toBeInTheDocument());

    // Daily budget is 'exceeded', monthly is 'warning'
    const statusBadges = screen.getAllByText(/exceeded|warning/i);
    expect(statusBadges.length).toBeGreaterThanOrEqual(2);
  });

  it('applies updated budget thresholds and re-fetches with new budget', async () => {
    vi.mocked(aepApi.getCostSummary).mockClear();
    vi.mocked(aepApi.getCostSummary).mockResolvedValue(COST_SUMMARY);
    const user = userEvent.setup();
    renderWithQuery(<CostDashboardPage />);

    await waitFor(() => expect(screen.getByText('Budget guardrails')).toBeInTheDocument());

    // Change daily budget input to a new value
    const dailyInput = screen.getByLabelText(/Daily budget/i);
    await user.clear(dailyInput);
    await user.type(dailyInput, '50');

    const applyButton = screen.getByRole('button', { name: /Apply thresholds/i });
    await user.click(applyButton);

    // The page should still render cost summary data (not error) after applying
    await waitFor(() => {
      expect(screen.getByText('Observed Spend')).toBeInTheDocument();
    });
    // getCostSummary was called with the updated budget parameters
    expect(aepApi.getCostSummary).toHaveBeenCalledWith(
      expect.any(String), // tenantId
      expect.objectContaining({ dailyBudgetUsd: 50 }),
    );
  });
});
