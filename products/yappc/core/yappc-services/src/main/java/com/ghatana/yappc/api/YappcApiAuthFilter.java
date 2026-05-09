package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Enforces fail-closed authentication for YAPPC ActiveJ HTTP endpoints and propagates principal tenant context
 * @doc.layer api
 * @doc.pattern Filter
 */
public final class YappcApiAuthFilter {

    private static final String API_KEYS_ENV = "YAPPC_API_KEYS";
    private static final String API_KEY_ROLE_MAP_ENV = "YAPPC_API_KEY_ROLE_MAP";
    private static final String API_KEY_TENANT_MAP_ENV = "YAPPC_API_KEY_TENANT_MAP";

    private final ApiKeyResolver resolver;

    public YappcApiAuthFilter(ApiKeyResolver resolver) {
        this.resolver = resolver;
    }

    public static YappcApiAuthFilter fromEnvironment() {
        return new YappcApiAuthFilter(buildApiKeyResolver());
    }

    public AsyncServlet secure(AsyncServlet delegate) {
        return request -> {
            Optional<Principal> principal = resolvePrincipal(request);
            if (principal.isEmpty()) {
                return Promise.of(unauthorized());
            }

            request.attach(Principal.class, principal.get());
            TenantContext.Scope scope = TenantContext.scope(principal.get());
            return delegate.serve(request)
                .whenComplete((response, error) -> scope.close());
        };
    }

    private Optional<Principal> resolvePrincipal(HttpRequest request) {
        String credential = request.getHeader(HttpHeaders.of("X-API-Key"));
        if (credential == null || credential.isBlank()) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                credential = authorization.substring("Bearer ".length()).trim();
            }
        }

        if (credential == null || credential.isBlank()) {
            return Optional.empty();
        }

        return resolver.resolve(credential);
    }

    private static HttpResponse unauthorized() {
        return HttpResponse.ofCode(401)
            .withHeader(HttpHeaders.of("WWW-Authenticate"), "Bearer realm=\"YAPPC API\"")
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .withJson("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Missing or invalid credentials\"}")
            .build();
    }

    private static ApiKeyResolver buildApiKeyResolver() {
        String keysEnv = System.getenv(API_KEYS_ENV);
        if (keysEnv == null || keysEnv.isBlank()) {
            throw new IllegalStateException(
                "YAPPC_API_KEYS environment variable is required. " +
                "Set it to a comma-separated list of API keys for production."
            );
        }
        
        String tenantMapEnv = System.getenv(API_KEY_TENANT_MAP_ENV);
        if (tenantMapEnv == null || tenantMapEnv.isBlank()) {
            throw new IllegalStateException(
                "YAPPC_API_KEY_TENANT_MAP environment variable is required. " +
                "Set it with format: key1=tenant1;key2=tenant2"
            );
        }

        Set<String> allowedKeys = parseCsvSet(keysEnv);
        Map<String, List<String>> roleMap = parseRoleMap(System.getenv(API_KEY_ROLE_MAP_ENV));
        Map<String, String> tenantMap = parseSimpleMap(tenantMapEnv);

        return apiKey -> {
            if (!allowedKeys.contains(apiKey)) {
                return Optional.empty();
            }
            List<String> roles = roleMap.get(apiKey);
            if (roles == null || roles.isEmpty()) {
                throw new IllegalStateException(
                    "No role mapping found for API key. " +
                    "Set YAPPC_API_KEY_ROLE_MAP with format: key1=role1|role2;key2=role3"
                );
            }
            String tenantId = tenantMap.get(apiKey);
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalStateException(
                    "No tenant mapping found for API key. " +
                    "Set YAPPC_API_KEY_TENANT_MAP with format: key1=tenant1;key2=tenant2"
                );
            }
            String principalName = "api-key-" + Integer.toUnsignedString(apiKey.hashCode(), 16);
            return Optional.of(new Principal(principalName, roles, tenantId));
        };
    }

    private static Set<String> parseCsvSet(String value) {
        return parseCsvList(value).stream().collect(Collectors.toSet());
    }

    private static List<String> parseCsvList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private static Map<String, List<String>> parseRoleMap(String value) {
        Map<String, String> raw = parseSimpleMap(value);
        Map<String, List<String>> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            List<String> roles = Arrays.stream(entry.getValue().split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
            if (!roles.isEmpty()) {
                parsed.put(entry.getKey(), roles);
            }
        }
        return parsed;
    }

    private static Map<String, String> parseSimpleMap(String value) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return parsed;
        }

        String[] pairs = value.split(";");
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = pair.substring(0, separator).trim();
            String mappedValue = pair.substring(separator + 1).trim();
            if (!key.isEmpty() && !mappedValue.isEmpty()) {
                parsed.put(key, mappedValue);
            }
        }

        return parsed;
    }
}
