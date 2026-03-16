package com.ghatana.services.auth;

import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.oauth2.OAuth2Config;
import com.ghatana.platform.security.oauth2.OAuth2Exception;
import com.ghatana.platform.security.oauth2.OAuth2Provider;
import com.ghatana.platform.security.oauth2.OidcSession;
import com.ghatana.platform.security.oauth2.OidcSessionManager;
import com.ghatana.platform.security.oauth2.TokenIntrospector;
import io.activej.eventloop.Eventloop;
import io.activej.http.*;
import io.activej.inject.annotation.Provides;
import io.activej.launcher.Launcher;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.activej.http.HttpMethod.*;

/**
 * Production-grade Authentication Service providing OIDC/OAuth2 login,
 * OIDC callback handling, cross-product platform token issuance,
 * token introspection, and session management.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /auth/login} — Initiate OIDC flow; returns authorization URL</li>
 *   <li>{@code GET  /auth/callback} — OIDC redirect handler; exchanges code for tokens,
 *       creates session, issues platform JWT, redirects browser to product</li>
 *   <li>{@code POST /auth/token/introspect} — Validate OIDC access token</li>
 *   <li>{@code GET  /auth/me} — Return current user from platform session</li>
 *   <li>{@code POST /auth/logout} — Invalidate session</li>
 *   <li>{@code GET  /health} — Health probe</li>
 *   <li>{@code GET  /metrics} — Basic metrics (active session count)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose OIDC-based centralised authentication service for all products
 * @doc.layer platform
 * @doc.pattern Service Launcher
 */
public class AuthService extends HttpServerLauncher {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** JWT TTL for the platform-issued cross-product token (15 minutes). */
    private static final long PLATFORM_TOKEN_TTL_MS = 15 * 60 * 1000L;

    // ─── Providers ───────────────────────────────────────────────────────────

    @Provides
    OAuth2Config oauth2Config() {
        Map<String, String> properties = Map.of(
            "oauth2.client-id",      System.getenv().getOrDefault("OAUTH2_CLIENT_ID", "ghatana"),
            "oauth2.client-secret",  System.getenv().getOrDefault("OAUTH2_CLIENT_SECRET", ""),
            "oauth2.discovery-uri",  System.getenv().getOrDefault("OAUTH2_DISCOVERY_URI", ""),
            "oauth2.redirect-uri",   System.getenv().getOrDefault("OAUTH2_REDIRECT_URI", "http://localhost:8080/auth/callback"),
            "oauth2.scopes",         System.getenv().getOrDefault("OAUTH2_SCOPES", "openid profile email")
        );
        return OAuth2Config.fromProperties(properties);
    }

    @Provides
    OAuth2Provider oauth2Provider(OAuth2Config config) {
        return new OAuth2Provider(config);
    }

    @Provides
    TokenIntrospector tokenIntrospector(OAuth2Config config) {
        return new TokenIntrospector(config);
    }

    @Provides
    OidcSessionManager sessionManager() {
        int ttlMinutes = Integer.parseInt(System.getenv().getOrDefault("SESSION_TTL_MINUTES", "60"));
        return new OidcSessionManager(Duration.ofMinutes(ttlMinutes));
    }

