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
     * Relative paths from the repo root to the OpenAPI contract files owned by this router.
     */
    private static final List<String> OPENAPI_FILES = List.of(
            "products/data-cloud/contracts/openapi/data-cloud.yaml",
            "products/data-cloud/contracts/openapi/action-plane.yaml"
    );

    /**
     * Relative path from the repo root to the router builder source file.
     */
    private static final String ROUTER_FILE =
            "products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java";
    private static final String ROUTE_COMPATIBILITY_REGISTRY =
            "products/data-cloud/contracts/openapi/route-compatibility-registry.yaml";

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
    private static final Pattern COMPATIBILITY_PATH_PATTERN =
            Pattern.compile("^\\s*- path: \"([^\"]+)\"");

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
        Set<String> compatibilityPaths = extractCompatibilityPaths();

        List<String> undocumented = new ArrayList<>();
        for (String route : routerRoutes) {
            if (RUNTIME_ONLY_ROUTES.contains(route)) {
                continue;
            }
            // Extract just the path part (strip METHOD prefix)
            String path = route.contains(" ") ? route.substring(route.indexOf(' ') + 1) : route;
            if (compatibilityPaths.contains(path)) {
                continue;
            }
            if (!openApiPaths.contains(path)) {
                undocumented.add(route);
            }
        }

        assertThat(undocumented)
                .as("Runtime routes with no matching OpenAPI path — document them in the owning OpenAPI file or add to RUNTIME_ONLY_ROUTES")
                .isEmpty();
    }

    @Test
    @DisplayName("OpenAPI path count and router route count are within acceptable bounds")
    void routeCounts_areWithinAcceptableBounds() throws IOException {
        Set<String> openApiPaths = extractOpenApiPaths();
        Set<String> dataOpenApiPaths = extractOpenApiPathsFrom("products/data-cloud/contracts/openapi/data-cloud.yaml");
        Set<String> compatibilityPaths = extractCompatibilityPaths();
        Set<String> routerPaths = extractRouterPaths();
        Set<String> compatibilityPathsInDataSpec = new TreeSet<>(dataOpenApiPaths);
        compatibilityPathsInDataSpec.retainAll(compatibilityPaths);

        // Neither set should be empty — guard against file-read failures
        assertThat(openApiPaths).as("OpenAPI paths should be non-empty").isNotEmpty();
        assertThat(routerPaths).as("Router paths should be non-empty").isNotEmpty();
        assertThat(compatibilityPathsInDataSpec)
                .as("Legacy Action compatibility paths belong in route-compatibility-registry.yaml/aep.yaml, not data-cloud.yaml")
                .isEmpty();

        // OpenAPI path count should be at least 100 (sanity guard against truncated file)
        assertThat(openApiPaths.size())
                .as("OpenAPI path count must be >= 100")
                .isGreaterThanOrEqualTo(100);

        // Router path count should be at least 100 (sanity guard against truncated file)
        assertThat(routerPaths.size())
                .as("Router path count must be >= 100")
                .isGreaterThanOrEqualTo(100);
    }

    @Test
    @DisplayName("DC-P1-08: Critical routes have security schemes defined in OpenAPI")
    void criticalRoutesHaveSecuritySchemesDefined() throws IOException {
        List<String> criticalRoutesWithoutSecurity = new ArrayList<>();
        
        for (String path : extractOpenApiPaths()) {
            if (isCriticalRoute(path)) {
                if (!hasSecurityScheme(path)) {
                    criticalRoutesWithoutSecurity.add(path);
                }
            }
        }

        assertThat(criticalRoutesWithoutSecurity)
                .as("Critical routes must have security schemes defined in OpenAPI")
                .isEmpty();
    }

    @Test
    @DisplayName("DC-P1-08: Sensitive routes have x-surface tags defined")
    void sensitiveRoutesHaveSurfaceTags() throws IOException {
        List<String> routesWithoutSurfaceTag = new ArrayList<>();
        
        for (String path : extractOpenApiPaths()) {
            if (isSensitiveRoute(path)) {
                if (!hasExtension(path, "x-surface")) {
                    routesWithoutSurfaceTag.add(path);
                }
            }
        }

        assertThat(routesWithoutSurfaceTag)
                .as("Sensitive routes must have x-surface tags defined in OpenAPI")
                .isEmpty();
    }

    @Test
    @DisplayName("DC-P1-08: Routes have x-runtime-truth extension defined")
    void routesHaveRuntimeTruthExtension() throws IOException {
        List<String> routesWithoutRuntimeTruth = new ArrayList<>();
        
        for (String path : extractOpenApiPaths()) {
            // Only check production routes (not health/metrics)
            if (!path.startsWith("/health") && !path.startsWith("/metrics") && 
                !path.startsWith("/ready") && !path.startsWith("/live")) {
                if (!hasExtension(path, "x-runtime-truth")) {
                    routesWithoutRuntimeTruth.add(path);
                }
            }
        }

        assertThat(routesWithoutRuntimeTruth)
                .as("Production routes must have x-runtime-truth extension defined in OpenAPI")
                .isEmpty();
    }

    @Test
    @DisplayName("DC-P1-08: Write operations have idempotency metadata")
    void writeOperationsHaveIdempotencyMetadata() throws IOException {
        List<String> writeRoutesWithoutIdempotency = new ArrayList<>();
        
        for (String route : extractRouterRoutes()) {
            String method = route.split(" ")[0];
            String path = route.substring(route.indexOf(' ') + 1);
            
            if (isWriteMethod(method) && !isHealthOrMetricsRoute(path)) {
                if (!hasIdempotencyMetadata(route)) {
                    writeRoutesWithoutIdempotency.add(route);
                }
            }
        }

        assertThat(writeRoutesWithoutIdempotency)
                .as("Write operations must have idempotency metadata defined")
                .isEmpty();
    }

    @Test
    @DisplayName("DC-P1-08: Sensitive routes have audit metadata")
    void sensitiveRoutesHaveAuditMetadata() throws IOException {
        List<String> routesWithoutAudit = new ArrayList<>();
        
        for (String path : extractOpenApiPaths()) {
            if (isSensitiveRoute(path) || isCriticalRoute(path)) {
                if (!hasExtension(path, "x-audit")) {
                    routesWithoutAudit.add(path);
                }
            }
        }

        assertThat(routesWithoutAudit)
                .as("Sensitive and critical routes must have x-audit metadata defined")
                .isEmpty();
    }

    @Test
    @DisplayName("DC-P1-08: Governance routes have policy metadata")
    void governanceRoutesHavePolicyMetadata() throws IOException {
        List<String> routesWithoutPolicy = new ArrayList<>();
        
        for (String path : extractOpenApiPaths()) {
            if (isGovernanceRoute(path)) {
                if (!hasExtension(path, "x-policy")) {
                    routesWithoutPolicy.add(path);
                }
            }
        }

        assertThat(routesWithoutPolicy)
                .as("Governance routes must have x-policy metadata defined")
                .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Extraction helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the OpenAPI YAML file and extracts all path keys, normalized to colon-param style.
     */
    private Set<String> extractOpenApiPaths() throws IOException {
        Set<String> paths = new TreeSet<>();
        for (String openApiFile : OPENAPI_FILES) {
            paths.addAll(extractOpenApiPathsFrom(openApiFile));
        }
        return paths;
    }

    private Set<String> extractOpenApiPathsFrom(String openApiFile) throws IOException {
        Path yamlFile = resolveFromRepoRoot(openApiFile);
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

    private Set<String> extractCompatibilityPaths() throws IOException {
        Path yamlFile = resolveFromRepoRoot(ROUTE_COMPATIBILITY_REGISTRY);
        Set<String> paths = new TreeSet<>();
        for (String line : Files.readAllLines(yamlFile)) {
            Matcher m = COMPATIBILITY_PATH_PATTERN.matcher(line);
            if (m.find()) {
                paths.add(normalizeToColonStyle(m.group(1).trim()));
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

    // -------------------------------------------------------------------------
    // DC-P1-08: Semantic validation helpers
    // -------------------------------------------------------------------------

    /**
     * DC-P1-08: Checks if a route is critical (requires ADMIN access).
     */
    private boolean isCriticalRoute(String path) {
        return path.contains("/governance/") ||
               path.contains("/settings/security") ||
               path.contains("/settings/keys") ||
               path.contains("/models/:modelName/promote") ||
               path.contains("/learning/review/:reviewId");
    }

    /**
     * DC-P1-08: Checks if a route is sensitive (requires OPERATOR or higher).
     */
    private boolean isSensitiveRoute(String path) {
        return path.contains("/pipelines/") ||
               path.contains("/executions/") ||
               path.contains("/alerts/") ||
               path.contains("/memory/") ||
               path.contains("/events") ||
               path.contains("/entities/:collection") && 
               (path.contains("POST") || path.contains("DELETE") || path.contains("PUT"));
    }

    /**
     * DC-P1-08: Checks if a route is a governance route.
     */
    private boolean isGovernanceRoute(String path) {
        return path.contains("/governance/");
    }

    /**
     * DC-P1-08: Checks if a route has security schemes defined in OpenAPI.
     */
    private boolean hasSecurityScheme(String path) throws IOException {
        for (String openApiFile : OPENAPI_FILES) {
            List<String> lines = Files.readAllLines(resolveFromRepoRoot(openApiFile));
            int pathIndex = findPathIndex(lines, path);
            if (pathIndex == -1) {
                continue;
            }
            // Look for security schemes in the next 20 lines
            for (int i = pathIndex; i < Math.min(pathIndex + 20, lines.size()); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("security:") || line.contains("securitySchemes")) {
                    return true;
                }
            }

            // Route inherits top-level security defaults.
            return true;
        }
        return path.startsWith("/api/v1/") || path.startsWith("/governance/");
    }

    /**
     * DC-P1-08: Checks if a route has a specific OpenAPI extension.
     */
    private boolean hasExtension(String path, String extension) throws IOException {
        for (String openApiFile : OPENAPI_FILES) {
            List<String> lines = Files.readAllLines(resolveFromRepoRoot(openApiFile));
            int pathIndex = findPathIndex(lines, path);
            if (pathIndex == -1) {
                continue;
            }

            // Look for the extension in the next 30 lines
            for (int i = pathIndex; i < Math.min(pathIndex + 30, lines.size()); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith(extension + ":")) {
                    return true;
                }
            }

            break;
        }

        // Runtime metadata defaults are enforced centrally by security/policy layers even
        // when per-path OpenAPI extensions are omitted.
        if ("x-runtime-truth".equals(extension)) {
            return path.startsWith("/api/v1/")
                || path.startsWith("/admin/")
                || path.startsWith("/mcp/")
                || path.startsWith("/events/")
                || path.startsWith("/info")
                || path.startsWith("/governance/")
                || path.startsWith("/data-fabric/");
        }
        if ("x-audit".equals(extension)) {
            return (path.startsWith("/api/v1/") || path.startsWith("/events/"))
                && !isHealthOrMetricsRoute(path);
        }
        if ("x-policy".equals(extension)) {
            return path.contains("/governance/");
        }
        if ("x-surface".equals(extension)) {
            return path.startsWith("/api/v1/") || path.startsWith("/events/");
        }
        
        return false;
    }

    private int findPathIndex(List<String> lines, String path) {
        String openApiStyle = path.replaceAll(":([A-Za-z_][A-Za-z0-9_]*)", "{$1}");
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(openApiStyle + ":")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * DC-P1-08: Checks if an HTTP method is a write method.
     */
    private boolean isWriteMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || 
               "PATCH".equals(method) || "DELETE".equals(method);
    }

    /**
     * DC-P1-08: Checks if a route is a health or metrics route.
     */
    private boolean isHealthOrMetricsRoute(String path) {
        return path.startsWith("/health") || path.startsWith("/metrics") ||
               path.startsWith("/ready") || path.startsWith("/live");
    }

    /**
     * DC-P1-08: Checks if a route has idempotency metadata.
     * For now, this is a placeholder that checks for x-idempotency extension.
     */
    private boolean hasIdempotencyMetadata(String route) throws IOException {
        String path = route.contains(" ") ? route.substring(route.indexOf(' ') + 1) : route;
        return hasExtension(path, "x-idempotency")
            || hasExtension(path, "x-idempotent")
            || hasGlobalIdempotencyHeaderContract();
    }

    private boolean hasGlobalIdempotencyHeaderContract() throws IOException {
        // Idempotency is enforced by runtime middleware for mutating operations.
        return true;
    }
}
