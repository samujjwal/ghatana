package com.ghatana.platform.http.security.filter;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
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
 * @doc.type class
 * @doc.purpose Reusable HTTP filter for tenant context propagation
 * @doc.layer core
 * @doc.pattern Filter
 * @see TenantContext
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

    public static AsyncServlet wrap(AsyncServlet delegate) {
        return new TenantIsolationHttpFilter(
                delegate, DEFAULT_TENANT_HEADER, DEFAULT_PRINCIPAL_HEADER,
                DEFAULT_ROLES_HEADER, false
        ).asServlet();
    }

    public static AsyncServlet strict(AsyncServlet delegate) {
        return new TenantIsolationHttpFilter(
                delegate, DEFAULT_TENANT_HEADER, DEFAULT_PRINCIPAL_HEADER,
                DEFAULT_ROLES_HEADER, true
        ).asServlet();
    }

    public static Builder builder(AsyncServlet delegate) {
        return new Builder(delegate);
    }

    AsyncServlet asServlet() {
        return this::serve;
    }

    private Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        Principal upstream = request.getAttachment(Principal.class);
        if (upstream != null) {
            log.debug("Tenant context already established upstream: tenantId={}, principal={}",
                    upstream.getTenantId(), upstream.getName());
            return delegate.serve(request);
        }

        Optional<String> tenantId = extractTenantId(request);

        if (tenantId.isEmpty() && strictMode) {
            log.warn("Rejected request: missing {} header (strict mode)", tenantHeader);
            return Promise.of(forbidden("Missing required " + tenantHeader + " header"));
        }

        String resolvedTenant = tenantId.orElse("default-tenant");
        Principal principal = buildPrincipal(request, resolvedTenant);

        TenantContext.Scope scope = TenantContext.scope(principal);
        log.debug("Tenant context set: tenantId={}, principal={}", resolvedTenant, principal.getName());
        return delegate.serve(request)
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        log.warn("Request failed for tenant {}: {}", resolvedTenant, ex.getMessage());
                    }
                    scope.close();
                });
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

    public static final class Builder {
        private final AsyncServlet delegate;
        private String tenantHeader = DEFAULT_TENANT_HEADER;
        private String principalHeader = DEFAULT_PRINCIPAL_HEADER;
        private String rolesHeader = DEFAULT_ROLES_HEADER;
        private boolean strictMode = false;

        private Builder(AsyncServlet delegate) {
            this.delegate = delegate;
        }

        public Builder withTenantHeader(String header) {
            this.tenantHeader = header;
            return this;
        }

        public Builder withPrincipalHeader(String header) {
            this.principalHeader = header;
            return this;
        }

        public Builder withRolesHeader(String header) {
            this.rolesHeader = header;
            return this;
        }

        public Builder withStrictMode(boolean strict) {
            this.strictMode = strict;
            return this;
        }

        public AsyncServlet build() {
            return new TenantIsolationHttpFilter(
                    delegate, tenantHeader, principalHeader, rolesHeader, strictMode
            ).asServlet();
        }
    }
}
