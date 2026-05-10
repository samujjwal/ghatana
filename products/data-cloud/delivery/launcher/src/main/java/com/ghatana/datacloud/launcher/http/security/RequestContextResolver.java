package com.ghatana.datacloud.launcher.http.security;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Resolves canonical {@link RequestContext} from HTTP requests.
 *
 * <p>This resolver enforces the security boundary between HTTP transport and domain logic:
 * <ul>
 *   <li>In production/staging: tenant MUST come from authenticated identity (JWT/API key)</li>
 *   <li>X-Tenant-Id header and tenantId query parameter are REJECTED in production</li>
 *   <li>Support access requires explicit delegation tokens with audit trail</li>
 *   <li>All tenant identifiers are validated against canonical format</li>
 * </ul>
 *
 * <p>The resolver produces an immutable RequestContext that is the single source of truth
 * for tenant, scope, and authorization for the entire request lifecycle.
 *
 * @doc.type class
 * @doc.purpose Resolve canonical authenticated request context from HTTP requests
 * @doc.layer product
 * @doc.pattern Factory, Security Boundary
 */
public final class RequestContextResolver {

    private static final Logger log = LoggerFactory.getLogger(RequestContextResolver.class);

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{1,62}[A-Za-z0-9]$");
    private static final Set<String> PRODUCTION_PROFILES = Set.of("production", "staging", "sovereign");
    private static final Set<String> SAFE_FALLBACK_PROFILES = Set.of("local", "test", "development", "preview");

    private final String deploymentProfile;
    private final boolean strictTenantResolution;
    private final boolean rejectHeaderTenant;
    private final boolean rejectQueryTenant;

    /**
     * Creates a resolver for the specified deployment profile.
     *
     * @param deploymentProfile the deployment profile (local, test, preview, staging, production, sovereign)
     * @param strictTenantResolution if true, rejects unauthenticated tenant resolution
     */
    public RequestContextResolver(String deploymentProfile, boolean strictTenantResolution) {
        this.deploymentProfile = deploymentProfile != null ? deploymentProfile.toLowerCase() : "local";
        this.strictTenantResolution = strictTenantResolution;
        this.rejectHeaderTenant = PRODUCTION_PROFILES.contains(this.deploymentProfile);
        this.rejectQueryTenant = PRODUCTION_PROFILES.contains(this.deploymentProfile);
    }

    /**
     * Resolves the canonical RequestContext from the HTTP request.
     *
     * <p>In production profiles, this method:
     * <ul>
     *   <li>Requires authentication (Principal must be present)</li>
     *   <li>Extracts tenant from Principal only</li>
     *   <li>Rejects X-Tenant-Id header with 403</li>
     *   <li>Rejects tenantId query parameter with 403</li>
     * </ul>
     *
     * <p>In local/development profiles, allows header/query fallback with warnings.
     *
     * @param request the HTTP request
     * @return ResolutionResult containing either the context or an error
     */
    public ResolutionResult resolve(HttpRequest request) {
        String correlationId = resolveCorrelationId(request);
        String traceId = resolveTraceId(request, correlationId);

        // Check for spoofed tenant sources in production
        if (rejectHeaderTenant) {
            Optional<String> headerTenant = TenantExtractor.fromHttp(request);
            if (headerTenant.isPresent()) {
                log.warn("[SECURITY] Rejected request with X-Tenant-Id header in production: path={}, correlationId={}",
                    request.getPath(), correlationId);
                return ResolutionResult.error(403, "X-Tenant-Id header is not allowed in production. " +
                    "Tenant must be provided via authenticated JWT or API key.");
            }
        }

        if (rejectQueryTenant) {
            String queryTenant = request.getQueryParameter("tenantId");
            if (queryTenant != null && !queryTenant.isBlank()) {
                log.warn("[SECURITY] Rejected request with tenantId query parameter in production: path={}, correlationId={}",
                    request.getPath(), correlationId);
                return ResolutionResult.error(403, "tenantId query parameter is not allowed in production. " +
                    "Tenant must be provided via authenticated JWT or API key.");
            }
        }

        // Extract authenticated principal
        Principal principal = request.getAttachment(Principal.class);

        // Resolve tenant from authenticated identity
        String tenantId = resolveTenantId(request, principal);

        if (tenantId == null) {
            if (strictTenantResolution) {
                log.warn("[SECURITY] Rejected unauthenticated request in strict mode: path={}, correlationId={}",
                    request.getPath(), correlationId);
                return ResolutionResult.error(401, "Authentication required. " +
                    "Provide valid JWT or API key with tenant claim.");
            }

            // Safe fallback only in non-production profiles
            if (SAFE_FALLBACK_PROFILES.contains(deploymentProfile)) {
                tenantId = "default";
                log.debug("[SECURITY] Using fallback tenant 'default' in {} mode: path={}",
                    deploymentProfile, request.getPath());
            } else {
                return ResolutionResult.error(401, "Unable to resolve tenant from request. " +
                    "Ensure authentication is properly configured.");
            }
        }

        // Validate tenant format
        if (!isValidTenantId(tenantId)) {
            log.warn("[SECURITY] Rejected request with invalid tenant ID format: tenantId={}, correlationId={}",
                maskTenantId(tenantId), correlationId);
            return ResolutionResult.error(400, "Invalid tenant ID format. " +
                "Tenant ID must be 3-64 alphanumeric characters with hyphens.");
        }

        // Extract workspace and project scope
        String workspaceId = extractWorkspaceId(request);
        String projectId = extractProjectId(request);

        // Build roles from principal or default
        Set<String> roles = principal != null && principal.getRoles() != null
            ? Set.copyOf(principal.getRoles())
            : Set.of();

        // Check for support access delegation
        boolean supportAccess = false;
        String supportReason = null;
        String supportHeader = request.getHeader(HttpHeaders.of("X-Support-Access"));
        if (supportHeader != null && !supportHeader.isBlank()) {
            // Support access requires specific role and audit trail
            if (roles.contains("SUPPORT") || roles.contains("PLATFORM_ADMIN")) {
                supportAccess = true;
                supportReason = supportHeader;
                log.info("[SECURITY] Support access granted: tenantId={}, reason={}, correlationId={}",
                    maskTenantId(tenantId), supportReason, correlationId);
            } else {
                log.warn("[SECURITY] Rejected support access without SUPPORT role: tenantId={}, correlationId={}",
                    maskTenantId(tenantId), correlationId);
                return ResolutionResult.error(403, "Support access requires SUPPORT or PLATFORM_ADMIN role.");
            }
        }

        RequestContext context = RequestContext.builder()
            .withTenantId(tenantId)
            .withWorkspace(workspaceId)
            .withProject(projectId)
            .withPrincipal(principal)
            .withRoles(roles)
            .withCorrelationId(correlationId)
            .withTraceId(traceId)
            .withRequestPath(request.getPath())
            .withRequestMethod(request.getMethod().name())
            .withSupportAccess(supportAccess, supportReason)
            .build();

        return ResolutionResult.success(context);
    }

