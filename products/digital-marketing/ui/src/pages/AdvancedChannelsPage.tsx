/**
 * Advanced Channels Page — programmatic and emerging channel execution.
 *
 * <p>P3-003: Capability-gated via {@code dmos.advanced-channels}. Surfaces
 * channel allocation data from the workspace's approved budget recommendation,
 * showing programmatic, CTV, and influencer spend breakdowns. Reuses
 * {@code useBudgetRecommendation} and {@code StatsDashboard} from the shared
 * platform.</p>
 *
 * @doc.type page
 * @doc.purpose Advanced channel execution: programmatic, CTV, and influencer management
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useBudgetRecommendation } from '@/hooks/useBudget';
import { StatsDashboard, EmptyState, Card, CardContent, CardHeader, Badge } from '@ghatana/design-system';
import type { ChannelAllocation } from '@/types/budget';

const ADVANCED_CHANNEL_TYPES = new Set(['PAID_SEARCH', 'SOCIAL', 'OMNICHANNEL', 'CTV', 'PROGRAMMATIC', 'INFLUENCER']);

const TIME_RANGE_OPTIONS = [
  { label: 'Last 7 days', value: '7d' },
  { label: 'Last 30 days', value: '30d' },
  { label: 'Last 90 days', value: '90d' },
];

export function AdvancedChannelsPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const [timeRange, setTimeRange] = useState('30d');

  // Call hook unconditionally (React rules)
  const { recommendation, isLoading, isError } = useBudgetRecommendation(workspaceId ?? null);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const allAllocations: ChannelAllocation[] = recommendation?.channelAllocations ?? [];
  const advancedAllocations = allAllocations.filter(
    (a) => ADVANCED_CHANNEL_TYPES.has(a.channelType.toUpperCase()),
  );
  const totalAdvancedBudget = advancedAllocations.reduce(
    (s, a) => s + a.recommendedAmount,
    0,
  );
  const totalBudget = allAllocations.reduce((s, a) => s + a.recommendedAmount, 0);

  return (
    <section data-testid="advanced-channels-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Advanced Channels</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      {isError && (
        <div className="mb-4 p-4 rounded-lg bg-red-50 text-red-700 text-sm">
          Failed to load budget data. Channel allocations may be incomplete.
        </div>
      )}

      {!isLoading && !recommendation ? (
        <EmptyState
          title="No budget recommendation yet"
          description="Generate a budget recommendation from the Budget page to view channel allocations here."
          size="lg"
        />
      ) : (
        <>
          <StatsDashboard<ChannelAllocation>
            items={advancedAllocations}
            title="Advanced Channel Allocations"
            loading={isLoading}
            loadingMessage="Loading channel data…"
            emptyMessage="No advanced channel allocations in the current budget."
            timeRangeConfig={{
              value: timeRange,
              onChange: setTimeRange,
              options: TIME_RANGE_OPTIONS,
            }}
            statsCards={[
              {
                title: 'Advanced Channels',
                calculate: (items) => items.length,
                variant: 'blue',
                subtitle: 'Allocated channels',
              },
              {
                title: 'Advanced Budget',
                calculate: () => `$${totalAdvancedBudget.toLocaleString()}`,
                variant: 'purple',
                subtitle: 'Total across advanced channels',
              },
              {
                title: 'Share of Total Budget',
                calculate: () =>
                  totalBudget > 0
                    ? `${((totalAdvancedBudget / totalBudget) * 100).toFixed(1)}%`
                    : '—',
                variant: 'orange',
                subtitle: 'vs. all channels',
              },
              {
                title: 'Highest Daily Cap',
                calculate: (items) =>
                  items.length > 0
                    ? `$${Math.max(...items.map((a) => a.dailyCap)).toLocaleString()}`
                    : '—',
                variant: 'green',
                subtitle: 'Single-channel daily limit',
              },
            ]}
            barCharts={[
              {
                title: 'Recommended Budget by Channel',
                items: allAllocations.map((a) => ({
                  label: a.channelType,
                  value: a.recommendedAmount,
                  displayValue: `$${a.recommendedAmount.toLocaleString()}`,
                  color: ADVANCED_CHANNEL_TYPES.has(a.channelType.toUpperCase())
                    ? '#7c3aed'
                    : '#9ca3af',
                })),
                emptyMessage: 'No channel allocations defined.',
                showNumbering: true,
              },
            ]}
            insights={[
              ...(recommendation?.rationale
                ? [{ text: `Budget rationale: ${recommendation.rationale}` }]
                : []),
              { text: `Daily cap across all channels: $${allAllocations.reduce((s, a) => s + a.dailyCap, 0).toLocaleString()}` },
            ]}
            insightsVariant="purple"
          />

          {advancedAllocations.length > 0 && (
            <div className="mt-8">
              <h2 className="text-lg font-semibold mb-4">Channel Allocation Details</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {advancedAllocations.map((alloc, idx) => (
                  <Card key={idx} variant="outlined">
                    <CardHeader
                      title={alloc.channelType}
                      action={<Badge>{`$${alloc.dailyCap.toLocaleString()}/day`}</Badge>}
                    />
                    <CardContent>
                      <p className="text-sm font-medium text-gray-900">
                        Recommended: ${alloc.recommendedAmount.toLocaleString()}
                      </p>
                      <p className="text-xs text-gray-500 mt-1">{alloc.rationale}</p>
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
