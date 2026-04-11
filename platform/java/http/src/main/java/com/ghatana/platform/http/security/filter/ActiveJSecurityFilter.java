package com.ghatana.platform.http.security.filter;

import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
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
 * @doc.type class
 * @doc.purpose ActiveJ security filter for tenant isolation
 * @doc.layer core
 * @doc.pattern Filter
 * @see TenantContext
 * @see Principal
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

    public static Builder builder() {
        return new Builder();
    }

    public Function<HttpRequest, Promise<HttpResponse>> wrap(
            Function<HttpRequest, Promise<HttpResponse>> handler) {
        return request -> {
            String path = request.getPath();
            if (isExcluded(path)) {
                return handler.apply(request);
            }
            return principalExtractor.extract(request)
                .then(optionalPrincipal -> {
                    if (requireAuthentication && optionalPrincipal.isEmpty()) {
                        return Promise.of(unauthorizedHandler.apply(request));
                    }
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

    @FunctionalInterface
    public interface PrincipalExtractor {
        Promise<Optional<Principal>> extract(HttpRequest request);

        static PrincipalExtractor fromHeaders() {
            return request -> {
                String tenantId = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id"));
                String userId = request.getHeader(io.activej.http.HttpHeaders.of("X-User-Id"));
                String roles = request.getHeader(io.activej.http.HttpHeaders.of("X-User-Roles"));
                if (tenantId == null || userId == null) {
                    return Promise.of(Optional.empty());
                }
                List<String> roleList = roles != null ? List.of(roles.split(",")) : List.of();
                Principal principal = new Principal(userId, roleList, tenantId);
                return Promise.of(Optional.of(principal));
            };
        }

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
                Optional<Principal> principal = resolver.resolve(apiKey);
                return Promise.of(principal);
            };
        }
    }

    public static class Builder {
        private PrincipalExtractor principalExtractor = PrincipalExtractor.fromHeaders();
        private boolean requireAuthentication = true;
        private final java.util.ArrayList<String> excludedPaths = new java.util.ArrayList<>();
        private Function<HttpRequest, HttpResponse> unauthorizedHandler =
            request -> HttpResponse.ofCode(401).withBody("Unauthorized".getBytes()).build();
        private Function<HttpRequest, HttpResponse> forbiddenHandler =
            request -> HttpResponse.ofCode(403).withBody("Forbidden".getBytes()).build();

        public Builder principalExtractor(PrincipalExtractor extractor) {
            this.principalExtractor = extractor;
            return this;
        }

        public Builder requireAuthentication(boolean required) {
            this.requireAuthentication = required;
            return this;
        }

        public Builder excludePath(String path) {
            this.excludedPaths.add(path);
            return this;
        }

        public Builder unauthorizedHandler(Function<HttpRequest, HttpResponse> handler) {
            this.unauthorizedHandler = handler;
            return this;
        }

        public Builder forbiddenHandler(Function<HttpRequest, HttpResponse> handler) {
            this.forbiddenHandler = handler;
            return this;
        }

        public ActiveJSecurityFilter build() {
            return new ActiveJSecurityFilter(this);
        }
    }
}
