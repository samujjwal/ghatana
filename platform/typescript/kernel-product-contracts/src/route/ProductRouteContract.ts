/**
 * K-001: Kernel product route contract schema.
 * PHR uses Kernel product route contract instead of product-local-only schema.
 */

export type RouteStability = 'stable' | 'preview' | 'blocked' | 'hidden';

export type RouteGroup = 'care' | 'governance' | 'clinical' | 'administrative' | 'profile' | 'dashboard';

export type RouteAction = {
  id: string;
  label: string;
  endpoint: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  policyId?: string;
  idempotent?: boolean;
  confirmationRequired?: boolean;
  visibility?: 'public' | 'authenticated' | 'role-restricted';
};

export type RouteCard = {
  id: string;
  title: string;
  description: string;
  icon?: string;
  badge?: string;
};

export type RouteMetadata = {
  apiEndpoint?: string;
  policyId?: string;
  testId?: string;
  featureFlag?: string;
  introducedAt?: string;
};

export type ProductRoute = {
  path: string;
  label: string;
  description: string;
  group: RouteGroup;
  minimumRole: string;
  personas?: string[];
  tiers?: string[];
  actions?: RouteAction[];
  cards?: RouteCard[];
  stability: RouteStability;
  featureFlag?: boolean;
  metadata?: RouteMetadata;
};

export type ProductRouteContract = {
  version: string;
  roleOrder: Record<string, number>;
  routes: ProductRoute[];
};

export const RouteStabilityValues: RouteStability[] = ['stable', 'preview', 'blocked', 'hidden'];
export const RouteGroupValues: RouteGroup[] = ['care', 'governance', 'clinical', 'administrative', 'profile', 'dashboard'];

export function isRouteStability(value: unknown): value is RouteStability {
  return typeof value === 'string' && RouteStabilityValues.includes(value as RouteStability);
}

export function isRouteGroup(value: unknown): value is RouteGroup {
  return typeof value === 'string' && RouteGroupValues.includes(value as RouteGroup);
}

export function validateProductRouteContract(contract: unknown): contract is ProductRouteContract {
  if (typeof contract !== 'object' || contract === null) return false;
  
  const c = contract as Record<string, unknown>;
  
  if (typeof c.version !== 'string') return false;
  if (typeof c.roleOrder !== 'object' || c.roleOrder === null) return false;
  if (!Array.isArray(c.routes)) return false;
  
  for (const route of c.routes) {
    if (typeof route !== 'object' || route === null) return false;
    const r = route as Record<string, unknown>;
    
    if (typeof r.path !== 'string') return false;
    if (typeof r.label !== 'string') return false;
    if (typeof r.description !== 'string') return false;
    if (!isRouteGroup(r.group)) return false;
    if (typeof r.minimumRole !== 'string') return false;
    if (!isRouteStability(r.stability)) return false;
  }
  
  return true;
}
