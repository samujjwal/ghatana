package com.ghatana.yappc.services.security;

import com.ghatana.platform.governance.security.ApiKeyAuthFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.RateLimitFilter;
import com.ghatana.platform.security.rbac.InMemoryPolicyRepository;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.security.rbac.RBACFilter;
import io.activej.http.AsyncServlet;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared API security composition for YAPPC services.
 *
 * <p>Builds a layered filter chain:
 * API key auth (principal resolution) -> RBAC (read/write) -> rate limiting.</p>
 *
 * @doc.type class
 * @doc.purpose Builds consistent API security filters (auth, RBAC, rate limit) for YAPPC services
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class YappcApiSecurity {

    private static final String API_KEYS_ENV = "YAPPC_API_KEYS";
    private static final String API_KEY_ROLE_MAP_ENV = "YAPPC_API_KEY_ROLE_MAP";
    private static final String API_KEY_TENANT_MAP_ENV = "YAPPC_API_KEY_TENANT_MAP";
    private static final String API_DEFAULT_ROLES_ENV = "YAPPC_API_DEFAULT_ROLES";

    private YappcApiSecurity() {}

    /**
     * Secures an API servlet with consistent YAPPC authn/authz controls.
     *
     * @param apiServlet API servlet to secure
     * @param resource RBAC resource name (for example, yappc:lifecycle-api)
     * @return read/write secured servlet pair
     */
    public static SecurityRoutes secureApi(AsyncServlet apiServlet, String resource) {
        int rateLimitMax = parseIntEnv("YAPPC_RATE_LIMIT_MAX", 100);
        long rateLimitWindow = parseLongEnv("YAPPC_RATE_LIMIT_WINDOW", 60L);

        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitMax, rateLimitWindow);
        ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(buildApiKeyResolver());

        PolicyService policyService = buildPolicyService(resource);
        RBACFilter readFilter = new RBACFilter(policyService, "read", resource);
        RBACFilter writeFilter = new RBACFilter(policyService, "write", resource);

        AsyncServlet readSecured = authFilter.secure(readFilter.secure(rateLimitFilter.wrap(apiServlet)));
        AsyncServlet writeSecured = authFilter.secure(writeFilter.secure(rateLimitFilter.wrap(apiServlet)));

        return new SecurityRoutes(readSecured, writeSecured);
    }

    /**
     * Secures a non-API endpoint that should be read-only (for example, metrics).
     *
     * @param endpoint servlet to secure
     * @param resource RBAC resource name (for example, yappc:lifecycle-metrics)
     * @return endpoint secured with API key auth, RBAC read permission, and rate limiting
     */
    public static AsyncServlet secureReadEndpoint(AsyncServlet endpoint, String resource) {
        int rateLimitMax = parseIntEnv("YAPPC_RATE_LIMIT_MAX", 100);
        long rateLimitWindow = parseLongEnv("YAPPC_RATE_LIMIT_WINDOW", 60L);

        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitMax, rateLimitWindow);
        ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(buildApiKeyResolver());
        PolicyService policyService = buildPolicyService(resource);
        RBACFilter readFilter = new RBACFilter(policyService, "read", resource);

        return authFilter.secure(readFilter.secure(rateLimitFilter.wrap(endpoint)));
    }

    private static ApiKeyResolver buildApiKeyResolver() {
        Set<String> allowedKeys = parseCsvSet(System.getenv().getOrDefault(API_KEYS_ENV, "dev-key"));
        Map<String, List<String>> roleMap = parseRoleMap(System.getenv(API_KEY_ROLE_MAP_ENV));
        Map<String, String> tenantMap = parseSimpleMap(System.getenv(API_KEY_TENANT_MAP_ENV));
        List<String> defaultRoles = parseCsvList(System.getenv().getOrDefault(API_DEFAULT_ROLES_ENV, "admin"));

        return apiKey -> {
            if (!allowedKeys.contains(apiKey)) {
                return Optional.empty();
            }
            List<String> roles = roleMap.getOrDefault(apiKey, defaultRoles);
            String tenantId = tenantMap.getOrDefault(apiKey, "default-tenant");
            String principalName = "api-key-" + Integer.toUnsignedString(apiKey.hashCode(), 16);
            return Optional.of(new Principal(principalName, roles, tenantId));
        };
    }

    private static PolicyService buildPolicyService(String resource) {
        PolicyService service = new PolicyService(new InMemoryPolicyRepository());

        // Admins get full access. Editors and agents can read/write. Viewers are read-only.
        service.createPolicy("yappc-admin-" + resource, "YAPPC admin policy", "admin", resource, Set.of("*"));
        service.createPolicy("yappc-editor-" + resource, "YAPPC editor policy", "editor", resource, Set.of("read", "write"));
        service.createPolicy("yappc-agent-" + resource, "YAPPC agent policy", "agent", resource, Set.of("read", "write"));
        service.createPolicy("yappc-viewer-" + resource, "YAPPC viewer policy", "viewer", resource, Set.of("read"));

        return service;
    }

    private static Set<String> parseCsvSet(String value) {
        return parseCsvList(value).stream().collect(Collectors.toSet());
    }

    private static List<String> parseCsvList(String value) {
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

    private static int parseIntEnv(String envName, int defaultValue) {
        try {
            return Integer.parseInt(System.getenv().getOrDefault(envName, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static long parseLongEnv(String envName, long defaultValue) {
        try {
            return Long.parseLong(System.getenv().getOrDefault(envName, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * Pair of secured API servlets split by read and write permission.
     */
    public record SecurityRoutes(AsyncServlet readApi, AsyncServlet writeApi) {}
}
