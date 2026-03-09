package com.ghatana.platform.governance.security;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reusable ActiveJ HTTP filter that extracts tenant identity from incoming requests
 * and establishes {@link TenantContext} for the duration of request processing.
 *
 * <h2>Purpose</h2>
 * Provides automatic multi-tenant isolation for any ActiveJ HTTP service by:
 * <ul>
 *   <li>Extracting tenant ID from {@code X-Tenant-ID} header</li>
 *   <li>Optionally extracting principal from {@code X-Principal} / {@code X-Roles} headers</li>
 *   <li>Setting up {@link TenantContext} before the delegate servlet runs</li>
 *   <li>Automatically cleaning up context after request completes (success or failure)</li>
 * </ul>
 *
 * <h2>Header Extraction Priority</h2>
 * <ol>
 *   <li>{@code X-Tenant-ID} header — explicit tenant scope</li>
 *   <li>{@code X-Principal} + {@code X-Roles} headers — full principal construction</li>
 *   <li>Falls back to {@code "default-tenant"} (configurable strict mode rejects instead)</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Wrap any RoutingServlet with tenant isolation
 * RoutingServlet routes = RoutingServlet.builder(eventloop)
 *     .with(GET, "/api/data", this::handleData)
 *     .build();
 *
 * // Lenient mode (allows default-tenant fallback)
 * AsyncServlet isolated = TenantIsolationHttpFilter.wrap(routes);
 *
 * // Strict mode (rejects requests without X-Tenant-ID)
 * AsyncServlet strict = TenantIsolationHttpFilter.strict(routes);
 *
 * // Builder for fine-grained control
 * AsyncServlet custom = TenantIsolationHttpFilter.builder(routes)
 *     .withTenantHeader("X-Custom-Tenant")
 *     .withStrictMode(true)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Reusable HTTP filter for tenant context propagation
 * @doc.layer core
 * @doc.pattern Filter
 * @see TenantContext
 * @see TenantIsolationEnforcer
 * @see Principal
 */
