import { useEffect, useMemo, useState } from 'react';
import type {
  ProductEntitledAction,
  ProductEntitledCard,
  ProductRouteCapability,
  ProductRouteEntitlement,
} from './types';
import { hydrateRoutesFromEntitlement } from './access';

export type ProductEntitlementStatus = 'idle' | 'loading' | 'ready' | 'denied' | 'error';

export interface UseProductEntitlementsOptions {
  readonly endpoint: string | null;
  readonly fallbackRoutes: readonly ProductRouteCapability[];
  readonly fetcher?: typeof fetch;
  readonly enabled?: boolean;
  readonly requestInit?: RequestInit;
}

export interface UseProductEntitlementsResult {
  readonly status: ProductEntitlementStatus;
  readonly entitlement: ProductRouteEntitlement | null;
  readonly routes: readonly ProductRouteCapability[];
  readonly error: Error | null;
  readonly refresh: () => void;
}

const entitlementCache = new Map<string, ProductRouteEntitlement>();

function hideRoutes(routes: readonly ProductRouteCapability[]): readonly ProductRouteCapability[] {
  return routes.map((route) => ({ ...route, discoverable: false }));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isOptionalString(value: unknown): value is string | undefined {
  return value === undefined || typeof value === 'string';
}

function isStringArray(value: unknown): value is readonly string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string');
}

