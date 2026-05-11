package com.ghatana.datacloud.launcher.http;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import com.ghatana.platform.http.security.filter.TenantIsolationHttpFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.security.SecurityUtils;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.datacloud.launcher.support.RequestContext;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Composable security filter chain for Data-Cloud HTTP endpoints.
 *
 * <p>Orchestrates the canonical policy enforcement order:
 * <pre>
 *   CORS / rate-limit / payload-size → (this filter) →
 *     1. Pass health/metrics probes without authentication ({@link EndpointSensitivity#PUBLIC}).
 *     2. API key authentication first, JWT bearer or auth-cookie authentication second.
 *     3. Tenant isolation setup via {@link TenantIsolationHttpFilter}.
 *     4. Policy engine check for {@link EndpointSensitivity#CRITICAL} routes.
 *     5. Async audit emission for SENSITIVE and CRITICAL routes.
 *     6. Delegate to the real route handler.
 * </pre>
 *
 * <h2>Security Properties</h2>
 * <ul>
 *   <li>Non-bypassable: every request goes through ALL active checks before
 *       reaching a business handler.</li>
 *   <li>DC-P1.16: Fail-closed: any unexpected exception during policy evaluation returns
 *       HTTP 403, never silently passes. AI suggestions remain advisory only and should not
 *       be treated as guaranteed outcomes when the service is degraded.</li>
 *   <li>Audit integrity: audit records are fire-and-forget but errors are
 *       logged; they never block the response path.</li>
 *   <li>SSRF-safe: API keys are validated server-side; no user-supplied URL
 *       is followed during authentication.</li>
 * </ul>
 *
 * <h2>Configuration Modes</h2>
 * <ul>
 *   <li><b>Enforcing</b> (default): authentication and policy checks block requests.</li>
 *   <li><b>Audit-only</b>: checks run but failures are logged rather than blocked.
 *       Useful for canary deployments.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DataCloudSecurityFilter security = DataCloudSecurityFilter.builder()
 *     .apiKeyResolver(apiKeyResolver)
 *     .policyEngine(policyEngine)
 *     .auditService(auditService)
 *     .blockingExecutor(executor)
 *     .build();
 *
 * AsyncServlet secured = security.apply(router);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Canonical security/policy/audit middleware for Data-Cloud HTTP routes
 * @doc.layer product
 * @doc.pattern Middleware, SecurityFilter
 */
public final class DataCloudSecurityFilter {

    private static final Logger log = LoggerFactory.getLogger(DataCloudSecurityFilter.class);

    /** Header name that carries the correlation ID for distributed tracing. */
    static final String HEADER_REQUEST_ID = "X-Request-ID";
    static final String HEADER_API_KEY = "X-API-Key";
    static final String HEADER_AUTHORIZATION = "Authorization";
    static final String HEADER_BREAK_GLASS_REASON = "X-Break-Glass-Reason";
    static final String AUTH_TOKEN_COOKIE = "auth_token";
    static final String DEFAULT_TENANT_CLAIM = "tenant_id";

    private final ApiKeyResolver apiKeyResolver;
    private final JwtTokenProvider jwtProvider;
    private final String jwtTenantClaim;
    private final PolicyEngine policyEngine;
    private final AuditService auditService;
    private final boolean enforcing;
    private final boolean strictTenantResolution;
    private final Set<String> breakGlassTenants;
    private final String deploymentProfile;

    private DataCloudSecurityFilter(Builder b) {
        if (b.apiKeyResolver == null && b.jwtProvider == null) {
            throw new NullPointerException("Either apiKeyResolver or jwtProvider must be configured");
        }
        this.apiKeyResolver         = b.apiKeyResolver;
        this.jwtProvider            = b.jwtProvider;
        this.jwtTenantClaim         = b.jwtTenantClaim != null && !b.jwtTenantClaim.isBlank()
                ? b.jwtTenantClaim
                : DEFAULT_TENANT_CLAIM;
        this.policyEngine           = b.policyEngine;        // nullable — CRITICAL routes fail-closed when null and enforcing=true
        this.auditService           = b.auditService;        // nullable — audit skipped when null
        this.enforcing              = b.enforcing;
        this.strictTenantResolution = b.strictTenantResolution;
        this.breakGlassTenants  = b.breakGlassTenants != null
            ? Set.copyOf(b.breakGlassTenants) : Set.of();
        this.deploymentProfile      = b.deploymentProfile != null ? b.deploymentProfile : "local";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public factory
    // ─────────────────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wraps the given delegate servlet with the full security/policy/audit chain.
     *
     * @param delegate the real route handler
     * @return secured servlet
     */
    public AsyncServlet apply(AsyncServlet delegate) {
        // Inner-most filter: tenant isolation sets TenantContext.
        AsyncServlet tenantWrapped = TenantIsolationHttpFilter.wrap(delegate);

        // Outer-most: our policy + audit logic that decides which path to take.
        return request -> {
            String path   = request.getPath();
            String method = request.getMethod().name();

            EndpointSensitivity sensitivity = EndpointSensitivity.classify(method, path);

            // (1) Public probes — skip auth and policy entirely.
            if (sensitivity == EndpointSensitivity.PUBLIC) {
                return delegate.serve(request);
            }

            // (1.5) Audit service check for SENSITIVE/CRITICAL routes in non-local profiles
            if ((sensitivity == EndpointSensitivity.SENSITIVE || sensitivity == EndpointSensitivity.CRITICAL)
                && auditService == null
                && isAuditRequiredForProfile(deploymentProfile)) {
                String requestId = ensureRequestId(request, null);
                log.error("[DC-SEC] Audit service is required for {} route {} {} requestId={} in profile '{}' — rejecting request",
                          sensitivity, method, path, requestId, deploymentProfile);
                return Promise.of(auditServiceRequiredResponse(requestId));
            }

            // (2–3) Authenticate and establish TenantContext before the tenant isolation filter.
            return authenticate(request, tenantWrapped, sensitivity)
                .then(response -> {
                    Principal authenticatedPrincipal = request.getAttachment(Principal.class);
                    String tenantId = extractTenantId(request, authenticatedPrincipal, strictTenantResolution);
                    String principalName = resolvePrincipalName(authenticatedPrincipal);
                    int code = response.getCode();
                    // In audit-only mode (enforcing=false), authentication/authorization failures
                    // are logged and audited but do not block the request — request passes through.
                    if (!enforcing && (code == 401 || code == 403)) {
                        String requestId = ensureRequestId(request, response);
                        log.warn("[DC-SEC][AUDIT-ONLY] Auth failed (status={}) but enforcing=false — request passes through path={} requestId={}",
                                code, path, requestId);
                        emitAudit(request, path, method, sensitivity, false, code, requestId, tenantId, principalName);
                        return serveDelegate(tenantWrapped, request);
                    }
                    return handlePostAuth(
                            request,
                            response,
                            path,
                            method,
                            sensitivity,
                            tenantId,
                            principalName);
                });
        };
    }

    private Promise<HttpResponse> authenticate(io.activej.http.HttpRequest request,
                                               AsyncServlet tenantWrapped,
                                               EndpointSensitivity sensitivity) {
        if (apiKeyResolver != null) {
            String apiKey = request.getHeader(HttpHeaders.of(HEADER_API_KEY));
            if (apiKey != null && !apiKey.isBlank()) {
                return authenticateApiKey(request, tenantWrapped, apiKey, sensitivity);
            }
        }

        if (jwtProvider != null) {
            String token = extractJwtToken(request);
            if (token != null && !token.isBlank()) {
                return authenticateJwt(request, tenantWrapped, token, sensitivity);
            }
        }

        return Promise.of(unauthorized("Missing authentication credentials"));
    }

    private String extractJwtToken(io.activej.http.HttpRequest request) {
        String headerToken = SecurityUtils.extractBearerToken(request.getHeader(HttpHeaders.of(HEADER_AUTHORIZATION)));
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }

        return extractCookieValue(request.getHeader(HttpHeaders.COOKIE), AUTH_TOKEN_COOKIE);
    }

    private String extractCookieValue(String cookieHeader, String cookieName) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }

        for (String cookie : cookieHeader.split(";")) {
            String[] nameValue = cookie.trim().split("=", 2);
            if (nameValue.length == 2 && cookieName.equals(nameValue[0])) {
                String value = nameValue[1].trim();
                return value.isEmpty() ? null : value;
            }
        }

        return null;
    }

    private Promise<HttpResponse> authenticateApiKey(io.activej.http.HttpRequest request,
                                                     AsyncServlet tenantWrapped,
                                                     String apiKey,
                                                     EndpointSensitivity sensitivity) {
        var principalOpt = apiKeyResolver.resolve(apiKey);
        if (principalOpt.isEmpty()) {
            log.warn("Unauthorized request: invalid API key");
            return Promise.of(unauthorized("Missing or invalid API key"));
        }

        return serveAsPrincipal(request, tenantWrapped, principalOpt.get(), sensitivity);
    }

    private Promise<HttpResponse> authenticateJwt(io.activej.http.HttpRequest request,
                                                  AsyncServlet tenantWrapped,
                                                  String token,
                                                  EndpointSensitivity sensitivity) {
        try {
            if (!jwtProvider.validateToken(token)) {
                log.warn("Unauthorized request: invalid JWT bearer token");
                return Promise.of(unauthorized("Missing, expired, or invalid JWT bearer token"));
            }

            String userId = jwtProvider.getUserIdFromToken(token).orElse(null);
            String tenantId = jwtProvider.extractClaims(token)
                    .map(claims -> claims.get(jwtTenantClaim))
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .orElse(null);

            if (userId == null || tenantId == null) {
                log.warn("Unauthorized request: JWT missing required identity claims userId={} tenantClaim={}",
                        userId != null,
                        jwtTenantClaim);
                return Promise.of(unauthorized("JWT missing required identity claims"));
            }

            String requestedTenantId = requestedTenantId(request);
            if (requestedTenantId != null && !requestedTenantId.equals(tenantId)) {
                log.warn("Forbidden request: JWT tenant claim does not match requested tenant claimTenant={} requestedTenant={}",
                        tenantId,
                        requestedTenantId);
                return Promise.of(forbiddenTenantMismatch());
            }

            Principal principal = new Principal(userId, jwtProvider.getRolesFromToken(token), tenantId);
            return serveAsPrincipal(request, tenantWrapped, principal, sensitivity);
        } catch (RuntimeException exception) {
            log.warn("Unauthorized request: JWT authentication failed: {}", exception.getMessage());
            return Promise.of(unauthorized("Missing, expired, or invalid JWT bearer token"));
        }
    }

    private Promise<HttpResponse> serveAsPrincipal(io.activej.http.HttpRequest request,
                                                   AsyncServlet tenantWrapped,
                                                   Principal principal,
                                                   EndpointSensitivity sensitivity) {
        request.attach(Principal.class, principal);
        String method = request.getMethod().name();
        String path = request.getPath();
        String requestId = ensureRequestId(request, null);

        // DC-SEC-TENANT: Global tenant presence check — enforced before any handler.
        // Principals without a tenant are rejected at the filter boundary so handlers
        // cannot accidentally serve a request with a missing tenant context.
        String principalTenantId = principal.getTenantId();
        if ((principalTenantId == null || principalTenantId.isBlank()) && enforcing) {
            log.warn("[DC-SEC] Rejecting request: authenticated principal has no tenant identifier principal={} method={} path={} requestId={}",
                    principal.getName(), method, path, requestId);
            return Promise.of(missingTenantResponse(requestId));
        }

        String requestedTenantId = requestedTenantId(request);
        if (principalTenantId != null && !principalTenantId.isBlank()
                && requestedTenantId != null
                && !principalTenantId.equals(requestedTenantId)) {
            log.warn("[DC-SEC] Rejecting request: requested tenant does not match principal tenant principal={} method={} path={} principalTenant={} requestedTenant={} requestId={}",
                principal.getName(), method, path, principalTenantId, requestedTenantId, requestId);
            if (enforcing) {
                return Promise.of(forbiddenTenantMismatch());
            }
        }

        AccessLevel requiredAccess = requiredAccess(method, path, sensitivity);
        if (!hasRequiredAccess(principal, requiredAccess)) {
            log.warn("Forbidden request: principal={} method={} path={} requiredAccess={}",
                    principal.getName(), method, path, requiredAccess.name());
            if (enforcing) {
                return Promise.of(forbiddenResponse(requestId, requiredAccess));
            }
        }

        TenantContext.Scope scope = TenantContext.scope(principal);
        RequestContext principalScope = RequestContext.bindPrincipal(principal.getName());
        try {
            return evaluatePolicyBeforeServing(request, tenantWrapped, principal, sensitivity, requestId)
                .whenComplete((response, error) -> {
                    principalScope.close();
                    scope.close();
                });
        } catch (Exception exception) {
            principalScope.close();
            scope.close();
            return Promise.ofException(exception);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post-auth: policy check + audit emission
    // ─────────────────────────────────────────────────────────────────────────

    private Promise<HttpResponse> handlePostAuth(
            io.activej.http.HttpRequest request,
            HttpResponse response,
            String path,
            String method,
            EndpointSensitivity sensitivity,
            String tenantId,
            String principalName) {

        int statusCode = response.getCode();
        String requestId = ensureRequestId(request, response);

        // Auth failed — emit audit and return immediately.
        if (statusCode == 401 || statusCode == 403) {
            emitAudit(request, path, method, sensitivity, false, statusCode, requestId, tenantId, principalName);
            return Promise.of(response);
        }

        if (sensitivity == EndpointSensitivity.SENSITIVE
            || sensitivity == EndpointSensitivity.CRITICAL
            || statusCode == 401
            || statusCode == 403) {
            emitAudit(
                    request,
                    path,
                    method,
                    sensitivity,
                    statusCode < 400,
                    statusCode,
                    requestId,
                    tenantId,
                    principalName);
        }

        return Promise.of(response);
    }

    private Promise<HttpResponse> evaluatePolicyBeforeServing(
            io.activej.http.HttpRequest request,
            AsyncServlet tenantWrapped,
            Principal principal,
            EndpointSensitivity sensitivity,
            String requestId) {
        String path = request.getPath();
        String method = request.getMethod().name();
        String tenantId = principal.getTenantId();

        if (sensitivity != EndpointSensitivity.CRITICAL) {
            return serveDelegate(tenantWrapped, request);
        }

        if (breakGlassTenants.contains(tenantId)) {
            if (!isBreakGlassAllowed(request, principal, tenantId, requestId)) {
                return Promise.of(policyDenyResponse(requestId));
            }
            return serveDelegate(tenantWrapped, request);
        }

        if (policyEngine == null) {
            log.error("[DC-SEC] Policy engine unavailable for CRITICAL route {} {} tenant={} requestId={} — denying request",
                      method, path, tenantId, requestId);
            if (enforcing) {
                return Promise.of(policyDenyResponse(requestId));
            }
            log.warn("[DC-SEC][AUDIT-ONLY] Allowing CRITICAL route despite missing policy engine (enforcing=false)");
            return serveDelegate(tenantWrapped, request);
        }

        Map<String, Object> ctx = Map.of(
            "tenantId",    tenantId,
            "path",        path,
            "method",      method,
            "sensitivity", sensitivity.name(),
            "roles",       principal.getRoles(),
            "requestId",   requestId,
            "timestamp",   Instant.now().toString()
        );

        return policyEngine.evaluate("datacloud.sensitive-route-access", ctx)
            .then(allowed -> {
                if (!allowed) {
                    if (enforcing) {
                        log.warn("[DC-SEC] Policy denied {} {} for tenant={} requestId={}",
                                 method, path, tenantId, requestId);
                        return Promise.of(policyDenyResponse(requestId));
                    } else {
                        log.warn("[DC-SEC][AUDIT-ONLY] Policy would deny {} {} for tenant={} requestId={}",
                                 method, path, tenantId, requestId);
                    }
                }
                return serveDelegate(tenantWrapped, request);
            }, e -> {
                log.error("[DC-SEC] Policy evaluation error for {} {} requestId={}: {}",
                          method, path, requestId, e.getMessage(), e);
                if (enforcing) {
                    return Promise.of(policyDenyResponse(requestId));
                }
                return serveDelegate(tenantWrapped, request);
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audit emission — fire-and-forget, never blocks the response path
    // ─────────────────────────────────────────────────────────────────────────

    private void emitAudit(
            io.activej.http.HttpRequest request,   // NOSONAR – kept as-is to avoid extra import
            String path,
            String method,
            EndpointSensitivity sensitivity,
            boolean success,
            int statusCode,
            String requestId,
            String tenantId,
            String principalName) {

        if (auditService == null) {
            if (sensitivity == EndpointSensitivity.CRITICAL || sensitivity == EndpointSensitivity.SENSITIVE) {
                if (isAuditRequiredForProfile(deploymentProfile)) {
                    log.error("[DC-SEC] Audit service is required for {} route {} {} tenant={} requestId={} in profile '{}' — rejecting request",
                              sensitivity, method, path, tenantId, requestId, deploymentProfile);
                    // For SENSITIVE/CRITICAL routes in non-local profiles, audit is mandatory
                    // This check happens during audit emission, which means the request has already
                    // been processed. In production, this should be caught at startup validation,
                    // but we add this runtime check as a defense-in-depth measure.
                } else {
                    log.error("[DC-SEC] Audit service unavailable for {} route {} {} tenant={} requestId={} — audit event dropped",
                              sensitivity, method, path, tenantId, requestId);
                }
            }
            return;
        }

        AuditEvent event = AuditEvent.builder()
            .tenantId(tenantId)
            .eventType(resolveAuditEventType(path, sensitivity, statusCode))
            .principal(principalName)
            .resourceType("HTTP_ENDPOINT")
            .resourceId(method + " " + path)
            .success(success)
            .detail("requestId",   requestId)
            .detail("sensitivity", sensitivity.name())
            .detail("statusCode",  statusCode)
            .detail("remoteIp",    remoteIp(request))
            .build();

        // Fire-and-forget — log errors but never propagate them to the caller.
        // AuditService.record() returns an ActiveJ Promise; we consume it safely.
        auditService.record(event).whenException(e ->
            log.warn("[DC-SEC] Audit emission failed for requestId={}: {}", requestId, e.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String ensureRequestId(io.activej.http.HttpRequest request, HttpResponse response) {
        RequestIdAttachment attachedRequestId = request.getAttachment(RequestIdAttachment.class);
        if (attachedRequestId != null) {
            return attachedRequestId.value();
        }
        String existing = request.getHeader(HttpHeaders.of(HEADER_REQUEST_ID));
        String requestId = (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
        request.attach(RequestIdAttachment.class, new RequestIdAttachment(requestId));
        return requestId;
    }

    /**
     * Default tenant identifier used when strict tenant resolution is disabled
     * and no explicit tenant is provided in the request.
     */
    public static final String DEFAULT_FALLBACK_TENANT = "default";

    private static String extractTenantId(
            io.activej.http.HttpRequest request,
            Principal authenticatedPrincipal,
            boolean strictTenantResolution) {
        if (authenticatedPrincipal != null && authenticatedPrincipal.getTenantId() != null) {
            return authenticatedPrincipal.getTenantId();
        }

        String tenantHeader = TenantExtractor.fromHttp(request).orElse(null);
        if (tenantHeader != null) {
            return tenantHeader;
        }

        String contextTenant = TenantContext.getCurrentTenantId();
        if (contextTenant != null) {
            return contextTenant;
        }

        if (strictTenantResolution) {
            return null;
        }

        // DC-AUD-014: Non-strict mode falls back to default tenant
        return DEFAULT_FALLBACK_TENANT;
    }

    private static String resolvePrincipalName(Principal authenticatedPrincipal) {
        if (authenticatedPrincipal != null) {
            return authenticatedPrincipal.getName();
        }

        return TenantContext.current()
            .map(Principal::getName)
            .orElse("anonymous");
    }

    private static String remoteIp(io.activej.http.HttpRequest request) {
        String forwarded = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        try {
            var addr = request.getRemoteAddress();
            return addr != null ? addr.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String requestedTenantId(io.activej.http.HttpRequest request) {
        String tenantHeader = TenantExtractor.fromHttp(request).orElse(null);
        if (tenantHeader != null) {
            return tenantHeader;
        }

        String tenantQuery = request.getQueryParameter("tenantId");
        if (tenantQuery != null && !tenantQuery.isBlank()) {
            return tenantQuery.trim();
        }

        return null;
    }

    private Promise<HttpResponse> serveDelegate(AsyncServlet delegate, io.activej.http.HttpRequest request) {
        try {
            return delegate.serve(request);
        } catch (Exception exception) {
            return Promise.ofException(exception);
        }
    }

    private static HttpResponse policyDenyResponse(String requestId) {
        String body = "{\"error\":{\"code\":\"POLICY_DENY\","
            + "\"message\":\"Request blocked by governance policy\","
            + "\"requestId\":\"" + requestId + "\"}}";
        return HttpResponse.ofCode(403)
            .withHeader(HttpHeaders.CONTENT_TYPE, io.activej.http.HttpHeaderValue.of("application/json"))
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
    }

    private static HttpResponse missingTenantResponse(String requestId) {
        String body = "{\"error\":{\"code\":\"TENANT_REQUIRED\","
            + "\"message\":\"X-Tenant-Id header is required\","
            + "\"requestId\":\"" + requestId + "\"}}";
        return HttpResponse.ofCode(400)
            .withHeader(HttpHeaders.CONTENT_TYPE, io.activej.http.HttpHeaderValue.of("application/json"))
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
    }

    private static HttpResponse forbiddenResponse(String requestId, AccessLevel requiredAccess) {
        String body = "{\"error\":{\"code\":\"FORBIDDEN\"," 
            + "\"message\":\"Request requires role " + requiredAccess.name() + " or higher\"," 
            + "\"requestId\":\"" + requestId + "\"}}";
        return HttpResponse.ofCode(403)
            .withHeader(HttpHeaders.CONTENT_TYPE, io.activej.http.HttpHeaderValue.of("application/json"))
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
    }

    private static HttpResponse forbiddenTenantMismatch() {
        String body = "{\"error\":{\"code\":\"FORBIDDEN\","
            + "\"message\":\"Requested tenant does not match authenticated tenant\"}}";
        return HttpResponse.ofCode(403)
            .withHeader(HttpHeaders.CONTENT_TYPE, io.activej.http.HttpHeaderValue.of("application/json"))
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
    }

    private static String resolveAuditEventType(String path, EndpointSensitivity sensitivity, int statusCode) {
        if (statusCode == 401) {
            return "AUTH_FAILURE";
        }
        if (statusCode == 403) {
            return path.startsWith("/api/v1/governance/") ? "POLICY_DENY" : "AUTHZ_FAILURE";
        }
        if (sensitivity == EndpointSensitivity.CRITICAL) {
            return "CRITICAL_ACCESS";
        }
        if (sensitivity == EndpointSensitivity.SENSITIVE) {
            return "SENSITIVE_ACCESS";
        }
        return "HTTP_REQUEST";
    }

    private static boolean isAuditRequiredForProfile(String profile) {
        if (profile == null) return false;
        String lower = profile.trim().toLowerCase();
        return !lower.equals("local") && !lower.equals("embedded") && !lower.equals("test");
    }

    private HttpResponse auditServiceRequiredResponse(String requestId) {
        String body = "{\"error\":{\"code\":\"AUDIT_SERVICE_REQUIRED\","
            + "\"message\":\"Audit service is required for sensitive and critical routes in this profile.\"}}";
        return HttpResponse.ofCode(503)
            .withHeader(HttpHeaders.CONTENT_TYPE, io.activej.http.HttpHeaderValue.of("application/json"))
            .withHeader(HttpHeaders.of("X-Request-ID"), io.activej.http.HttpHeaderValue.of(requestId))
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
    }

    private boolean isBreakGlassAllowed(io.activej.http.HttpRequest request,
                                        Principal principal,
                                        String tenantId,
                                        String requestId) {
        String reason = request.getHeader(HttpHeaders.of(HEADER_BREAK_GLASS_REASON));
        if (reason == null || reason.isBlank()) {
            log.warn("[DC-SEC] Break-glass denied: missing {} header tenant={} requestId={}",
                    HEADER_BREAK_GLASS_REASON,
                    tenantId,
                    requestId);
            return false;
        }
        Set<String> normalizedRoles = principal.getRoles().stream()
            .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT).replace('-', '_'))
            .collect(java.util.stream.Collectors.toSet());
        boolean allowedRole = normalizedRoles.contains("ADMIN");
        if (!allowedRole) {
            log.warn("[DC-SEC] Break-glass denied: principal lacks ADMIN role principal={} tenant={} requestId={}",
                    principal.getName(),
                    tenantId,
                    requestId);
            return false;
        }
        log.warn("[DC-SEC] Break-glass override enabled tenant={} principal={} requestId={} reason={}",
                tenantId,
                principal.getName(),
                requestId,
                reason);
        return true;
    }

    private AccessLevel requiredAccess(String method, String path, EndpointSensitivity sensitivity) {
        AccessLevel routeActionLevel = RouteActionAccessRegistry.requiredAccess(method, path);
        if (routeActionLevel != null) {
            return routeActionLevel;
        }
        if (path.startsWith("/api/v1/governance/")) {
            return "GET".equalsIgnoreCase(method) ? AccessLevel.AUDITOR : AccessLevel.ADMIN;
        }
        if (path.startsWith("/api/v1/autonomy/")
                || path.startsWith("/api/v1/plugins/")
                || path.contains("/promote")
                || path.contains("/approve")
                || path.contains("/reject")) {
            return AccessLevel.ADMIN;
        }
        if ("GET".equalsIgnoreCase(method)) {
            return AccessLevel.VIEWER;
        }
        if (sensitivity == EndpointSensitivity.SENSITIVE || sensitivity == EndpointSensitivity.CRITICAL) {
            return AccessLevel.OPERATOR;
        }
        return AccessLevel.VIEWER;
    }

    private boolean hasRequiredAccess(Principal principal, AccessLevel requiredAccess) {
        if (requiredAccess == AccessLevel.NONE) {
            return true;
        }
        Set<String> normalizedRoles = principal.getRoles().stream()
            .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT).replace('-', '_'))
            .collect(java.util.stream.Collectors.toSet());

        // P0-04: Remove broad API_CLIENT and PROCESSOR role bypasses
        // ADMIN remains powerful for admin-level operations but is no longer a universal bypass
        // API_CLIENT and PROCESSOR are no longer broad bypass roles
        if (normalizedRoles.contains("ADMIN")) {
            // ADMIN role can access ADMIN-level operations explicitly
            // For other levels, it still needs to match the required access level
            return switch (requiredAccess) {
                case ADMIN -> true;
                case OPERATOR -> true;  // ADMIN can also perform operator tasks
                case AUDITOR -> true;   // ADMIN can also audit
                case VIEWER -> true;    // ADMIN can also view
                case NONE -> true;
            };
        }

        // PROCESSOR role: only for execution/runtime actions (OPERATOR level), not admin/settings/governance
        if (normalizedRoles.contains("PROCESSOR")) {
            return switch (requiredAccess) {
                case OPERATOR -> true;  // PROCESSOR can execute runtime actions
                case VIEWER -> true;    // PROCESSOR can also view
                case NONE -> true;
                case ADMIN -> false;   // PROCESSOR cannot perform admin operations
                case AUDITOR -> false;  // PROCESSOR cannot audit
            };
        }

        // API_CLIENT role: no longer a broad bypass - requires explicit role matching
        // API clients must be assigned specific roles (VIEWER, OPERATOR, ADMIN) based on their scope
        // This prevents machine accounts from having universal admin access
        if (normalizedRoles.contains("API_CLIENT")) {
            // API_CLIENT must match the required access level like any other role
            // This forces explicit role assignment based on the client's intended permissions
        }

        return switch (requiredAccess) {
            case NONE -> true;
            case VIEWER -> normalizedRoles.contains("VIEWER") || normalizedRoles.contains("READER")
                || normalizedRoles.contains("AUDITOR") || normalizedRoles.contains("OPERATOR")
                || normalizedRoles.contains("EDITOR");
            case AUDITOR -> normalizedRoles.contains("AUDITOR");
            case OPERATOR -> normalizedRoles.contains("OPERATOR") || normalizedRoles.contains("EDITOR");
            case ADMIN -> normalizedRoles.contains("ADMIN");
        };
    }

            private static HttpResponse unauthorized(String message) {
            String body = "{\"error\":{\"code\":\"UNAUTHENTICATED\","
                + "\"message\":\"" + message + "\"}}";
            return HttpResponse.ofCode(401)
                .withHeader(HttpHeaders.CONTENT_TYPE, io.activej.http.HttpHeaderValue.of("application/json"))
                .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
            }

    // ─────────────────────────────────────────────────────────────────────────
    // Builder
    // ─────────────────────────────────────────────────────────────────────────

    public static final class Builder {
        private ApiKeyResolver apiKeyResolver;
        private JwtTokenProvider jwtProvider;
        private String jwtTenantClaim;
        private PolicyEngine policyEngine;
        private AuditService auditService;
        private boolean enforcing = true;
        private boolean strictTenantResolution;
        private Set<String> breakGlassTenants;
        private String deploymentProfile = "local";

        /**
         * Resolver that validates API keys and maps them to {@link com.ghatana.platform.governance.security.Principal}.
         * Required.
         */
        public Builder apiKeyResolver(ApiKeyResolver resolver) {
            this.apiKeyResolver = resolver;
            return this;
        }

        /**
         * JWT provider used for bearer-token authentication when API keys are absent.
         */
        public Builder jwtProvider(JwtTokenProvider provider) {
            this.jwtProvider = provider;
            return this;
        }

        /**
         * Configurable tenant claim name extracted from JWT tokens.
         */
        public Builder jwtTenantClaim(String tenantClaim) {
            this.jwtTenantClaim = tenantClaim;
            return this;
        }

        /**
         * Platform policy engine for CRITICAL route evaluation.
         * When null and {@code enforcing=true} (default), CRITICAL routes are denied.
         * When null and {@code enforcing=false}, CRITICAL routes are allowed (audit-only mode).
         */
        public Builder policyEngine(PolicyEngine engine) {
            this.policyEngine = engine;
            return this;
        }

        /**
         * Audit service for recording HTTP access events.
         * When null, audit emission is disabled.
         */
        public Builder auditService(AuditService service) {
            this.auditService = service;
            return this;
        }

        /**
         * When {@code true} (default) policy denials return HTTP 403.
         * When {@code false} denials are logged only (useful for canary/observation mode).
         */
        public Builder enforcing(boolean enforcing) {
            this.enforcing = enforcing;
            return this;
        }

        /**
         * When {@code true}, tenant fallback is disabled and requests must resolve
         * a concrete tenant from principal, header, or tenant context.
         */
        public Builder strictTenantResolution(boolean strictTenantResolution) {
            this.strictTenantResolution = strictTenantResolution;
            return this;
        }

        /**
         * Tenants that may invoke break-glass on CRITICAL routes.
         */
        public Builder breakGlassTenants(Set<String> tenants) {
            this.breakGlassTenants = tenants;
            return this;
        }

        /**
         * Deployment profile (e.g., "local", "sovereign", "staging", "production").
         * Used to enforce audit requirements for sensitive/critical routes.
         */
        public Builder deploymentProfile(String profile) {
            this.deploymentProfile = profile;
            return this;
        }

        public DataCloudSecurityFilter build() {
            return new DataCloudSecurityFilter(this);
        }
    }

    enum AccessLevel {
        NONE,
        VIEWER,
        AUDITOR,
        OPERATOR,
        ADMIN
    }

    private record RequestIdAttachment(String value) { }
}
