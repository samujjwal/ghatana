/**
 * Kernel Health Dashboard Route
 *
 * Renders the Kernel Health Dashboard in list view (no specific ProductUnit selected).
 * Access is restricted to OWNER/ADMIN roles via capability gating.
 *
 * @doc.type route
 * @doc.purpose Kernel health dashboard page
 * @doc.layer routes
 * @doc.pattern Route Module
 */

import { Suspense } from 'react';
import { KernelHealthDashboardPage } from '../../pages/kernel-health/KernelHealthDashboardPage';
import { YappcPageShell } from '../../components/layout/YappcPageShell';
import { RouteLoadingSpinner } from '../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../components/route/ErrorBoundary';
import { AdminRouteGate } from './admin/AdminRouteGate';

export function Component() {
  return (
    <AdminRouteGate capability="kernel-health:read" deniedTestId="kernel-health-unavailable">
      <YappcPageShell
        title="Kernel Health"
        description="Track product-unit lifecycle status, gate health, and recovery actions."
        testId="kernel-health-shell"
      >
        <Suspense fallback={<RouteLoadingSpinner />}>
          <KernelHealthDashboardPage />
        </Suspense>
      </YappcPageShell>
    </AdminRouteGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
