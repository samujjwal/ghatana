/**
 * Legacy Canvas route.
 *
 * @doc.type route
 * @doc.purpose Redirect legacy canvas deep links to the canonical Shape phase
 * @doc.layer product
 * @doc.pattern Redirect Component
 */
import { LegacyProjectRedirectRoute } from './LegacyProjectRedirectRoute';
import { LEGACY_PROJECT_ROUTE_POLICIES } from './legacyProjectRoutePolicy';

export default function CanvasRedirectRoute() {
  return <LegacyProjectRedirectRoute policy={LEGACY_PROJECT_ROUTE_POLICIES.canvas} />;
}

export function ErrorBoundary() {
  return null;
}
