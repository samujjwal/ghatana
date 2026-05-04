/**
 * Evolve Phase — Plan the next cycle
 *
 * Refine, promote, or retire: capability planning, roadmap evolution,
 * and lifecycle phase transitions informed by the Learn phase outputs.
 *
 * @doc.type route
 * @doc.purpose Evolve phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { EvolveCockpitRoute } from './_phaseCockpit';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

export default EvolveCockpitRoute;
export function ErrorBoundary() {
  return <RouteErrorBoundary />;
}
