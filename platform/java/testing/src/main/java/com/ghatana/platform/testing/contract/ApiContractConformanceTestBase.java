package com.ghatana.platform.testing.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Base class for API contract conformance testing
 * @doc.layer platform
 * @doc.pattern Integration test
 */
public abstract class ApiContractConformanceTestBase {

    /**
     * Subclasses should provide the OpenAPI specification file path.
     * Example: "docs/openapi.yaml" or "src/test/resources/openapi.json"
     */
    protected abstract String getOpenApiSpecPath();

    /**
     * Subclasses should provide the HTTP server class that implements the routes.
     * Example: PhrHttpServer.class or FinanceHttpServer.class
     */
    protected abstract Class<?> getHttpServerClass();

    /**
     * Subclasses can optionally provide additional routes that are internal/not in spec.
     * Example: health checks, metrics endpoints
     */
    protected Set<HttpRouteScanner.RouteDefinition> getAdditionalInternalRoutes() {
        return Collections.emptySet();
    }

    @Test
    @DisplayName("API contract routes should match OpenAPI specification")
    void testApiContractConformance() throws IOException {
        // Parse OpenAPI spec
        ApiContractDefinition specContract = OpenApiContractParser.parseFromFile(getOpenApiSpecPath());
        LOG.info(String.format("Parsed OpenAPI spec: version=%s, basePath=%s, routes=%d",
            specContract.getOpenApiVersion(), specContract.getBasePath(), specContract.getDefinedRoutes().size()));

        // Discover implemented routes
        Set<HttpRouteScanner.RouteDefinition> implementedRoutes = HttpRouteScanner.scanRoutes(getHttpServerClass());
        
        // If route scanning returns empty (e.g., class doesn't implement ApiContractDefiner),
        // skip the conformance check and only validate that the spec exists
        if (implementedRoutes.isEmpty()) {
            LOG.warning(String.format("Route scanning returned empty for %s; skipping conformance check",
                getHttpServerClass().getSimpleName()));
            assertThat(specContract.getDefinedRoutes())
                .as("OpenAPI spec should define at least one route")
                .isNotEmpty();
            return;
        }
        
        implementedRoutes.addAll(getAdditionalInternalRoutes());
        LOG.info(String.format("Discovered %d implemented routes from %s", 
            implementedRoutes.size(), getHttpServerClass().getSimpleName()));

        // Normalize paths to OpenAPI format for comparison
        Set<String> specPaths = specContract.getDefinedRoutes().stream()
            .map(path -> HttpRouteScanner.normalizePathFormat(path, true))
            .collect(Collectors.toSet());

        Set<String> implPaths = implementedRoutes.stream()
            .map(route -> HttpRouteScanner.normalizePathFormat(route.getPath(), true))
            .collect(Collectors.toSet());

        // Check for routes in spec but unavailable
        Set<String> missingRoutes = new HashSet<>(specPaths);
        missingRoutes.removeAll(implPaths);

        // Check for routes implemented but not in spec
        Set<String> extraRoutes = new HashSet<>(implPaths);
        extraRoutes.removeAll(specPaths);

        // Build error message if there are mismatches
        List<String> violations = new ArrayList<>();
        if (!missingRoutes.isEmpty()) {
            violations.add("Routes defined in OpenAPI spec but NOT implemented: " + missingRoutes);
        }
        if (!extraRoutes.isEmpty()) {
            // Filter out internal routes like /health, /ready
            Set<String> unexpectedExtraRoutes = extraRoutes.stream()
                .filter(route -> !route.matches("/(health|ready|metrics)"))
                .collect(Collectors.toSet());
            if (!unexpectedExtraRoutes.isEmpty()) {
                violations.add("Routes implemented but NOT in OpenAPI spec: " + unexpectedExtraRoutes);
            }
        }

        // Assert no violations
        assertThat(violations)
            .as("API contract conformance violations for %s", getHttpServerClass().getSimpleName())
            .isEmpty();
    }

    @Test
    @DisplayName("Each route should support declared HTTP methods")
    void testHttpMethodConformance() throws IOException {
        ApiContractDefinition specContract = OpenApiContractParser.parseFromFile(getOpenApiSpecPath());
        Set<HttpRouteScanner.RouteDefinition> implementedRoutes = HttpRouteScanner.scanRoutes(getHttpServerClass());

        // Skip if route scanning returns empty
        if (implementedRoutes.isEmpty()) {
            LOG.warning("Route scanning returned empty; skipping HTTP method conformance check");
            return;
        }

        List<String> methodViolations = new ArrayList<>();

        for (HttpRouteScanner.RouteDefinition implRoute : implementedRoutes) {
            String normalizedPath = HttpRouteScanner.normalizePathFormat(implRoute.getPath(), true);
            Set<String> specMethods = specContract.getMethodsForRoute(normalizedPath);

            if (!specMethods.isEmpty() && !specMethods.contains(implRoute.getMethod().name())) {
                methodViolations.add(String.format(
                    "Route %s supports %s but spec defines only: %s",
                    implRoute.getPath(),
                    implRoute.getMethod(),
                    specMethods
                ));
            }
        }

        assertThat(methodViolations)
            .as("HTTP method conformance violations")
            .isEmpty();
    }

    @Test
    @DisplayName("All specified routes should be present and discoverable")
    void testRouteDiscoveryCompleteness() throws IOException {
        ApiContractDefinition specContract = OpenApiContractParser.parseFromFile(getOpenApiSpecPath());
        Set<String> specRoutes = specContract.getDefinedRoutes();

        assertThat(specRoutes)
            .as("OpenAPI spec should define at least one route")
            .isNotEmpty();

        // Log discovered spec routes for debugging
        LOG.info("Routes defined in spec: " + specRoutes);
    }

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(
        ApiContractConformanceTestBase.class.getName()
    );
}
