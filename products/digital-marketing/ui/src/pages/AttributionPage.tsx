/**
 * Attribution Reporting Page — reporting capability placeholder.
 *
 * <p>P0-003: Route is capability-gated via {@code dmos.reporting}. Shows a
 * feature unavailable view until the reporting backend is available.</p>
 *
 * @doc.type page
 * @doc.purpose Multi-touch attribution reporting view
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { FeatureUnavailablePage } from '@/pages/FeatureUnavailablePage';

export function AttributionPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <FeatureUnavailablePage
      featureName="Attribution Reporting"
      reason={`is currently unavailable for workspace ${workspaceId ?? 'unknown'}.`}
      capability="dmos.reporting"
      connector="Attribution source-event lineage"
      productionGate="Locked until touchpoint lineage, attribution model, source, freshness, and confidence are persisted."
      remediation="Use explicit campaign source metadata and approved reports until attribution runtime is connected."
    />
  );
}
