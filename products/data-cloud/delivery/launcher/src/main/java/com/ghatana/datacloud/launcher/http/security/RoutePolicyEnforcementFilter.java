package com.ghatana.datacloud.launcher.http.security;

import com.ghatana.aep.security.AepAuthFilter;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpMethod;
import io.activej.http.MediaTypes;
import io.activej.http.ContentType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Route-based policy enforcement filter for Data Cloud HTTP server.
 *
 * <p><b>Purpose</b><br>
 * Enforces security policies per route based on the RouteSensitivityMatrix.
 * Validates authentication, authorization, policy checks, audit logging, and tenant isolation
 * requirements for each incoming request.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RoutePolicyEnforcementFilter filter = new RoutePolicyEnforcementFilter(nextServlet, policyEvaluator);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Enforce route-based security policies
 * @doc.layer product
 * @doc.pattern Filter
 */
public final class RoutePolicyEnforcementFilter implements AsyncServlet {

    private static final Logger log = LoggerFactory.getLogger(RoutePolicyEnforcementFilter.class);
    private static final HttpHeader CORRELATION_ID_HEADER = HttpHeaders.of("X-Correlation-ID");
    private static final HttpHeader TENANT_ID_HEADER = HttpHeaders.of("X-Tenant-ID");
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String TENANT_ID_MDC_KEY = "tenantId";

    private final AsyncServlet next;
    private final PolicyEvaluator policyEvaluator;

    public RoutePolicyEnforcementFilter(AsyncServlet next, PolicyEvaluator policyEvaluator) {
        this.next = next;
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        String path = request.getPath();
        String correlationId = resolveCorrelationId(request);
        String tenantId = resolveTenantId(request);

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        if (tenantId != null) {
            MDC.put(TENANT_ID_MDC_KEY, tenantId);
        }

        try {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = RouteSensitivityMatrix.getRouteSensitivity(path);

            // Check authentication
            if (sensitivity.requiresAuthentication()) {
                AepAuthFilter.JwtPayload jwtPayload = request.getAttachment(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT);
                if (jwtPayload == null) {
                    log.warn("Authentication required for path {} but no JWT payload found", path);
                    return Promise.of(forbiddenResponse("Authentication required", correlationId));
                }

                // Check roles
                Set<String> requiredRoles = sensitivity.getRequiredRoles();
                if (!requiredRoles.isEmpty() && !hasAnyRole(jwtPayload, requiredRoles)) {
                    log.warn("Insufficient roles for path {}: required {}, found {}", 
                            path, requiredRoles, jwtPayload.roles());
                    return Promise.of(forbiddenResponse("Insufficient permissions", correlationId));
                }

                // Check permissions
                Set<String> requiredPermissions = sensitivity.getRequiredPermissions();
                if (!requiredPermissions.isEmpty() && !hasAnyPermission(jwtPayload, requiredPermissions)) {
                    log.warn("Insufficient permissions for path {}: required {}, found {}", 
                            path, requiredPermissions, jwtPayload.permissions());
                    return Promise.of(forbiddenResponse("Insufficient permissions", correlationId));
                }

                // Enforce tenant isolation
                if (sensitivity.requiresTenantIsolation()) {
                    String jwtTenantId = jwtPayload.tenantId();
                    if (jwtTenantId == null || jwtTenantId.isBlank()) {
                        log.warn("Tenant isolation required for path {} but JWT has no tenantId", path);
                        return Promise.of(forbiddenResponse("Tenant ID required", correlationId));
                    }
                    if (tenantId != null && !tenantId.isBlank() && !tenantId.equals(jwtTenantId)) {
                        log.warn("Tenant ID mismatch for path {}: header={}, jwt={}", 
                                path, tenantId, jwtTenantId);
                        return Promise.of(forbiddenResponse("Tenant ID mismatch", correlationId));
                    }
                    // Use JWT tenantId if header not provided
                    if (tenantId == null || tenantId.isBlank()) {
                        tenantId = jwtTenantId;
                        MDC.put(TENANT_ID_MDC_KEY, tenantId);
                    }
                }
            }

            // Check policy requirements
            if (sensitivity.requiresPolicyCheck() && policyEvaluator != null) {
                try {
                    PolicyEvaluationResult result = policyEvaluator.evaluate(request, sensitivity);
                    if (!result.isAllowed()) {
                        log.warn("Policy check failed for path {}: {}", path, result.getReason());
                        return Promise.of(forbiddenResponse("Policy violation: " + result.getReason(), correlationId));
                    }
                } catch (Exception e) {
                    log.error("Policy evaluation error for path {}", path, e);
                    return Promise.of(forbiddenResponse("Policy evaluation error", correlationId));
                }
            }

            // Audit logging
            if (sensitivity.requiresAuditLogging()) {
                logAuditEvent(request, sensitivity, correlationId, tenantId);
            }

            return next.serve(request)
                    .map(response -> withCorrelationHeader(response, correlationId))
                    .whenComplete(($, e) -> {
                        MDC.remove(CORRELATION_ID_MDC_KEY);
                        MDC.remove(TENANT_ID_MDC_KEY);
                    });

        } catch (Exception e) {
            log.error("Error in policy enforcement filter for path {}", path, e);
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(TENANT_ID_MDC_KEY);
            return Promise.of(serverErrorResponse("Internal server error", correlationId));
        }
    }

