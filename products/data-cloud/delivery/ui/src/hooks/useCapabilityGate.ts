/**
 * Runtime capability gating hook (P0.7).
 *
 * Hides UI controls that are not backed by a live API capability.
 * Uses the runtime /api/v1/capabilities endpoint as the single source of truth.
 *
 * @doc.type hook
 * @doc.purpose Runtime capability-based UI gating
 * @doc.layer frontend
 */

import { useCapabilityRegistry, getCapabilitySignal, type CapabilitySignal } from '../api/surfaces.service';

export type GateMode = 'active' | 'activeOrDegraded' | 'notUnavailable';

function isAllowed(status: CapabilitySignal['status'], mode: GateMode): boolean {
  switch (mode) {
    case 'active':
      return status === 'active';
    case 'activeOrDegraded':
      return status === 'active' || status === 'degraded';
    case 'notUnavailable':
      return status !== 'unavailable';
  }
}

/**
 * Check whether a runtime capability is available according to the live registry.
 *
 * @param aliases - One or more capability keys to check (first match wins)
 * @param mode - Gate strictness (default: 'active')
 * @returns boolean indicating whether the capability is available
 */
export function useCapabilityGate(aliases: string[], mode: GateMode = 'active'): boolean {
  const { data, isLoading } = useCapabilityRegistry();

  if (isLoading || !data) {
    // While loading, default to open to avoid flicker; strict pages can check isLoading separately
    return true;
  }

  const signal = getCapabilitySignal(data.capabilities, aliases);
  if (!signal) {
    return false;
  }

  return isAllowed(signal.status, mode);
}

/**
 * Get the live capability signal for display / diagnostics.
 */
export function useCapabilitySignal(aliases: string[]): CapabilitySignal | undefined {
  const { data } = useCapabilityRegistry();
  if (!data) return undefined;
  return getCapabilitySignal(data.capabilities, aliases);
}
