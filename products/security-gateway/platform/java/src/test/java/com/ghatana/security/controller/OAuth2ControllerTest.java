package com.ghatana.security.controller;

import com.ghatana.platform.security.oauth2.OAuth2Provider;
import com.ghatana.platform.security.oauth2.OidcSessionManager;
import com.ghatana.platform.security.oauth2.TokenIntrospector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for OAuth2 state handling and callback hardening.
 *
 * @doc.type class
 * @doc.purpose Verify OAuth authorize/callback flow state storage and validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("OAuth2Controller")
class OAuth2ControllerTest extends EventloopTestBase {

    private OAuth2Provider oauth2Provider;
    private RoutingServlet servlet;

    @BeforeEach
    void setUp() {
        oauth2Provider = mock(OAuth2Provider.class);
        TokenIntrospector tokenIntrospector = mock(TokenIntrospector.class);
        OidcSessionManager sessionManager = new OidcSessionManager(Duration.ofMinutes(30));

        when(oauth2Provider.generateAuthorizationUrl(anyString(), anyString()))
            .thenReturn(new OAuth2Provider.AuthResponse(
                "https://identity.example.com/authorize?state=expected-state",
                "expected-state",
                "expected-nonce"
            ));

        OAuth2Controller controller = new OAuth2Controller(oauth2Provider, tokenIntrospector, sessionManager);
        servlet = controller.servlet(eventloop());
    }

    @Test
    @DisplayName("authorize issues redirect and flow cookie")
    void authorizeIssuesRedirectAndFlowCookie() {
        HttpRequest request = HttpRequest.get("http://localhost/oauth2/authorize")
            .withHeader(HttpHeaders.HOST, "localhost:8080")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION))
            .contains("https://identity.example.com/authorize");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
            .contains("ghatana_oauth_flow=")
            .contains("HttpOnly")
            .contains("SameSite=Lax");
    }

    @Test
    @DisplayName("callback accepts matching flow cookie and state")
    void callbackAcceptsMatchingFlowCookieAndState() {
        HttpRequest authorizeRequest = HttpRequest.get("http://localhost/oauth2/authorize")
            .withHeader(HttpHeaders.HOST, "localhost:8080")
            .build();
        HttpResponse authorizeResponse = runPromise(() -> servlet.serve(authorizeRequest));
        String cookieHeader = authorizeResponse.getHeader(HttpHeaders.SET_COOKIE);
        String cookieValue = cookieHeader.substring(0, cookieHeader.indexOf(';'));

        HttpRequest callbackRequest = HttpRequest.get("http://localhost/oauth2/callback?code=abc123&state=expected-state")
            .withHeader(HttpHeaders.HOST, "localhost:8080")
            .withHeader(HttpHeaders.COOKIE, cookieValue)
            .build();

        HttpResponse callbackResponse = runPromise(() -> servlet.serve(callbackRequest));

        assertThat(callbackResponse.getCode()).isEqualTo(200);
        assertThat(callbackResponse.getBody().getString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("\"success\":true");
        assertThat(callbackResponse.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("callback without flow cookie is rejected")
    void callbackWithoutFlowCookieIsRejected() {
        HttpRequest callbackRequest = HttpRequest.get("http://localhost/oauth2/callback?code=abc123&state=expected-state")
            .withHeader(HttpHeaders.HOST, "localhost:8080")
            .build();

        HttpResponse callbackResponse = runPromise(() -> servlet.serve(callbackRequest));

        assertThat(callbackResponse.getCode()).isEqualTo(400);
        assertThat(callbackResponse.getBody().getString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("Missing OAuth flow state");
    }
}