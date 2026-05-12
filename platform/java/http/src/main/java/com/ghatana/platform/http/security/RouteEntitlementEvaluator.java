package com.ghatana.platform.http.security;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Kernel-owned route entitlement evaluator for filtering routes based on role, persona, tier, and cards.
 * <p>
 * Provides a shared implementation for route entitlement filtering that products can use
 * instead of maintaining local filtering logic. This ensures consistency across all products.
 * </p>
 *
 * @doc.type class
 * @doc.purpose Shared route entitlement filtering logic for Kernel products
 * @doc.layer kernel
 * @doc.pattern Evaluator
 */
public final class RouteEntitlementEvaluator {

    private final RoleEvaluator roleEvaluator;

    public RouteEntitlementEvaluator(RoleEvaluator roleEvaluator) {
        this.roleEvaluator = Objects.requireNonNull(roleEvaluator, "roleEvaluator must not be null");
    }

    /**
     * Filters routes based on the current role using the provided role order.
     *
     * @param routes    the list of routes to filter
     * @param currentRole the current user's role
     * @param roleOrder  the role hierarchy mapping from role name to numeric order
     * @return filtered list of routes
     */
    public List<ProductRouteEntitlement.RouteEntitlement> filterByRole(
            List<ProductRouteEntitlement.RouteEntitlement> routes,
            String currentRole,
            Map<String, Integer> roleOrder) {
        Objects.requireNonNull(routes, "routes must not be null");
        Objects.requireNonNull(currentRole, "currentRole must not be null");
        Objects.requireNonNull(roleOrder, "roleOrder must not be null");

        try {
            return routes.stream()
                .filter(route -> roleEvaluator.isRoleSufficient(currentRole, route.minimumRole(), roleOrder))
                .toList();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /**
     * Filters routes based on the current persona.
     *
     * @param routes     the list of routes to filter
     * @param persona    the current user's persona
     * @return filtered list of routes
     */
    public List<ProductRouteEntitlement.RouteEntitlement> filterByPersona(
            List<ProductRouteEntitlement.RouteEntitlement> routes,
            String persona) {
        Objects.requireNonNull(routes, "routes must not be null");
        if (persona == null || persona.isBlank()) {
            return routes;
        }

        return routes.stream()
            .filter(route -> route.personas().contains(persona))
            .toList();
    }

    /**
     * Filters routes based on the current commercial tier.
     *
     * @param routes the list of routes to filter
     * @param tier  the current user's commercial tier
     * @return filtered list of routes
     */
    public List<ProductRouteEntitlement.RouteEntitlement> filterByTier(
            List<ProductRouteEntitlement.RouteEntitlement> routes,
            String tier) {
        Objects.requireNonNull(routes, "routes must not be null");
        if (tier == null || tier.isBlank()) {
            return routes;
        }

        return routes.stream()
            .filter(route -> route.tiers().contains(tier))
            .toList();
    }

    /**
     * Filters routes based on all applicable criteria: role, persona, and tier.
     *
     * @param routes      the list of routes to filter
     * @param currentRole the current user's role
     * @param roleOrder   the role hierarchy mapping
     * @param persona     the current user's persona (optional)
     * @param tier        the current user's commercial tier (optional)
     * @return filtered list of routes
     */
    public List<ProductRouteEntitlement.RouteEntitlement> filterByAll(
            List<ProductRouteEntitlement.RouteEntitlement> routes,
            String currentRole,
            Map<String, Integer> roleOrder,
            String persona,
            String tier) {
        List<ProductRouteEntitlement.RouteEntitlement> filtered = filterByRole(routes, currentRole, roleOrder);
        filtered = filterByPersona(filtered, persona);
        filtered = filterByTier(filtered, tier);
        return filtered;
    }

    /**
     * Filters actions from a list of routes based on the current role.
     *
     * @param routes      the list of routes
     * @param currentRole the current user's role
     * @param roleOrder   the role hierarchy mapping
     * @return filtered list of actions
     */
    public List<ProductRouteEntitlement.ActionEntitlement> filterActionsByRole(
            List<ProductRouteEntitlement.RouteEntitlement> routes,
            String currentRole,
            Map<String, Integer> roleOrder) {
        Objects.requireNonNull(routes, "routes must not be null");
        Objects.requireNonNull(currentRole, "currentRole must not be null");
        Objects.requireNonNull(roleOrder, "roleOrder must not be null");

        List<ProductRouteEntitlement.RouteEntitlement> filteredRoutes = filterByRole(routes, currentRole, roleOrder);
        return filteredRoutes.stream()
            .flatMap(route -> route.actions().stream()
                .map(action -> new ProductRouteEntitlement.ActionEntitlement(action, labelFromId(action), route.path())))
            .toList();
    }

    /**
     * Filters cards from a list of routes based on the current role.
     *
     * @param routes      the list of routes
     * @param currentRole the current user's role
     * @param roleOrder   the role hierarchy mapping
     * @return filtered list of cards
     */
    public List<ProductRouteEntitlement.CardEntitlement> filterCardsByRole(
            List<ProductRouteEntitlement.RouteEntitlement> routes,
            String currentRole,
            Map<String, Integer> roleOrder) {
        Objects.requireNonNull(routes, "routes must not be null");
        Objects.requireNonNull(currentRole, "currentRole must not be null");
        Objects.requireNonNull(roleOrder, "roleOrder must not be null");

        List<ProductRouteEntitlement.RouteEntitlement> filteredRoutes = filterByRole(routes, currentRole, roleOrder);
        return filteredRoutes.stream()
            .flatMap(route -> route.cards().stream()
                .map(card -> new ProductRouteEntitlement.CardEntitlement(card, labelFromId(card), route.path(), "dashboard")))
            .toList();
    }

    private static String labelFromId(String id) {
        return Arrays.stream(id.replace("-", " ")
            .replace("_", " ")
            .split(" "))
            .map(part -> part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase())
            .reduce((a, b) -> a + " " + b)
            .orElse(id);
    }
}
