/**
 * CapabilityGate Component — YAPPC Web.
 *
 * Conditionally renders children when the named capability is granted.
 * Renders a fallback (default: nothing) when the capability is denied.
 *
 * ## Usage — wrap routes or page sections
 * ```tsx
 * <CapabilityGate capability="ops:alerts">
 *   <AlertsPage />
 * </CapabilityGate>
 *
 * <CapabilityGate
 *   capability="admin:billing"
 *   fallback={<ComingSoon label="Billing" />}
 * >
 *   <BillingPage />
 * </CapabilityGate>
 * ```
 *
 * @doc.type component
 * @doc.purpose Declarative capability gate for role + backend-flag protected surfaces
 * @doc.layer product
 * @doc.pattern Gate
 */

import React from 'react';

import { useCapabilityGate } from '../../hooks/useCapabilityGate';
import type { CapabilityName } from '../../hooks/useCapabilityGate';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

interface CapabilityGateProps {
  /** The capability to check before rendering children. */
  capability: CapabilityName;
  /** Content rendered when the capability is granted. */
  children: React.ReactNode;
  /**
   * Content rendered when the capability is denied.
   * Defaults to `null` (render nothing).
   */
  fallback?: React.ReactNode;
}

// ─────────────────────────────────────────────────────────────────────────────
// Component
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Conditionally renders `children` when the named capability is granted for
 * the current user. Renders `fallback` (default `null`) when denied.
 */
export const CapabilityGate: React.FC<CapabilityGateProps> = ({
  capability,
  children,
  fallback = null,
}) => {
  const { granted } = useCapabilityGate(capability);

  // eslint-disable-next-line react/jsx-no-useless-fragment
  return granted ? <>{children}</> : <>{fallback}</>;
};
