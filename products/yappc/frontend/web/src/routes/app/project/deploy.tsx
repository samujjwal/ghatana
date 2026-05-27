/**
 * Legacy Deploy route.
 *
 * @doc.type route
 * @doc.purpose Redirect legacy deploy deep links to the canonical Run phase
 * @doc.layer product
 * @doc.pattern Redirect Component
 */
import { LegacyProjectRedirectRoute } from './LegacyProjectRedirectRoute';
import { LEGACY_PROJECT_ROUTE_POLICIES } from './legacyProjectRoutePolicy';

export default function DeployRedirectRoute() {
  return <LegacyProjectRedirectRoute policy={LEGACY_PROJECT_ROUTE_POLICIES.deploy} />;
}

export function ErrorBoundary() {
  return null;
}
