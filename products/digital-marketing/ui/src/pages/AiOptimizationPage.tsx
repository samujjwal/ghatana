/**
 * AI Optimization Page — capability boundary.
 *
 * @doc.type page
 * @doc.purpose Explicit unavailable state for AI optimization until backend APIs are integrated
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { FeatureUnavailablePage } from '@/pages/FeatureUnavailablePage';

export function AiOptimizationPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <FeatureUnavailablePage
      featureName="AI Optimization"
      reason={`is currently unavailable for workspace ${workspaceId ?? 'unknown'} (requires integrated optimization APIs and provenance controls).`}
    />
  );
}