/**
 * Admin: Feature Flags Route
 *
 * Operator-level route that renders FeatureFlagsPage behind a capability
 * gate restricting access to OWNER and ADMIN roles (F-Y047).
 *
 * @doc.type route
 * @doc.purpose Admin tenant feature flag management page
 * @doc.layer routes
 * @doc.pattern Route Module
 */

import { Suspense } from 'react';
import { RouteLoadingSpinner } from '../../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { FeatureFlagsPage } from '../../../components/admin/FeatureFlagsPage';
import { AdminRouteGate } from './AdminRouteGate';

export function Component() {
  return (
    <AdminRouteGate capability="admin:feature-flags" deniedTestId="admin-feature-flags-unavailable">
      <Suspense fallback={<RouteLoadingSpinner />}>
        <FeatureFlagsPage className="min-h-screen bg-surface" />
      </Suspense>
    </AdminRouteGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;
