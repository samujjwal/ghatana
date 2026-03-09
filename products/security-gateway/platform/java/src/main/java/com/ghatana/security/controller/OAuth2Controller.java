package com.ghatana.security.controller;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.security.oauth2.OAuth2Provider;
import com.ghatana.platform.security.oauth2.OidcSessionManager;
import com.ghatana.platform.security.oauth2.TokenIntrospector;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    
    private final OAuth2Provider oauth2Provider;
    private final TokenIntrospector tokenIntrospector;
    private final OidcSessionManager sessionManager;
    private final ExecutorService executorService;
    
    public OAuth2Controller(OAuth2Provider oauth2Provider, 
                          TokenIntrospector tokenIntrospector,
                          OidcSessionManager sessionManager) {
        this.oauth2Provider = oauth2Provider;
        this.tokenIntrospector = tokenIntrospector;
        this.sessionManager = sessionManager;
        this.executorService = Executors.newCachedThreadPool();
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
                String redirectUri = buildRedirectUri(request);
                String nonce = UUID.randomUUID().toString();
                OAuth2Provider.AuthResponse authResponse = oauth2Provider.generateAuthorizationUrl(redirectUri, nonce);
                
                request.attach(SESSION_STATE, authResponse.getState());
                request.attach(SESSION_NONCE, authResponse.getNonce());
                
                HttpResponse response = HttpResponse.redirect302(authResponse.getAuthorizationUrl()).build();
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
        String scheme = request.getHeader(HttpHeaders.HOST).contains("localhost") ? "http" : "https";
        return scheme + "://" + request.getHeader(HttpHeaders.HOST) + "/oauth2/callback";
    }
    
    private Promise<HttpResponse> handleCallback(HttpRequest request) {
        return Promise.ofBlocking(executorService, () -> {
            try {
                String code = request.getQueryParameter("code");
                String state = request.getQueryParameter("state");
                if (code == null || state == null) {
                    HttpResponse response = HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Missing required parameters\"}").build();
                    return response;
                }
                
                String storedState = request.getAttachment(SESSION_STATE);
                if (storedState == null || !storedState.equals(state)) {
                    HttpResponse response = HttpResponse.ofCode(400)
                        .withJson("{\"success\":false,\"error\":\"Invalid state parameter\"}").build();
                    return response;
                }
                
                HttpResponse response = ResponseBuilder.ok()
                    .json("{\"success\":true,\"code\":\"" + code + "\"}").build();
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
}
