import type { ProductRouteCapability, ProductRouteEntitlement } from './types';

export function resolveHighestRole(
  roles: readonly string[] | undefined,
  roleOrder: Readonly<Record<string, number>>,
  fallbackRole: string,
): string {
  if (!roles || roles.length === 0) {
    return fallbackRole;
  }

  return roles.reduce((highestRole, candidateRole) => {
    const highestOrder = roleOrder[highestRole] ?? Number.NEGATIVE_INFINITY;
    const candidateOrder = roleOrder[candidateRole] ?? Number.NEGATIVE_INFINITY;
    return candidateOrder > highestOrder ? candidateRole : highestRole;
  }, fallbackRole);
}

export function isRouteAllowed(
  route: Pick<ProductRouteCapability, 'minimumRole'>,
  role: string,
  roleOrder: Readonly<Record<string, number>>,
): boolean {
  if (!route.minimumRole) {
    return true;
  }

  const currentOrder = roleOrder[role];
  const minimumOrder = roleOrder[route.minimumRole];
  
  // Fail closed: unknown roles cannot access routes
  if (currentOrder === undefined) {
    console.warn(`Unknown role '${role}' in roleOrder. Denying access to prevent privilege escalation.`);
    return false;
  }
  
  if (minimumOrder === undefined) {
    console.warn(`Route minimumRole '${route.minimumRole}' not found in roleOrder. Denying access.`);
    return false;
  }

  return currentOrder >= minimumOrder;
}

export function filterDiscoverableRoutes(
  routes: readonly ProductRouteCapability[],
  role: string,
  roleOrder: Readonly<Record<string, number>>,
): readonly ProductRouteCapability[] {
  return routes.filter(
    (route) =>
      route.lifecycle !== 'boundary' &&
      route.discoverable !== false &&
      isRouteAllowed(route, role, roleOrder),
  );
}

export function hydrateRoutesFromEntitlement(
  routes: readonly ProductRouteCapability[],
  entitlement: ProductRouteEntitlement | null | undefined,
): readonly ProductRouteCapability[] {
  if (!entitlement) {
    return routes;
  }

  const entitlementByPath = new Map(
    entitlement.routes.map((route) => [route.path, route] as const),
  );

  return routes.map((route) => {
    const entitledRoute = entitlementByPath.get(route.path);
    if (!entitledRoute) {
      return {
        ...route,
        discoverable: false,
      };
    }

    return {
      ...route,
      ...entitledRoute,
    };
  });
}
