import React, { Suspense, lazy } from 'react';
import { useParams } from 'react-router-dom';
import { useCapabilityEnabled } from '@/hooks/useCapabilities';

// Lazy load the feature unavailable page to reduce initial bundle
const FeatureUnavailablePage = lazy(() =>
  import('@/pages/FeatureUnavailablePage').then((m) => ({
    default: m.FeatureUnavailablePage,
  }))
);

interface FeatureFlaggedRouteProps {
  capabilityKey: string;
  children: React.ReactNode;
  featureName?: string;
}

/**
 * P1-016: Capability-Driven Route.
 *
 * Wraps a route and only renders its children if the backend capability is enabled.
 * Uses runtime capability checks from the backend instead of build-time environment variables.
 *
 * <p>P0-004 Fix: Uses Vite-safe {@code import.meta.env} instead of Node's {@code process.env}.
 * P0-005 Fix: Renders a proper "feature unavailable" boundary instead of redirecting to login.
 * P1-016: Backend capability-driven instead of build-time feature flags.</p>
 */
const CapabilityDrivenRoute: React.FC<FeatureFlaggedRouteProps> = ({
  capabilityKey,
  children,
  featureName,
}) => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const isEnabled = useCapabilityEnabled(workspaceId ?? null, capabilityKey);

  // While loading, show loading state
  // In production, you might want to show a skeleton or the feature with a loading indicator
  // For now, we'll render children and let them handle their own loading states

  // P0-005: Render feature unavailable page instead of redirecting to login
  if (!isEnabled) {
    return (
      <Suspense fallback={<div className="p-4 text-gray-500">Loading...</div>}>
        <FeatureUnavailablePage
          featureName={featureName || getCapabilityDisplayName(capabilityKey)}
          reason="is currently disabled. Please contact your administrator to enable this feature."
        />
      </Suspense>
    );
  }

  return <>{children}</>;
};

/**
 * Returns a user-friendly display name for a capability key.
 */
function getCapabilityDisplayName(capabilityKey: string): string {
  const nameMap: Record<string, string> = {
    'dmos.campaigns': 'Campaigns',
    'dmos.strategy': 'Strategy',
    'dmos.budget': 'Budget',
    'dmos.approvals': 'Approvals',
    'dmos.ai_actions': 'AI Action Log',
  };
  return nameMap[capabilityKey] || 'This feature';
}

// Export with backward-compatible name
export default CapabilityDrivenRoute;
