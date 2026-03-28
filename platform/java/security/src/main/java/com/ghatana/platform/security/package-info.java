/**
 * Core security module providing authentication, authorization, and security utilities.
 *
 * <p>This module includes:
 * <ul>
 *   <li>Authentication providers (JWT, API keys, OAuth2)</li>
 *   <li>Role-based access control (RBAC)</li>
 *   <li>Audit logging</li>
 *   <li>Rate limiting</li>
 *   <li>Security headers and middleware</li>
 *   <li>Encryption and key management</li>
 * </ul>
 *
 * <h2>Authorization Pattern — RBAC (SEC-003)</h2>
 *
 * <p>Use {@link com.ghatana.platform.security.rbac.RBACFilter} to enforce role-based
 * access control at the HTTP routing layer. Register it once as a filter around
 * the secured routes rather than duplicating role checks inside individual handlers:</p>
 *
 * <pre>{@code
 * RBACFilter rbacFilter = new RBACFilter(authorizationService, rolePermissionRegistry);
 *
 * RoutingServlet servlet = RoutingServlet.builder(eventloop)
 *     .map(HttpMethod.POST, "/api/admin/*", rbacFilter.requireRole("ADMIN", adminHandler))
 *     .map(HttpMethod.GET,  "/api/data/*",  rbacFilter.requireRole("READER", dataHandler))
 *     .build();
 * }</pre>
 *
 * <p>Declarative policy rules should be registered in
 * {@link com.ghatana.platform.security.rbac.RolePermissionRegistry} (or its in-memory
 * implementation) so that permission logic is centralized and testable independently
 * of HTTP infrastructure code.</p>
 *
 * <h2>Security Context (SEC-004)</h2>
 *
 * <p>Propagate authenticated-user context through
 * {@link com.ghatana.platform.security.SecurityContext} rather than passing raw
 * user IDs and tenant IDs as raw parameters through every method call:</p>
 *
 * <pre>{@code
 * // In the authentication filter — set context after token validation:
 * SecurityContext ctx = SecurityContext.of(userId, tenantId, roles);
 * RequestContext.set(request, ctx);
 *
 * // In downstream handlers — read without re-parsing the token:
 * SecurityContext ctx = RequestContext.get(request);
 * String currentUserId = ctx.userId();
 * }</pre>
 *
 * <h2>JWT Authentication</h2>
 *
 * <p>Always obtain {@link com.ghatana.platform.security.jwt.JwtTokenProvider} from
 * the platform factory methods. Do not construct JWT handling code directly in
 * service code:</p>
 *
 * <pre>{@code
 * // Shared-secret HS256 (most services):
 * JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(secret, expiryMs);
 *
 * // RSA key-pair RS256 (used at gateway edge only):
 * JwtTokenProvider provider = JwtTokenProviders.fromKeyPair(privateKey, publicKey, expiryMs);
 * }</pre>
 *
 * <h2>Rate Limiting</h2>
 *
 * <p>Use {@link com.ghatana.platform.security.ratelimit.DefaultRateLimiter} for all
 * per-client token-bucket throttling. Inspect the returned
 * {@code RateLimiter.AcquireResult} to extract response headers:</p>
 *
 * <pre>{@code
 * RateLimiter.AcquireResult result = rateLimiter.tryAcquire(clientId);
 * if (!result.allowed()) {
 *     // return 429 with X-RateLimit-* headers using result.remainingTokens(),
 *     // result.resetAtEpochSeconds(), result.retryAfterSeconds()
 * }
 * }</pre>
 *
 * @doc.type package
 * @doc.purpose Core security module providing authentication, authorization, and security utilities
 * @doc.layer platform
 * @doc.pattern Package
 */
package com.ghatana.platform.security;
