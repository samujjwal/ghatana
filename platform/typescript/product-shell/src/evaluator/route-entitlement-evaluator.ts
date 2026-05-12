/**
 * Kernel-owned route entitlement evaluator for filtering routes based on role, persona, tier, and cards.
 * Provides a shared implementation for route entitlement filtering that products can use
 * instead of maintaining local filtering logic.
 */

import type { ProductRouteEntitlement, RouteEntitlement, ActionEntitlement, CardEntitlement } from '../contracts/product-route-entitlement.js';

export interface RoleOrder {
  readonly [role: string]: number;
}

export class RouteEntitlementEvaluator {
  constructor(private readonly roleOrder: RoleOrder) {
    if (!roleOrder || Object.keys(roleOrder).length === 0) {
      throw new Error('roleOrder must not be empty');
    }
  }

  /**
   * Filters routes based on the current role using the provided role order.
   */
  filterByRole(routes: readonly RouteEntitlement[], currentRole: string): readonly RouteEntitlement[] {
    if (!currentRole || currentRole === '') {
      console.warn('Current role is empty, denying access to prevent privilege escalation');
      return [];
    }

    const currentOrder = this.roleOrder[currentRole];
    if (currentOrder === undefined) {
      console.warn(`Unknown role '${currentRole}' in roleOrder. Denying access to prevent privilege escalation.`);
      return [];
    }

    return routes.filter((route) => {
      const minimumOrder = this.roleOrder[route.minimumRole];
      if (minimumOrder === undefined) {
        console.warn(`Route minimumRole '${route.minimumRole}' not found in roleOrder. Denying access.`);
        return false;
      }
      return currentOrder >= minimumOrder;
    });
  }

  /**
   * Filters routes based on the current persona.
   */
  filterByPersona(routes: readonly RouteEntitlement[], persona: string | undefined): readonly RouteEntitlement[] {
    if (!persona || persona === '') {
      return routes;
    }

    return routes.filter((route) => route.personas.includes(persona));
  }

  /**
   * Filters routes based on the current commercial tier.
   */
  filterByTier(routes: readonly RouteEntitlement[], tier: string | undefined): readonly RouteEntitlement[] {
    if (!tier || tier === '') {
      return routes;
    }

    return routes.filter((route) => route.tiers.includes(tier));
  }

  /**
   * Filters routes based on all applicable criteria: role, persona, and tier.
   */
  filterByAll(
    routes: readonly RouteEntitlement[],
    currentRole: string,
    persona: string | undefined,
    tier: string | undefined
  ): readonly RouteEntitlement[] {
    let filtered = this.filterByRole(routes, currentRole);
    filtered = this.filterByPersona(filtered, persona);
    filtered = this.filterByTier(filtered, tier);
    return filtered;
  }

  /**
   * Filters actions from a list of routes based on the current role.
   */
  filterActionsByRole(routes: readonly RouteEntitlement[], currentRole: string): readonly ActionEntitlement[] {
    const filteredRoutes = this.filterByRole(routes, currentRole);
    return filteredRoutes.flatMap((route) =>
      route.actions.map((action) => ({
        id: action,
        label: this.labelFromId(action),
        routePath: route.path,
      }))
    );
  }

  /**
   * Filters cards from a list of routes based on the current role.
   */
  filterCardsByRole(routes: readonly RouteEntitlement[], currentRole: string): readonly CardEntitlement[] {
    const filteredRoutes = this.filterByRole(routes, currentRole);
    return filteredRoutes.flatMap((route) =>
      route.cards.map((card) => ({
        id: card,
        title: this.labelFromId(card),
        routePath: route.path,
        surface: 'dashboard' as const,
      }))
    );
  }

  private labelFromId(id: string): string {
    return id
      .replace(/-/g, ' ')
      .replace(/_/g, ' ')
      .split(' ')
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
      .join(' ');
  }
}