    /**
     * Platform JWT provider — issues short-lived cross-product tokens after OIDC login.
     * Uses a separate secret from product-scoped JWTs so products can distinguish origin.
     */
    @Provides
    JwtTokenProvider platformJwtProvider() {
        String secret = System.getenv("PLATFORM_JWT_SECRET");
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            // During development fall back to an insecure default so the service starts.
            // Production MUST supply PLATFORM_JWT_SECRET.
            log.warn("PLATFORM_JWT_SECRET not set — using insecure development default. " +
                     "Set PLATFORM_JWT_SECRET (32+ chars) before deploying to production.");
            secret = "dev-platform-jwt-secret-change-me-in-prod!";
        }
        return new JwtTokenProvider(secret, PLATFORM_TOKEN_TTL_MS);
    }

    @Provides
    Eventloop eventloop() {
        return Eventloop.builder()
                .withThreadName("auth-service")
                .build();
    }

    // ─── Servlet ─────────────────────────────────────────────────────────────

    @Provides
    AsyncServlet servlet(
            Eventloop eventloop,
            OAuth2Config oauth2Config,
            OAuth2Provider oauth2Provider,
            TokenIntrospector tokenIntrospector,
            OidcSessionManager sessionManager,
            JwtTokenProvider platformJwtProvider
    ) {
        return RoutingServlet.builder(eventloop)

            // ── Initiate OIDC flow ────────────────────────────────────────────
            .with(POST, "/auth/login", request -> {
                String nonce = java.util.UUID.randomUUID().toString();
                String redirectUri = oauth2Config.getRedirectUri() != null
                        ? oauth2Config.getRedirectUri().toString()
                        : "http://localhost:8080/auth/callback";
                OAuth2Provider.AuthResponse authResponse =
                        oauth2Provider.generateAuthorizationUrl(redirectUri, nonce);
                String json = String.format(
                    "{\"authorizationUrl\":\"%s\",\"state\":\"%s\",\"nonce\":\"%s\"}",
                    authResponse.getAuthorizationUrl(),
                    authResponse.getState(),
                    authResponse.getNonce());
                log.info("OIDC flow initiated — state={}", authResponse.getState());
                return HttpResponse.ok200().withJson(json).build().toPromise();
            })

            // ── OIDC Callback — the heart of SSO ─────────────────────────────
            //
            // The IdP redirects the user's browser here after successful login.
            // We:
            //   1. Parse `code` and `state` from the query string.
            //   2. Exchange the code for tokens via oauth2Provider.authenticate().
            //   3. Create a server-side OidcSession via sessionManager.
            //   4. Mint a short-lived platform-wide JWT so every product can
            //      validate the same token without calling this service.
            //   5. Redirect the browser to the product with the session cookie
            //      and a `token` query param the UI can store.
            .with(GET, "/auth/callback", request -> {
                String queryString = request.getUrl().getQuery();
                String code  = getQueryParam(queryString, "code");
                String state = getQueryParam(queryString, "state");
                String error = getQueryParam(queryString, "error");

                // IdP reported an error (user denied, etc.)
                if (error != null && !error.isEmpty()) {
                    String desc = getQueryParam(queryString, "error_description");
                    log.warn("OIDC callback error: {} — {}", error, desc);
                    return HttpResponse.ofCode(400)
                            .withJson(String.format(
                                "{\"error\":\"%s\",\"error_description\":\"%s\"}",
                                sanitize(error), sanitize(desc == null ? "" : desc)))
                            .build()
                            .toPromise();
                }

                if (code == null || code.isEmpty()) {
                    return HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"missing_code\"}")
                            .build()
                            .toPromise();
                }
                if (state == null || state.isEmpty()) {
                    return HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"missing_state\"}")
                            .build()
                            .toPromise();
                }

                String redirectUri = oauth2Config.getRedirectUri() != null
                        ? oauth2Config.getRedirectUri().toString()
                        : "http://localhost:8080/auth/callback";

                return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                    try {
                        // Exchange authorization code → IdP tokens + user info
                        User user = oauth2Provider.authenticate(code, state, state, redirectUri);

                        // Retrieve IdP token values from user attributes
                        String idToken      = (String) user.getAttributes().getOrDefault("idToken", "");
                        String accessToken  = (String) user.getAttributes().getOrDefault("accessToken", "");
                        String refreshToken = (String) user.getAttributes().getOrDefault("refreshToken", "");

                        // Persist server-side OIDC session
                        String sessionId = sessionManager.createSession(user, idToken, accessToken, refreshToken);

                        // Mint platform-wide cross-product JWT (15-minute TTL)
                        String platformToken = platformJwtProvider.createToken(
                            user.getUserId(),
                            user.getRoles().stream()
                                    .map(r -> r instanceof Enum ? ((Enum<?>) r).name() : r.toString())
                                    .collect(java.util.stream.Collectors.toList()),
                            Map.of(
                                "email",      user.getEmail() == null ? "" : user.getEmail(),
                                "sessionId",  sessionId,
                                "tokenType",  "PLATFORM",
                                "issuer",     "ghatana-auth-service"
                            )
                        );

                        log.info("OIDC login success — userId={} sessionId={}", user.getUserId(), sessionId);

                        // Determine where to send the browser.
                        // Products may pass a `post_login_redirect` query param before starting
                        // the OIDC flow; fall back to the root dashboard.
                        String postLoginRedirect = getQueryParam(queryString, "post_login_redirect");
                        if (postLoginRedirect == null || postLoginRedirect.isBlank()) {
                            postLoginRedirect = System.getenv().getOrDefault(
                                "POST_LOGIN_REDIRECT_URL", "http://localhost:3000/dashboard");
                        }
                        // Append session token so the frontend can bootstrap
                        String separator = postLoginRedirect.contains("?") ? "&" : "?";
                        String redirectTarget = postLoginRedirect + separator + "platform_token=" + platformToken;

                        return HttpResponse.redirect302(redirectTarget)
                                .withHeader(HttpHeaders.SET_COOKIE,
                                    String.format("ghatana_session=%s; Path=/; HttpOnly; SameSite=Lax; Max-Age=3600",
                                        sessionId))
                                .build();
                    } catch (OAuth2Exception e) {
                        log.warn("OIDC callback authentication failed: {}", e.getMessage());
                        return HttpResponse.ofCode(401)
                                .withJson(String.format("{\"error\":\"authentication_failed\",\"detail\":\"%s\"}",
                                    sanitize(e.getMessage())))
                                .build();
                    }
                });
            })

            // ── Token introspection ───────────────────────────────────────────
            .with(POST, "/auth/token/introspect", request ->
                request.loadBody().then(body -> {
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    String token;
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                    } else {
                        String bodyStr = body.getString(StandardCharsets.UTF_8);
                        token = extractJsonField(bodyStr, "token");
                    }
                    if (token == null || token.isEmpty()) {
                        return Promise.of(HttpResponse.ofCode(400)
                                .withJson("{\"active\":false,\"error\":\"No token provided\"}")
                                .build());
                    }
                    return tokenIntrospector.introspect(token)
                        .map(user -> HttpResponse.ok200()
                            .withJson(String.format(
                                "{\"active\":true,\"subject\":\"%s\",\"email\":\"%s\"}",
                                user.getUserId(),
                                user.getEmail() == null ? "" : user.getEmail()))
                            .build())
                        .then(Promise::of,
                            error -> Promise.of(HttpResponse.ofCode(401)
                                .withJson("{\"active\":false}")
                                .build()));
                })
            )

            // ── Current user (requires valid session cookie or Bearer token) ──
            .with(GET, "/auth/me", request -> {
                // Try session cookie first
                String cookieHeader = request.getHeader(HttpHeaders.COOKIE);
                String sessionId = extractCookieValue(cookieHeader, "ghatana_session");

                // Fall back to Bearer token carrying a sessionId claim
                if (sessionId == null) {
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        // The platform JWT carries the sessionId as a claim
                        String bearerToken = authHeader.substring(7);
                        sessionId = extractSessionIdFromPlatformToken(platformJwtProvider, bearerToken);
                    }
                }

                if (sessionId == null) {
                    return Promise.of(HttpResponse.ofCode(401)
                            .withJson("{\"error\":\"No session\"}")
                            .build());
                }

                Optional<OidcSession> sessionOpt = sessionManager.getSession(sessionId);
                if (sessionOpt.isEmpty()) {
                    return Promise.of(HttpResponse.ofCode(401)
                            .withJson("{\"error\":\"Session expired or not found\"}")
                            .build());
                }

                OidcSession session = sessionOpt.get();
                User user = session.getUser();
                String json = String.format(
                    "{\"userId\":\"%s\",\"email\":\"%s\",\"authenticated\":true,\"sessionId\":\"%s\"}",
                    user.getUserId(),
                    user.getEmail() == null ? "" : user.getEmail(),
                    sessionId);
                return Promise.of(HttpResponse.ok200().withJson(json).build());
            })

            // ── Session logout ────────────────────────────────────────────────
            .with(POST, "/auth/logout", request ->
                request.loadBody().then(body -> {
                    // Try session cookie first, then request body
                    String cookieHeader = request.getHeader(HttpHeaders.COOKIE);
                    String sessionId = extractCookieValue(cookieHeader, "ghatana_session");
                    if (sessionId == null) {
                        String bodyStr = body.getString(StandardCharsets.UTF_8);
                        sessionId = extractJsonField(bodyStr, "sessionId");
                    }
                    if (sessionId != null && !sessionId.isEmpty()) {
                        sessionManager.invalidateSession(sessionId);
                        log.info("Session invalidated: {}", sessionId);
                    }
                    return Promise.of(HttpResponse.ok200()
                            .withHeader(HttpHeaders.SET_COOKIE,
                                "ghatana_session=; Path=/; HttpOnly; Max-Age=0")
                            .withJson("{\"status\":\"logged_out\"}")
                            .build());
                })
            )

            // ── Health ────────────────────────────────────────────────────────
            .with(GET, "/health", request ->
                HttpResponse.ok200()
                    .withJson("{\"status\":\"UP\",\"service\":\"auth-service\",\"version\":\"2.0.0\"}")
                    .build()
                    .toPromise()
            )

            // ── Metrics ───────────────────────────────────────────────────────
            .with(GET, "/metrics", request ->
                HttpResponse.ok200()
                    .withJson(String.format("{\"active_sessions\":%d}",
                        sessionManager.getActiveSessionCount()))
                    .build()
                    .toPromise()
            )
            .build();
    }

    // ─── Entry point ─────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        log.info("Starting Authentication Service v2 (OIDC)...");
        Launcher launcher = new AuthService();
        launcher.launch(args);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Extract a single query parameter from a raw query string. */
    private static String getQueryParam(String queryString, String name) {
        if (queryString == null || queryString.isEmpty()) return null;
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = decode(pair.substring(0, eq));
            if (name.equals(key)) {
                return decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String decode(String s) {
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }

    /** Extract a named cookie value from a raw Cookie header string. */
    private static String extractCookieValue(String cookieHeader, String cookieName) {
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.strip();
            int eq = trimmed.indexOf('=');
            if (eq < 0) continue;
            if (cookieName.equals(trimmed.substring(0, eq).strip())) {
                return trimmed.substring(eq + 1).strip();
            }
        }
        return null;
    }

    /** Read the `sessionId` claim from a platform JWT without full validation (best-effort). */
    private static String extractSessionIdFromPlatformToken(JwtTokenProvider provider, String token) {
        try {
            return provider.extractClaims(token)
                    .map(claims -> (String) claims.get("sessionId"))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Sanitize a string for safe JSON inclusion (no quotes, no control chars). */
    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }

    /** Simple JSON field extractor (avoids adding a JSON library dependency). */
    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }
}


