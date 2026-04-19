package com.ghatana.phr.api;

import com.ghatana.platform.governance.security.TenantContext;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * HTTP filter that extracts and propagates the tenant identity for every PHR
 * request before it reaches the domain controllers.
 *
 * <p>Tenant ID extraction priority:
 * <ol>
 *   <li>Already-attached {@link TenantContext} (e.g. populated by a JWT auth
 *       filter earlier in the chain) — preferred path.</li>
 *   <li>{@code X-Tenant-ID} request header — fallback for service-to-service
 *       calls that authenticate without JWT.</li>
 * </ol>
 *
 * <p>If no tenant ID can be resolved the request is rejected with HTTP 400 to
 * prevent cross-tenant data access.  The tenant context is unconditionally
 * cleared after the downstream promise completes (success or failure), so
 * thread-local state never leaks across requests.
 *
 * <p>This filter must be placed <em>after</em> any authentication filter so
 * that a JWT-derived tenant context is already attached when this filter runs.
 *
 * @doc.type class
 * @doc.purpose Tenant context extraction and isolation filter for PHR HTTP endpoints
 * @doc.layer product
 * @doc.pattern Filter
 * @since 1.0.0
 */
public final class PhrTenantContextFilter implements AsyncServlet {

    private static final Logger LOG = LoggerFactory.getLogger(PhrTenantContextFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final AsyncServlet delegate;

    /**
     * Creates a new filter wrapping the given downstream servlet.
     *
     * @param delegate the next servlet in the chain; must not be null
     */
    public PhrTenantContextFilter(AsyncServlet delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        Optional<String> tenantId = extractTenantId(request);

        if (tenantId.isEmpty()) {
            LOG.warn("PHR request rejected: no tenant ID found [path={}]", request.getPath());
            TenantContext.clear();
            return Promise.of(HttpResponse.ofCode(400)
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .withJson("{\"error\":\"MISSING_TENANT_ID\","
                            + "\"message\":\"X-Tenant-ID header or an authenticated tenant context is required\"}")
                    .build());
        }

        LOG.debug("PHR tenant context attached [tenantId={}]", tenantId.get());
        TenantContext.setCurrentTenantId(tenantId.get());

        try {
            return delegate.serve(request)
                    .whenComplete((response, exception) -> {
                        // Always clear to prevent thread-local leakage across pooled threads
                        TenantContext.clear();
                    });
        } catch (Exception exception) {
            TenantContext.clear();
            LOG.error("PHR request failed before delegate execution [path={}]", request.getPath(), exception);
            return Promise.ofException(exception);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helper
    // -------------------------------------------------------------------------

    private Optional<String> extractTenantId(HttpRequest request) {
        // Priority 1: already-set context (e.g. from JWT auth filter)
        String fromContext = TenantContext.getCurrentTenantId();
        if (fromContext != null && !fromContext.isBlank()) {
            return Optional.of(fromContext);
        }

        // Priority 2: X-Tenant-ID header
        String fromHeader = request.getHeader(io.activej.http.HttpHeaders.of(TENANT_HEADER));
        if (fromHeader != null && !fromHeader.isBlank()) {
            return Optional.of(fromHeader.trim());
        }

        return Optional.empty();
    }
}
