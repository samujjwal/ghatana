/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-CON-001: OpenAPI route parity test.
 *
 * <p>Verifies that every path defined in {@code data-cloud.yaml} has at least one
 * matching route registered in {@link DataCloudRouterBuilder}, and that every route
 * in the router has a corresponding OpenAPI path.
 *
 * <p>Route parameter formats are normalized before comparison:
 * <ul>
 *   <li>OpenAPI style: {@code /api/v1/entities/{collection}/{id}}</li>
 *   <li>ActiveJ router style: {@code /api/v1/entities/:collection/:id}</li>
 * </ul>
 * Both are normalized to the ActiveJ colon style for comparison.
 *
 * <p>Routes known to exist only as runtime-only (no OpenAPI doc) or only in
 * OpenAPI (future/optional) are listed in {@code RUNTIME_ONLY_ROUTES} and
 * {@code OPENAPI_ONLY_PATHS} respectively to prevent false positives.
 *
 * @doc.type class
 * @doc.purpose CI contract parity check — DC-CON-001
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-CON-001: OpenAPI ↔ Router route parity")
class OpenApiRouteParity_DC_CON_001_Test {

    /**
     * Relative path from the repo root to the OpenAPI contract file.
     */
    private static final String OPENAPI_FILE = "products/data-cloud/contracts/openapi/data-cloud.yaml";

    /**
     * Relative path from the repo root to the router builder source file.
     */
    private static final String ROUTER_FILE =
            "products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java";

    /**
     * Routes that exist in the router but intentionally have no OpenAPI path.
     * These are internal/infrastructure endpoints not exposed in the public API doc.
     */
    private static final Set<String> RUNTIME_ONLY_ROUTES = Set.of(
            // SSE streaming endpoints not in OpenAPI
            "GET /api/v1/entities/:collection/stream",
            "GET /api/v1/entities/:collection/query/stream",
            "GET /api/v1/brain/workspace/stream",
            "GET /api/v1/alerts/stream",
            // Analytics SSE stream
            "GET /api/v1/analytics/stream",
            // Collection metadata upsert uses POST /api/v1/collections/:collection/metadata (router uses :collection)
            "POST /api/v1/collections/:collection/metadata"
    );

    /**
     * OpenAPI paths that intentionally have no corresponding router entry.
     * These cover future/planned or externally-served routes.
     */
    private static final Set<String> OPENAPI_ONLY_PATHS = Set.of(
            // Served by separate services or not yet implemented
    );

    /**
     * Pattern to extract HttpMethod + path from router source lines.
     * Matches: .with(HttpMethod.GET, "/api/v1/foo/:id", ...)
     */
    private static final Pattern ROUTER_ROUTE_PATTERN =
            Pattern.compile("HttpMethod\\.([A-Z]+)[,\\s]+\"(/[^\"]+)\"");

    /**
     * Pattern to extract path keys from OpenAPI YAML (lines starting with exactly 2 spaces + /path:).
     */
    private static final Pattern OPENAPI_PATH_PATTERN =
            Pattern.compile("^  (/[^:]+):$");

    @Test
    @DisplayName("every OpenAPI path has a matching runtime route")
    void everyOpenApiPathHasAMatchingRuntimeRoute() throws IOException {
        Set<String> openApiPaths = extractOpenApiPaths();
        Set<String> routerPaths = extractRouterPaths();

        List<String> missing = new ArrayList<>();
        for (String openApiPath : openApiPaths) {
            if (OPENAPI_ONLY_PATHS.contains(openApiPath)) {
                continue;
            }
            if (!routerPaths.contains(openApiPath)) {
                missing.add(openApiPath);
            }
        }

        assertThat(missing)
                .as("OpenAPI paths with no matching runtime route — add routes to DataCloudRouterBuilder or add to OPENAPI_ONLY_PATHS")
                .isEmpty();
    }

