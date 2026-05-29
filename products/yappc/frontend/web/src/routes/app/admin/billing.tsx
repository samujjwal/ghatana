/**
 * Admin: Billing Route
 *
 * Gated to OWNER/ADMIN roles AND requires the billing backend to be live.
 * When the backend is not live (`admin:billing` capability = denied with
 * reason `backend-not-live`), renders a "coming soon" placeholder rather
 * than broken empty state.
 *
 * @doc.type route
 * @doc.purpose Workspace billing page with dual-gate (role + backend)
 * @doc.layer routes
 * @doc.pattern Route Module
 */

import React, { Suspense } from 'react';

import { useCapabilityGate } from '../../../hooks/useCapabilityGate';
import { YappcPageShell } from '../../../components/layout/YappcPageShell';
import { RouteLoadingSpinner } from '../../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { AdminRouteGate } from './AdminRouteGate';

// ── Coming-soon placeholder ────────────────────────────────────────────────────

function BillingComingSoon({ reason }: { reason: 'backend-not-live' | 'insufficient-role' | 'unauthenticated' | undefined }) {
  const message =
    reason === 'insufficient-role'
      ? 'You do not have permission to view billing details.'
      : reason === 'unauthenticated'
      ? 'Please log in to access billing.'
      : 'Billing is not yet available in this workspace.';

  return (
    <div className="flex min-h-[40vh] items-center justify-center" data-testid="billing-unavailable">
      <div className="max-w-sm space-y-2 text-center">
        <p className="text-sm text-fg-muted">{message}</p>
      </div>
    </div>
  );
}

// ── Gate wrapper ───────────────────────────────────────────────────────────────

function BillingGate({ children }: { children: React.ReactNode }) {
  const { granted, reason } = useCapabilityGate('admin:billing');

  if (!granted) {
    return <BillingComingSoon reason={reason} />;
  }

  return <>{children}</>;
}

// ── Page (lazy-loaded when backend is live) ────────────────────────────────────

/** Placeholder for the real BillingPage — replace when billing backend ships. */
function BillingPage() {
  return (
    <div data-testid="billing-page">
      <h1 className="text-xl font-semibold text-text-primary">Billing</h1>
    </div>
  );
}

// ── Route entry ───────────────────────────────────────────────────────────────

export function Component() {
  return (
    <AdminRouteGate capability="admin:billing" deniedTestId="admin-billing-unavailable">
      <YappcPageShell title="Billing" description="Monitor billing, plan limits, and payment readiness." testId="admin-billing-shell">
        <BillingGate>
          <Suspense fallback={<RouteLoadingSpinner />}>
            <BillingPage />
          </Suspense>
        </BillingGate>
      </YappcPageShell>
    </AdminRouteGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
