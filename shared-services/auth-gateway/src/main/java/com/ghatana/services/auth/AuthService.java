package com.ghatana.services.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.oauth2.OAuth2Config;
import com.ghatana.platform.security.oauth2.OAuth2Provider;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

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
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

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
            // Fail fast in production — no insecure fallback allowed.
            String env = System.getenv().getOrDefault("ENVIRONMENT", "development");
            if ("production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env)) {
                throw new IllegalStateException(
                    "PLATFORM_JWT_SECRET must be set to at least 32 characters in production. " +
                    "Set the PLATFORM_JWT_SECRET environment variable before deploying.");
            }
            log.warn("PLATFORM_JWT_SECRET not set or too short (< 32 chars) — " +
                     "using INSECURE development default. NEVER deploy this to production. " +
                     "Set PLATFORM_JWT_SECRET (32+ chars) before deploying.");
            secret = "dev-platform-jwt-secret-change-me-in-prod!";
        }
        return JwtTokenProviders.fromSharedSecret(secret, PLATFORM_TOKEN_TTL_MS);
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
                String code  = request.getQueryParameter("code");
                String state = request.getQueryParameter("state");
                String error = request.getQueryParameter("error");
                String postLoginRedirectParam = request.getQueryParameter("post_login_redirect");

                // IdP reported an error (user denied, etc.)
                if (error != null && !error.isEmpty()) {
                    String desc = request.getQueryParameter("error_description");
                    log.warn("OIDC callback error: {} — {}", error, desc);
                    return HttpResponse.ofCode(400)
                            .withJson(errorJson(400, "OIDC_CALLBACK_ERROR", sanitize(error), sanitize(desc == null ? "" : desc)))
                            .build()
                            .toPromise();
                }

                if (code == null || code.isEmpty()) {
                    return HttpResponse.ofCode(400)
                            .withJson(errorJson(400, "MISSING_CODE", "Missing authorization code"))
                            .build()
                            .toPromise();
                }
                if (state == null || state.isEmpty()) {
                    return HttpResponse.ofCode(400)
                            .withJson(errorJson(400, "MISSING_STATE", "Missing OAuth state"))
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
                                    .map(Object::toString)
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
                        String postLoginRedirect = postLoginRedirectParam;
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
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        // OAuth2 protocol validation errors (bad state, invalid code, expired token, etc.)
                        log.warn("OIDC authentication validation failed: {}", e.getMessage());
                        return HttpResponse.ofCode(401)
                                .withJson(errorJson(401, "AUTHENTICATION_FAILED", "Authentication failed"))
                                .build();
                    } catch (Exception e) {
                        // Unexpected system error — do not leak details to the caller
                        log.error("Unexpected error during OIDC callback", e);
                        return HttpResponse.ofCode(500)
                                .withJson(errorJson(500, "INTERNAL_ERROR", "Authentication service unavailable"))
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
                        try {
                            var tokenRequest = JsonUtils.fromJson(bodyStr, java.util.Map.class);
                            token = tokenRequest != null ? (String) tokenRequest.get("token") : null;
                        } catch (Exception e) {
                            token = null;
                        }
                    }
                    if (token == null || token.isEmpty()) {
                        return Promise.of(HttpResponse.ofCode(400)
                                .withJson(errorJson(400, "MISSING_TOKEN", "No token provided"))
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
                            .withJson(errorJson(401, "NO_SESSION", "No session"))
                            .build());
                }

                var sessionOpt = sessionManager.getSession(sessionId);
                if (sessionOpt.isEmpty()) {
                    return Promise.of(HttpResponse.ofCode(401)
                            .withJson(errorJson(401, "SESSION_NOT_FOUND", "Session expired or not found"))
                            .build());
                }

                var session = sessionOpt.get();
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
                        try {
                            var logoutRequest = JsonUtils.fromJson(bodyStr, java.util.Map.class);
                            sessionId = logoutRequest != null ? (String) logoutRequest.get("sessionId") : null;
                        } catch (Exception e) {
                            sessionId = null;
                        }
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

    static ErrorResponse standardError(int status, String code, String message) {
        return ErrorResponse.of(status, code, message);
    }

    static ErrorResponse standardError(int status, String code, String message, String details) {
        return ErrorResponse.builder()
                .status(status)
                .code(code)
                .message(message)
                .details(details)
                .build();
    }

    static String errorJson(int status, String code, String message) {
        return writeJson(standardError(status, code, message));
    }

    static String errorJson(int status, String code, String message, String details) {
        return writeJson(standardError(status, code, message, details));
    }

    private static String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize error response", exception);
        }
    }

}
