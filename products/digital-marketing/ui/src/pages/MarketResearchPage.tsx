/**
 * Market Research Page — market intelligence and competitive analysis.
 *
 * <p>P1-003: This page is currently a boundary feature. Market research requires
 * its own data model, API, external/source evidence, personas, competitors,
 * confidence scores, refresh time, and audit trail — separate from strategy-derived
 * summaries. Currently shows "Feature Not Available" until real market research
 * infrastructure is implemented.</p>
 *
 * @doc.type page
 * @doc.purpose Market research, trend analysis, and buyer persona management view
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { EmptyState } from '@ghatana/design-system';

export function MarketResearchPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <section data-testid="market-research-page" className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Market Research</h1>
        <span className="text-sm text-gray-500">
          Workspace: <code>{workspaceId}</code>
        </span>
      </div>

      <EmptyState
        title="Market Research Not Available"
        description="Market research requires dedicated data models, external evidence sources, competitor intelligence, buyer personas, and audit trails. This feature is currently under development."
        size="lg"
      />
    </section>
  );
}
