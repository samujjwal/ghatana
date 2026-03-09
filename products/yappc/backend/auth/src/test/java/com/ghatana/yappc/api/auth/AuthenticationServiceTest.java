/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — AuthenticationService Tests
 */
package com.ghatana.yappc.api.auth;

import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.yappc.api.auth.dto.UserProfile;
import com.ghatana.yappc.api.auth.model.User;
import com.ghatana.yappc.api.auth.repository.UserRepository;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthenticationService}.
 *
 * <p>Covers authentication, token refresh, logout, current-user retrieval,
 * password reset flow, and token revocation cleanup.
 
 * @doc.type class
 * @doc.purpose Handles authentication service test operations
 * @doc.layer product
 * @doc.pattern Test
*/
class AuthenticationServiceTest extends EventloopTestBase {

    private UserRepository userRepository;
    private JwtTokenProvider jwtTokenProvider;
    private AuthenticationService service;

    private static final String USERNAME = "testuser";
    private static final String EMAIL = "test@ghatana.com";
    private static final String RAW_PASSWORD = "SecureP@ss123";
    private static final String ACCESS_TOKEN = "access.jwt.token";
    private static final String REFRESH_TOKEN = "refresh.jwt.token";

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        service = new AuthenticationService(userRepository, jwtTokenProvider);

