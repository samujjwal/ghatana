/**
 * Project Ops: Alerts Route (C-Y9)
 *
 * Ops pages must NOT render content when the ops backend is not wired up.
 * This route uses `useCapabilityGate('ops:alerts')` — which returns
 * `backend-not-live` until the YAPPC Ops backend is deployed — and shows
 * a clear "not yet available" placeholder instead of an empty or broken page.
 *
 * @doc.type route
 * @doc.purpose Ops alerts page gated on ops backend availability
 * @doc.layer routes
 * @doc.pattern Route Module
 */

import React, { Suspense } from 'react';

import { useCapabilityGate } from '../../../hooks/useCapabilityGate';
import { RouteLoadingSpinner } from '../../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

// ── Unavailable placeholder ────────────────────────────────────────────────────

function OpsUnavailable({
  label,
  reason,
}: {
  label: string;
  reason: 'backend-not-live' | 'insufficient-role' | 'unauthenticated' | undefined;
}) {
  const message =
    reason === 'insufficient-role'
      ? `You do not have permission to view ${label}.`
      : reason === 'unauthenticated'
      ? `Please log in to access ${label}.`
      : `${label} requires the operations backend which is not yet available.`;

  return (
    <div
      className="flex min-h-[60vh] items-center justify-center"
      data-testid="ops-unavailable"
    >
      <div className="max-w-sm space-y-2 text-center">
        <p className="text-sm text-zinc-400">{message}</p>
      </div>
    </div>
  );
}

// ── Gate wrapper ───────────────────────────────────────────────────────────────

function OpsAlertsGate({ children }: { children: React.ReactNode }) {
  const { granted, reason } = useCapabilityGate('ops:alerts');

  if (!granted) {
    return <OpsUnavailable label="Alerts" reason={reason} />;
  }

  return <>{children}</>;
}

// ── Page placeholder ──────────────────────────────────────────────────────────

/** Placeholder rendered when the ops backend is live. */
function AlertsPage() {
  return (
    <div className="p-6" data-testid="ops-alerts-page">
      <h1 className="text-xl font-semibold text-text-primary">Alerts</h1>
    </div>
  );
}

// ── Route entry ───────────────────────────────────────────────────────────────

export function Component() {
  return (
    <OpsAlertsGate>
      <Suspense fallback={<RouteLoadingSpinner />}>
        <AlertsPage />
      </Suspense>
    </OpsAlertsGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
