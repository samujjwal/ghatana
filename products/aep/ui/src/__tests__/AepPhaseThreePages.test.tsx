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

vi.mock('@/api/aep.api');

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
    vi.mocked(aepApi.listMarketplaceAgents).mockResolvedValue([MARKETPLACE_AGENT]);
    vi.mocked(aepApi.getMarketplaceAgent).mockResolvedValue(MARKETPLACE_DETAIL);
    vi.mocked(aepApi.publishMarketplaceAgent).mockResolvedValue(MARKETPLACE_AGENT);
    vi.mocked(aepApi.createMarketplaceReview).mockResolvedValue(MARKETPLACE_DETAIL.reviews[0]);
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

    await waitFor(() => expect(aepApi.publishMarketplaceAgent).toHaveBeenCalled());
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
    expect(screen.getByText('Daily spend exceeded')).toBeInTheDocument();
    expect(screen.getByText('Fraud Triage')).toBeInTheDocument();
  });
});