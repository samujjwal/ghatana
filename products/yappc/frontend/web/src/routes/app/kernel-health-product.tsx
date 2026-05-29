/**
 * Kernel Health Product Detail Route
 *
 * Renders the Kernel Health Dashboard in detail view for a specific ProductUnit.
 * The :productUnitId param is forwarded to the page via React Router's useParams.
 * Access is restricted to OWNER/ADMIN roles via capability gating.
 *
 * @doc.type route
 * @doc.purpose Kernel health product detail page
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
    <AdminRouteGate capability="kernel-health:read" deniedTestId="kernel-health-product-unavailable">
      <YappcPageShell
        title="Kernel Health"
        description="Inspect lifecycle truth and health status for a selected product unit."
        testId="kernel-health-product-shell"
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