/**
 * Production-grade Authentication Service
 * 
 * Centralized OAuth2/OIDC authentication service for all products.
 * 
 * Features:
 * - OAuth2 / OIDC authentication
 * - Token introspection and validation
 * - Session management with caching
 * - Multi-tenant support
 * - High-performance async architecture
 * 
 * Endpoints:
 * - POST /auth/login - Start OAuth2 flow (returns authorization URL)
 * - POST /auth/token/introspect - Validate access token
 * - POST /auth/logout - Invalidate session
 * - GET /health - Health check
 * 
 * @author Ghatana Platform Team
 * @version 1.0.0
 */
public class AuthService extends HttpServerLauncher {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    
    @Provides
    OAuth2Config oauth2Config() {
        Map<String, String> properties = Map.of(
            "oauth2.client-id", System.getenv().getOrDefault("OAUTH2_CLIENT_ID", "ghatana"),
            "oauth2.client-secret", System.getenv().getOrDefault("OAUTH2_CLIENT_SECRET", ""),
            "oauth2.discovery-uri", System.getenv().getOrDefault("OAUTH2_DISCOVERY_URI", ""),
            "oauth2.redirect-uri", System.getenv().getOrDefault("OAUTH2_REDIRECT_URI", "http://localhost:8080/auth/callback"),
            "oauth2.scopes", System.getenv().getOrDefault("OAUTH2_SCOPES", "openid,profile,email")
        );
        
        return OAuth2Config.fromProperties(properties);
    }
    