public final class TenantIsolationHttpFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolationHttpFilter.class);

    private static final String DEFAULT_TENANT_HEADER = "X-Tenant-ID";
    private static final String DEFAULT_PRINCIPAL_HEADER = "X-Principal";
    private static final String DEFAULT_ROLES_HEADER = "X-Roles";

    private final AsyncServlet delegate;
    private final String tenantHeader;
    private final String principalHeader;
    private final String rolesHeader;
    private final boolean strictMode;

    private TenantIsolationHttpFilter(AsyncServlet delegate,
                                       String tenantHeader,
                                       String principalHeader,
                                       String rolesHeader,
                                       boolean strictMode) {
        this.delegate = Objects.requireNonNull(delegate, "delegate servlet");
        this.tenantHeader = Objects.requireNonNull(tenantHeader, "tenantHeader");
        this.principalHeader = Objects.requireNonNull(principalHeader, "principalHeader");
        this.rolesHeader = Objects.requireNonNull(rolesHeader, "rolesHeader");
        this.strictMode = strictMode;
    }

    /**
     * Wrap a servlet with tenant isolation in lenient mode.
     * Requests without a tenant header fall back to "default-tenant".
     *
     * @param delegate the servlet to wrap
     * @return a new servlet that establishes TenantContext per request
     */
    public static AsyncServlet wrap(AsyncServlet delegate) {
        return new TenantIsolationHttpFilter(
                delegate, DEFAULT_TENANT_HEADER, DEFAULT_PRINCIPAL_HEADER,
                DEFAULT_ROLES_HEADER, false
        ).asServlet();
    }

    /**
     * Wrap a servlet with tenant isolation in strict mode.
     * Requests without a valid {@code X-Tenant-ID} header receive 403 Forbidden.
     *
     * @param delegate the servlet to wrap
     * @return a new servlet that requires tenant context
     */
    public static AsyncServlet strict(AsyncServlet delegate) {
        return new TenantIsolationHttpFilter(
                delegate, DEFAULT_TENANT_HEADER, DEFAULT_PRINCIPAL_HEADER,
                DEFAULT_ROLES_HEADER, true
        ).asServlet();
    }

    /**
     * Create a builder for fine-grained configuration.
     *
     * @param delegate the servlet to wrap
     * @return builder instance
     */
    public static Builder builder(AsyncServlet delegate) {
        return new Builder(delegate);
    }

    /**
     * Create the filtering {@link AsyncServlet}.
     */
    AsyncServlet asServlet() {
        return this::serve;
    }

    private Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        Optional<String> tenantId = extractTenantId(request);

        if (tenantId.isEmpty() && strictMode) {
            log.warn("Rejected request: missing {} header (strict mode)", tenantHeader);
            return Promise.of(forbidden("Missing required " + tenantHeader + " header"));
        }

        String resolvedTenant = tenantId.orElse("default-tenant");
        Principal principal = buildPrincipal(request, resolvedTenant);

        try (var ignored = TenantContext.scope(principal)) {
            log.debug("Tenant context set: tenantId={}, principal={}",
                    resolvedTenant, principal.getName());
            return delegate.serve(request)
                    .whenComplete((response, ex) -> {
                        if (ex != null) {
                            log.warn("Request failed for tenant {}: {}",
                                    resolvedTenant, ex.getMessage());
                        }
                    });
        }
    }

    private Optional<String> extractTenantId(HttpRequest request) {
        String value = request.getHeader(HttpHeaders.of(tenantHeader));
        if (value != null && !value.isBlank()) {
            return Optional.of(value.trim());
        }
        return Optional.empty();
    }

    private Principal buildPrincipal(HttpRequest request, String tenantId) {
        String name = request.getHeader(HttpHeaders.of(principalHeader));
        String rolesValue = request.getHeader(HttpHeaders.of(rolesHeader));

        String principalName = (name != null && !name.isBlank())
                ? name.trim()
                : "anonymous";

        List<String> roles = (rolesValue != null && !rolesValue.isBlank())
                ? List.of(rolesValue.split(","))
                : List.of();

        return new Principal(principalName, roles, tenantId);
    }

    private static HttpResponse forbidden(String message) {
        return HttpResponse.ofCode(403)
                .withHeader(HttpHeaders.of("Content-Type"), "application/json")
                .withJson("{\"error\":{\"code\":\"TENANT_REQUIRED\",\"message\":\"" + message + "\"}}")
                .build();
    }

    /**
     * Builder for customizing tenant isolation filter behavior.
     *
     * @doc.type class
     * @doc.purpose Builder for TenantIsolationHttpFilter configuration
     * @doc.layer core
     * @doc.pattern Builder
     */
    public static final class Builder {
        private final AsyncServlet delegate;
        private String tenantHeader = DEFAULT_TENANT_HEADER;
        private String principalHeader = DEFAULT_PRINCIPAL_HEADER;
        private String rolesHeader = DEFAULT_ROLES_HEADER;
        private boolean strictMode = false;

        private Builder(AsyncServlet delegate) {
            this.delegate = delegate;
        }

        /**
         * Set custom header name for tenant ID extraction.
         *
         * @param header the HTTP header name (default: "X-Tenant-ID")
         * @return this builder
         */
        public Builder withTenantHeader(String header) {
            this.tenantHeader = header;
            return this;
        }

        /**
         * Set custom header name for principal name extraction.
         *
         * @param header the HTTP header name (default: "X-Principal")
         * @return this builder
         */
        public Builder withPrincipalHeader(String header) {
            this.principalHeader = header;
            return this;
        }

        /**
         * Set custom header name for roles extraction.
         *
         * @param header the HTTP header name (default: "X-Roles")
         * @return this builder
         */
        public Builder withRolesHeader(String header) {
            this.rolesHeader = header;
            return this;
        }

        /**
         * Enable strict mode — requests without tenant header receive 403.
         *
         * @param strict true to enable strict mode
         * @return this builder
         */
        public Builder withStrictMode(boolean strict) {
            this.strictMode = strict;
            return this;
        }

        /**
         * Build the filter as an AsyncServlet.
         *
         * @return the wrapping AsyncServlet
         */
        public AsyncServlet build() {
            return new TenantIsolationHttpFilter(
                    delegate, tenantHeader, principalHeader, rolesHeader, strictMode
            ).asServlet();
        }
    }
}
