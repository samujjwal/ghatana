/**
 * Agency Operations Page — multi-client workspace management.
 *
 * <p>P3-005: Capability-gated via {@code dmos.agency}. Surfaces cross-domain
 * aggregate metrics by combining campaign counts, strategy status, and budget
 * totals from the workspace. Reuses {@code useCampaigns},
 * {@code useBudgetRecommendation}, {@code useStrategy}, and
 * {@code StatsDashboard} from the shared platform.</p>
 *
 * @doc.type page
 * @doc.purpose Agency operations: client onboarding, white-label reports, multi-client management
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useCampaigns } from '@/hooks/useCampaigns';
import { useBudgetRecommendation } from '@/hooks/useBudget';
import { useStrategy } from '@/hooks/useStrategy';
import { StatsDashboard } from '@ghatana/design-system';
import type { Campaign } from '@/types/campaign';

const TIME_RANGE_OPTIONS = [
  { label: 'Last 7 days', value: '7d' },
  { label: 'Last 30 days', value: '30d' },
  { label: 'Last 90 days', value: '90d' },
];

export function AgencyOperationsPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const [timeRange, setTimeRange] = useState('30d');

  // Call all hooks unconditionally (React rules)
  const { campaigns, isLoading: campaignsLoading } = useCampaigns(workspaceId ?? null, { limit: 100 });
  const { recommendation, isLoading: budgetLoading } = useBudgetRecommendation(workspaceId ?? null);
  const { strategy, isLoading: strategyLoading } = useStrategy(workspaceId ?? null);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const isLoading = campaignsLoading || budgetLoading || strategyLoading;
  const launchedCampaigns = campaigns.filter((c: Campaign) => c.status === 'LAUNCHED');
  const totalBudget = recommendation?.totalMonthlyCap ?? 0;
  const channelCount = recommendation?.channelAllocations.length ?? 0;

  return (
    <section data-testid="agency-operations-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Agency Operations</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      <StatsDashboard<Campaign>
        items={campaigns}
        title="Workspace Health Overview"
        loading={isLoading}
        loadingMessage="Loading agency metrics…"
        emptyMessage="No workspace activity found."
        timeRangeConfig={{
          value: timeRange,
          onChange: setTimeRange,
          options: TIME_RANGE_OPTIONS,
        }}
        statsCards={[
          {
            title: 'Total Campaigns',
            calculate: (items) => items.length,
            variant: 'blue',
            subtitle: 'All lifecycle states',
          },
          {
            title: 'Live Campaigns',
            calculate: () => launchedCampaigns.length,
            variant: 'green',
            subtitle: 'Currently LAUNCHED',
          },
          {
            title: 'Monthly Budget Cap',
            calculate: () =>
              totalBudget > 0 ? `$${totalBudget.toLocaleString()}` : '—',
            variant: 'purple',
            subtitle: 'From approved budget',
          },
          {
            title: 'Active Channels',
            calculate: () => channelCount > 0 ? channelCount : '—',
            variant: 'orange',
            subtitle: 'Allocated channel types',
          },
        ]}
        barCharts={[
          {
            title: 'Campaigns by Status',
            items: (['DRAFT', 'LAUNCHED', 'PAUSED', 'COMPLETED', 'ARCHIVED'] as const).map(
              (status) => ({
                label: status,
                value: campaigns.filter((c) => c.status === status).length,
                displayValue: String(campaigns.filter((c) => c.status === status).length),
              }),
            ).filter((item) => item.value > 0),
            emptyMessage: 'No campaigns yet.',
          },
          {
            title: 'Campaigns by Type',
            items: (['EMAIL', 'SOCIAL', 'PAID_SEARCH', 'PUSH', 'SMS', 'OMNICHANNEL'] as const).map(
              (type) => ({
                label: type,
                value: campaigns.filter((c) => c.type === type).length,
                displayValue: String(campaigns.filter((c) => c.type === type).length),
              }),
            ).filter((item) => item.value > 0),
            emptyMessage: 'No campaigns by type.',
            showNumbering: true,
          },
        ]}
        insights={[
          { text: `Strategy status: ${strategy?.status ?? 'No strategy generated yet'}` },
          { text: `Budget status: ${recommendation?.status ?? 'No budget recommendation yet'}` },
          ...(recommendation?.rationale
            ? [{ text: `Budget rationale: ${recommendation.rationale}` }]
            : []),
        ]}
        insightsTitle="📋 Workspace Summary"
        insightsVariant="green"
      />
    </section>
  );
}
