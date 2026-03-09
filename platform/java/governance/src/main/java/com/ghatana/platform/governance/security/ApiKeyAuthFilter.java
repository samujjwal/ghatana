package com.ghatana.platform.governance.security;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/**
 * Simple API key filter for ingress endpoints.
 * Validates X-API-Key header against an allowlist.
 
 *
 * @doc.type class
 * @doc.purpose Api key auth filter
 * @doc.layer platform
 * @doc.pattern Filter
*/
public class ApiKeyAuthFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    private final Set<String> allowedKeys;
    private final String headerName;
    private final ApiKeyResolver resolver;

    public ApiKeyAuthFilter(Set<String> allowedKeys) {
        this(allowedKeys, "X-API-Key");
    }

    public ApiKeyAuthFilter(Set<String> allowedKeys, String headerName) {
        this.allowedKeys = Objects.requireNonNull(allowedKeys, "allowedKeys");
        this.headerName = Objects.requireNonNull(headerName, "headerName");
        this.resolver = null;
    }

    /**
     * New resolver-based constructor. Uses ApiKeyResolver to derive Principal and set TenantContext.
     */
    public ApiKeyAuthFilter(ApiKeyResolver resolver) {
        this(resolver, "X-API-Key");
    }

    /**
     * New resolver-based constructor with custom header name.
     */
    public ApiKeyAuthFilter(ApiKeyResolver resolver, String headerName) {
        this.allowedKeys = null;
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.headerName = Objects.requireNonNull(headerName, "headerName");
    }

    public AsyncServlet secure(AsyncServlet delegate) {
        return request -> {
            String key = request.getHeader(HttpHeaders.of(headerName));
            if (key == null) {
                log.warn("Unauthorized request: missing API key header {}", headerName);
                return Promise.of(unauthorized());
            }

            // Resolver path (preferred)
            if (resolver != null) {
                var principalOpt = resolver.resolve(key);
                if (principalOpt.isEmpty()) {
                    log.warn("Unauthorized request: invalid API key");
                    return Promise.of(unauthorized());
                }
                var principal = principalOpt.get();
                try (var ignored = TenantContext.scope(principal)) {
                    return delegate.serve(request);
                }
            }

            // Backward-compatible allowlist path
            if (allowedKeys == null || !allowedKeys.contains(key)) {
                log.warn("Unauthorized request: invalid API key");
                return Promise.of(unauthorized());
            }
            return delegate.serve(request);
        };
    }

    private static HttpResponse unauthorized() {
        return HttpResponse.ofCode(401)
                .withHeader(HttpHeaders.of("Content-Type"), "application/json")
                .withJson("{\"error\":{\"code\":\"UNAUTHENTICATED\",\"message\":\"Missing or invalid API key\"}}")
                .build();
    }
}
