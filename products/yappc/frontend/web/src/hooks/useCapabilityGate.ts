/**
 * useCapabilityGate Hook — YAPPC Web.
 *
 * Determines whether a named product capability is available to the current user.
 * Capabilities gate entire page-sections that require both a user role AND a backend
 * feature flag before they become accessible (F-Y012, F-Y013, SIMP-Y5).
 *
 * ## Rationale
 * Some YAPPC surfaces (ops dashboards, billing, team management) are only available
 * when the supporting backend is live **and** the user has sufficient privileges.
 * Gating them in two dimensions — role + backend flag — avoids showing broken UI or
 * leaking admin surfaces to unprivileged users.
 *
 * ## Capability Registry
 * Each capability string maps to a `CapabilityConfig` that declares:
 * - `requiredRoles`: roles that may access the surface when it is enabled.
 * - `enabled`: whether the backend supporting this surface is deployed. Starts `false`
 *   for surfaces whose backends are not yet live. Set to `true` when the backend ships.
 *
 * ## Usage
 * ```tsx
 * const { granted, reason } = useCapabilityGate('ops:alerts');
 * if (!granted) return <ComingSoon reason={reason} />;
 * return <AlertsPage />;
 * ```
 *
 * @doc.type hook
 * @doc.purpose Role + backend-flag capability gate for privileged page surfaces
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useMemo } from 'react';

import { useAuth } from './useAuth';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

/** Named YAPPC capabilities that gate privileged or backend-dependent surfaces. */
export type CapabilityName =
  | 'ops:alerts'
  | 'ops:incidents'
  | 'ops:runbooks'
  | 'ops:oncall'
  | 'ops:warroom'
  | 'ops:postmortems'
  | 'ops:servicemap'
  | 'ops:metrics'
  | 'ops:logs'
  | 'ops:dashboards'
  | 'admin:billing'
  | 'admin:teams'
  | 'admin:prompt-versions'
  | 'admin:ab-testing'
  | 'admin:feature-flags';

/** Reason why a capability was denied, useful for rendering contextual messages. */
export type DenialReason =
  | 'backend-not-live'
  | 'insufficient-role'
  | 'unauthenticated';

/** Return value of {@link useCapabilityGate}. */
export interface CapabilityGateResult {
  /** Whether the capability is granted for the current user. */
  granted: boolean;
  /**
   * Reason for denial when {@link granted} is false.
   * `undefined` when {@link granted} is true.
   */
  reason: DenialReason | undefined;
}

// ─────────────────────────────────────────────────────────────────────────────
// Capability registry
// ─────────────────────────────────────────────────────────────────────────────

interface CapabilityConfig {
  /**
   * Whether the backend supporting this capability is deployed.
   * Set to `false` (default) for surfaces whose backends are not yet live.
   * Flip to `true` when the backend ships — no code changes needed in consumers.
   */
  enabled: boolean;
  /** Roles that are allowed to access the surface when it is enabled. */
  requiredRoles: readonly string[];
}

/**
 * Central registry of YAPPC capabilities.
 *
 * Ops pages (F-Y012): gated until the YAPPC ops backend is live.
 * Billing (F-Y013): gated until billing integration is deployed.
 * Teams (F-Y013): gated to OWNER and ADMIN roles.
 */
const CAPABILITY_REGISTRY: Readonly<Record<CapabilityName, CapabilityConfig>> = {
  // ── Ops surfaces (F-Y012) ──────────────────────────────────────────────────
  // All ops pages require the ops backend to be live. Flip `enabled: true`
  // when the YAPPC Ops backend (Alerts/Incidents/On-Call) is deployed.
  'ops:alerts':       { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD', 'DEVELOPER'] },
  'ops:incidents':    { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD', 'DEVELOPER'] },
  'ops:runbooks':     { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD', 'DEVELOPER'] },
  'ops:oncall':       { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD'] },
  'ops:warroom':      { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD'] },
  'ops:postmortems':  { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD', 'DEVELOPER'] },
  'ops:servicemap':   { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD', 'DEVELOPER'] },
  'ops:metrics':      { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD', 'DEVELOPER'] },
  'ops:logs':         { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD', 'DEVELOPER'] },
  'ops:dashboards':   { enabled: false, requiredRoles: ['OWNER', 'ADMIN', 'LEAD', 'DEVELOPER'] },

  // ── Admin surfaces (F-Y013) ───────────────────────────────────────────────
  // Billing requires the billing integration backend. Flip `enabled: true` when
  // the Stripe/billing service is wired up.
  'admin:billing': { enabled: false, requiredRoles: ['OWNER', 'ADMIN'] },
  // Teams is role-gated but can be shown once the teams API is live.
  'admin:teams':   { enabled: false, requiredRoles: ['OWNER', 'ADMIN'] },
  // Prompt version management — enabled when PromptVersioningService is live.
  'admin:prompt-versions': { enabled: true, requiredRoles: ['OWNER', 'ADMIN'] },
  // A/B testing dashboard — enabled when ABTestingEvaluationService is live.
  'admin:ab-testing': { enabled: true, requiredRoles: ['OWNER', 'ADMIN'] },
  // Tenant-scoped feature flag management (F-Y047).
  'admin:feature-flags': { enabled: true, requiredRoles: ['OWNER', 'ADMIN'] },
};

// ─────────────────────────────────────────────────────────────────────────────
// Hook
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Determines whether a named capability is granted for the current user.
 *
 * A capability is granted when ALL of the following hold:
 * 1. The user is authenticated.
 * 2. The backend for the capability is enabled (`enabled: true` in the registry).
 * 3. The user holds at least one of the capability's `requiredRoles`.
 *
 * @param capability The capability to check.
 * @returns `{ granted, reason }` — reason is undefined when granted is true.
 */
export function useCapabilityGate(capability: CapabilityName): CapabilityGateResult {
  const { isAuthenticated, hasRole } = useAuth();

  return useMemo((): CapabilityGateResult => {
    if (!isAuthenticated) {
      return { granted: false, reason: 'unauthenticated' };
    }

    const config = CAPABILITY_REGISTRY[capability];

    if (!config.enabled) {
      return { granted: false, reason: 'backend-not-live' };
    }

    const roleGranted = config.requiredRoles.some((role) => hasRole(role));
    if (!roleGranted) {
      return { granted: false, reason: 'insufficient-role' };
    }

    return { granted: true, reason: undefined };
  }, [capability, isAuthenticated, hasRole]);
}