    /**
     * Resolves tenant ID from authenticated sources only.
     * Priority: Principal tenant claim > (header in non-prod only) > (query in non-prod only)
     */
    private String resolveTenantId(HttpRequest request, Principal principal) {
        // Primary: Authenticated principal
        if (principal != null && principal.getTenantId() != null && !principal.getTenantId().isBlank()) {
            return sanitizeTenantId(principal.getTenantId());
        }

        // In non-production profiles, allow header/query as fallback (with warnings)
        if (!rejectHeaderTenant) {
            Optional<String> headerTenant = TenantExtractor.fromHttp(request);
            if (headerTenant.isPresent()) {
                log.debug("[SECURITY] Resolved tenant from X-Tenant-Id header in {} mode", deploymentProfile);
                return sanitizeTenantId(headerTenant.get());
            }
        }

        if (!rejectQueryTenant) {
            String queryTenant = request.getQueryParameter("tenantId");
            if (queryTenant != null && !queryTenant.isBlank()) {
                log.debug("[SECURITY] Resolved tenant from query parameter in {} mode", deploymentProfile);
                return sanitizeTenantId(queryTenant);
            }
        }

        return null;
    }

    private String resolveCorrelationId(HttpRequest request) {
        // Check for existing correlation ID in headers
        String fromRequestId = request.getHeader(HttpHeaders.of("X-Request-Id"));
        if (fromRequestId != null && !fromRequestId.isBlank()) {
            return fromRequestId.trim();
        }

        String fromCorrelationId = request.getHeader(HttpHeaders.of("X-Correlation-Id"));
        if (fromCorrelationId != null && !fromCorrelationId.isBlank()) {
            return fromCorrelationId.trim();
        }

        // Generate new correlation ID
        return UUID.randomUUID().toString();
    }

    private String resolveTraceId(HttpRequest request, String correlationId) {
        String traceparent = request.getHeader(HttpHeaders.of("traceparent"));
        if (traceparent != null && !traceparent.isBlank()) {
            // Extract trace ID from W3C traceparent format
            String[] parts = traceparent.split("-");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return correlationId;
    }

    private String extractWorkspaceId(HttpRequest request) {
        String raw = request.getHeader(HttpHeaders.of("X-Workspace-Id"));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String candidate = raw.trim();
        if (candidate.length() < 1 || candidate.length() > 64) {
            return null;
        }
        return candidate;
    }

    private String extractProjectId(HttpRequest request) {
        String raw = request.getHeader(HttpHeaders.of("X-Project-Id"));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String candidate = raw.trim();
        if (candidate.length() < 1 || candidate.length() > 64) {
            return null;
        }
        return candidate;
    }

    private String sanitizeTenantId(String rawTenantId) {
        if (rawTenantId == null) {
            return null;
        }
        String candidate = rawTenantId.trim();
        if (candidate.isBlank()) {
            return null;
        }
        return candidate;
    }

    private boolean isValidTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        if (tenantId.length() < 3 || tenantId.length() > 64) {
            return false;
        }
        return TENANT_ID_PATTERN.matcher(tenantId).matches();
    }

    private String maskTenantId(String tenantId) {
        if (tenantId == null || tenantId.length() < 4) {
            return "***";
        }
        return tenantId.substring(0, 2) + "***" + tenantId.substring(tenantId.length() - 2);
    }

    /**
     * Result of request context resolution - either success with context or error.
     */
    public static final class ResolutionResult {
        private final RequestContext context;
        private final int errorCode;
        private final String errorMessage;
        private final boolean success;

        private ResolutionResult(RequestContext context, int errorCode, String errorMessage, boolean success) {
            this.context = context;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.success = success;
        }

        static ResolutionResult success(RequestContext context) {
            return new ResolutionResult(context, 0, null, true);
        }

        static ResolutionResult error(int code, String message) {
            return new ResolutionResult(null, code, message, false);
        }

        public boolean isSuccess() {
            return success;
        }

        public Optional<RequestContext> context() {
            return Optional.ofNullable(context);
        }

        public int errorCode() {
            return errorCode;
        }

        public String errorMessage() {
            return errorMessage;
        }
    }
}
