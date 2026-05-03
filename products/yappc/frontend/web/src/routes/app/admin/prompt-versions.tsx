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
import { useCapabilityGate } from '../../../hooks/useCapabilityGate';
import { RouteLoadingSpinner } from '../../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { PromptVersionsPage } from '../../../components/admin/PromptVersionsPage';

function AdminGate({ children }: { children: React.ReactNode }) {
  const { granted, reason } = useCapabilityGate('admin:prompt-versions');

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
    <AdminGate>
      <Suspense fallback={<RouteLoadingSpinner />}>
        <PromptVersionsPage className="min-h-screen bg-zinc-950" />
      </Suspense>
    </AdminGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
