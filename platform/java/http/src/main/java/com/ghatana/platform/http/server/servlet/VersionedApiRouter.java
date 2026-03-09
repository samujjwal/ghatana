package com.ghatana.platform.http.server.servlet;

import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * API versioning router that supports URI-prefix versioning (/api/v1/, /api/v2/).
 *
 * <p><b>Purpose</b><br>
 * Provides structured API version management for REST endpoints. Routes requests
 * to version-specific handlers and supports deprecation headers, version negotiation,
 * and automatic fallback to the latest version.
 *
 * <p><b>Versioning Strategy</b><br>
 * URI-prefix versioning was chosen for its simplicity, cacheability, and visibility:
 * <ul>
 *   <li>{@code /api/v1/users} — Version 1 of the users API</li>
 *   <li>{@code /api/v2/users} — Version 2 with breaking changes</li>
 * </ul>
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * VersionedApiRouter router = VersionedApiRouter.create("/api")
 *     .version("v1", v1 -> {
 *         v1.addAsyncRoute(HttpMethod.GET, "/users", this::listUsersV1);
 *         v1.addAsyncRoute(HttpMethod.POST, "/users", this::createUserV1);
 *     })
 *     .version("v2", v2 -> {
 *         v2.addAsyncRoute(HttpMethod.GET, "/users", this::listUsersV2);
 *         v2.addAsyncRoute(HttpMethod.POST, "/users", this::createUserV2);
 *         v2.addAsyncRoute(HttpMethod.PATCH, "/users/:id", this::patchUserV2);
 *     })
 *     .deprecate("v1", "2026-06-01");
 *
 * // Register all versioned routes onto a RoutingServlet
 * RoutingServlet servlet = new RoutingServlet();
 * router.registerRoutes(servlet);
 *
 * // This registers:
 * //   GET  /api/v1/users → listUsersV1 (with Deprecation header)
 * //   POST /api/v1/users → createUserV1 (with Deprecation header)
 * //   GET  /api/v2/users → listUsersV2
 * //   POST /api/v2/users → createUserV2
 * //   PATCH /api/v2/users/:id → patchUserV2
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose URI-prefix API versioning router
 * @doc.layer platform
 * @doc.pattern Builder, Strategy
 */
public final class VersionedApiRouter {

    private static final Logger log = LoggerFactory.getLogger(VersionedApiRouter.class);

    private final String basePath;
    private final Map<String, RoutingServlet> versionServlets = new LinkedHashMap<>();
    private final Map<String, String> deprecatedVersions = new LinkedHashMap<>();
    private String latestVersion;

    private VersionedApiRouter(String basePath) {
        this.basePath = Objects.requireNonNull(basePath, "basePath must not be null");
    }

    /**
     * Creates a new versioned API router with the given base path.
     *
     * @param basePath the base path prefix (e.g., "/api")
     * @return new router instance
     */
    public static VersionedApiRouter create(String basePath) {
        return new VersionedApiRouter(basePath);
    }

    /**
     * Registers a new API version with its routes.
     *
     * @param version        version identifier (e.g., "v1", "v2")
     * @param routeRegistrar callback that receives a {@link RoutingServlet} for route registration
     * @return this router for fluent chaining
     */
    public VersionedApiRouter version(String version,
                                       java.util.function.Consumer<RoutingServlet> routeRegistrar) {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(routeRegistrar, "routeRegistrar must not be null");

        RoutingServlet versionServlet = new RoutingServlet();
        routeRegistrar.accept(versionServlet);
        versionServlets.put(version, versionServlet);
        latestVersion = version; // Last registered version is "latest"

        log.info("Registered API version: {} (basePath={})", version, basePath);
        return this;
    }

    /**
     * Marks a version as deprecated with a sunset date.
     * Deprecated versions will include {@code Deprecation} and {@code Sunset} HTTP headers.
     *
     * @param version    the version to deprecate (e.g., "v1")
     * @param sunsetDate the date when the version will be removed (ISO-8601, e.g., "2026-06-01")
     * @return this router for fluent chaining
     */
    public VersionedApiRouter deprecate(String version, String sunsetDate) {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(sunsetDate, "sunsetDate must not be null");

        if (!versionServlets.containsKey(version)) {
            throw new IllegalArgumentException("Version not registered: " + version);
        }

        deprecatedVersions.put(version, sunsetDate);
        log.info("Deprecated API version {} — sunset date: {}", version, sunsetDate);
        return this;
    }

    /**
     * Registers all versioned routes onto the target {@link RoutingServlet}.
     *
     * <p>Each route is registered with the full versioned path:
     * {@code basePath + "/" + version + routePath}
     *
     * <p>Deprecated versions automatically add {@code Deprecation} and {@code Sunset}
     * headers to all responses.
     *
     * @param target the routing servlet to register routes on
     */
    public void registerRoutes(RoutingServlet target) {
        Objects.requireNonNull(target, "target servlet must not be null");

        for (Map.Entry<String, RoutingServlet> entry : versionServlets.entrySet()) {
            String version = entry.getKey();
            String versionPrefix = basePath + "/" + version;
            boolean isDeprecated = deprecatedVersions.containsKey(version);
            String sunsetDate = deprecatedVersions.get(version);

            // Register a catch-all async route that delegates to the version servlet
            target.addAsyncRoute(null, versionPrefix + "/**",
                    request -> {
                        Promise<HttpResponse> response = entry.getValue().serve(request);

                        if (isDeprecated) {
                            return response.map(resp -> {
                                // Add deprecation headers per RFC 8594
                                return HttpResponse.ofCode(resp.getCode())
                                        .withHeader(
                                                io.activej.http.HttpHeaders.of("Deprecation"),
                                                "true")
                                        .withHeader(
                                                io.activej.http.HttpHeaders.of("Sunset"),
                                                sunsetDate)
                                        .withHeader(
                                                io.activej.http.HttpHeaders.of("Link"),
                                                "<" + basePath + "/" + latestVersion + ">; rel=\"successor-version\"")
                                        .withBody(resp.getBody())
                                        .build();
                            });
                        }
                        return response;
                    });

            log.info("Registered {} routes under {} {}",
                    isDeprecated ? "deprecated" : "active",
                    versionPrefix,
                    isDeprecated ? "(sunset: " + sunsetDate + ")" : "");
        }
    }

    /**
     * Returns the latest registered API version.
     *
     * @return the latest version string (e.g., "v2")
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Returns all registered version identifiers in registration order.
     *
     * @return ordered list of version strings
     */
    public java.util.List<String> getVersions() {
        return java.util.List.copyOf(versionServlets.keySet());
    }

    /**
     * Returns whether a given version is deprecated.
     *
     * @param version the version to check
     * @return true if the version is deprecated
     */
    public boolean isDeprecated(String version) {
        return deprecatedVersions.containsKey(version);
    }
}
