/**
 * Admin: Teams Route
 *
 * Gated to OWNER/ADMIN roles AND requires the teams backend to be live.
 * When denied, renders a contextual "not yet available" or "permission denied"
 * placeholder rather than a broken empty page.
 *
 * @doc.type route
 * @doc.purpose Workspace team management page with dual-gate (role + backend)
 * @doc.layer routes
 * @doc.pattern Route Module
 */

import React, { Suspense } from 'react';

import { useCapabilityGate } from '../../../hooks/useCapabilityGate';
import { RouteLoadingSpinner } from '../../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

// ── Coming-soon placeholder ────────────────────────────────────────────────────

function TeamsComingSoon({ reason }: { reason: 'backend-not-live' | 'insufficient-role' | 'unauthenticated' | undefined }) {
  const message =
    reason === 'insufficient-role'
      ? 'You do not have permission to manage teams.'
      : reason === 'unauthenticated'
      ? 'Please log in to access team management.'
      : 'Team management is not yet available in this workspace.';

  return (
    <div
      className="flex min-h-[60vh] items-center justify-center"
      data-testid="teams-unavailable"
    >
      <div className="max-w-sm space-y-2 text-center">
        <p className="text-sm text-zinc-400">{message}</p>
      </div>
    </div>
  );
}

// ── Gate wrapper ───────────────────────────────────────────────────────────────

function TeamsGate({ children }: { children: React.ReactNode }) {
  const { granted, reason } = useCapabilityGate('admin:teams');

  if (!granted) {
    return <TeamsComingSoon reason={reason} />;
  }

  return <>{children}</>;
}

// ── Page (lazy-loaded when backend is live) ────────────────────────────────────

/** Placeholder for the real TeamsPage — replace when teams API ships. */
function TeamsPage() {
  return (
    <div className="p-6" data-testid="teams-page">
      <h1 className="text-xl font-semibold text-text-primary">Team Management</h1>
    </div>
  );
}

// ── Route entry ───────────────────────────────────────────────────────────────

export function Component() {
  return (
    <TeamsGate>
      <Suspense fallback={<RouteLoadingSpinner />}>
        <TeamsPage />
      </Suspense>
    </TeamsGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
