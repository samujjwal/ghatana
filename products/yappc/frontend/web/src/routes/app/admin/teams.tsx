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

import React from 'react';

import { useCapabilityGate } from '../../../hooks/useCapabilityGate';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

const TEAMS_BACKEND_LIVE = false;

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
        <p className="text-sm text-fg-muted">{message}</p>
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

  if (!TEAMS_BACKEND_LIVE) {
    return <TeamsComingSoon reason="backend-not-live" />;
  }

  return <>{children}</>;
}

// ── Route entry ───────────────────────────────────────────────────────────────

export function Component() {
  return (
    <TeamsGate>
      <div className="p-6" data-testid="teams-page" />
    </TeamsGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
