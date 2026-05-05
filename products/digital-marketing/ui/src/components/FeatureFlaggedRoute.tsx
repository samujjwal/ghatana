import React, { Suspense, lazy } from 'react';

// Lazy load the feature unavailable page to reduce initial bundle
const FeatureUnavailablePage = lazy(() =>
  import('@/pages/FeatureUnavailablePage').then((m) => ({
    default: m.FeatureUnavailablePage,
  }))
);

interface FeatureFlaggedRouteProps {
  flagKey: string;
  children: React.ReactNode;
}

/**
 * FeatureFlaggedRoute wraps a route and only renders its children if the feature flag is enabled.
 *
 * <p>P0-004 Fix: Uses Vite-safe {@code import.meta.env} instead of Node's {@code process.env}.
 * P0-005 Fix: Renders a proper "feature unavailable" boundary instead of redirecting to login.</p>
 *
 * <p>Feature flags are read from Vite environment variables at build time.
 * In production, flags should be served from a backend capability endpoint for
 * runtime toggling without rebuilds.</p>
 */
const FeatureFlaggedRoute: React.FC<FeatureFlaggedRouteProps> = ({ flagKey, children }) => {
  // P0-004: Use Vite-safe import.meta.env (not process.env)
  const isEnabled = checkFeatureFlag(flagKey);

  // P0-005: Render feature unavailable page instead of redirecting to login
  if (!isEnabled) {
    return (
      <Suspense fallback={<div className="p-4 text-gray-500">Loading...</div>}>
        <FeatureUnavailablePage
          featureName={getFeatureDisplayName(flagKey)}
          reason="is currently disabled. Please contact your administrator to enable this feature."
        />
      </Suspense>
    );
  }

  return <>{children}</>;
};

/**
 * Checks if a feature flag is enabled using Vite environment variables.
 *
 * <p>Maps flag keys like "dmos.campaigns_page_enabled" to Vite env vars
 * like {@code import.meta.env.VITE_DMOS_CAMPAIGNS_PAGE_ENABLED}.</p>
 */
function checkFeatureFlag(flagKey: string): boolean {
  // Convert flag key to Vite environment variable name
  // e.g., "dmos.campaigns_page_enabled" -> "VITE_DMOS_CAMPAIGNS_PAGE_ENABLED"
  const envVarName = 'VITE_' + flagKey.toUpperCase().replace(/\./g, '_');

  // P0-004: Use import.meta.env (Vite) instead of process.env (Node)
  const envValue = (import.meta.env as Record<string, string | undefined>)[envVarName];

  // Fail closed: default to false if not explicitly enabled
  return envValue === 'true' || envValue === '1';
}

/**
 * Returns a user-friendly display name for a feature flag key.
 */
function getFeatureDisplayName(flagKey: string): string {
  const nameMap: Record<string, string> = {
    'dmos.campaigns_page_enabled': 'Campaigns',
    'dmos.strategy_page_enabled': 'Strategy',
    'dmos.budget_page_enabled': 'Budget',
  };
  return nameMap[flagKey] || 'This feature';
}

export default FeatureFlaggedRoute;
