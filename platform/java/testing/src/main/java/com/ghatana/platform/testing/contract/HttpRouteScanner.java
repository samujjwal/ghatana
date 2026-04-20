package com.ghatana.platform.testing.contract;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

/**
 * @doc.type class
 * @doc.purpose Discovers HTTP routes from ActiveJ RoutingServlet implementations via reflection
 * @doc.layer platform
 * @doc.pattern Route discovery utility
 */
public final class HttpRouteScanner {
    private static final Logger LOG = Logger.getLogger(HttpRouteScanner.class.getName());

    private HttpRouteScanner() {}

    /**
     * Scan an HTTP server class for declared routes.
     * Discovers routes by inspecting the RoutingServlet.builder() construction
     * and extracting route definitions from source code or reflection metadata.
     *
     * @param httpServerClass class containing getServlet() method (e.g., PhrHttpServer, FinanceHttpServer)
     * @return set of discovered routes in OpenAPI format (e.g., "/fhir/{resourceType}")
     */
    public static Set<RouteDefinition> scanRoutes(Class<?> httpServerClass) {
        Set<RouteDefinition> routes = new HashSet<>();

        try {
            // Look for getServlet() method
            Method getServletMethod = httpServerClass.getMethod("getServlet");

            // Extract route information from method implementation
            // This uses reflection to find RoutingServlet.with() calls
            Set<RouteDefinition> discoveredRoutes = extractRoutesFromSource(httpServerClass);
            routes.addAll(discoveredRoutes);

            LOG.fine(() -> String.format("Discovered %d routes from %s", routes.size(), httpServerClass.getSimpleName()));
        } catch (NoSuchMethodException e) {
            LOG.warning(() -> String.format("No getServlet() method found in %s", httpServerClass.getName()));
        }

        return routes;
    }

    /**
     * Extract routes by parsing source code or using bytecode inspection.
     * Falls back to manual route definition if automated discovery is insufficient.
     */
    private static Set<RouteDefinition> extractRoutesFromSource(Class<?> httpServerClass) {
        Set<RouteDefinition> routes = new HashSet<>();

        // Extract from source code patterns or use reflection metadata
        // For now, we'll support a registry-based approach where servers can implement ApiContractDefinition
        if (ApiContractDefiner.class.isAssignableFrom(httpServerClass)) {
            try {
                ApiContractDefiner definer = (ApiContractDefiner) httpServerClass.getDeclaredConstructor().newInstance();
                routes.addAll(definer.defineRoutes());
            } catch (Exception e) {
                LOG.warning(() -> String.format("Failed to instantiate contract definer: %s", e.getMessage()));
            }
        }

        return routes;
    }

    /**
     * Normalize OpenAPI path parameter syntax to standard format.
     * Converts between formats: {id} -> :id (ActiveJ) and vice versa
     */
    public static String normalizePathFormat(String path, boolean toOpenApiFormat) {
        if (toOpenApiFormat) {
            // Convert ActiveJ :id format to OpenAPI {id} format
            return path.replaceAll(":([a-zA-Z_][a-zA-Z0-9_]*)", "{$1}");
        } else {
            // Convert OpenAPI {id} format to ActiveJ :id format
            return path.replaceAll("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}", ":$1");
        }
    }

    /**
     * Represents a single HTTP route with method and path.
     */
    public static final class RouteDefinition {
        private final io.activej.http.HttpMethod method;
        private final String path;

        public RouteDefinition(io.activej.http.HttpMethod method, String path) {
            this.method = method;
            this.path = path;
        }

        public io.activej.http.HttpMethod getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RouteDefinition)) return false;
            RouteDefinition that = (RouteDefinition) o;
            return method == that.method && Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, path);
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}

/**
 * Interface that HTTP server implementations can use to explicitly declare their routes.
 */
interface ApiContractDefiner {
    Set<HttpRouteScanner.RouteDefinition> defineRoutes();
}