        // Default token creation returns stable tokens
        when(jwtTokenProvider.createToken(anyString(), anyList(), anyMap()))
                .thenReturn(ACCESS_TOKEN, REFRESH_TOKEN);
    }

    // =========================================================================
    // authenticate
    // =========================================================================

    @Nested
    class Authenticate {

        @Test
        void shouldAuthenticateValidUser() {
            User user = createActiveUser();
            when(userRepository.findByUsernameOrEmail(USERNAME))
                    .thenReturn(Promise.of(Optional.of(user)));
            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> Promise.of(inv.getArgument(0)));

            AuthenticationResult result = runPromise(() -> service.authenticate(USERNAME, RAW_PASSWORD));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAccessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(result.getTokenType()).isEqualTo("Bearer");
            assertThat(result.getExpiresIn()).isEqualTo(3600);
            assertThat(result.getUserProfile()).isNotNull();
            assertThat(result.getUserProfile().username()).isEqualTo(USERNAME);
            verify(userRepository).save(any(User.class));
        }

        @Test
        void shouldRejectUnknownUser() {
            when(userRepository.findByUsernameOrEmail(anyString()))
                    .thenReturn(Promise.of(Optional.empty()));

            AuthenticationResult result = runPromise(() -> service.authenticate("nobody", "pass"));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Invalid username or password");
        }

        @Test
        void shouldRejectWrongPassword() {
            User user = createActiveUser();
            // Give the user a hash that won't match "wrong-password"
            user.setPasswordHash("$2a$10$INVALIDHASH_THAT_WONT_MATCH");
            when(userRepository.findByUsernameOrEmail(USERNAME))
                    .thenReturn(Promise.of(Optional.of(user)));

            AuthenticationResult result = runPromise(() -> service.authenticate(USERNAME, "wrong-password"));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Invalid username or password");
        }

        @Test
        void shouldRejectDisabledAccount() {
            User user = createActiveUser();
            user.setActive(false);
            when(userRepository.findByUsernameOrEmail(USERNAME))
                    .thenReturn(Promise.of(Optional.of(user)));

            AuthenticationResult result = runPromise(() -> service.authenticate(USERNAME, RAW_PASSWORD));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Account is disabled");
        }
    }

    // =========================================================================
    // refreshToken
    // =========================================================================

    @Nested
    class RefreshToken {

        @Test
        void shouldRefreshValidToken() {
            User user = createActiveUser();
            when(jwtTokenProvider.validateToken(REFRESH_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(REFRESH_TOKEN))
                    .thenReturn(Optional.of(user.getId().toString()));
            when(userRepository.findById(user.getId()))
                    .thenReturn(Promise.of(Optional.of(user)));

            AuthenticationResult result = runPromise(() -> service.refreshToken(REFRESH_TOKEN));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAccessToken()).isNotNull();
        }

        @Test
        void shouldRejectInvalidRefreshToken() {
            when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);

            AuthenticationResult result = runPromise(() -> service.refreshToken("bad-token"));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Invalid token");
        }

        @Test
        void shouldRejectRefreshForDisabledUser() {
            User user = createActiveUser();
            user.setActive(false);
            when(jwtTokenProvider.validateToken(REFRESH_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(REFRESH_TOKEN))
                    .thenReturn(Optional.of(user.getId().toString()));
            when(userRepository.findById(user.getId()))
                    .thenReturn(Promise.of(Optional.of(user)));

            AuthenticationResult result = runPromise(() -> service.refreshToken(REFRESH_TOKEN));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Account is disabled");
        }
    }

    // =========================================================================
    // logout
    // =========================================================================

    @Nested
    class Logout {

        @Test
        void shouldLogoutWithValidToken() {
            when(jwtTokenProvider.validateToken(ACCESS_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(ACCESS_TOKEN))
                    .thenReturn(Optional.of("fixed-user-id-123"));

            Boolean result = runPromise(() -> service.logout(ACCESS_TOKEN));

            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseForInvalidLogoutToken() {
            when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);

            Boolean result = runPromise(() -> service.logout("invalid-token"));

            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // getCurrentUser
    // =========================================================================

    @Nested
    class GetCurrentUser {

        @Test
        void shouldReturnUserProfileForValidToken() {
            User user = createActiveUser();
            when(jwtTokenProvider.validateToken(ACCESS_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(ACCESS_TOKEN))
                    .thenReturn(Optional.of(user.getId().toString()));
            when(userRepository.findById(user.getId()))
                    .thenReturn(Promise.of(Optional.of(user)));

            Optional<UserProfile> result = runPromise(() -> service.getCurrentUser(ACCESS_TOKEN));

            assertThat(result).isPresent();
            assertThat(result.get().username()).isEqualTo(USERNAME);
            assertThat(result.get().email()).isEqualTo(EMAIL);
        }

        @Test
        void shouldReturnEmptyForInvalidToken() {
            when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);

            Optional<UserProfile> result = runPromise(() -> service.getCurrentUser("bad-token"));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // requestPasswordReset
    // =========================================================================

    @Nested
    class PasswordReset {

        @Test
        void shouldSetResetTokenForExistingUser() {
            User user = createActiveUser();
            when(userRepository.findByUsernameOrEmail(EMAIL))
                    .thenReturn(Promise.of(Optional.of(user)));
            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> Promise.of(inv.getArgument(0)));

            Boolean result = runPromise(() -> service.requestPasswordReset(EMAIL));

            assertThat(result).isTrue();
            verify(userRepository).save(argThat(u ->
                    u.getPasswordResetToken() != null &&
                    u.getPasswordResetExpiresAt() != null &&
                    u.getPasswordResetExpiresAt().isAfter(Instant.now())));
        }

        @Test
        void shouldReturnTrueForUnknownEmail() {
            when(userRepository.findByUsernameOrEmail(anyString()))
                    .thenReturn(Promise.of(Optional.empty()));

            Boolean result = runPromise(() -> service.requestPasswordReset("unknown@example.com"));

            assertThat(result).isTrue(); // Don't reveal user existence
            verify(userRepository, never()).save(any());
        }

        @Test
        void shouldConfirmPasswordResetWithValidToken() {
            User user = createActiveUser();
            user.setPasswordResetToken("reset-123");
            user.setPasswordResetExpiresAt(Instant.now().plusSeconds(3600));
            when(userRepository.findByPasswordResetToken("reset-123"))
                    .thenReturn(Promise.of(Optional.of(user)));
            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> Promise.of(inv.getArgument(0)));

            Boolean result = runPromise(() -> service.confirmPasswordReset("reset-123", "NewP@ss456"));

            assertThat(result).isTrue();
            verify(userRepository).save(argThat(u ->
                    u.getPasswordResetToken() == null &&
                    u.getPasswordResetExpiresAt() == null &&
                    u.getPasswordHash() != null));
        }

        @Test
        void shouldRejectExpiredResetToken() {
            User user = createActiveUser();
            user.setPasswordResetToken("expired-token");
            user.setPasswordResetExpiresAt(Instant.now().minusSeconds(60));
            when(userRepository.findByPasswordResetToken("expired-token"))
                    .thenReturn(Promise.of(Optional.of(user)));

            Boolean result = runPromise(() -> service.confirmPasswordReset("expired-token", "NewP@ss"));

            assertThat(result).isFalse();
            verify(userRepository, never()).save(any());
        }

        @Test
        void shouldRejectInvalidResetToken() {
            when(userRepository.findByPasswordResetToken(anyString()))
                    .thenReturn(Promise.of(Optional.empty()));

            Boolean result = runPromise(() -> service.confirmPasswordReset("invalid", "NewP@ss"));

            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // cleanupRevokedTokens
    // =========================================================================

    @Test
    void shouldCleanupExpiredRevokedTokens() {
        // Just verify it doesn't throw — internal state management
        assertThatNoException().isThrownBy(() -> service.cleanupRevokedTokens());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private User createActiveUser() {
        // Use PasswordHasher to generate a valid hash for the test password
        com.ghatana.platform.security.crypto.PasswordHasher hasher =
                new com.ghatana.platform.security.crypto.PasswordHasher();
        User user = new User(USERNAME, EMAIL, hasher.hash(RAW_PASSWORD),
                Set.of("ROLE_USER", "ROLE_DEVELOPER"));
        user.setActive(true);
        return user;
    }
}
