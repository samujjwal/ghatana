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
import { FeatureUnavailablePage } from '@/pages/FeatureUnavailablePage';

export function MarketResearchPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <FeatureUnavailablePage
      featureName="Market Research"
      reason={`is currently unavailable for workspace ${workspaceId ?? 'unknown'} (requires dedicated market intelligence APIs).`}
    />
  );
}