    @Provides
    OAuth2Provider oauth2Provider(OAuth2Config config) {
        return new OAuth2Provider(config);
    }
    
    @Provides
    TokenIntrospector tokenIntrospector(OAuth2Config config) {
        return new TokenIntrospector(config);
    }
    
    @Provides
    OidcSessionManager sessionManager() {
        int ttlMinutes = Integer.parseInt(System.getenv().getOrDefault("SESSION_TTL_MINUTES", "60"));
        return new OidcSessionManager(Duration.ofMinutes(ttlMinutes));
    }
    
    @Provides
    Eventloop eventloop() {
        return Eventloop.builder()
                .withThreadName("auth-service")
                .build();
    }
    
    @Provides
    AsyncServlet servlet(
            Eventloop eventloop,
            OAuth2Provider oauth2Provider,
            TokenIntrospector tokenIntrospector,
            OidcSessionManager sessionManager
    ) {
        return RoutingServlet.builder(eventloop)
            // Authentication - generate authorization URL
            .with(POST, "/auth/login", request -> {
                String nonce = java.util.UUID.randomUUID().toString();
                OAuth2Provider.AuthResponse authResponse = oauth2Provider.generateAuthorizationUrl("openid profile email", nonce);
                String json = String.format(
                    "{\"authorizationUrl\":\"%s\",\"state\":\"%s\",\"nonce\":\"%s\"}",
                    authResponse.getAuthorizationUrl(),
                    authResponse.getState(),
                    authResponse.getNonce());
                return HttpResponse.ok200().withJson(json).build().toPromise();
            })
            // Token introspection
            .with(POST, "/auth/token/introspect", request -> {
                // Extract token from Authorization header or request body
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                String token;
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                } else {
                    // Fallback: extract token from body (expects {"token":"..."})
                    String body = request.loadBody().getResult().getString(java.nio.charset.StandardCharsets.UTF_8);
                    token = extractJsonField(body, "token");
                }
                if (token == null || token.isEmpty()) {
                    return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"active\":false,\"error\":\"No token provided\"}")
                            .build());
                }
                return tokenIntrospector.introspect(token)
                    .map(user -> HttpResponse.ok200()
                        .withJson(String.format("{\"active\":true,\"subject\":\"%s\"}", user.getUserId()))
                        .build())
                    .then(
                        Promise::of,
                        error -> Promise.of(HttpResponse.ofCode(401)
                            .withJson("{\"active\":false}")
                            .build())
                    );
            })
            // Session logout
            .with(POST, "/auth/logout", request -> {
                // Extract session ID from X-Session-Id header or cookie
                String sessionId = request.getHeader(HttpHeaders.of("X-Session-Id"));
                if (sessionId == null || sessionId.isEmpty()) {
                    // Fallback: extract from request body
                    String body = request.loadBody().getResult().getString(java.nio.charset.StandardCharsets.UTF_8);
                    sessionId = extractJsonField(body, "sessionId");
                }
                if (sessionId != null && !sessionId.isEmpty()) {
                    sessionManager.invalidateSession(sessionId);
                    log.info("Session invalidated: {}", sessionId);
                }
                return HttpResponse.ok200()
                    .withJson("{\"status\":\"logged_out\"}")
                    .build()
                    .toPromise();
            })
            // Health check
            .with(GET, "/health", request ->
                HttpResponse.ok200()
                    .withJson("{\"status\":\"UP\",\"service\":\"auth-service\",\"version\":\"1.0.0\"}")
                    .build()
                    .toPromise()
            )
            // Metrics endpoint
            .with(GET, "/metrics", request ->
                HttpResponse.ok200()
                    .withJson(String.format("{\"active_sessions\":%d}",
                        sessionManager.getActiveSessionCount()))
                    .build()
                    .toPromise()
            )
            .build();
    }
    
    public static void main(String[] args) throws Exception {
        log.info("Starting Authentication Service...");
        Launcher launcher = new AuthService();
        launcher.launch(args);
    }

    /**
     * Simple JSON field extractor (avoids adding a JSON library dependency).
     * Handles fields with string values in flat JSON objects.
     */
    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }
}
