/**
 * Legacy Lifecycle route.
 *
 * @doc.type route
 * @doc.purpose Redirect legacy lifecycle deep links to the canonical Intent phase
 * @doc.layer product
 * @doc.pattern Redirect Component
 */
import { LegacyProjectRedirectRoute } from './LegacyProjectRedirectRoute';
import { LEGACY_PROJECT_ROUTE_POLICIES } from './legacyProjectRoutePolicy';

export default function LifecycleRedirectRoute() {
  return <LegacyProjectRedirectRoute policy={LEGACY_PROJECT_ROUTE_POLICIES.lifecycle} />;
}

export function ErrorBoundary() {
  return null;
}
