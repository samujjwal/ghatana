/**
 * AI Optimization Page — AI-driven campaign optimization management.
 *
 * <p>P3-004: Capability-gated via {@code dmos.ai-optimization}. Surfaces AI-generated
 * next-best-action recommendations, anomaly detection results, experiment suggestions,
 * and budget reallocation proposals. Uses shared platform components for consistent UX.</p>
 *
 * @doc.type page
 * @doc.purpose AI optimization and recommendation management view
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { StatsDashboard } from '@ghatana/design-system';
import { EmptyState } from '@ghatana/design-system';
import { Card } from '@ghatana/design-system';
import { Badge } from '@ghatana/design-system';

const TIME_RANGE_OPTIONS = [
  { label: 'Last 7 days', value: '7d' },
  { label: 'Last 30 days', value: '30d' },
  { label: 'Last 90 days', value: '90d' },
];

type OptimizationItem = {
  id: string;
  title: string;
  description: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'RESOLVED' | 'DISMISSED' | 'EXECUTED' | 'EXPIRED';
  confidenceScore?: number;
  severity?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  createdAt: string;
};

export function AiOptimizationPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();
  const [timeRange, setTimeRange] = useState('30d');

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Placeholder data - will be replaced with real API calls
  const recommendations: OptimizationItem[] = [];
  const anomalies: OptimizationItem[] = [];
  const experiments: OptimizationItem[] = [];
  const budgetProposals: OptimizationItem[] = [];

  const totalPending = recommendations.filter(r => r.status === 'PENDING').length +
    anomalies.filter(a => a.status === 'PENDING').length +
    experiments.filter(e => e.status === 'PENDING').length +
    budgetProposals.filter(b => b.status === 'PENDING').length;

  return (
    <section data-testid="ai-optimization-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">AI Optimization</h1>
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-500">
            Workspace: <code>{workspaceId}</code>
          </span>
          {totalPending > 0 && (
            <Badge tone="warning">{totalPending} pending actions</Badge>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        <Card title="Next-Best-Action Recommendations">
          {recommendations.length === 0 ? (
            <EmptyState
              title="No recommendations yet"
              description="AI-generated recommendations for campaign optimization will appear here."
              size="sm"
            />
          ) : (
            <div className="space-y-3">
              {recommendations.map((rec) => (
                <div key={rec.id} className="p-3 border rounded-lg">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="font-medium">{rec.title}</h3>
                      <p className="text-sm text-gray-600">{rec.description}</p>
                    </div>
                    <Badge tone={rec.status === 'PENDING' ? 'info' : 'success'}>
                      {rec.status}
                    </Badge>
                  </div>
                  {rec.confidenceScore !== undefined && (
                    <div className="mt-2 text-xs text-gray-500">
                      Confidence: {(rec.confidenceScore * 100).toFixed(0)}%
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="Anomaly Detection">
          {anomalies.length === 0 ? (
            <EmptyState
              title="No anomalies detected"
              description="AI-detected performance anomalies will appear here."
              size="sm"
            />
          ) : (
            <div className="space-y-3">
              {anomalies.map((anomaly) => (
                <div key={anomaly.id} className="p-3 border rounded-lg">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="font-medium">{anomaly.title}</h3>
                      <p className="text-sm text-gray-600">{anomaly.description}</p>
                    </div>
                    <Badge tone={anomaly.severity === 'HIGH' || anomaly.severity === 'CRITICAL' ? 'danger' : 'warning'}>
                      {anomaly.severity}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="Experiment Suggestions">
          {experiments.length === 0 ? (
            <EmptyState
              title="No experiment suggestions"
              description="AI-generated A/B test suggestions will appear here."
              size="sm"
            />
          ) : (
            <div className="space-y-3">
              {experiments.map((exp) => (
                <div key={exp.id} className="p-3 border rounded-lg">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="font-medium">{exp.title}</h3>
                      <p className="text-sm text-gray-600">{exp.description}</p>
                    </div>
                    <Badge tone={exp.status === 'PENDING' ? 'info' : 'success'}>
                      {exp.status}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="Budget Reallocation Proposals">
          {budgetProposals.length === 0 ? (
            <EmptyState
              title="No budget proposals"
              description="AI-generated budget reallocation proposals will appear here."
              size="sm"
            />
          ) : (
            <div className="space-y-3">
              {budgetProposals.map((proposal) => (
                <div key={proposal.id} className="p-3 border rounded-lg">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="font-medium">{proposal.title}</h3>
                      <p className="text-sm text-gray-600">{proposal.description}</p>
                    </div>
                    <Badge tone={proposal.status === 'PENDING' ? 'info' : proposal.status === 'APPROVED' ? 'warning' : 'success'}>
                      {proposal.status}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      <StatsDashboard<OptimizationItem>
        items={[]}
        title="Optimization Overview"
        loading={false}
        loadingMessage="Loading optimization metrics…"
        emptyMessage="No optimization data available."
        timeRangeConfig={{
          value: timeRange,
          onChange: setTimeRange,
          options: TIME_RANGE_OPTIONS,
        }}
        statsCards={[
          {
            title: 'Pending Actions',
            calculate: () => totalPending,
            variant: 'blue',
            subtitle: 'Awaiting review',
          },
          {
            title: 'Total Recommendations',
            calculate: () => recommendations.length,
            variant: 'green',
            subtitle: 'Next-best-actions',
          },
          {
            title: 'Active Anomalies',
            calculate: () => anomalies.filter(a => a.status === 'PENDING').length,
            variant: 'orange',
            subtitle: 'Requires attention',
          },
          {
            title: 'Approved Experiments',
            calculate: () => experiments.filter(e => e.status === 'APPROVED').length,
            variant: 'purple',
            subtitle: 'A/B tests running',
          },
        ]}
        insights={[
          { text: 'AI optimization features are currently being integrated.' },
          { text: 'Connect to backend APIs to enable real-time recommendations.' },
        ]}
      />
    </section>
  );
}
