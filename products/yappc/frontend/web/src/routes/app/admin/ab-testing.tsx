/**
 * Admin: A/B Testing Route
 *
 * Operator-level route that renders ABTestingDashboardPage behind a capability
 * gate restricting access to OWNER and ADMIN roles.
 *
 * @doc.type route
 * @doc.purpose Admin A/B testing dashboard page
 * @doc.layer routes
 * @doc.pattern Route Module
 */

import { Suspense } from 'react';
import { RouteLoadingSpinner } from '../../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { ABTestingDashboardPage } from '../../../components/admin/ABTestingDashboardPage';
import { YappcPageShell } from '../../../components/layout/YappcPageShell';
import { AdminRouteGate } from './AdminRouteGate';

export function Component() {
  return (
    <AdminRouteGate capability="admin:ab-testing" deniedTestId="admin-ab-testing-unavailable">
      <YappcPageShell
        title="A/B Testing"
        description="Track experiment readiness, outcomes, and release guardrails."
        testId="admin-ab-testing-shell"
      >
        <Suspense fallback={<RouteLoadingSpinner />}>
          <ABTestingDashboardPage className="bg-surface" />
        </Suspense>
      </YappcPageShell>
    </AdminRouteGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
