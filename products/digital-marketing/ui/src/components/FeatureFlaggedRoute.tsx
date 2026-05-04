import React from 'react';
import { Navigate } from 'react-router-dom';

interface FeatureFlaggedRouteProps {
  flagKey: string;
  children: React.ReactNode;
}

/**
 * FeatureFlaggedRoute wraps a route and only renders its children if the feature flag is enabled.
 * If the flag is disabled, redirects to the dashboard with a helpful message.
 */
const FeatureFlaggedRoute: React.FC<FeatureFlaggedRouteProps> = ({ flagKey, children }) => {
  // Check environment variable for feature flag
  const isEnabled = checkFeatureFlag(flagKey);

  if (!isEnabled) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
};

function checkFeatureFlag(flagKey: string): boolean {
  // Convert flag key to environment variable name
  // e.g., "dmos.budget_page_enabled" -> "DMOS_BUDGET_PAGE_ENABLED"
  const envVarName = flagKey.toUpperCase().replace(/\./g, '_');
  const envValue = process.env[envVarName];

  // Default to false if not set
  return envValue === 'true' || envValue === '1';
}

export default FeatureFlaggedRoute;
