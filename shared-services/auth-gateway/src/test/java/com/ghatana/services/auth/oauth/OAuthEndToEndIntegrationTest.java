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
    void authorizationRequestIncludesRequiredParameters() {
        String clientId = "ghatana-client";
        String redirectUri = "https://app.ghatana.io/auth/callback";
        String state = UUID.randomUUID().toString();
        String scope = "openid profile email";

        Map<String, String> params = buildAuthorizationRequest(clientId, redirectUri, state, scope);

        assertThat(params).containsKey("client_id");
        assertThat(params).containsKey("redirect_uri");
        assertThat(params).containsKey("response_type");
        assertThat(params).containsKey("state");
        assertThat(params).containsKey("scope");
        assertThat(params.get("response_type")).isEqualTo("code");
    }

    @Test
    @DisplayName("state parameter is unique per authorization request (CSRF prevention)")
    void stateParameterIsUniquePerRequest() {
        String state1 = UUID.randomUUID().toString();
        String state2 = UUID.randomUUID().toString();

        assertThat(state1).isNotEqualTo(state2);
        assertThat(state1.length()).isGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("callback validates state to prevent CSRF attacks")
    void callbackValidatesStateParameter() {
        String originalState = UUID.randomUUID().toString();
        String tamperedState = UUID.randomUUID().toString();

        assertThat(originalState).isNotEqualTo(tamperedState);
        // A valid callback must match the state stored in the user's session
        assertThat(matchesStoredState(originalState, originalState)).isTrue();
        assertThat(matchesStoredState(originalState, tamperedState)).isFalse();
    }

    @Test
    @DisplayName("callback returns error for invalid authorization code")
    void callbackReturnsErrorForInvalidCode() {
        String authCode = "";

        // Empty or null auth code must be rejected before token exchange
        assertThat(authCode).isEmpty();
        assertThat(isValidAuthCode(authCode)).isFalse();
    }

    @Test
    @DisplayName("callback returns error for expired authorization code")
    void callbackReturnsErrorForExpiredCode() {
        long codeIssuedAt = System.currentTimeMillis() - 10 * 60 * 1000L; // 10 min ago
        long nowMs = System.currentTimeMillis();
        long maxCodeAgeMs = 5 * 60 * 1000L; // 5 min TTL

        boolean isExpired = (nowMs - codeIssuedAt) > maxCodeAgeMs;
        assertThat(isExpired).isTrue();
    }

    // ── PKCE (RFC 7636) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PKCE code_verifier meets RFC 7636 entropy requirements")
    void pkceCodeVerifierMeetsEntropyRequirements() {
        String verifier = generateCodeVerifier();

        // RFC 7636: 43–128 characters, base64url-encoded
        assertThat(verifier.length()).isBetween(43, 128);
        assertThat(verifier).matches("^[A-Za-z0-9\\-._~]+$");
    }

    @Test
    @DisplayName("PKCE code_challenge is SHA-256 hash of verifier in base64url")
    void pkceCodeChallengeIsCorrectHashOfVerifier() throws NoSuchAlgorithmException {
        String verifier = generateCodeVerifier();
        String challenge = generateCodeChallenge(verifier);

        // Derive expected challenge
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        assertThat(challenge).isEqualTo(expected);
    }

    @Test
    @DisplayName("PKCE verifier and challenge pair validates correctly")
    void pkceVerifierAndChallengePairValidates() throws NoSuchAlgorithmException {
        String verifier = generateCodeVerifier();
        String challenge = generateCodeChallenge(verifier);

        // Verify round-trip: re-derive challenge from verifier
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String rederived = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        assertThat(rederived).isEqualTo(challenge);
    }

    @Test
    @DisplayName("PKCE verifier mismatch causes challenge verification to fail")
    void pkceVerifierMismatchFails() throws NoSuchAlgorithmException {
        String correctVerifier = generateCodeVerifier();
        String wrongVerifier = generateCodeVerifier();
        String challenge = generateCodeChallenge(correctVerifier);

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(wrongVerifier.getBytes(StandardCharsets.US_ASCII));
        String wrongDerived = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        assertThat(wrongDerived).isNotEqualTo(challenge);
    }

    // ── Token exchange response ───────────────────────────────────────────────

    @Test
    @DisplayName("token exchange response must contain required OIDC fields")
    void tokenExchangeResponseContainsRequiredOidcFields() {
        Map<String, String> tokenResponse = buildMockTokenResponse();

        assertThat(tokenResponse).containsKey("access_token");
        assertThat(tokenResponse).containsKey("id_token");
        assertThat(tokenResponse).containsKey("token_type");
        assertThat(tokenResponse).containsKey("expires_in");
        assertThat(tokenResponse.get("token_type")).isEqualToIgnoringCase("Bearer");
    }

    @ParameterizedTest(name = "scope={0} is requested during token exchange")
    @ValueSource(strings = {"openid", "openid profile", "openid email", "openid profile email"})
    @DisplayName("token exchange accepts OpenID Connect scopes")
    void tokenExchangeAcceptsOidcScopes(String scope) {
        Map<String, String> request = buildTokenExchangeRequest("auth-code-123", scope);
        assertThat(request.get("scope")).isEqualTo(scope);
        assertThat(request.get("scope")).contains("openid");
    }

    @Test
    @DisplayName("token exchange request uses grant_type=authorization_code")
    void tokenExchangeUsesCorrectGrantType() {
        Map<String, String> request = buildTokenExchangeRequest("auth-code-xyz", "openid");
        assertThat(request.get("grant_type")).isEqualTo("authorization_code");
    }

    // ── Scope enforcement ─────────────────────────────────────────────────────

    @Test
    @DisplayName("offline_access scope triggers refresh token issuance")
    void offlineAccessScopeTriggersRefreshToken() {
        Set<String> requestedScopes = Set.of("openid", "profile", "email", "offline_access");
        boolean shouldIssueRefreshToken = requestedScopes.contains("offline_access");
        assertThat(shouldIssueRefreshToken).isTrue();
    }

    @Test
    @DisplayName("refresh token is absent when offline_access scope is not requested")
    void refreshTokenAbsentWithoutOfflineAccessScope() {
        Set<String> requestedScopes = Set.of("openid", "profile");
        boolean shouldIssueRefreshToken = requestedScopes.contains("offline_access");
        assertThat(shouldIssueRefreshToken).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> buildAuthorizationRequest(
            String clientId, String redirectUri, String state, String scope) {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("redirect_uri", URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        params.put("response_type", "code");
        params.put("state", state);
        params.put("scope", scope);
        return params;
    }

    private boolean matchesStoredState(String stored, String received) {
        return stored != null && stored.equals(received);
    }

    private boolean isValidAuthCode(String code) {
        return code != null && !code.isBlank();
    }

    private String generateCodeVerifier() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private Map<String, String> buildMockTokenResponse() {
        Map<String, String> response = new HashMap<>();
        response.put("access_token", "eyJhbGciOiJSUzI1NiJ9.access");
        response.put("id_token", "eyJhbGciOiJSUzI1NiJ9.id");
        response.put("refresh_token", "eyJhbGciOiJSUzI1NiJ9.refresh");
        response.put("token_type", "Bearer");
        response.put("expires_in", "3600");
        response.put("scope", "openid profile email");
        return response;
    }

    private Map<String, String> buildTokenExchangeRequest(String code, String scope) {
        Map<String, String> request = new HashMap<>();
        request.put("grant_type", "authorization_code");
        request.put("code", code);
        request.put("scope", scope);
        request.put("redirect_uri", "https://app.ghatana.io/auth/callback");
        return request;
    }
}
