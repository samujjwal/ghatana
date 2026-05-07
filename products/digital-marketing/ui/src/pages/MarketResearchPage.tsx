/**
 * Market Research Page — market intelligence derived from marketing strategy.
 *
 * <p>P3-002: Capability-gated via {@code dmos.market-research}. Surfaces goals,
 * channel plans, and strategic insights from the workspace's approved
 * {@code MarketingStrategy}. Reuses {@code useStrategy} and
 * {@code StatsDashboard} from the shared platform.</p>
 *
 * @doc.type page
 * @doc.purpose Market research, trend analysis, and buyer persona management view
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useStrategy } from '@/hooks/useStrategy';
import { StatsDashboard, EmptyState, Card, CardContent, CardHeader } from '@ghatana/design-system';
import type { CampaignPlan } from '@/types/strategy';

const TIME_RANGE_OPTIONS = [
  { label: 'Last 7 days', value: '7d' },
  { label: 'Last 30 days', value: '30d' },
  { label: 'Last 90 days', value: '90d' },
];

export function MarketResearchPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const [timeRange, setTimeRange] = useState('30d');

  // Call hook unconditionally (React rules)
  const { strategy, isLoading, isError } = useStrategy(workspaceId ?? null);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const channelPlans: CampaignPlan[] = strategy?.channelPlans ?? [];
  const goals = strategy?.goals ?? [];

  return (
    <section data-testid="market-research-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Market Research</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      {isError && (
        <div className="mb-4 p-4 rounded-lg bg-red-50 text-red-700 text-sm">
          Failed to load strategy data. Research insights may be incomplete.
        </div>
      )}

      {!isLoading && !strategy ? (
        <EmptyState
          title="No marketing strategy yet"
          description="Generate a marketing strategy from the Strategy page to populate market research insights."
          size="lg"
        />
      ) : (
        <>
          <StatsDashboard<CampaignPlan>
            items={channelPlans}
            title="Channel Strategy Overview"
            loading={isLoading}
            loadingMessage="Loading market intelligence…"
            emptyMessage="No channel plans in the current strategy."
            timeRangeConfig={{
              value: timeRange,
              onChange: setTimeRange,
              options: TIME_RANGE_OPTIONS,
            }}
            statsCards={[
              {
                title: 'Strategic Goals',
                calculate: () => goals.length,
                variant: 'blue',
                subtitle: 'Defined objectives',
              },
              {
                title: 'Channel Plans',
                calculate: (items) => items.length,
                variant: 'green',
                subtitle: 'Active channels',
              },
              {
                title: 'Total Est. Budget',
                calculate: (items) =>
                  `$${items.reduce((s, p) => s + (p.estimatedBudget ?? 0), 0).toLocaleString()}`,
                variant: 'purple',
                subtitle: 'Across all channels',
              },
              {
                title: 'Strategy Status',
                calculate: () => strategy?.status ?? '—',
                variant: strategy?.status === 'APPROVED' ? 'green' : 'yellow',
                subtitle: 'Current lifecycle state',
              },
            ]}
            barCharts={[
              {
                title: 'Estimated Budget by Channel',
                items: channelPlans.map((p) => ({
                  label: p.channelType,
                  value: p.estimatedBudget,
                  displayValue: `$${p.estimatedBudget.toLocaleString()}`,
                })),
                emptyMessage: 'No channel budgets defined.',
                showNumbering: true,
              },
            ]}
            insights={[
              ...(strategy?.rationale ? [{ text: `Rationale: ${strategy.rationale}` }] : []),
              ...(strategy?.assumptions ? [{ text: `Assumptions: ${strategy.assumptions}` }] : []),
              ...(strategy?.measurementPlan ? [{ text: `Measurement: ${strategy.measurementPlan}` }] : []),
            ]}
            insightsTitle="📊 Strategic Insights"
            insightsVariant="blue"
          />

          {goals.length > 0 && (
            <div className="mt-8">
              <h2 className="text-lg font-semibold mb-4">Strategic Goals</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {goals.map((goal, idx) => (
                  <Card key={idx} variant="outlined">
                    <CardHeader title={goal.goalType} subheader={goal.targetMetric} />
                    <CardContent>
                      <p className="text-sm text-gray-700">{goal.description}</p>
                      <p className="text-xs text-gray-500 mt-2">
                        Measured by: {goal.measurementMethod}
                      </p>
                    </CardContent>
                  </Card>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </section>
  );
}
