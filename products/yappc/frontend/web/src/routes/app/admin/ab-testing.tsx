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
import { useCapabilityGate } from '../../../hooks/useCapabilityGate';
import { RouteLoadingSpinner } from '../../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { ABTestingDashboardPage } from '../../../components/admin/ABTestingDashboardPage';

function AdminGate({ children }: { children: React.ReactNode }) {
  const { granted, reason } = useCapabilityGate('admin:ab-testing');

  if (!granted) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center space-y-2">
          <p className="text-zinc-400 text-sm">
            {reason === 'insufficient-role'
              ? 'You do not have permission to access this page.'
              : reason === 'unauthenticated'
              ? 'Please log in to access this page.'
              : 'This feature is not yet available.'}
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}

export function Component() {
  return (
    <RouteErrorBoundary>
      <AdminGate>
        <Suspense fallback={<RouteLoadingSpinner />}>
          <ABTestingDashboardPage className="min-h-screen bg-zinc-950" />
        </Suspense>
      </AdminGate>
    </RouteErrorBoundary>
  );
}

export default Component;
