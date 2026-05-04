/**
 * Shape Phase — Define the solution
 *
 * Requirements, user stories, and design artefacts.
 * Routes to the canvas with a focus on the Shape workflow
 * (requirement management, design constraints, acceptance criteria).
 *
 * @doc.type route
 * @doc.purpose Shape phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { ShapeCockpitRoute } from './_phaseCockpit';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

export default ShapeCockpitRoute;
export function ErrorBoundary() {
  return <RouteErrorBoundary />;
}