    private String resolveCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private String resolveTenantId(HttpRequest request) {
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        return null;
    }

    private boolean hasAnyRole(AepAuthFilter.JwtPayload jwtPayload, Set<String> requiredRoles) {
        if (jwtPayload.roles() == null || jwtPayload.roles().isEmpty()) {
            return false;
        }
        return jwtPayload.roles().stream()
                .anyMatch(requiredRoles::contains);
    }

    private boolean hasAnyPermission(AepAuthFilter.JwtPayload jwtPayload, Set<String> requiredPermissions) {
        if (jwtPayload.permissions() == null || jwtPayload.permissions().isEmpty()) {
            return false;
        }
        // Check for wildcard permission
        if (jwtPayload.permissions().contains("*")) {
            return true;
        }
        return jwtPayload.permissions().stream()
                .anyMatch(requiredPermissions::contains);
    }

    private void logAuditEvent(HttpRequest request, RouteSensitivityMatrix.RouteSensitivity sensitivity,
                               String correlationId, String tenantId) {
        log.info("AUDIT: method={}, path={}, sensitivity={}, correlationId={}, tenantId={}",
                request.getMethod(), request.getPath(), sensitivity.getSensitivity(),
                correlationId, tenantId);
    }

    private HttpResponse withCorrelationHeader(HttpResponse response, String correlationId) {
        HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode());
        for (var entry : response.getHeaders()) {
            builder.withHeader(entry.getKey(), entry.getValue());
        }
        builder.withHeader(CORRELATION_ID_HEADER, HttpHeaderValue.of(correlationId));
        return builder.build();
    }

    private HttpResponse forbiddenResponse(String message, String correlationId) {
        String body = String.format(
                "{\"error\":\"Forbidden\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message.replace("\"", "\\\""), Instant.now()
        );
        return HttpResponse.ofCode(403)
                .withHeader(HttpHeaders.CONTENT_TYPE,
                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(CORRELATION_ID_HEADER, HttpHeaderValue.of(correlationId))
                .withBody(body.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private HttpResponse serverErrorResponse(String message, String correlationId) {
        String body = String.format(
                "{\"error\":\"Internal Server Error\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message.replace("\"", "\\\""), Instant.now()
        );
        return HttpResponse.ofCode(500)
                .withHeader(HttpHeaders.CONTENT_TYPE,
                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(CORRELATION_ID_HEADER, HttpHeaderValue.of(correlationId))
                .withBody(body.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Policy evaluation result.
     */
    public static class PolicyEvaluationResult {
        private final boolean allowed;
        private final String reason;

        public PolicyEvaluationResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getReason() {
            return reason;
        }

        public static PolicyEvaluationResult allowed() {
            return new PolicyEvaluationResult(true, null);
        }

        public static PolicyEvaluationResult denied(String reason) {
            return new PolicyEvaluationResult(false, reason);
        }
    }

    /**
     * Policy evaluator interface for route-specific policy checks.
     */
    public interface PolicyEvaluator {
        PolicyEvaluationResult evaluate(HttpRequest request, RouteSensitivityMatrix.RouteSensitivity sensitivity);
    }
}
