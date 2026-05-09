/**
 * Self-Marketing Funnel Page — capability boundary.
 *
 * @doc.type page
 * @doc.purpose Explicit unavailable state for self-marketing funnel until dedicated backend APIs are implemented
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { FeatureUnavailablePage } from '@/pages/FeatureUnavailablePage';

export function SelfMarketingFunnelPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <FeatureUnavailablePage
      featureName="Self-Marketing Funnel"
      reason={`is currently unavailable for workspace ${workspaceId ?? 'unknown'} (requires dedicated funnel analytics APIs).`}
    />
  );
}