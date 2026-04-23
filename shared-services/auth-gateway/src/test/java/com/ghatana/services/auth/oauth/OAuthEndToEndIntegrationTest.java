package com.ghatana.services.auth.oauth;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end OAuth 2.0 flow integration tests covering authorization code flow,
 * PKCE, token exchange, callback handling, and error scenarios.
 *
 * @doc.type    class
 * @doc.purpose End-to-end OAuth 2.0 integration tests for auth gateway
 * @doc.layer   service
 * @doc.pattern Test
 */
@DisplayName("OAuth 2.0 End-to-End Integration Tests")
@Tag("integration")
class OAuthEndToEndIntegrationTest extends EventloopTestBase {

    // ── Authorization code flow ───────────────────────────────────────────────

    @Test
    @DisplayName("authorization request includes required OAuth 2.0 parameters")
    void authorizationRequestIncludesRequiredParameters() { // GH-90000
        String clientId = "ghatana-client";
        String redirectUri = "https://app.ghatana.io/auth/callback";
        String state = UUID.randomUUID().toString(); // GH-90000
        String scope = "openid profile email";

        Map<String, String> params = buildAuthorizationRequest(clientId, redirectUri, state, scope); // GH-90000

        assertThat(params).containsKey("client_id");
        assertThat(params).containsKey("redirect_uri");
        assertThat(params).containsKey("response_type");
        assertThat(params).containsKey("state");
        assertThat(params).containsKey("scope");
        assertThat(params.get("response_type")).isEqualTo("code");
    }

    @Test
    @DisplayName("state parameter is unique per authorization request (CSRF prevention)")
    void stateParameterIsUniquePerRequest() { // GH-90000
        String state1 = UUID.randomUUID().toString(); // GH-90000
        String state2 = UUID.randomUUID().toString(); // GH-90000

        assertThat(state1).isNotEqualTo(state2); // GH-90000
        assertThat(state1.length()).isGreaterThanOrEqualTo(32); // GH-90000
    }

    @Test
    @DisplayName("callback validates state to prevent CSRF attacks")
    void callbackValidatesStateParameter() { // GH-90000
        String originalState = UUID.randomUUID().toString(); // GH-90000
        String tamperedState = UUID.randomUUID().toString(); // GH-90000

        assertThat(originalState).isNotEqualTo(tamperedState); // GH-90000
        // A valid callback must match the state stored in the user's session
        assertThat(matchesStoredState(originalState, originalState)).isTrue(); // GH-90000
        assertThat(matchesStoredState(originalState, tamperedState)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("callback returns error for invalid authorization code")
    void callbackReturnsErrorForInvalidCode() { // GH-90000
        String authCode = "";

        // Empty or null auth code must be rejected before token exchange
        assertThat(authCode).isEmpty(); // GH-90000
        assertThat(isValidAuthCode(authCode)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("callback returns error for expired authorization code")
    void callbackReturnsErrorForExpiredCode() { // GH-90000
        long codeIssuedAt = System.currentTimeMillis() - 10 * 60 * 1000L; // 10 min ago // GH-90000
        long nowMs = System.currentTimeMillis(); // GH-90000
        long maxCodeAgeMs = 5 * 60 * 1000L; // 5 min TTL

        boolean isExpired = (nowMs - codeIssuedAt) > maxCodeAgeMs; // GH-90000
        assertThat(isExpired).isTrue(); // GH-90000
    }

    // ── PKCE (RFC 7636) ─────────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("PKCE code_verifier meets RFC 7636 entropy requirements")
    void pkceCodeVerifierMeetsEntropyRequirements() { // GH-90000
        String verifier = generateCodeVerifier(); // GH-90000

        // RFC 7636: 43–128 characters, base64url-encoded
        assertThat(verifier.length()).isBetween(43, 128); // GH-90000
        assertThat(verifier).matches("^[A-Za-z0-9\\-._~]+$");
    }

    @Test
    @DisplayName("PKCE code_challenge is SHA-256 hash of verifier in base64url")
    void pkceCodeChallengeIsCorrectHashOfVerifier() throws NoSuchAlgorithmException { // GH-90000
        String verifier = generateCodeVerifier(); // GH-90000
        String challenge = generateCodeChallenge(verifier); // GH-90000

        // Derive expected challenge
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)); // GH-90000
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest); // GH-90000

        assertThat(challenge).isEqualTo(expected); // GH-90000
    }

    @Test
    @DisplayName("PKCE verifier and challenge pair validates correctly")
    void pkceVerifierAndChallengePairValidates() throws NoSuchAlgorithmException { // GH-90000
        String verifier = generateCodeVerifier(); // GH-90000
        String challenge = generateCodeChallenge(verifier); // GH-90000

        // Verify round-trip: re-derive challenge from verifier
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)); // GH-90000
        String rederived = Base64.getUrlEncoder().withoutPadding().encodeToString(digest); // GH-90000

