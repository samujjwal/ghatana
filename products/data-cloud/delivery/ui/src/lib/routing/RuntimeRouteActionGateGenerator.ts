/**
 * Runtime Route/Action Gate Generator
 *
 * Generates route gate metadata directly from runtime-truth capability snapshots
 * plus the canonical route registry.
 *
 * @doc.type module
 * @doc.purpose Generate route/action gates from runtime truth capability signals
 * @doc.layer shared
 * @doc.pattern Generator
 */

import type { CapabilitySignal } from '@/api/capabilities.service';
import {
  canonicalRouteRegistry,
  type RouteCapability,
  type RouteLifecycle,
} from './RouteCapabilityRegistry';

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
  minimumShellRole: RouteCapability['minimumShellRole'];
  discoverable: boolean;
  status: GeneratedGateStatus;
  actions: GeneratedActionGate[];
}

function normalizeStatus(status: CapabilitySignal['status']): GeneratedGateStatus {
  if (status === 'active' || status === 'degraded' || status === 'unavailable') {
    return status;
  }
  return 'unknown';
}

function toLookup(capabilities: CapabilitySignal[]): Map<string, CapabilitySignal> {
  const lookup = new Map<string, CapabilitySignal>();
  for (const capability of capabilities) {
    lookup.set(capability.key.toLowerCase(), capability);
  }
  return lookup;
}

function canonicalToken(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function resolveCapabilitySignal(
  alias: string,
  lookup: Map<string, CapabilitySignal>,
): CapabilitySignal | undefined {
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

export function generateRouteActionGates(capabilities: CapabilitySignal[]): GeneratedRouteGate[] {
  const lookup = toLookup(capabilities);

  return Object.values(canonicalRouteRegistry)
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
