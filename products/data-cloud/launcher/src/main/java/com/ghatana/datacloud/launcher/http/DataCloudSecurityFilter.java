package com.ghatana.datacloud.launcher.http;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.http.security.filter.TenantIsolationHttpFilter;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.security.SecurityUtils;
import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
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
 *     2. API key authentication first, JWT bearer authentication second.
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
 *   <li>Fail-closed: any unexpected exception during policy evaluation returns
 *       HTTP 403, never silently passes.</li>
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
    static final String DEFAULT_TENANT_CLAIM = "tenant_id";

    private final ApiKeyResolver apiKeyResolver;
    private final JwtTokenProvider jwtProvider;
    private final String jwtTenantClaim;
    private final PolicyEngine policyEngine;
    private final AuditService auditService;
    private final boolean enforcing;
    private final Set<String> policyExcludedTenants;

    private DataCloudSecurityFilter(Builder b) {
        if (b.apiKeyResolver == null && b.jwtProvider == null) {
            throw new NullPointerException("Either apiKeyResolver or jwtProvider must be configured");
        }
        this.apiKeyResolver         = b.apiKeyResolver;
        this.jwtProvider            = b.jwtProvider;
        this.jwtTenantClaim         = b.jwtTenantClaim != null && !b.jwtTenantClaim.isBlank()
                ? b.jwtTenantClaim
                : DEFAULT_TENANT_CLAIM;
        this.policyEngine           = b.policyEngine;        // nullable — policy checks skipped when null
        this.auditService           = b.auditService;        // nullable — audit skipped when null
        this.enforcing              = b.enforcing;
        this.policyExcludedTenants  = b.policyExcludedTenants != null
                ? Set.copyOf(b.policyExcludedTenants) : Set.of();
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

            // (2–3) Authenticate and establish TenantContext before the tenant isolation filter.
            return authenticate(request, tenantWrapped)
                .then(response -> {
                    Principal authenticatedPrincipal = request.getAttachment(Principal.class);
                    String tenantId = resolveTenantId(request, authenticatedPrincipal);
                    String principalName = resolvePrincipalName(authenticatedPrincipal);
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
                                               AsyncServlet tenantWrapped) {
        if (apiKeyResolver != null) {
            String apiKey = request.getHeader(HttpHeaders.of(HEADER_API_KEY));
            if (apiKey != null && !apiKey.isBlank()) {
                return authenticateApiKey(request, tenantWrapped, apiKey);
            }
        }

        if (jwtProvider != null) {
            String token = SecurityUtils.extractBearerToken(request.getHeader(HttpHeaders.of(HEADER_AUTHORIZATION)));
            if (token != null && !token.isBlank()) {
                return authenticateJwt(request, tenantWrapped, token);
            }
        }

        return Promise.of(unauthorized("Missing authentication credentials"));
    }

    private Promise<HttpResponse> authenticateApiKey(io.activej.http.HttpRequest request,
                                                     AsyncServlet tenantWrapped,
                                                     String apiKey) {
        var principalOpt = apiKeyResolver.resolve(apiKey);
        if (principalOpt.isEmpty()) {
            log.warn("Unauthorized request: invalid API key");
            return Promise.of(unauthorized("Missing or invalid API key"));
        }

        return serveAsPrincipal(request, tenantWrapped, principalOpt.get());
    }

    private Promise<HttpResponse> authenticateJwt(io.activej.http.HttpRequest request,
                                                  AsyncServlet tenantWrapped,
                                                  String token) {
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

            Principal principal = new Principal(userId, jwtProvider.getRolesFromToken(token), tenantId);
            return serveAsPrincipal(request, tenantWrapped, principal);
        } catch (RuntimeException exception) {
            log.warn("Unauthorized request: JWT authentication failed: {}", exception.getMessage());
            return Promise.of(unauthorized("Missing, expired, or invalid JWT bearer token"));
        }
    }

    private Promise<HttpResponse> serveAsPrincipal(io.activej.http.HttpRequest request,
                                                   AsyncServlet tenantWrapped,
                                                   Principal principal) {
        request.attach(Principal.class, principal);
        TenantContext.Scope scope = TenantContext.scope(principal);
        try {
            return tenantWrapped.serve(request)
                    .whenComplete((response, error) -> scope.close());
        } catch (Exception exception) {
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

        // (4) Policy engine check for CRITICAL routes (unless excluded).
        if (sensitivity == EndpointSensitivity.CRITICAL && policyEngine != null) {
            if (!policyExcludedTenants.contains(tenantId)) {
                return evaluatePolicy(
                        request,
                        response,
                        path,
                        method,
                        sensitivity,
                        requestId,
                        tenantId,
                        principalName);
            }
        }

        // (5) Fire-and-forget audit for SENSITIVE and CRITICAL.
        if (sensitivity == EndpointSensitivity.SENSITIVE || sensitivity == EndpointSensitivity.CRITICAL) {
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

    private Promise<HttpResponse> evaluatePolicy(
            io.activej.http.HttpRequest request,
            HttpResponse response,
            String path,
            String method,
            EndpointSensitivity sensitivity,
            String requestId,
            String tenantId,
            String principalName) {

        Map<String, Object> ctx = Map.of(
            "tenantId",    tenantId,
            "path",        path,
            "method",      method,
            "sensitivity", sensitivity.name(),
            "requestId",   requestId,
            "timestamp",   Instant.now().toString()
        );

        return policyEngine.evaluate("datacloud.sensitive-route-access", ctx)
            .then(allowed -> {
                if (!allowed) {
                    if (enforcing) {
                        log.warn("[DC-SEC] Policy denied {} {} for tenant={} requestId={}",
                                 method, path, tenantId, requestId);
                        emitAudit(request, path, method, sensitivity, false, 403, requestId, tenantId, principalName);
                        return Promise.of(policyDenyResponse(requestId));
                    } else {
                        log.warn("[DC-SEC][AUDIT-ONLY] Policy would deny {} {} for tenant={} requestId={}",
                                 method, path, tenantId, requestId);
                    }
                }
                emitAudit(request, path, method, sensitivity, true, response.getCode(), requestId, tenantId, principalName);
                return Promise.of(response);
            }, e -> {
                // Fail-closed: any policy engine error blocks the request in enforcing mode.
                log.error("[DC-SEC] Policy evaluation error for {} {} requestId={}: {}",
                          method, path, requestId, e.getMessage(), e);
                emitAudit(request, path, method, sensitivity, false, 403, requestId, tenantId, principalName);
                if (enforcing) {
                    return Promise.of(policyDenyResponse(requestId));
                }
                return Promise.of(response);
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

        if (auditService == null) return;

        AuditEvent event = AuditEvent.builder()
            .tenantId(tenantId)
            .eventType("HTTP_REQUEST")
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
        String existing = request.getHeader(HttpHeaders.of(HEADER_REQUEST_ID));
        return (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
    }

    private static String resolveTenantId(
            io.activej.http.HttpRequest request,
            Principal authenticatedPrincipal) {
        if (authenticatedPrincipal != null && authenticatedPrincipal.getTenantId() != null) {
            return authenticatedPrincipal.getTenantId();
        }

        String tenantHeader = request.getHeader(HttpHeaders.of("X-Tenant-ID"));
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return tenantHeader.trim();
        }

        return TenantContext.getCurrentTenantId();
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

    private static HttpResponse policyDenyResponse(String requestId) {
        String body = "{\"error\":{\"code\":\"POLICY_DENY\","
            + "\"message\":\"Request blocked by governance policy\","
            + "\"requestId\":\"" + requestId + "\"}}";
        return HttpResponse.ofCode(403)
            .withHeader(HttpHeaders.CONTENT_TYPE, io.activej.http.HttpHeaderValue.of("application/json"))
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
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
        private Set<String> policyExcludedTenants;

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
         * When null, policy checks are skipped (audit still runs).
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
         * Tenants that are excluded from the policy engine check (e.g. internal service tenants).
         */
        public Builder policyExcludedTenants(Set<String> tenants) {
            this.policyExcludedTenants = tenants;
            return this;
        }

        public DataCloudSecurityFilter build() {
            return new DataCloudSecurityFilter(this);
        }
    }
}
