package com.ghatana.datacloud.security;

import com.ghatana.datacloud.security.RoutePolicyEnforcer.*;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Security interceptor for HTTP request policy enforcement.
 *
 * <p><b>Purpose</b><br>
 * Intercepts HTTP requests and enforces security policies based on the route
 * sensitivity matrix, including authentication, authorization, tenant isolation,
 * and data classification checks.
 *
 * @doc.type class
 * @doc.purpose HTTP security interceptor for policy enforcement
 * @doc.layer product
 * @doc.pattern Security Interceptor
 */
public final class SecurityInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SecurityInterceptor.class);

    private final RoutePolicyEnforcer policyEnforcer;
    private static final Set<String> MEDIA_CREATE = Set.of("media:artifact:create");
    private static final Set<String> MEDIA_READ = Set.of("media:artifact:read");
    private static final Set<String> MEDIA_READ_RESULT = Set.of("media:artifact:read-result");
    private static final Set<String> MEDIA_PROCESS = Set.of("media:artifact:process", "media:artifact:retry");
    private static final Set<String> MEDIA_UPDATE = Set.of("media:artifact:update-consent");
    private static final Set<String> MEDIA_DELETE = Set.of("media:artifact:delete");
    private static final Set<String> MEDIA_ALL = Set.of(
            "media:artifact:create",
            "media:artifact:read",
            "media:artifact:delete",
            "media:artifact:update-consent",
            "media:artifact:process",
            "media:artifact:retry",
            "media:artifact:read-result"
        );

    public SecurityInterceptor(RoutePolicyEnforcer policyEnforcer) {
        this.policyEnforcer = Objects.requireNonNull(policyEnforcer, "policyEnforcer cannot be null");
    }

    /**
     * Intercepts and enforces policy on an HTTP request.
     */
    public Promise<HttpResponse> intercept(HttpRequest request, SecurityContext context) {
        String path = request.getPath();
        String method = request.getMethod().name();

        log.debug("[security-interceptor] Intercepting request: {} {}", method, path);

        EnforcementResult result = policyEnforcer.enforcePolicy(path, method, context);

        if (!result.allowed()) {
            log.warn("[security-interceptor] Request denied: {} {} - Reason: {}", method, path, result.reason());
            return Promise.of(buildDenialResponse(result));
        }

        log.debug("[security-interceptor] Request allowed: {} {}", method, path);
        return null; // Allow request to proceed (null means continue to handler)
    }

    /**
     * Builds a denial response based on the enforcement result.
     */
    private HttpResponse buildDenialResponse(EnforcementResult result) {
        int statusCode = getStatusCodeForViolation(result.violationType());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", result.reason());
        body.put("violationType", result.violationType());
        body.put("context", result.context());

        return HttpResponse.ofCode(statusCode)
                .withHeader(HttpHeaders.of("Content-Type"), "application/json")
                .withBody(serializeToJson(body))
                .build();
    }

    private int getStatusCodeForViolation(String violationType) {
        if (violationType == null) {
            return 403;
        }

        return switch (violationType) {
            case "AUTHENTICATION_REQUIRED", "STRICT_AUTHENTICATION_REQUIRED", "MFA_REQUIRED" -> 401;
            case "TENANT_ID_REQUIRED" -> 400;
            case "ROLE_NOT_FOUND", "PERMISSION_NOT_FOUND", "POLICY_ROLE_NOT_FOUND",
                 "POLICY_PERMISSION_NOT_FOUND", "INSUFFICIENT_CLEARANCE" -> 403;
            default -> 403;
        };
    }

    private byte[] serializeToJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                sb.append(serializeMapToJson((Map<?, ?>) value));
            } else if (value instanceof Collection) {
                sb.append(serializeCollectionToJson((Collection<?>) value));
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString().getBytes();
    }

    private String serializeMapToJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                sb.append(serializeMapToJson((Map<?, ?>) value));
            } else if (value instanceof Collection) {
                sb.append(serializeCollectionToJson((Collection<?>) value));
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String serializeCollectionToJson(Collection<?> collection) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (Object item : collection) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            if (item instanceof String) {
                sb.append("\"").append(item).append("\"");
            } else if (item instanceof Map) {
                sb.append(serializeMapToJson((Map<?, ?>) item));
            } else if (item instanceof Collection) {
                sb.append(serializeCollectionToJson((Collection<?>) item));
            } else {
                sb.append(item);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Extracts security context from HTTP request.
     * WS4-1: Use TenantContext instead of direct header extraction for tenant ID.
     */
    public SecurityContext extractSecurityContext(HttpRequest request) {
        // WS4-1: Use TenantContext instead of direct header extraction
        String tenantId = TenantContext.getCurrentTenantId();
        String userId = request.getHeader(io.activej.http.HttpHeaders.of("X-User-ID"));
        String rolesHeader = request.getHeader(io.activej.http.HttpHeaders.of("X-User-Roles"));
        String permissionsHeader = request.getHeader(io.activej.http.HttpHeaders.of("X-User-Permissions"));
        String ipAddress = request.getRemoteAddress() != null ? request.getRemoteAddress().toString() : "unknown";
        String userAgent = request.getHeader(io.activej.http.HttpHeaders.of("User-Agent"));

        Set<String> roles = parseHeaderSet(rolesHeader);
        Set<String> permissions = parseHeaderSet(permissionsHeader);

        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("mfaVerified", Boolean.parseBoolean(request.getHeader(io.activej.http.HttpHeaders.of("X-MFA-Verified"))));
        additionalContext.put("dataClearance", request.getHeader(io.activej.http.HttpHeaders.of("X-Data-Clearance")));

        return new SecurityContext(
                tenantId,
                userId,
                roles,
                permissions,
                ipAddress,
                userAgent,
                additionalContext
        );
    }

    /**
     * Builds a security context from the authenticated principal attached to the request.
     */
    public SecurityContext extractSecurityContext(HttpRequest request, Principal principal) {
        Set<String> roles = principal == null ? Set.of() : new LinkedHashSet<>(principal.getRoles());
        Set<String> permissions = principal == null ? Set.of() : derivePermissions(roles);
        String ipAddress = request.getRemoteAddress() != null ? request.getRemoteAddress().toString() : "unknown";
        String userAgent = request.getHeader(io.activej.http.HttpHeaders.of("User-Agent"));

        return new SecurityContext(
                principal != null ? principal.getTenantId() : null,
                principal != null ? principal.getName() : null,
                roles,
                permissions,
                ipAddress,
                userAgent,
                Map.of()
        );
    }

    private Set<String> derivePermissions(Set<String> roles) {
        Set<String> permissions = new LinkedHashSet<>();
        for (String role : roles) {
            String normalizedRole = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
            switch (normalizedRole) {
                case "ADMIN", "MEDIA-ADMIN", "MEDIA_ADMIN" -> permissions.addAll(MEDIA_ALL);
                case "EDITOR", "OPERATOR", "MEDIA-WRITER", "MEDIA_WRITER" -> {
                    permissions.addAll(MEDIA_CREATE);
                    permissions.addAll(MEDIA_READ);
                    permissions.addAll(MEDIA_READ_RESULT);
                    permissions.addAll(MEDIA_PROCESS);
                    permissions.addAll(MEDIA_UPDATE);
                }
                case "PROCESSOR" -> {
                    permissions.addAll(MEDIA_PROCESS);
                    permissions.addAll(MEDIA_READ_RESULT);
                }
                case "VIEWER", "MEDIA-READER", "MEDIA_READER" -> {
                    permissions.addAll(MEDIA_READ);
                    permissions.addAll(MEDIA_READ_RESULT);
                }
                default -> {
                }
            }
        }
        return Set.copyOf(permissions);
    }

    private Set<String> parseHeaderSet(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Set.of(header.split(","));
    }

    /**
     * Checks if audit logging is required for the request.
     */
    public boolean requiresAuditLogging(HttpRequest request) {
        return policyEnforcer.requiresAuditLogging(request.getPath(), request.getMethod().name());
    }

    /**
     * Checks if rate limiting is required for the request.
     */
    public boolean requiresRateLimiting(HttpRequest request) {
        return policyEnforcer.requiresRateLimiting(request.getPath(), request.getMethod().name());
    }
}
