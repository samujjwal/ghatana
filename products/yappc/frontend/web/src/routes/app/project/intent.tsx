/**
 * Intent Phase — Capture the "why"
 *
 * Goal and problem capture: ideas, research, user problems.
 * Renders the project overview cockpit which surfaces intent artifacts
 * via the IntentDrawer (triggered from the URL `?drawer=idea|research|problem`).
 *
 * @doc.type route
 * @doc.purpose Intent phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { IntentCockpitRoute } from './_phaseCockpit';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

export default IntentCockpitRoute;
export function ErrorBoundary() {
  return <RouteErrorBoundary />;
}
