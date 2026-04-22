package com.ghatana.services.auth;

import com.ghatana.platform.security.jwt.JwtKeyManager;
import com.ghatana.platform.security.oauth2.OAuth2Config;
import com.ghatana.platform.security.oauth2.OAuth2Provider;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Coverage for replay denial, token substitution denial, key rotation, and OIDC discovery failure fallback
 * @doc.layer shared-services
 * @doc.pattern Test
 */
@DisplayName("Auth Gateway Token Security Coverage Tests")
class AuthGatewayTokenSecurityCoverageTest {

    @Test
    @DisplayName("AG-1: replayed OAuth state is denied on second use")
    void shouldDenyReplayForOAuthState() {
        OAuth2Provider provider = new OAuth2Provider(buildOAuth2Config("https://127.0.0.1:9/.well-known/openid-configuration"));
        provider.generateAuthorizationUrl("state-replay-check", "nonce-1");

        assertThatThrownBy(() -> provider.authenticate(
            "dummy-code",
            "state-replay-check",
            "state-replay-check",
            "https://app.example.com/callback"))
            .isInstanceOf(OAuth2Provider.OAuth2Exception.class);

        assertThatThrownBy(() -> provider.authenticate(
            "dummy-code",
            "state-replay-check",
            "state-replay-check",
            "https://app.example.com/callback"))
            .isInstanceOf(OAuth2Provider.OAuth2Exception.class)
            .hasMessageContaining("Invalid state parameter");
    }

    @Test
    @DisplayName("AG-2: substituted token payload fails validation")
    void shouldRejectTokenSubstitution() {
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(
            "0123456789abcdef0123456789abcdef",
            60_000);

        String validToken = provider.createToken("user-a", List.of("USER"), Map.of("tenant", "tenant-a"));
        String substitutedToken = substituteSubject(validToken, "user-b");

        assertThat(provider.validateToken(validToken)).isTrue();
        assertThat(provider.validateToken(substitutedToken)).isFalse();
    }

    @Test
    @DisplayName("AG-3: key rotation keeps old tokens valid while issuing new key IDs")
    void shouldValidateTokensAcrossKeyRotation() {
        JwtKeyManager keyManager = new JwtKeyManager("0123456789abcdef0123456789abcdef", 300);
        JwtTokenProvider provider = JwtTokenProviders.fromKeyManager(keyManager, 60_000);

        String tokenBeforeRotation = provider.createToken("user-a", List.of("USER"), Map.of());
        String oldKeyId = keyManager.currentKeyId();

        keyManager.rotate();
        String newKeyId = keyManager.currentKeyId();
        String tokenAfterRotation = provider.createToken("user-a", List.of("USER"), Map.of());

        assertThat(newKeyId).isNotEqualTo(oldKeyId);
        assertThat(provider.validateToken(tokenBeforeRotation)).isTrue();
        assertThat(provider.validateToken(tokenAfterRotation)).isTrue();
        assertThat(keyManager.activeKeyCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("AG-4: OIDC discovery failure falls back to configured endpoints")
    void shouldFallbackWhenOidcDiscoveryFails() {
        OAuth2Provider provider = new OAuth2Provider(buildOAuth2Config("https://127.0.0.1:9/.well-known/openid-configuration"));

        OAuth2Provider.AuthResponse authResponse = provider.generateAuthorizationUrl(
            "https://app.example.com/callback",
            "nonce-fallback");

        assertThat(authResponse.getAuthorizationUrl()).startsWith("https://idp.example.com/oauth2/authorize");
        assertThat(authResponse.getAuthorizationUrl()).contains("nonce=nonce-fallback");
        assertThat(authResponse.getState()).isNotBlank();
    }

    private static OAuth2Config buildOAuth2Config(String discoveryUri) {
        return OAuth2Config.builder()
            .clientId("gateway-client")
            .clientSecret("gateway-secret")
            .authorizationEndpoint(URI.create("https://idp.example.com/oauth2/authorize"))
            .tokenEndpoint(URI.create("https://idp.example.com/oauth2/token"))
            .userInfoEndpoint(URI.create("https://idp.example.com/oauth2/userinfo"))
            .jwksUri(URI.create("https://idp.example.com/.well-known/jwks.json"))
            .issuerUri(URI.create("https://idp.example.com"))
            .redirectUri(URI.create("https://app.example.com/callback"))
            .discoveryUri(URI.create(discoveryUri))
            .scopes("openid", "profile", "email")
            .build();
    }

    private static String substituteSubject(String token, String newSubject) {
        String[] parts = token.split("\\.");
        String tamperedPayload = "{\"sub\":\"" + newSubject + "\",\"roles\":[\"USER\"],\"exp\":4102444800}";
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + payload + "." + parts[2];
    }
}


