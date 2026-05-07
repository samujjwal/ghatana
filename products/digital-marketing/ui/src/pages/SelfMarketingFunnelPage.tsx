/**
 * Self-Marketing Funnel Page — product-led growth funnel management.
 *
 * <p>P3-001: Capability-gated via {@code dmos.self-marketing}. Surfaces PLG
 * funnel metrics derived from existing campaign data: awareness, lead capture,
 * trial activations, and conversion stages. Reuses {@code useCampaigns} and
 * {@code StatsDashboard} from the shared platform.</p>
 *
 * @doc.type page
 * @doc.purpose Self-marketing funnel and product-led growth management view
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useCampaigns } from '@/hooks/useCampaigns';
import { StatsDashboard } from '@ghatana/design-system';
import { EmptyState } from '@ghatana/design-system';
import type { Campaign } from '@/types/campaign';

const TIME_RANGE_OPTIONS = [
  { label: 'Last 7 days', value: '7d' },
  { label: 'Last 30 days', value: '30d' },
  { label: 'Last 90 days', value: '90d' },
];

export function SelfMarketingFunnelPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const [timeRange, setTimeRange] = useState('30d');

  // Call hook unconditionally (React rules)
  const { campaigns, isLoading, isError } = useCampaigns(workspaceId ?? null, { limit: 100 });

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const awarenessCampaigns = campaigns.filter((c: Campaign) => c.objective === 'AWARENESS');
  const leadCampaigns = campaigns.filter((c: Campaign) => c.objective === 'LEADS');
  const conversionCampaigns = campaigns.filter((c: Campaign) => c.objective === 'CONVERSIONS');
  const activeFunnelCampaigns = campaigns.filter(
    (c: Campaign) => c.status === 'LAUNCHED' && (
      c.objective === 'AWARENESS' || c.objective === 'LEADS' || c.objective === 'CONVERSIONS'
    ),
  );

  return (
    <section data-testid="self-marketing-funnel-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Self-Marketing Funnel</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      {isError && (
        <div className="mb-4 p-4 rounded-lg bg-red-50 text-red-700 text-sm">
          Failed to load campaign data. Funnel metrics may be incomplete.
        </div>
      )}

      {!isLoading && campaigns.length === 0 ? (
        <EmptyState
          title="No funnel campaigns yet"
          description="Launch awareness, lead-capture, or conversion campaigns to populate the PLG funnel view."
          size="lg"
        />
      ) : (
        <StatsDashboard<Campaign>
          items={activeFunnelCampaigns}
          title="Product-Led Growth Funnel"
          loading={isLoading}
          loadingMessage="Loading funnel metrics…"
          emptyMessage="No active funnel campaigns in this period."
          timeRangeConfig={{
            value: timeRange,
            onChange: setTimeRange,
            options: TIME_RANGE_OPTIONS,
          }}
          statsCards={[
            {
              title: 'Awareness Campaigns',
              calculate: () => awarenessCampaigns.length,
              variant: 'blue',
              subtitle: 'Top-of-funnel',
            },
            {
              title: 'Lead Campaigns',
              calculate: () => leadCampaigns.length,
              variant: 'green',
              subtitle: 'Mid-funnel',
            },
            {
              title: 'Conversion Campaigns',
              calculate: () => conversionCampaigns.length,
              variant: 'purple',
              subtitle: 'Bottom-of-funnel',
            },
            {
              title: 'Active Funnel Campaigns',
              calculate: (items) => items.length,
              variant: 'orange',
              subtitle: 'Currently running',
            },
          ]}
          barCharts={[
            {
              title: 'Campaign Budget by Funnel Stage (¢)',
              items: [
                {
                  label: 'Awareness',
                  value: awarenessCampaigns.reduce((s, c) => s + (c.budgetCents ?? 0), 0),
                  displayValue: `$${(awarenessCampaigns.reduce((s, c) => s + (c.budgetCents ?? 0), 0) / 100).toFixed(0)}`,
                  color: '#3b82f6',
                },
                {
                  label: 'Leads',
                  value: leadCampaigns.reduce((s, c) => s + (c.budgetCents ?? 0), 0),
                  displayValue: `$${(leadCampaigns.reduce((s, c) => s + (c.budgetCents ?? 0), 0) / 100).toFixed(0)}`,
                  color: '#22c55e',
                },
                {
                  label: 'Conversions',
                  value: conversionCampaigns.reduce((s, c) => s + (c.budgetCents ?? 0), 0),
                  displayValue: `$${(conversionCampaigns.reduce((s, c) => s + (c.budgetCents ?? 0), 0) / 100).toFixed(0)}`,
                  color: '#a855f7',
                },
              ],
              emptyMessage: 'No budget data available.',
            },
          ]}
          insights={[
            { text: `${campaigns.length} total campaigns tracked across all funnel stages.` },
            { text: `${activeFunnelCampaigns.length} campaigns are actively running in the funnel.` },
          ]}
        />
      )}
    </section>
  );
}
