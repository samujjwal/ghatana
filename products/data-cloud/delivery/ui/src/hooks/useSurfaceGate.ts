/**
 * Runtime surface gating hook (P0.7).
 *
 * Hides UI controls that are not backed by a live runtime surface signal.
 * Uses the runtime /api/v1/surfaces endpoint as the single source of truth,
 * with compatibility fallback when needed.
 *
 * @doc.type hook
 * @doc.purpose Runtime surface-based UI gating
 * @doc.layer frontend
 */

import { useSurfaceRegistry, getSurfaceSignal, type SurfaceSignal } from '../api/surfaces.service';

export type GateMode = 'active' | 'activeOrDegraded' | 'notUnavailable';

function isAllowed(status: SurfaceSignal['status'], mode: GateMode): boolean {
  switch (mode) {
    case 'active':
      return status === 'LIVE';
    case 'activeOrDegraded':
      return status === 'LIVE' || status === 'DEGRADED' || status === 'PREVIEW';
    case 'notUnavailable':
      return status !== 'UNAVAILABLE' && status !== 'DISABLED' && status !== 'MISCONFIGURED';
  }
}

/**
 * Check whether a runtime surface is available according to the live registry.
 *
 * @param aliases - One or more runtime surface keys to check (first match wins)
 * @param mode - Gate strictness (default: 'active')
 * @returns boolean indicating whether the runtime surface is available
 */
export function useSurfaceGate(aliases: string[], mode: GateMode = 'active'): boolean {
  const { data, isLoading } = useSurfaceRegistry();

  if (isLoading || !data) {
    // While loading, default to open to avoid flicker; strict pages can check isLoading separately
    return true;
  }

  const signal = getSurfaceSignal(data.surfaces, aliases);
  if (!signal) {
    return false;
  }

  return isAllowed(signal.status, mode);
}

/**
 * Get the live runtime surface signal for display / diagnostics.
 */
export function useSurfaceSignal(aliases: string[]): SurfaceSignal | undefined {
  const { data } = useSurfaceRegistry();
  if (!data) return undefined;
  return getSurfaceSignal(data.surfaces, aliases);
}

// Backward compatibility aliases (deprecated)
export const useCapabilityGate = useSurfaceGate;
export const useCapabilitySignal = useSurfaceSignal;
