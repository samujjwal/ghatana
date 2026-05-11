/**
 * ROI &amp; ROAS Reporting Page — reporting capability placeholder.
 *
 * <p>P0-003: Route is capability-gated via {@code dmos.reporting}. Shows a
 * feature unavailable view until the reporting backend is available.</p>
 *
 * @doc.type page
 * @doc.purpose Return on investment and return on ad spend reporting view
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { FeatureUnavailablePage } from '@/pages/FeatureUnavailablePage';

export function RoiRoasPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <FeatureUnavailablePage
      featureName="ROI and ROAS"
      reason={`is currently unavailable for workspace ${workspaceId ?? 'unknown'}.`}
      capability="dmos.reporting"
      connector="Cost and revenue analytics runtime"
      productionGate="Locked until cost, revenue, currency, formula version, freshness, and confidence are persisted."
      remediation="Use Dashboard Summary budget metrics and approved exports until ROI/ROAS formulas are production-backed."
    />
  );
}
