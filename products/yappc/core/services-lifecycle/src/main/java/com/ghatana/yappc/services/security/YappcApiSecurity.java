package com.ghatana.yappc.services.security;

import com.ghatana.platform.http.security.filter.ApiKeyAuthFilter;
import com.ghatana.platform.http.security.filter.RBACFilter;
import com.ghatana.platform.http.security.filter.RateLimitFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.InMemoryPolicyRepository;
import com.ghatana.platform.security.rbac.PolicyService;
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

    /**
     * Test-friendly overload that accepts API key configuration as parameters.
     *
     * @param endpoint servlet to secure
     * @param resource RBAC resource name
     * @param apiKeys comma-separated list of valid API keys
     * @param tenantMap API key to tenant mapping (key1=tenant1;key2=tenant2)
     * @param roleMap API key to role mapping (key1=role1|role2)
     * @param rateLimitMax maximum requests per window
     * @param rateLimitWindow window duration in seconds
     * @return endpoint secured with API key auth, RBAC read permission, and rate limiting
     */
    public static AsyncServlet secureReadEndpoint(
            AsyncServlet endpoint,
            String resource,
            String apiKeys,
            String tenantMap,
            String roleMap,
            int rateLimitMax,
            long rateLimitWindow) {
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitMax, rateLimitWindow);
        ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(
                buildApiKeyResolver(apiKeys, tenantMap, roleMap, "admin"));
        PolicyService policyService = buildPolicyService(resource);
        RBACFilter readFilter = new RBACFilter(policyService, "read", resource);

        return authFilter.secure(readFilter.secure(rateLimitFilter.wrap(endpoint)));
    }

    private static ApiKeyResolver buildApiKeyResolver() {
        String keysEnv = System.getenv(API_KEYS_ENV);
        if (keysEnv == null || keysEnv.isBlank()) {
            throw new IllegalStateException(
                "YAPPC_API_KEYS environment variable is required. " +
                "Set it to a comma-separated list of API keys for production."
            );
        }
        Set<String> allowedKeys = parseCsvSet(keysEnv);
        Map<String, List<String>> roleMap = parseRoleMap(System.getenv(API_KEY_ROLE_MAP_ENV));
        Map<String, String> tenantMap = parseSimpleMap(System.getenv(API_KEY_TENANT_MAP_ENV));
        List<String> defaultRoles = parseCsvList(System.getenv().getOrDefault(API_DEFAULT_ROLES_ENV, "admin"));

        return buildApiKeyResolver(allowedKeys, tenantMap, roleMap, defaultRoles);
    }

    private static ApiKeyResolver buildApiKeyResolver(
            String apiKeys,
            String tenantMap,
            String roleMap,
            String defaultRoles) {
        Set<String> allowedKeys = parseCsvSet(apiKeys);
        Map<String, List<String>> parsedRoleMap = parseRoleMap(roleMap);
        Map<String, String> parsedTenantMap = parseSimpleMap(tenantMap);
        List<String> parsedDefaultRoles = parseCsvList(defaultRoles);

        return buildApiKeyResolver(allowedKeys, parsedTenantMap, parsedRoleMap, parsedDefaultRoles);
    }

    private static ApiKeyResolver buildApiKeyResolver(
            Set<String> allowedKeys,
            Map<String, String> tenantMap,
            Map<String, List<String>> roleMap,
            List<String> defaultRoles) {
        return apiKey -> {
            if (!allowedKeys.contains(apiKey)) {
                return Optional.empty();
            }
            List<String> roles = roleMap.getOrDefault(apiKey, defaultRoles);
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
