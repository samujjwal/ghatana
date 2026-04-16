package com.ghatana.refactorer.server.auth;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Filter that extracts tenant ID from request and makes it available via
 * thread-local storage.
 *
 * This filter should run AFTER authentication filters (like JwtAuthFilter) so
 * the tenant context is already attached to the request.
 *
 * <p>Tenant ID extraction priority:
 * 1. Already attached TenantContext (from JWT or other auth filter) - preferred
 * 2. X-Tenant-ID header - fallback for requests without JWT
 *
 * <p>If no tenant ID is found, the request is rejected with a 400 error.
 * This ensures proper tenant isolation and prevents cross-tenant data access.
 *
 * @doc.type class
 * @doc.purpose Apply tenant context concerns before requests reach the REST controllers.
 * @doc.layer product
 * @doc.pattern Filter
 */
public final class TenantContextFilter implements AsyncServlet {

    private static final Logger logger = LogManager.getLogger(TenantContextFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final AsyncServlet delegate;

    public TenantContextFilter(AsyncServlet delegate) {
        this.delegate = delegate;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        return filter(request, delegate);
    }

    public Promise<HttpResponse> filter(HttpRequest request, AsyncServlet next) {
        try {
            // Extract tenant ID with priority: attached context > X-Tenant-ID header
            Optional<String> tenantId = extractTenantId(request);

            if (tenantId.isPresent()) {
                logger.debug("Extracted tenant ID: {}", tenantId.get());
                com.ghatana.platform.governance.security.TenantContext.setCurrentTenantId(tenantId.get());
            } else {
                // No tenant ID found - reject request
                logger.error("No tenant ID found in request - rejecting");
                com.ghatana.platform.governance.security.TenantContext.clear();
                return Promise.of(HttpResponse.ofCode(400)
                        .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                        .withJson("{\"error\":\"MISSING_TENANT_ID\",\"message\":\"X-Tenant-ID header or authenticated tenant context is required\"}")
                        .build());
            }

            // Serve request and ensure cleanup on completion
            return next.serve(request)
                    .whenComplete((response, exception) -> com.ghatana.platform.governance.security.TenantContext.clear());

        } catch (Exception e) {
            logger.error("Error extracting tenant context", e);
            // Always cleanup, even on error
            com.ghatana.platform.governance.security.TenantContext.clear();
            return Promise.ofException(e);
        }
    }

    /**
     * Extracts tenant ID from request using priority order: 1. Attached
     * TenantContext (from previous auth filter) 2. X-Tenant-ID header
     *
     * @param request HTTP request
     * @return Optional containing tenant ID if found
     */
    private Optional<String> extractTenantId(HttpRequest request) {
        // First, check if TenantContext is already attached (from JWT auth or similar)
        TenantContext attachedContext = TenantResolver.get(request);
        if (attachedContext != null && attachedContext.tenantId() != null) {
            return Optional.of(attachedContext.tenantId());
        }

        // Fall back to X-Tenant-ID header
        String headerValue = request.getHeader(io.activej.http.HttpHeaders.of(TENANT_HEADER));
        if (headerValue != null && !headerValue.isBlank()) {
            return Optional.of(headerValue.trim());
        }

        return Optional.empty();
    }
}
