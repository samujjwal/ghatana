/**
 * Generate Phase — AI-powered artefact generation
 *
 * Code, test, and documentation generation via the unified canvas.
 * The canvas carries all Epic 1–10 AI generation capabilities.
 *
 * @doc.type route
 * @doc.purpose Generate phase page
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { GenerateCockpitRoute } from './_phaseCockpit';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

export default GenerateCockpitRoute;
export function ErrorBoundary() {
  return <RouteErrorBoundary />;
}
