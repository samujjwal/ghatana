import { useEffect, useMemo, useState } from 'react';
import type { ProductRouteCapability, ProductRouteEntitlement } from './types';
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
        const payload = (await response.json()) as ProductRouteEntitlement;
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

  const routes = useMemo(
    () => hydrateRoutesFromEntitlement(fallbackRoutes, entitlement),
    [fallbackRoutes, entitlement],
  );

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