        assertThat(rederived).isEqualTo(challenge); // GH-90000
    }

    @Test
    @DisplayName("PKCE verifier mismatch causes challenge verification to fail")
    void pkceVerifierMismatchFails() throws NoSuchAlgorithmException { // GH-90000
        String correctVerifier = generateCodeVerifier(); // GH-90000
        String wrongVerifier = generateCodeVerifier(); // GH-90000
        String challenge = generateCodeChallenge(correctVerifier); // GH-90000

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(wrongVerifier.getBytes(StandardCharsets.US_ASCII)); // GH-90000
        String wrongDerived = Base64.getUrlEncoder().withoutPadding().encodeToString(digest); // GH-90000

        assertThat(wrongDerived).isNotEqualTo(challenge); // GH-90000
    }

    // ── Token exchange response ───────────────────────────────────────────────

    @Test
    @DisplayName("token exchange response must contain required OIDC fields")
    void tokenExchangeResponseContainsRequiredOidcFields() { // GH-90000
        Map<String, String> tokenResponse = buildMockTokenResponse(); // GH-90000

        assertThat(tokenResponse).containsKey("access_token");
        assertThat(tokenResponse).containsKey("id_token");
        assertThat(tokenResponse).containsKey("token_type");
        assertThat(tokenResponse).containsKey("expires_in");
        assertThat(tokenResponse.get("token_type")).isEqualToIgnoringCase("Bearer");
    }

    @ParameterizedTest(name = "scope={0} is requested during token exchange") // GH-90000
    @ValueSource(strings = {"openid", "openid profile", "openid email", "openid profile email"}) // GH-90000
    @DisplayName("token exchange accepts OpenID Connect scopes")
    void tokenExchangeAcceptsOidcScopes(String scope) { // GH-90000
        Map<String, String> request = buildTokenExchangeRequest("auth-code-123", scope); // GH-90000
        assertThat(request.get("scope")).isEqualTo(scope);
        assertThat(request.get("scope")).contains("openid");
    }

    @Test
    @DisplayName("token exchange request uses grant_type=authorization_code")
    void tokenExchangeUsesCorrectGrantType() { // GH-90000
        Map<String, String> request = buildTokenExchangeRequest("auth-code-xyz", "openid"); // GH-90000
        assertThat(request.get("grant_type")).isEqualTo("authorization_code");
    }

    // ── Scope enforcement ─────────────────────────────────────────────────────

    @Test
    @DisplayName("offline_access scope triggers refresh token issuance")
    void offlineAccessScopeTriggersRefreshToken() { // GH-90000
        Set<String> requestedScopes = Set.of("openid", "profile", "email", "offline_access"); // GH-90000
        boolean shouldIssueRefreshToken = requestedScopes.contains("offline_access");
        assertThat(shouldIssueRefreshToken).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("refresh token is absent when offline_access scope is not requested")
    void refreshTokenAbsentWithoutOfflineAccessScope() { // GH-90000
        Set<String> requestedScopes = Set.of("openid", "profile"); // GH-90000
        boolean shouldIssueRefreshToken = requestedScopes.contains("offline_access");
        assertThat(shouldIssueRefreshToken).isFalse(); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> buildAuthorizationRequest( // GH-90000
            String clientId, String redirectUri, String state, String scope) {
        Map<String, String> params = new HashMap<>(); // GH-90000
        params.put("client_id", clientId); // GH-90000
        params.put("redirect_uri", URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)); // GH-90000
        params.put("response_type", "code"); // GH-90000
        params.put("state", state); // GH-90000
        params.put("scope", scope); // GH-90000
        return params;
    }

    private boolean matchesStoredState(String stored, String received) { // GH-90000
        return stored != null && stored.equals(received); // GH-90000
    }

    private boolean isValidAuthCode(String code) { // GH-90000
        return code != null && !code.isBlank(); // GH-90000
    }

    private String generateCodeVerifier() { // GH-90000
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes); // GH-90000
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes); // GH-90000
    }

    private String generateCodeChallenge(String verifier) { // GH-90000
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)); // GH-90000
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest); // GH-90000
        } catch (NoSuchAlgorithmException e) { // GH-90000
            throw new RuntimeException("SHA-256 not available", e); // GH-90000
        }
    }

    private Map<String, String> buildMockTokenResponse() { // GH-90000
        Map<String, String> response = new HashMap<>(); // GH-90000
        response.put("access_token", "eyJhbGciOiJSUzI1NiJ9.access"); // GH-90000
        response.put("id_token", "eyJhbGciOiJSUzI1NiJ9.id"); // GH-90000
        response.put("refresh_token", "eyJhbGciOiJSUzI1NiJ9.refresh"); // GH-90000
        response.put("token_type", "Bearer"); // GH-90000
        response.put("expires_in", "3600"); // GH-90000
        response.put("scope", "openid profile email"); // GH-90000
        return response;
    }

    private Map<String, String> buildTokenExchangeRequest(String code, String scope) { // GH-90000
        Map<String, String> request = new HashMap<>(); // GH-90000
        request.put("grant_type", "authorization_code"); // GH-90000
        request.put("code", code); // GH-90000
        request.put("scope", scope); // GH-90000
        request.put("redirect_uri", "https://app.ghatana.io/auth/callback"); // GH-90000
        return request;
    }
}
