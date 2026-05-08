/**
 * Funnel Analytics Page — reporting capability placeholder.
 *
 * <p>P0-003: Route is capability-gated via {@code dmos.reporting}. Shows a
 * feature unavailable view until the reporting backend is available.</p>
 *
 * @doc.type page
 * @doc.purpose Funnel analytics reporting view
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { FeatureUnavailablePage } from '@/pages/FeatureUnavailablePage';

export function FunnelAnalyticsPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <FeatureUnavailablePage featureName="Funnel Analytics" reason={`is currently unavailable for workspace ${workspaceId ?? 'unknown'} (requires dmos.reporting capability).`} />;
}
