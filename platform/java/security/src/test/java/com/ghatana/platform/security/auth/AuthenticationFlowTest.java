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
@DisplayName("Authentication Flow Tests [GH-90000]")
class AuthenticationFlowTest {

    @Test
    @DisplayName("Should validate JWT tokens correctly [GH-90000]")
    void shouldValidateJwtTokensCorrectly() { // GH-90000
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class); // GH-90000
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider); // GH-90000

        when(mockTokenProvider.validateToken(anyString())).thenReturn(true); // GH-90000
        when(mockTokenProvider.getUserIdFromToken(anyString())).thenReturn(Optional.of("user123 [GH-90000]"));
        when(mockTokenProvider.getRolesFromToken(anyString())).thenReturn(List.of("USER [GH-90000]"));

        TokenCredentials credentials = new TokenCredentials("valid-token [GH-90000]");
        Promise<Optional<User>> result = provider.authenticate(credentials); // GH-90000

        assertThat(result.getResult()).isPresent(); // GH-90000
        assertThat(result.getResult().get().getUserId()).isEqualTo("user123 [GH-90000]");
    }

    @Test
    @DisplayName("Should handle token refresh correctly [GH-90000]")
    void shouldHandleTokenRefreshCorrectly() { // GH-90000
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class); // GH-90000
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider); // GH-90000

        when(mockTokenProvider.getUserIdFromToken(anyString())).thenReturn(Optional.of("user123 [GH-90000]"));
        when(mockTokenProvider.getRolesFromToken(anyString())).thenReturn(List.of("USER [GH-90000]"));
        when(mockTokenProvider.createToken(anyString(), anyList(), any())).thenReturn("new-token [GH-90000]");

        String newToken = provider.refreshToken("old-token [GH-90000]");

        assertThat(newToken).isEqualTo("new-token [GH-90000]");
    }

    @Test
    @DisplayName("Should propagate security context correctly [GH-90000]")
    void shouldPropagateSecurityContextCorrectly() { // GH-90000
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class); // GH-90000
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider); // GH-90000

        when(mockTokenProvider.validateToken(anyString())).thenReturn(true); // GH-90000
        when(mockTokenProvider.getUserIdFromToken(anyString())).thenReturn(Optional.of("user123 [GH-90000]"));
        when(mockTokenProvider.getRolesFromToken(anyString())).thenReturn(List.of("ADMIN [GH-90000]"));

        TokenCredentials credentials = new TokenCredentials("valid-token [GH-90000]");
        Promise<Optional<User>> result = provider.authenticate(credentials); // GH-90000

        assertThat(result.getResult()).isPresent(); // GH-90000
        assertThat(result.getResult().get().getRoles()).contains("ADMIN [GH-90000]");
    }

    @Test
    @DisplayName("Should reject expired tokens [GH-90000]")
    void shouldRejectExpiredTokens() { // GH-90000
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class); // GH-90000
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider); // GH-90000

        when(mockTokenProvider.validateToken(anyString())).thenReturn(false); // GH-90000

        TokenCredentials credentials = new TokenCredentials("expired-token [GH-90000]");
        Promise<Optional<User>> result = provider.authenticate(credentials); // GH-90000

        assertThat(result.getResult()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should reject invalid signatures [GH-90000]")
    void shouldRejectInvalidSignatures() { // GH-90000
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class); // GH-90000
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider); // GH-90000

        when(mockTokenProvider.validateToken(anyString())).thenReturn(false); // GH-90000

        TokenCredentials credentials = new TokenCredentials("invalid-signature [GH-90000]");
        Promise<Optional<User>> result = provider.authenticate(credentials); // GH-90000

        assertThat(result.getResult()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should handle token revocation [GH-90000]")
    void shouldHandleTokenRevocation() { // GH-90000
        JwtTokenProvider mockTokenProvider = mock(JwtTokenProvider.class); // GH-90000
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(mockTokenProvider); // GH-90000

        when(mockTokenProvider.validateToken(anyString())).thenReturn(false); // GH-90000

        TokenCredentials credentials = new TokenCredentials("revoked-token [GH-90000]");
        Promise<Optional<User>> result = provider.authenticate(credentials); // GH-90000

        assertThat(result.getResult()).isEmpty(); // GH-90000
    }
}
