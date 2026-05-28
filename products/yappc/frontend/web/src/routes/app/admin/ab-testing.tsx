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
import { AdminRouteGate } from './AdminRouteGate';

export function Component() {
  return (
    <AdminRouteGate capability="admin:ab-testing" deniedTestId="admin-ab-testing-unavailable">
      <Suspense fallback={<RouteLoadingSpinner />}>
        <ABTestingDashboardPage className="min-h-screen bg-surface" />
      </Suspense>
    </AdminRouteGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
