/**
 * Validate Phase — Review and gate requirements
 *
 * Approval workflows, requirement reviews, and governance gates.
 * Uses the lifecycle page which surfaces the requirements/approvals board.
 *
 * @doc.type route
 * @doc.purpose Validate phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { ValidateCockpitRoute } from './_phaseCockpit';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

export default ValidateCockpitRoute;
export function ErrorBoundary() {
  return <RouteErrorBoundary />;
}