function requiredString(record: Record<string, unknown>, key: string): string {
  const value = record[key];
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Product entitlement payload must include ${key}`);
  }
  return value;
}

function optionalString(record: Record<string, unknown>, key: string): string | undefined {
  const value = record[key];
  if (!isOptionalString(value)) {
    throw new Error(`Product entitlement payload has an invalid ${key}`);
  }
  return value;
}

function parseRouteCapability(value: unknown, index: number): ProductRouteCapability {
  if (!isRecord(value)) {
    throw new Error(`Product entitlement route at index ${index} must be an object`);
  }

  if (typeof value.path !== 'string' || value.path.length === 0) {
    throw new Error(`Product entitlement route at index ${index} must include a path`);
  }
  if (typeof value.label !== 'string' || value.label.length === 0) {
    throw new Error(`Product entitlement route ${value.path} must include a label`);
  }
  if (!isOptionalString(value.description)) {
    throw new Error(`Product entitlement route ${value.path} has an invalid description`);
  }
  if (!isOptionalString(value.iconName)) {
    throw new Error(`Product entitlement route ${value.path} has an invalid iconName`);
  }
  if (!isOptionalString(value.group)) {
    throw new Error(`Product entitlement route ${value.path} has an invalid group`);
  }
  if (!isOptionalString(value.minimumRole)) {
    throw new Error(`Product entitlement route ${value.path} has an invalid minimumRole`);
  }
  if (
    value.lifecycle !== undefined &&
    value.lifecycle !== 'stable' &&
    value.lifecycle !== 'preview' &&
    value.lifecycle !== 'hidden' &&
    value.lifecycle !== 'blocked' &&
    value.lifecycle !== 'deferred' &&
    value.lifecycle !== 'removed' &&
    value.lifecycle !== 'boundary' &&
    value.lifecycle !== 'deprecated'
  ) {
    throw new Error(`Product entitlement route ${value.path} has an invalid lifecycle`);
  }
  if (
    value.stability !== undefined &&
    value.stability !== 'stable' &&
    value.stability !== 'preview' &&
    value.stability !== 'hidden' &&
    value.stability !== 'blocked' &&
    value.stability !== 'deferred' &&
    value.stability !== 'removed'
  ) {
    throw new Error(`Product entitlement route ${value.path} has an invalid stability`);
  }
  if (value.hidden !== undefined && typeof value.hidden !== 'boolean') {
    throw new Error(`Product entitlement route ${value.path} has an invalid hidden flag`);
  }
  if (value.blocked !== undefined && typeof value.blocked !== 'boolean') {
    throw new Error(`Product entitlement route ${value.path} has an invalid blocked flag`);
  }
  if (value.discoverable !== undefined && typeof value.discoverable !== 'boolean') {
    throw new Error(`Product entitlement route ${value.path} has an invalid discoverable flag`);
  }
  if (value.personas !== undefined && !isStringArray(value.personas)) {
    throw new Error(`Product entitlement route ${value.path} has invalid personas`);
  }
  if (value.tiers !== undefined && !isStringArray(value.tiers)) {
    throw new Error(`Product entitlement route ${value.path} has invalid tiers`);
  }
  if (value.actions !== undefined && !isStringArray(value.actions)) {
    throw new Error(`Product entitlement route ${value.path} has invalid actions`);
  }
  if (value.cards !== undefined && !isStringArray(value.cards)) {
    throw new Error(`Product entitlement route ${value.path} has invalid cards`);
  }

  const route: ProductRouteCapability = {
    path: value.path,
    label: value.label,
    ...(value.description !== undefined ? { description: value.description } : {}),
    ...(value.iconName !== undefined ? { iconName: value.iconName } : {}),
    ...(value.group !== undefined ? { group: value.group } : {}),
    ...(value.minimumRole !== undefined ? { minimumRole: value.minimumRole } : {}),
    ...(value.lifecycle !== undefined ? { lifecycle: value.lifecycle } : {}),
    ...(value.stability !== undefined ? { stability: value.stability } : {}),
    ...(value.hidden !== undefined ? { hidden: value.hidden } : {}),
    ...(value.blocked !== undefined ? { blocked: value.blocked } : {}),
    ...(value.discoverable !== undefined ? { discoverable: value.discoverable } : {}),
    ...(value.personas !== undefined ? { personas: value.personas } : {}),
    ...(value.tiers !== undefined ? { tiers: value.tiers } : {}),
    ...(value.actions !== undefined ? { actions: value.actions } : {}),
    ...(value.cards !== undefined ? { cards: value.cards } : {}),
  };

  return route;
}

function parseEntitledAction(value: unknown, index: number): ProductEntitledAction {
  if (!isRecord(value)) {
    throw new Error(`Product entitlement action at index ${index} must be an object`);
  }
  if (typeof value.id !== 'string' || value.id.length === 0) {
    throw new Error(`Product entitlement action at index ${index} must include an id`);
  }
  if (typeof value.label !== 'string' || value.label.length === 0) {
    throw new Error(`Product entitlement action ${value.id} must include a label`);
  }
  if (!isOptionalString(value.routePath)) {
    throw new Error(`Product entitlement action ${value.id} has an invalid routePath`);
  }
  if (value.requiresConfirmation !== undefined && typeof value.requiresConfirmation !== 'boolean') {
    throw new Error(`Product entitlement action ${value.id} has an invalid requiresConfirmation flag`);
  }

  return {
    id: value.id,
    label: value.label,
    ...(value.routePath !== undefined ? { routePath: value.routePath } : {}),
    ...(value.requiresConfirmation !== undefined ? { requiresConfirmation: value.requiresConfirmation } : {}),
  };
}

function parseEntitledCard(value: unknown, index: number): ProductEntitledCard {
  if (!isRecord(value)) {
    throw new Error(`Product entitlement card at index ${index} must be an object`);
  }
  if (typeof value.id !== 'string' || value.id.length === 0) {
    throw new Error(`Product entitlement card at index ${index} must include an id`);
  }
  if (typeof value.title !== 'string' || value.title.length === 0) {
    throw new Error(`Product entitlement card ${value.id} must include a title`);
  }
  if (!isOptionalString(value.routePath)) {
    throw new Error(`Product entitlement card ${value.id} has an invalid routePath`);
  }
  if (
    value.surface !== undefined &&
    value.surface !== 'dashboard' &&
    value.surface !== 'detail' &&
    value.surface !== 'sidebar' &&
    value.surface !== 'modal'
  ) {
    throw new Error(`Product entitlement card ${value.id} has an invalid surface`);
  }

  return {
    id: value.id,
    title: value.title,
    ...(value.routePath !== undefined ? { routePath: value.routePath } : {}),
    ...(value.surface !== undefined ? { surface: value.surface } : {}),
  };
}

function parseProductEntitlement(payload: unknown): ProductRouteEntitlement {
  if (!isRecord(payload)) {
    throw new Error('Product entitlement payload must be an object');
  }

  const product = requiredString(payload, 'product');
  const principalId = requiredString(payload, 'principalId');
  const tenantId = requiredString(payload, 'tenantId');
  const role = requiredString(payload, 'role');
  const persona = optionalString(payload, 'persona');
  const tier = optionalString(payload, 'tier');
  const correlationId = optionalString(payload, 'correlationId');
  if (!Array.isArray(payload.routes)) {
    throw new Error('Product entitlement payload must include routes');
  }

  return {
    product,
    principalId,
    tenantId,
    role,
    ...(persona !== undefined ? { persona } : {}),
    ...(tier !== undefined ? { tier } : {}),
    ...(correlationId !== undefined ? { correlationId } : {}),
    routes: payload.routes.map(parseRouteCapability),
    ...(Array.isArray(payload.actions) ? { actions: payload.actions.map(parseEntitledAction) } : {}),
    ...(Array.isArray(payload.cards) ? { cards: payload.cards.map(parseEntitledCard) } : {}),
  };
}

export function useProductEntitlements({
  endpoint,
  fallbackRoutes,
  fetcher = fetch,
  enabled = true,
  requestInit,
}: UseProductEntitlementsOptions): UseProductEntitlementsResult {
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [entitlement, setEntitlement] = useState<ProductRouteEntitlement | null>(() =>
    endpoint ? entitlementCache.get(endpoint) ?? null : null,
  );
  const [status, setStatus] = useState<ProductEntitlementStatus>(() =>
    entitlement ? 'ready' : 'idle',
  );
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!enabled || !endpoint) {
      setStatus('idle');
      setError(null);
      return;
    }

    const cached = entitlementCache.get(endpoint);
    if (cached) {
      setEntitlement(cached);
      setStatus('ready');
      setError(null);
      return;
    }

    const controller = new AbortController();
    setStatus('loading');
    setError(null);

    fetcher(endpoint, {
      ...requestInit,
      signal: requestInit?.signal ?? controller.signal,
    })
      .then(async (response) => {
        if (response.status === 401 || response.status === 403) {
          setStatus('denied');
          setEntitlement(null);
          return;
        }
        if (!response.ok) {
          throw new Error(`Product entitlement request failed with ${response.status}`);
        }
        const payload = parseProductEntitlement(await response.json());
        entitlementCache.set(endpoint, payload);
        setEntitlement(payload);
        setStatus('ready');
      })
      .catch((caught: unknown) => {
        if (caught instanceof DOMException && caught.name === 'AbortError') {
          return;
        }
        setError(caught instanceof Error ? caught : new Error(String(caught)));
        setStatus('error');
      });

    return () => controller.abort();
  }, [enabled, endpoint, fetcher, refreshVersion, requestInit]);

  const routes = useMemo(() => {
    if (entitlement) {
      return hydrateRoutesFromEntitlement(fallbackRoutes, entitlement);
    }

    if (enabled && endpoint) {
      return hideRoutes(fallbackRoutes);
    }

    return fallbackRoutes;
  }, [enabled, endpoint, fallbackRoutes, entitlement]);

  return {
    status,
    entitlement,
    routes,
    error,
    refresh: () => {
      if (endpoint) {
        entitlementCache.delete(endpoint);
      }
      setRefreshVersion((version) => version + 1);
    },
  };
}
