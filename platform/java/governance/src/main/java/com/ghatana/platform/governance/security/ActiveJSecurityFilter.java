package com.ghatana.platform.governance.security;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * ActiveJ-compatible security filter for tenant isolation and authentication.
 * 
 * <p>Extracts principal from HTTP request (headers, JWT, API key) and
 * establishes TenantContext for the request lifecycle. Designed for use
 * with ActiveJ's RoutingServlet.
 * 
 * <h2>Usage with ActiveJ RoutingServlet</h2>
 * <pre>{@code
 * // Create filter
 * ActiveJSecurityFilter filter = ActiveJSecurityFilter.builder()
 *     .principalExtractor(new JwtPrincipalExtractor(jwtVerifier))
 *     .requireAuthentication(true)
 *     .excludePath("/health")
 *     .excludePath("/metrics")
 *     .build();
 * 
 * // Wrap handler
 * RoutingServlet servlet = RoutingServlet.create(eventloop)
 *     .map("/api/*", filter.wrap(apiHandler));
 * }</pre>
 * 
 * <h2>Principal Extraction</h2>
 * The filter uses a {@link PrincipalExtractor} to extract principal from request:
 * <ul>
 *   <li>JWT token from Authorization header</li>
 *   <li>API key from X-API-Key header</li>
 *   <li>Session from cookie</li>
 *   <li>Custom extraction logic</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose ActiveJ security filter for tenant isolation
 * @doc.layer core
 * @doc.pattern Filter
 * @see TenantContext
 * @see Principal
 * @see TenantIsolationEnforcer
 */
public class ActiveJSecurityFilter {

    private final PrincipalExtractor principalExtractor;
    private final boolean requireAuthentication;
    private final List<String> excludedPaths;
    private final Function<HttpRequest, HttpResponse> unauthorizedHandler;
    private final Function<HttpRequest, HttpResponse> forbiddenHandler;

    private ActiveJSecurityFilter(Builder builder) {
        this.principalExtractor = Objects.requireNonNull(builder.principalExtractor, "principalExtractor required");
        this.requireAuthentication = builder.requireAuthentication;
        this.excludedPaths = List.copyOf(builder.excludedPaths);
        this.unauthorizedHandler = builder.unauthorizedHandler;
        this.forbiddenHandler = builder.forbiddenHandler;
    }

    /**
     * Create a builder for the security filter.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Wrap a handler with security filtering.
     * 
     * <p>The returned function can be used directly with ActiveJ routing.
     *
     * @param handler the handler to wrap
     * @return wrapped handler with security checks
     */
    public Function<HttpRequest, Promise<HttpResponse>> wrap(
            Function<HttpRequest, Promise<HttpResponse>> handler) {
        
        return request -> {
            String path = request.getPath();
            
            // Check excluded paths
            if (isExcluded(path)) {
                return handler.apply(request);
            }
            
            // Extract principal
            return principalExtractor.extract(request)
                .then(optionalPrincipal -> {
                    if (requireAuthentication && optionalPrincipal.isEmpty()) {
                        return Promise.of(unauthorizedHandler.apply(request));
                    }
                    
                    // Set tenant context and invoke handler
                    Principal principal = optionalPrincipal.orElse(null);
                    
                    if (principal != null) {
                        try (AutoCloseable scope = TenantContext.scope(principal)) {
                            return handler.apply(request);
                        } catch (Exception e) {
                            return Promise.ofException(e);
                        }
                    } else {
                        return handler.apply(request);
                    }
                });
        };
    }

    /**
     * Wrap a synchronous handler with security filtering.
     */
    public Function<HttpRequest, Promise<HttpResponse>> wrapSync(
            Function<HttpRequest, HttpResponse> handler) {
        return wrap(request -> Promise.of(handler.apply(request)));
    }

    private boolean isExcluded(String path) {
        return excludedPaths.stream().anyMatch(excluded -> {
            if (excluded.endsWith("/*")) {
                String prefix = excluded.substring(0, excluded.length() - 2);
                return path.startsWith(prefix);
            }
            return path.equals(excluded);
        });
    }

    /**
     * Extracts principal from HTTP request.
     */
    @FunctionalInterface
    public interface PrincipalExtractor {
        
        /**
         * Extract principal from request.
         *
         * @param request the HTTP request
         * @return promise of optional principal
         */
        Promise<Optional<Principal>> extract(HttpRequest request);

        /**
         * Header-based extractor that reads tenant and user from headers.
         * For development/testing only.
         */
        static PrincipalExtractor fromHeaders() {
            return request -> {
                String tenantId = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id"));
                String userId = request.getHeader(io.activej.http.HttpHeaders.of("X-User-Id"));
                String roles = request.getHeader(io.activej.http.HttpHeaders.of("X-User-Roles"));
                
                if (tenantId == null || userId == null) {
                    return Promise.of(Optional.empty());
                }
                
                List<String> roleList = roles != null ? 
                    List.of(roles.split(",")) : List.of();
                
                Principal principal = new Principal(userId, roleList, tenantId);
                return Promise.of(Optional.of(principal));
            };
        }

        /**
         * API key based extractor using ApiKeyResolver.
         */
        static PrincipalExtractor fromApiKey(ApiKeyResolver resolver) {
            return request -> {
                String apiKey = request.getHeader(io.activej.http.HttpHeaders.of("X-API-Key"));
                if (apiKey == null) {
                    apiKey = request.getHeader(io.activej.http.HttpHeaders.AUTHORIZATION);
                    if (apiKey != null && apiKey.startsWith("Bearer ")) {
                        apiKey = apiKey.substring(7);
                    } else {
                        apiKey = null;
                    }
                }
                
                if (apiKey == null) {
                    return Promise.of(Optional.empty());
                }
                
                String finalApiKey = apiKey;
                Optional<Principal> principal = resolver.resolve(finalApiKey);
                return Promise.of(principal);
            };
        }
    }

    /**
     * Builder for ActiveJSecurityFilter.
     */
    public static class Builder {
        private PrincipalExtractor principalExtractor = PrincipalExtractor.fromHeaders();
        private boolean requireAuthentication = true;
        private final java.util.ArrayList<String> excludedPaths = new java.util.ArrayList<>();
        private Function<HttpRequest, HttpResponse> unauthorizedHandler = 
            request -> HttpResponse.ofCode(401).withBody("Unauthorized".getBytes()).build();
        private Function<HttpRequest, HttpResponse> forbiddenHandler = 
            request -> HttpResponse.ofCode(403).withBody("Forbidden".getBytes()).build();

        /**
         * Set the principal extractor.
         */
        public Builder principalExtractor(PrincipalExtractor extractor) {
            this.principalExtractor = extractor;
            return this;
        }

        /**
         * Set whether authentication is required.
         */
        public Builder requireAuthentication(boolean required) {
            this.requireAuthentication = required;
            return this;
        }

        /**
         * Exclude a path from security checks.
         * Supports wildcard suffix (e.g., "/health/*").
         */
        public Builder excludePath(String path) {
            this.excludedPaths.add(path);
            return this;
        }

        /**
         * Set handler for unauthorized responses.
         */
        public Builder unauthorizedHandler(Function<HttpRequest, HttpResponse> handler) {
            this.unauthorizedHandler = handler;
            return this;
        }

        /**
         * Set handler for forbidden responses.
         */
        public Builder forbiddenHandler(Function<HttpRequest, HttpResponse> handler) {
            this.forbiddenHandler = handler;
            return this;
        }

        public ActiveJSecurityFilter build() {
            return new ActiveJSecurityFilter(this);
        }
    }
}
