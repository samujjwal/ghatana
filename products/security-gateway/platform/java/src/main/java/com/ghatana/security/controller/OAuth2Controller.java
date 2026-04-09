package com.ghatana.security.controller;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.ghatana.platform.security.oauth2.OAuth2Provider;
import com.ghatana.platform.security.oauth2.OidcSessionManager;
import com.ghatana.platform.security.oauth2.TokenIntrospector;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

/**
 * Handles OAuth2 authentication endpoints.

 *
 * @doc.type class
 * @doc.purpose Oauth2controller
 * @doc.layer core
 * @doc.pattern Controller
*/
public class OAuth2Controller extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(OAuth2Controller.class);
    private static final String SESSION_NONCE = "oauth2_nonce";
    private static final String SESSION_STATE = "oauth2_state";
    private static final String OAUTH_FLOW_COOKIE = "ghatana_oauth_flow";
    private static final Duration OAUTH_FLOW_TTL = Duration.ofMinutes(10);
    private static final int OAUTH_RATE_LIMIT_PER_MINUTE = 30;

    private final OAuth2Provider oauth2Provider;
    private final TokenIntrospector tokenIntrospector;
    private final OidcSessionManager sessionManager;
    private final ExecutorService executorService;
    private final RateLimiter oauthRateLimiter;
    private final Map<String, PendingOAuthFlow> pendingFlows;

    public OAuth2Controller(OAuth2Provider oauth2Provider,
                          TokenIntrospector tokenIntrospector,
                          OidcSessionManager sessionManager) {
        this.oauth2Provider = oauth2Provider;
        this.tokenIntrospector = tokenIntrospector;
        this.sessionManager = sessionManager;
        this.executorService = Executors.newCachedThreadPool();
        this.oauthRateLimiter = DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(OAUTH_RATE_LIMIT_PER_MINUTE)
                .burstSize(OAUTH_RATE_LIMIT_PER_MINUTE)
                .windowDuration(Duration.ofMinutes(1))
                .build()
        );
        this.pendingFlows = new ConcurrentHashMap<>();
    }

    @Provides
    public RoutingServlet servlet(Reactor reactor) {
        return RoutingServlet.builder(reactor)
            .with(GET, "/oauth2/authorize", this::handleAuthorize)
            .with(GET, "/oauth2/callback", this::handleCallback)
            .with(POST, "/oauth2/refresh", this::handleRefresh)
            .with(GET, "/oauth2/introspect", this::handleIntrospect)
            .with(POST, "/oauth2/introspect", this::handleIntrospect)
            .with(POST, "/oauth2/revoke", this::handleRevoke)
            .with(POST, "/oauth2/logout", this::handleLogout)
            .build();
    }

    private Promise<HttpResponse> handleAuthorize(HttpRequest request) {
        return Promise.ofBlocking(executorService, () -> {
            try {
                HttpResponse throttled = rateLimitResponseIfNeeded(request, "authorize");
                if (throttled != null) {
                    return throttled;
                }

                String redirectUri = buildRedirectUri(request);
                String nonce = UUID.randomUUID().toString();
                OAuth2Provider.AuthResponse authResponse = oauth2Provider.generateAuthorizationUrl(redirectUri, nonce);
                String flowId = UUID.randomUUID().toString();
                pendingFlows.put(flowId, new PendingOAuthFlow(
                    authResponse.getState(),
                    authResponse.getNonce(),
                    Instant.now().plus(OAUTH_FLOW_TTL)
                ));

                request.attach(SESSION_STATE, authResponse.getState());
                request.attach(SESSION_NONCE, authResponse.getNonce());

                HttpResponse response = HttpResponse.redirect302(authResponse.getAuthorizationUrl())
                    .withHeader(HttpHeaders.SET_COOKIE, HttpHeaderValue.of(buildFlowCookie(flowId, isSecureRequest(request), false)))
                    .build();
                return response;
            } catch (Exception e) {
                logger.error("Authorization request failed", e);
                HttpResponse response = HttpResponse.ofCode(500)
                    .withJson("{\"success\":false,\"error\":\"Failed to initiate OAuth2 flow\"}").build();
                return response;
            }
        });
    }

    private String buildRedirectUri(HttpRequest request) {
        String host = request.getHeader(HttpHeaders.HOST);
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Missing Host header");
        }
        String scheme = host.contains("localhost") ? "http" : "https";
        return scheme + "://" + host + "/oauth2/callback";
    }

    private Promise<HttpResponse> handleCallback(HttpRequest request) {
        return Promise.ofBlocking(executorService, () -> {
            try {
                HttpResponse throttled = rateLimitResponseIfNeeded(request, "callback");
                if (throttled != null) {
                    return throttled;
                }

                String code = request.getQueryParameter("code");
                String state = request.getQueryParameter("state");
                if (code == null || code.isBlank() || state == null || state.isBlank()) {
                    HttpResponse response = HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Missing required parameters\"}").build();
                    return response;
                }

                String flowId = extractCookie(request, OAUTH_FLOW_COOKIE);
                if (flowId == null || flowId.isBlank()) {
                    return HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Missing OAuth flow state\"}")
                        .build();
                }

                PendingOAuthFlow pendingFlow = pendingFlows.remove(flowId);
                if (pendingFlow == null || pendingFlow.isExpired()) {
                    return HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Expired OAuth flow\"}")
                        .withHeader(HttpHeaders.SET_COOKIE, HttpHeaderValue.of(buildFlowCookie("expired", isSecureRequest(request), true)))
                        .build();
                }

                String storedState = request.getAttachment(SESSION_STATE);
                String expectedState = storedState != null ? storedState : pendingFlow.state();
                if (!timingSafeEquals(expectedState, state)) {
                    HttpResponse response = HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Invalid state parameter\"}")
                        .withHeader(HttpHeaders.SET_COOKIE, HttpHeaderValue.of(buildFlowCookie("invalid", isSecureRequest(request), true)))
                        .build();
                    return response;
                }

                HttpResponse response = HttpResponse.ofCode(200)
                    .withJson("{\"success\":true,\"code\":\"" + code + "\"}")
                    .withHeader(HttpHeaders.SET_COOKIE, HttpHeaderValue.of(buildFlowCookie(flowId, isSecureRequest(request), true)))
                    .build();
                return response;
            } catch (Exception e) {
                logger.error("Callback handling failed", e);
                HttpResponse response = HttpResponse.ofCode(500)
                    .withJson("{\"success\":false,\"error\":\"Callback handling failed\"}").build();
                return response;
            }
        });
    }

    private Promise<HttpResponse> handleRefresh(HttpRequest request) {
        return Promise.ofBlocking(executorService, () -> {
            try {
                HttpResponse throttled = rateLimitResponseIfNeeded(request, "refresh");
                if (throttled != null) {
                    return throttled;
                }
                String refreshToken = request.getPostParameters().get("refresh_token");
                if (refreshToken == null || refreshToken.isBlank()) {
                    HttpResponse response = HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Missing refresh_token\"}").build();
                    return response;
                }
                HttpResponse response = ResponseBuilder.ok()
                    .json("{\"success\":true,\"token\":\"new_access_token\"}").build();
                return response;
            } catch (Exception e) {
                logger.error("Token refresh failed", e);
                HttpResponse response = HttpResponse.ofCode(500)
                    .withJson("{\"success\":false,\"error\":\"Token refresh failed\"}").build();
                return response;
            }
        });
    }

    private Promise<HttpResponse> handleIntrospect(HttpRequest request) {
        return Promise.ofBlocking(executorService, () -> {
            try {
                HttpResponse throttled = rateLimitResponseIfNeeded(request, "introspect");
                if (throttled != null) {
                    return throttled;
                }
                String token = request.getPostParameters().get("token");
                if (token == null || token.isBlank()) {
                    HttpResponse response = HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Missing token\"}").build();
                    return response;
                }
                HttpResponse response = ResponseBuilder.ok()
                    .json("{\"success\":true,\"active\":true}").build();
                return response;
            } catch (Exception e) {
                logger.error("Token introspection failed", e);
                HttpResponse response = HttpResponse.ofCode(500)
                    .withJson("{\"success\":false,\"error\":\"Token introspection failed\"}").build();
                return response;
            }
        });
    }

    private Promise<HttpResponse> handleRevoke(HttpRequest request) {
        return Promise.ofBlocking(executorService, () -> {
            try {
                HttpResponse throttled = rateLimitResponseIfNeeded(request, "revoke");
                if (throttled != null) {
                    return throttled;
                }
                String token = request.getPostParameters().get("token");
                if (token == null || token.isBlank()) {
                    HttpResponse response = HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Missing token\"}").build();
                    return response;
                }
                HttpResponse response = ResponseBuilder.ok()
                    .json("{\"success\":true}").build();
                return response;
            } catch (Exception e) {
                logger.error("Token revocation failed", e);
                HttpResponse response = HttpResponse.ofCode(500)
                    .withJson("{\"success\":false,\"error\":\"Token revocation failed\"}").build();
                return response;
            }
        });
    }

    private Promise<HttpResponse> handleLogout(HttpRequest request) {
        return Promise.ofBlocking(executorService, () -> {
            try {
                HttpResponse throttled = rateLimitResponseIfNeeded(request, "logout");
                if (throttled != null) {
                    return throttled;
                }
                String sessionId = request.getHeader(HttpHeaders.of("X-Session-Id"));
                if (sessionId == null || sessionId.isBlank()) {
                    HttpResponse response = HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Missing session ID\"}").build();
                    return response;
                }
                HttpResponse response = ResponseBuilder.ok()
                    .json("{\"success\":true}").build();
                return response;
            } catch (Exception e) {
                logger.error("Logout failed", e);
                HttpResponse response = HttpResponse.ofCode(500)
                    .withJson("{\"success\":false,\"error\":\"Logout failed\"}").build();
                return response;
            }
        });
    }

    /**
     * Parses form data from the request body.
     *
     * @param formData The form data string (e.g., "key1=value1&key2=value2")
     * @return A map of parameter names to their values
     */
    private Map<String, String> parseFormData(String formData) {
        Map<String, String> params = new HashMap<>();
        if (formData == null || formData.isEmpty()) {
            return params;
        }

        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) : pair;
                String value = idx > 0 && pair.length() > idx + 1 ?
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
                if (!key.isEmpty()) {
                    params.put(key, value);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse form data pair: " + pair, e);
            }
        }
        return params;
    }

    private HttpResponse rateLimitResponseIfNeeded(HttpRequest request, String action) {
        pruneExpiredFlows();
        String rateLimitKey = resolveRateLimitKey(request, action);
        RateLimiter.AcquireResult acquireResult = oauthRateLimiter.tryAcquire(rateLimitKey);
        if (acquireResult.allowed()) {
            return null;
        }
        return HttpResponse.ofCode(429)
            .withHeader(HttpHeaders.of("Retry-After"), String.valueOf(acquireResult.retryAfterSeconds()))
            .withJson("{\"success\":false,\"error\":\"Too many OAuth requests\"}")
            .build();
    }

    private String resolveRateLimitKey(HttpRequest request, String action) {
        String clientAddress = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (clientAddress == null || clientAddress.isBlank()) {
            clientAddress = request.getHeader(HttpHeaders.of("X-Real-IP"));
        }
        if (clientAddress == null || clientAddress.isBlank()) {
            clientAddress = request.getHeader(HttpHeaders.HOST);
        }
        if (clientAddress == null || clientAddress.isBlank()) {
            clientAddress = "unknown-client";
        }
        int commaIndex = clientAddress.indexOf(',');
        String normalizedClient = commaIndex > 0 ? clientAddress.substring(0, commaIndex).trim() : clientAddress.trim();
        return action + ":" + normalizedClient;
    }

    private void pruneExpiredFlows() {
        Instant now = Instant.now();
        pendingFlows.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private boolean isSecureRequest(HttpRequest request) {
        String host = request.getHeader(HttpHeaders.HOST);
        return host != null && !host.contains("localhost");
    }

    private String buildFlowCookie(String flowId, boolean secure, boolean expireImmediately) {
        String value = expireImmediately ? "" : flowId;
        StringBuilder cookie = new StringBuilder();
        cookie.append(OAUTH_FLOW_COOKIE).append("=").append(value).append("; Path=/; HttpOnly; SameSite=Lax");
        if (secure) {
            cookie.append("; Secure");
        }
        if (expireImmediately) {
            cookie.append("; Max-Age=0");
        } else {
            cookie.append("; Max-Age=").append(OAUTH_FLOW_TTL.getSeconds());
        }
        return cookie.toString();
    }

    private String extractCookie(HttpRequest request, String cookieName) {
        String cookieHeader = request.getHeader(HttpHeaders.COOKIE);
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String[] nameValue = cookie.trim().split("=", 2);
            if (nameValue.length == 2 && Objects.equals(nameValue[0], cookieName)) {
                return nameValue[1];
            }
        }
        return null;
    }

    private boolean timingSafeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < leftBytes.length; index++) {
            result |= leftBytes[index] ^ rightBytes[index];
        }
        return result == 0;
    }

    private record PendingOAuthFlow(String state, String nonce, Instant expiresAt) {
        private boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }
    }
}
