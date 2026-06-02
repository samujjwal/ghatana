package com.ghatana.datacloud.launcher.http;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * P1-12: Registry mapping routes to surface IDs.
 * 
 * <p>This registry provides the canonical mapping from HTTP routes to surface IDs,
 * which are used for runtime truth, UI gating, authorization metadata, audit event types,
 * OpenAPI metadata, and SDK feature flags.
 *
 * @doc.type class
 * @doc.purpose Registry mapping routes to surface IDs
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class RouteSurfaceMapping {
    private static final Map<String, String> ROUTE_TO_SURFACE = buildRouteToSurfaceMap();

    private static Map<String, String> buildRouteToSurfaceMap() {
        Map<String, String> routes = new LinkedHashMap<>();
        for (RouteSecurityMetadata metadata : RouteSecurityRegistry.allRoutes().values()) {
            String surfaceId = metadata.runtimeTruthSurface();
            if (surfaceId != null && !surfaceId.isBlank()) {
                routes.put(metadata.method() + " " + metadata.canonicalPath(), surfaceId);
            }
        }
        return Map.copyOf(routes);
    }
    
    private RouteSurfaceMapping() {}
    
    /**
     * Gets the surface ID for a given route.
     *
     * @param method the HTTP method
     * @param path the route path
     * @return the surface ID, or null if not mapped
     */
    public static String getSurfaceId(String method, String path) {
        String normalized = normalizePath(path);
        return ROUTE_TO_SURFACE.get(method.toUpperCase() + " " + normalized);
    }
    
    /**
     * Checks if a route has a surface ID mapping.
     *
     * @param method the HTTP method
     * @param path the route path
     * @return true if the route has a surface ID mapping
     */
    public static boolean hasSurfaceMapping(String method, String path) {
        String normalized = normalizePath(path);
        return ROUTE_TO_SURFACE.containsKey(method.toUpperCase() + " " + normalized);
    }
    
    /**
     * Gets all registered surface IDs.
     *
     * @return set of all surface IDs
     */
    public static Set<String> getAllSurfaceIds() {
        return Set.copyOf(ROUTE_TO_SURFACE.values());
    }
    
    /**
     * Normalizes a path for mapping (replaces UUID-like patterns with {id}).
     */
    private static String normalizePath(String path) {
        String normalized = path.replaceAll("/[0-9a-fA-F-]{8,}", "/{id}");
        normalized = normalized.replaceAll("/connectors/[^/]+", "/connectors/{id}");
        normalized = normalized.replaceAll("/action/pipelines/[^/]+", "/action/pipelines/{id}");
        normalized = normalized.replaceAll("/action/plugins/[^/]+", "/action/plugins/{id}");
        normalized = normalized.replaceAll("/action/agents/[^/]+", "/action/agents/{id}");
        normalized = normalized.replaceAll("/action/executions/[^/]+", "/action/executions/{id}");
        normalized = normalized.replaceAll("/executions/[^/]+", "/executions/{id}");
        normalized = normalized.replaceAll("/alerts/[^/]+", "/alerts/{id}");
        normalized = normalized.replaceAll("/alerts/groups/[^/]+", "/alerts/groups/{id}");
        normalized = normalized.replaceAll("/alerts/suggestions/[^/]+", "/alerts/suggestions/{id}");
        normalized = normalized.replaceAll("/alerts/rules/[^/]+", "/alerts/rules/{id}");
        normalized = normalized.replaceAll("/governance/policies/[^/]+", "/governance/policies/{id}");
        normalized = normalized.replaceAll("/learning/review/[^/]+", "/learning/review/{id}");
        normalized = normalized.replaceAll("/models/[^/]+", "/models/{id}");
        normalized = normalized.replaceAll("/settings/keys/[^/]+", "/settings/keys/{id}");
        normalized = normalized.replaceAll("/settings/approvals/[^/]+", "/settings/approvals/{id}");
        normalized = normalized.replaceAll("/plugins/[^/]+", "/plugins/{id}");
        normalized = normalized.replaceAll("/media/artifacts/[^/]+", "/media/artifacts/{id}");
        normalized = normalized.replaceAll("/context/keys/[^/]+", "/context/keys/{id}");
        normalized = normalized.replaceAll("/entities/[^/]+", "/entities/{collection}");
        return normalized;
    }
}
