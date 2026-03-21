/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contract for API surfaces: HTTP endpoints, RPC services, gateway route registrations.
 *
 * <p>An API contract declares the routes a module exposes, the HTTP methods supported,
 * version policy, and deprecation lifecycle. Aligns with the AppPlatform gateway's
 * versioned routing and RFC 8594 deprecation headers.</p>
 *
 * @doc.type class
 * @doc.purpose API contract for endpoint and route declarations
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class ApiContract extends KernelContract {

    /**
     * Declares a single API route.
     */
    public record RouteDeclaration(String method, String path, String handler,
                                   String versionPolicy) {
        public RouteDeclaration {
            Objects.requireNonNull(method, "method required");
            Objects.requireNonNull(path, "path required");
            Objects.requireNonNull(handler, "handler required");
            if (versionPolicy == null) versionPolicy = "url-prefix";
        }
    }

    /**
     * API deprecation schedule following RFC 8594.
     */
    public record DeprecationSchedule(String deprecatedVersion, String sunsetDate,
                                      String successorVersion) {
        public DeprecationSchedule {
            Objects.requireNonNull(deprecatedVersion, "deprecatedVersion required");
        }
    }

    private final List<RouteDeclaration> routes;
    private final List<DeprecationSchedule> deprecations;
    private final String basePath;

    private ApiContract(Builder builder) {
        super(builder.contractId, builder.name, builder.version,
              ContractFamily.API, builder.metadata);
        this.routes = builder.routes != null ? List.copyOf(builder.routes) : List.of();
        this.deprecations = builder.deprecations != null ? List.copyOf(builder.deprecations) : List.of();
        this.basePath = builder.basePath;
        validate();
    }

    public List<RouteDeclaration> getRoutes() { return routes; }
    public List<DeprecationSchedule> getDeprecations() { return deprecations; }
    public String getBasePath() { return basePath; }

    @Override
    protected void validate() {
        super.validate();
        if (basePath != null && !basePath.startsWith("/")) {
            throw new IllegalArgumentException("basePath must start with /: " + basePath);
        }
        for (RouteDeclaration route : routes) {
            if (!route.path().startsWith("/")) {
                throw new IllegalArgumentException(
                    "Route path must start with /: " + route.path());
            }
            String upper = route.method().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
                      .contains(upper)) {
                throw new IllegalArgumentException("Invalid HTTP method: " + route.method());
            }
        }
    }

    /**
     * Creates a new builder for {@link ApiContract}.
     */
    public static Builder builder(String contractId, String name, String version) {
        return new Builder(contractId, name, version);
    }

    /**
     * Fluent builder for {@link ApiContract}.
     */
    public static final class Builder {
        private final String contractId;
        private final String name;
        private final String version;
        private Map<String, String> metadata = Map.of();
        private List<RouteDeclaration> routes = List.of();
        private List<DeprecationSchedule> deprecations = List.of();
        private String basePath;

        private Builder(String contractId, String name, String version) {
            this.contractId = contractId;
            this.name = name;
            this.version = version;
        }

        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder routes(List<RouteDeclaration> routes) { this.routes = routes; return this; }
        public Builder deprecations(List<DeprecationSchedule> deprecations) { this.deprecations = deprecations; return this; }
        public Builder basePath(String basePath) { this.basePath = basePath; return this; }

        public ApiContract build() { return new ApiContract(this); }
    }
}
