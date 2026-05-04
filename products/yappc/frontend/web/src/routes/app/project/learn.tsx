/**
 * Learn Phase — Retrospectives and AI insights
 *
 * Knowledge capture, AI-generated insights, retrospectives,
 * and pattern learning from previous cycles.
 *
 * @doc.type route
 * @doc.purpose Learn phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { LearnCockpitRoute } from './_phaseCockpit';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

export default LearnCockpitRoute;
export function ErrorBoundary() {
  return <RouteErrorBoundary />;
}
