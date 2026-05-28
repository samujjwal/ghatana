/**
 * Runtime Route/Action Gate Generator
 *
 * Generates route gate metadata directly from runtime-truth surface snapshots
 * plus the canonical route registry.
 *
 * @doc.type module
 * @doc.purpose Generate route/action gates from runtime truth surface signals
 * @doc.layer shared
 * @doc.pattern Generator
 */

import type { SurfaceSignal } from '@/api/surfaces.service';
import {
  canonicalRouteSurfaceRegistry,
  type RouteSurface,
  type RouteLifecycle,
} from './RouteSurfaceRegistry';

export type GeneratedGateStatus = 'active' | 'degraded' | 'unavailable' | 'unknown';

export interface GeneratedActionGate {
  id: string;
  capabilityAlias: string;
  status: GeneratedGateStatus;
}

export interface GeneratedRouteGate {
  path: string;
  label: string;
  lifecycle: RouteLifecycle;
  minimumShellRole: RouteSurface['minimumShellRole'];
  discoverable: boolean;
  status: GeneratedGateStatus;
  actions: GeneratedActionGate[];
}

function normalizeStatus(status: SurfaceSignal['status']): GeneratedGateStatus {
  if (status === 'LIVE') {
    return 'active';
  }
  if (status === 'DEGRADED' || status === 'PREVIEW') {
    return 'degraded';
  }
  if (status === 'UNAVAILABLE' || status === 'DISABLED' || status === 'MISCONFIGURED') {
    return 'unavailable';
  }
  return 'unknown';
}

function toLookup(surfaces: SurfaceSignal[]): Map<string, SurfaceSignal> {
  const lookup = new Map<string, SurfaceSignal>();
  for (const surface of surfaces) {
    lookup.set(surface.key.toLowerCase(), surface);
  }
  return lookup;
}

function canonicalToken(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function resolveCapabilitySignal(
  alias: string,
  lookup: Map<string, SurfaceSignal>,
): SurfaceSignal | undefined {
  const normalizedAlias = alias.toLowerCase();
  const aliasToken = canonicalToken(alias);
  const exact = lookup.get(normalizedAlias);
  if (exact) {
    return exact;
  }

  // Fallback: match suffix tokens for capability aliases like `event-stream` vs `events.streaming`.
  const suffix = normalizedAlias.replace(/-/g, '.');
  for (const [key, capability] of lookup.entries()) {
    const keyToken = canonicalToken(key);
    if (key.endsWith(suffix) || key.includes(normalizedAlias) || keyToken.includes(aliasToken)) {
      return capability;
    }
  }

  return undefined;
}

function reduceRouteStatus(actions: GeneratedActionGate[]): GeneratedGateStatus {
  if (actions.some((action) => action.status === 'active')) {
    return 'active';
  }
  if (actions.some((action) => action.status === 'degraded')) {
    return 'degraded';
  }
  if (actions.some((action) => action.status === 'unavailable')) {
    return 'unavailable';
  }
  return 'unknown';
}

export function generateRouteActionGates(surfaces: SurfaceSignal[]): GeneratedRouteGate[] {
  const lookup = toLookup(surfaces);

  return Object.values(canonicalRouteSurfaceRegistry)
    .map((route) => {
      const actions = route.capabilities.map((alias) => {
        const signal = resolveCapabilitySignal(alias, lookup);
        return {
          id: `${route.path}:${alias}`,
          capabilityAlias: alias,
          status: signal ? normalizeStatus(signal.status) : 'unknown',
        } satisfies GeneratedActionGate;
      });

      return {
        path: route.path,
        label: route.label,
        lifecycle: route.lifecycle,
        minimumShellRole: route.minimumShellRole,
        discoverable: route.discoverable,
        status: reduceRouteStatus(actions),
        actions,
      } satisfies GeneratedRouteGate;
    })
    .sort((left, right) => left.path.localeCompare(right.path));
}
