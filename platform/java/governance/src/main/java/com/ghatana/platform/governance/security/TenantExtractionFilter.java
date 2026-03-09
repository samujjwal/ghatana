package com.ghatana.platform.governance.security;

import com.ghatana.platform.http.server.filter.FilterChain;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * A {@link FilterChain.Filter} implementation that extracts tenant identity from
 * request headers and establishes {@link TenantContext} for the request duration.
 *
 * <h2>Purpose</h2>
 * Plugs into {@link com.ghatana.platform.http.server.server.HttpServerBuilder#addFilter}
 * to provide automatic multi-tenant isolation for services built on the platform HTTP server.
 *
 * <h2>Usage with HttpServerBuilder</h2>
 * <pre>{@code
 * HttpServerBuilder.create()
 *     .withPort(8080)
 *     .addFilter(TenantExtractionFilter.lenient())
 *     .addAsyncRoute(HttpMethod.POST, "/api/data", this::handleData)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose FilterChain.Filter for tenant context extraction in HttpServerBuilder pipelines
 * @doc.layer core
 * @doc.pattern Filter
 * @see TenantContext
 * @see TenantIsolationHttpFilter
 * @see FilterChain
 */
public final class TenantExtractionFilter implements FilterChain.Filter {

    private static final Logger log = LoggerFactory.getLogger(TenantExtractionFilter.class);

    private static final String DEFAULT_TENANT_HEADER = "X-Tenant-ID";
    private static final String DEFAULT_PRINCIPAL_HEADER = "X-Principal";
    private static final String DEFAULT_ROLES_HEADER = "X-Roles";

    private final String tenantHeader;
    private final String principalHeader;
    private final String rolesHeader;
    private final boolean strictMode;

    private TenantExtractionFilter(String tenantHeader, String principalHeader,
                                    String rolesHeader, boolean strictMode) {
        this.tenantHeader = tenantHeader;
        this.principalHeader = principalHeader;
        this.rolesHeader = rolesHeader;
        this.strictMode = strictMode;
    }

    /**
     * Create a lenient filter. Requests without tenant header fall back to "default-tenant".
     *
     * @return lenient filter instance
     */
    public static TenantExtractionFilter lenient() {
        return new TenantExtractionFilter(
                DEFAULT_TENANT_HEADER, DEFAULT_PRINCIPAL_HEADER,
                DEFAULT_ROLES_HEADER, false);
    }

    /**
     * Create a strict filter. Requests without tenant header receive 403 Forbidden.
     *
     * @return strict filter instance
     */
    public static TenantExtractionFilter strict() {
        return new TenantExtractionFilter(
                DEFAULT_TENANT_HEADER, DEFAULT_PRINCIPAL_HEADER,
                DEFAULT_ROLES_HEADER, true);
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, AsyncServlet next) throws Exception {
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
            return next.serve(request);
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

        String principalName = (name != null && !name.isBlank()) ? name.trim() : "anonymous";
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
}
