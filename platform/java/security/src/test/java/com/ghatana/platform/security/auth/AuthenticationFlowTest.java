/**
 * @doc.type class
 * @doc.purpose JWT validation, token refresh, and security context propagation tests
 * @doc.layer platform
 * @doc.pattern Test
 */
package com.ghatana.platform.security.auth;

import com.ghatana.platform.security.auth.impl.JwtAuthenticationProvider;
import com.ghatana.platform.security.auth.impl.TokenCredentials;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Authentication Flow Tests
 *
 * JWT validation, token refresh, and security context propagation tests.
 */
@DisplayName("Authentication Flow Tests")
class AuthenticationFlowTest {

    @Test
    @DisplayName("Should validate JWT tokens correctly")
    void shouldValidateJwtTokensCorrectly() {
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider);

        when(mockTokenProvider.validateToken(anyString())).thenReturn(true);
        when(mockTokenProvider.getUserIdFromToken(anyString())).thenReturn(Optional.of("user123"));
        when(mockTokenProvider.getRolesFromToken(anyString())).thenReturn(List.of("USER"));

        TokenCredentials credentials = new TokenCredentials("valid-token");
        Promise<Optional<User>> result = provider.authenticate(credentials);

        assertThat(result.getResult()).isPresent();
        assertThat(result.getResult().get().getUserId()).isEqualTo("user123");
    }

    @Test
    @DisplayName("Should handle token refresh correctly")
    void shouldHandleTokenRefreshCorrectly() {
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider);

        when(mockTokenProvider.getUserIdFromToken(anyString())).thenReturn(Optional.of("user123"));
        when(mockTokenProvider.getRolesFromToken(anyString())).thenReturn(List.of("USER"));
        when(mockTokenProvider.createToken(anyString(), anyList(), any())).thenReturn("new-token");

        String newToken = provider.refreshToken("old-token");

        assertThat(newToken).isEqualTo("new-token");
    }

    @Test
    @DisplayName("Should propagate security context correctly")
    void shouldPropagateSecurityContextCorrectly() {
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider);

        when(mockTokenProvider.validateToken(anyString())).thenReturn(true);
        when(mockTokenProvider.getUserIdFromToken(anyString())).thenReturn(Optional.of("user123"));
        when(mockTokenProvider.getRolesFromToken(anyString())).thenReturn(List.of("ADMIN"));

        TokenCredentials credentials = new TokenCredentials("valid-token");
        Promise<Optional<User>> result = provider.authenticate(credentials);

        assertThat(result.getResult()).isPresent();
        assertThat(result.getResult().get().getRoles()).contains("ADMIN");
    }

    @Test
    @DisplayName("Should reject expired tokens")
    void shouldRejectExpiredTokens() {
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider);

        when(mockTokenProvider.validateToken(anyString())).thenReturn(false);

        TokenCredentials credentials = new TokenCredentials("expired-token");
        Promise<Optional<User>> result = provider.authenticate(credentials);

        assertThat(result.getResult()).isEmpty();
    }

    @Test
    @DisplayName("Should reject invalid signatures")
    void shouldRejectInvalidSignatures() {
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider);

        when(mockTokenProvider.validateToken(anyString())).thenReturn(false);

        TokenCredentials credentials = new TokenCredentials("invalid-signature");
        Promise<Optional<User>> result = provider.authenticate(credentials);

        assertThat(result.getResult()).isEmpty();
    }

    @Test
    @DisplayName("Should handle token revocation")
    void shouldHandleTokenRevocation() {
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class);
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider);

        when(mockTokenProvider.validateToken(anyString())).thenReturn(false);

        TokenCredentials credentials = new TokenCredentials("revoked-token");
        Promise<Optional<User>> result = provider.authenticate(credentials);

        assertThat(result.getResult()).isEmpty();
    }
}