    @Test
    @DisplayName("every runtime route has a matching OpenAPI path")
    void everyRuntimeRouteHasAMatchingOpenApiPath() throws IOException {
        Set<String> openApiPaths = extractOpenApiPaths();
        Set<String> routerRoutes = extractRouterRoutes();

        List<String> undocumented = new ArrayList<>();
        for (String route : routerRoutes) {
            if (RUNTIME_ONLY_ROUTES.contains(route)) {
                continue;
            }
            // Extract just the path part (strip METHOD prefix)
            String path = route.contains(" ") ? route.substring(route.indexOf(' ') + 1) : route;
            if (!openApiPaths.contains(path)) {
                undocumented.add(route);
            }
        }

        assertThat(undocumented)
                .as("Runtime routes with no matching OpenAPI path — document them in data-cloud.yaml or add to RUNTIME_ONLY_ROUTES")
                .isEmpty();
    }

    @Test
    @DisplayName("OpenAPI path count and router route count are within acceptable bounds")
    void routeCounts_areWithinAcceptableBounds() throws IOException {
        Set<String> openApiPaths = extractOpenApiPaths();
        Set<String> routerPaths = extractRouterPaths();

        // Neither set should be empty — guard against file-read failures
        assertThat(openApiPaths).as("OpenAPI paths should be non-empty").isNotEmpty();
        assertThat(routerPaths).as("Router paths should be non-empty").isNotEmpty();

        // OpenAPI path count should be at least 100 (sanity guard against truncated file)
        assertThat(openApiPaths.size())
                .as("OpenAPI path count must be >= 100")
                .isGreaterThanOrEqualTo(100);

        // Router path count should be at least 100 (sanity guard against truncated file)
        assertThat(routerPaths.size())
                .as("Router path count must be >= 100")
                .isGreaterThanOrEqualTo(100);
    }

    // -------------------------------------------------------------------------
    // Extraction helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the OpenAPI YAML file and extracts all path keys, normalized to colon-param style.
     */
    private Set<String> extractOpenApiPaths() throws IOException {
        Path yamlFile = resolveFromRepoRoot(OPENAPI_FILE);
        Set<String> paths = new TreeSet<>();
        for (String line : Files.readAllLines(yamlFile)) {
            Matcher m = OPENAPI_PATH_PATTERN.matcher(line);
            if (m.matches()) {
                String path = m.group(1).trim();
                paths.add(normalizeToColonStyle(path));
            }
        }
        return paths;
    }

    /**
     * Reads the router builder Java source and extracts unique path strings (without HTTP method),
     * normalized to colon-param style.
     */
    private Set<String> extractRouterPaths() throws IOException {
        return extractRouterRoutes().stream()
                .map(route -> route.contains(" ") ? route.substring(route.indexOf(' ') + 1) : route)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Reads the router builder Java source and extracts unique "METHOD /path" strings,
     * normalized to colon-param style.
     */
    private Set<String> extractRouterRoutes() throws IOException {
        Path javaFile = resolveFromRepoRoot(ROUTER_FILE);
        Set<String> routes = new TreeSet<>();
        for (String line : Files.readAllLines(javaFile)) {
            Matcher m = ROUTER_ROUTE_PATTERN.matcher(line);
            if (m.find()) {
                String method = m.group(1);
                String path = normalizeToColonStyle(m.group(2));
                routes.add(method + " " + path);
            }
        }
        return routes;
    }

    /**
     * Converts OpenAPI-style path parameters ({paramName}) to ActiveJ colon style (:paramName).
     */
    private String normalizeToColonStyle(String path) {
        return path.replaceAll("\\{([^}]+)}", ":$1");
    }

    /**
     * Resolves a path relative to the repository root.
     * Walks up from the current working directory to find the root (containing settings.gradle.kts).
     */
    private Path resolveFromRepoRoot(String relativePath) throws IOException {
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd;
        // Walk up to find repo root (contains settings.gradle.kts)
        for (int i = 0; i < 10; i++) {
            if (Files.exists(candidate.resolve("settings.gradle.kts"))) {
                Path result = candidate.resolve(relativePath);
                if (!Files.exists(result)) {
                    throw new IOException("File not found: " + result);
                }
                return result;
            }
            candidate = candidate.getParent();
            if (candidate == null) {
                break;
            }
        }
        throw new IOException("Could not locate repo root from: " + cwd);
    }
}
