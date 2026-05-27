/**
 * Legacy Preview route.
 *
 * @doc.type route
 * @doc.purpose Redirect legacy preview deep links to the canonical Observe phase
 * @doc.layer product
 * @doc.pattern Redirect Component
 */
import { LegacyProjectRedirectRoute } from './LegacyProjectRedirectRoute';
import { LEGACY_PROJECT_ROUTE_POLICIES } from './legacyProjectRoutePolicy';

export default function PreviewRedirectRoute() {
  return <LegacyProjectRedirectRoute policy={LEGACY_PROJECT_ROUTE_POLICIES.preview} />;
}

export function ErrorBoundary() {
  return null;
}
