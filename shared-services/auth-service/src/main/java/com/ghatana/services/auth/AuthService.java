package com.ghatana.services.auth;

import com.ghatana.platform.security.oauth2.OAuth2Config;
import com.ghatana.platform.security.oauth2.OAuth2Provider;
import com.ghatana.platform.security.oauth2.TokenIntrospector;
import com.ghatana.platform.security.oauth2.OidcSessionManager;
import io.activej.eventloop.Eventloop;
import io.activej.http.*;
import io.activej.inject.annotation.Provides;
import io.activej.launcher.Launcher;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

import static io.activej.http.HttpMethod.*;

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
