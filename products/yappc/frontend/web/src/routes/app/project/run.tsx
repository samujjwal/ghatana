/**
 * Run Phase — Execute pipelines and deployments
 *
 * Pipeline execution, deployment management, and agent workflow runs.
 * Admin tools (prompt versions, A/B testing) surface as context-sensitive
 * panels within this phase via the header actions menu.
 *
 * NOTE: This phase is gated behind the PHASE_RUN feature flag until GitHub Actions
 * CI/CD integration is complete. See R-6 in the audit document.
 *
 * @doc.type route
 * @doc.purpose Run phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { RunCockpitRoute } from './_phaseCockpit';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

export default RunCockpitRoute;
export function Component() {
  return <RunCockpitRoute />;
}

export function ErrorBoundary() {
  return <RouteErrorBoundary />;
}
