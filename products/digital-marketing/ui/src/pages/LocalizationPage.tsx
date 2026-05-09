/**
 * Localization Page — capability boundary.
 *
 * @doc.type page
 * @doc.purpose Explicit unavailable state for localization workflows until locale-specific APIs are implemented
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { FeatureUnavailablePage } from '@/pages/FeatureUnavailablePage';

export function LocalizationPage(): React.ReactElement {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <FeatureUnavailablePage
      featureName="Localization"
      reason={`is currently unavailable for workspace ${workspaceId ?? 'unknown'} (requires dedicated localization and translation APIs).`}
    />
  );
}