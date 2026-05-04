/**
 * Observe Phase — Metrics, incidents, and live signals
 *
 * Operational observability: dashboards, alerts, incident timelines,
 * and real-time agent activity. The lifecycle page's Observe/Monitor
 * sections are the canonical view for this phase.
 *
 * @doc.type route
 * @doc.purpose Observe phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { ObserveCockpitRoute } from './_phaseCockpit';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

export default ObserveCockpitRoute;
export function ErrorBoundary() {
  return <RouteErrorBoundary />;
}
