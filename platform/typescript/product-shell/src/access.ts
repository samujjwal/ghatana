import type { ProductRouteCapability, ProductRouteEntitlement } from './types';

export type RoleHierarchy<Role extends string = string> = Readonly<Record<Role, number>>;

export interface RouteAccessEvaluator<Role extends string = string> {
  readonly roleOrder: RoleHierarchy<Role>;
  resolveHighestRole(roles: readonly string[] | undefined, fallbackRole: Role): Role;
  hasMinimumRole(roles: readonly string[] | undefined, minimumRole: Role, fallbackRole: Role): boolean;
  isRouteAllowed(route: RouteAccessInput, role: Role): boolean;
  isRouteDirectLinkAllowed(route: RouteAccessInput, role: Role): boolean;
  filterDiscoverableRoutes(routes: readonly ProductRouteCapability[], role: Role): readonly ProductRouteCapability[];
}

export type RouteAccessInput = Pick<
  ProductRouteCapability,
  'minimumRole' | 'lifecycle' | 'stability' | 'hidden' | 'blocked'
>;

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

export function createRoleHierarchy<Role extends string>(
  roleOrder: RoleHierarchy<Role>,
): RoleHierarchy<Role> {
  return Object.freeze({ ...roleOrder });
}

export function hasMinimumRole<Role extends string>(
  roles: readonly string[] | undefined,
  minimumRole: Role,
  roleOrder: RoleHierarchy<Role>,
  fallbackRole: Role,
): boolean {
  const highestRole = resolveHighestRole(roles, roleOrder, fallbackRole) as Role;
  return isRouteAllowed({ minimumRole }, highestRole, roleOrder);
}

export function isRouteAllowed(
  route: RouteAccessInput,
  role: string,
  roleOrder: Readonly<Record<string, number>>,
): boolean {
  if (isRouteSuppressed(route)) {
    return false;
  }

  return hasRouteMinimumRole(route, role, roleOrder);
}

export function isRouteDirectLinkAllowed(
  route: RouteAccessInput,
  role: string,
  roleOrder: Readonly<Record<string, number>>,
): boolean {
  return isRouteAllowed(route, role, roleOrder);
}

function hasRouteMinimumRole(
  route: Pick<ProductRouteCapability, 'minimumRole'>,
  role: string,
  roleOrder: Readonly<Record<string, number>>,
): boolean {
  if (!route.minimumRole) {
    return true;
  }

  const currentOrder = roleOrder[role];
  const minimumOrder = roleOrder[route.minimumRole];

  if (currentOrder === undefined) {
    return false;
  }

  if (minimumOrder === undefined) {
    return false;
  }

  return currentOrder >= minimumOrder;
}

function isRouteSuppressed(route: RouteAccessInput): boolean {
  if (route.hidden === true || route.blocked === true) {
    return true;
  }

  const state = route.stability ?? route.lifecycle;
  return state === 'hidden' || state === 'blocked' || state === 'boundary' || state === 'deprecated';
}

export function filterDiscoverableRoutes(
  routes: readonly ProductRouteCapability[],
  role: string,
  roleOrder: Readonly<Record<string, number>>,
): readonly ProductRouteCapability[] {
  return routes.filter(
    (route) =>
      route.discoverable !== false &&
      isRouteAllowed(route, role, roleOrder),
  );
}

export function createRouteAccessEvaluator<Role extends string>(
  roleOrder: RoleHierarchy<Role>,
): RouteAccessEvaluator<Role> {
  const hierarchy = createRoleHierarchy(roleOrder);

  return {
    roleOrder: hierarchy,
    resolveHighestRole(roles: readonly string[] | undefined, fallbackRole: Role): Role {
      return resolveHighestRole(roles, hierarchy, fallbackRole) as Role;
    },
    hasMinimumRole(roles: readonly string[] | undefined, minimumRole: Role, fallbackRole: Role): boolean {
      return hasMinimumRole(roles, minimumRole, hierarchy, fallbackRole);
    },
    isRouteAllowed(route: RouteAccessInput, role: Role): boolean {
      return isRouteAllowed(route, role, hierarchy);
    },
    isRouteDirectLinkAllowed(route: RouteAccessInput, role: Role): boolean {
      return isRouteAllowed(route, role, hierarchy);
    },
    filterDiscoverableRoutes(
      routes: readonly ProductRouteCapability[],
      role: Role,
    ): readonly ProductRouteCapability[] {
      return filterDiscoverableRoutes(routes, role, hierarchy);
    },
  };
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
