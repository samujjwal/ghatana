/**
 * Admin: Prompt Versions Route
 *
 * Admin-only route that renders the PromptVersionsPage behind a capability
 * gate restricting access to OWNER and ADMIN roles.
 *
 * @doc.type route
 * @doc.purpose Admin prompt version management page
 * @doc.layer routes
 * @doc.pattern Route Module
 */

import { Suspense } from 'react';
import { RouteLoadingSpinner } from '../../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { PromptVersionsPage } from '../../../components/admin/PromptVersionsPage';
import { YappcPageShell } from '../../../components/layout/YappcPageShell';
import { AdminRouteGate } from './AdminRouteGate';

export function Component() {
  return (
    <AdminRouteGate capability="admin:prompt-versions" deniedTestId="admin-prompt-versions-unavailable">
      <YappcPageShell
        title="Prompt Versions"
        description="Review and promote prompt revisions with audit-safe rollout context."
        testId="admin-prompt-versions-shell"
      >
        <Suspense fallback={<RouteLoadingSpinner />}>
          <PromptVersionsPage className="bg-surface" />
        </Suspense>
      </YappcPageShell>
    </AdminRouteGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
