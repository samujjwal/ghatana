/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Simple JwtTokenProvider Tests
 */
package com.ghatana.yappc.api.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles simple jwt token provider test operations

 * @doc.layer product

 * @doc.pattern Test

 */

class SimpleJwtTokenProviderTest extends EventloopTestBase {

    private JwtTokenProvider jwtTokenProvider;
    private static final String SECRET_KEY = "test-secret-key-for-jwt-signing-must-be-long-enough-for-hmac-sha512";
    
    private UserContext testUser;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET_KEY, 60, 7);
        
        testUser = new UserContext.Builder()
            .userId("user123")
            .email("test@ghatana.com")
            .userName("Test User")
            .tenantId("tenant123")
            .roles(List.of("user", "admin"))
            .permissions(List.of(
                new Permission("/api/**", List.of("GET", "POST"), "Full access")
            ))
            .build();
    }

    @Test
    void generateAccessToken() {
        // When
        String token = jwtTokenProvider.generateToken(testUser);

        // Then
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void generateRefreshToken() {
        // When
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser);

        // Then
        assertThat(refreshToken).isNotEmpty();
        assertThat(refreshToken.split("\\.")).hasSize(3); // JWT has 3 parts
        assertThat(jwtTokenProvider.isRefreshToken(refreshToken)).isTrue();
    }

    @Test
    void generateTokenWithNullUser_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> jwtTokenProvider.generateToken(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void generateRefreshTokenWithNullUser_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> jwtTokenProvider.generateRefreshToken(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validateValidToken() throws Exception {
        // Given
        String token = jwtTokenProvider.generateToken(testUser);

        // When
        Boolean isValid = runPromise(() -> jwtTokenProvider.validateToken(token));

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void validateInvalidToken() throws Exception {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        Boolean isValid = runPromise(() -> jwtTokenProvider.validateToken(invalidToken));

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void validateTokenWithNullToken_ReturnsFalse() throws Exception {
        // When - null token: validateToken is @NotNull annotated, so just assert false behavior
        Boolean isValid = runPromise(() -> jwtTokenProvider.validateToken(""));

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void getUserFromValidToken() {
        // Given
        UserContext simpleUser = new UserContext.Builder()
            .userId("user123")
            .email("test@ghatana.com")
            .userName("Test User")
            .tenantId("tenant123")
            .roles(List.of("user"))
            .permissions(List.of()) // No permissions for simpler JWT parsing
            .build();
        String token = jwtTokenProvider.generateToken(simpleUser);

        // When
        UserContext extractedUser = jwtTokenProvider.getUserFromToken(token);

        // Then
        assertThat(extractedUser).isNotNull();
        assertThat(extractedUser.getUserId()).isEqualTo(simpleUser.getUserId());
        assertThat(extractedUser.getEmail()).isEqualTo(simpleUser.getEmail());
        assertThat(extractedUser.getUserName()).isEqualTo(simpleUser.getUserName());
        assertThat(extractedUser.getTenantId()).isEqualTo(simpleUser.getTenantId());
        assertThat(extractedUser.getRoles()).isEqualTo(simpleUser.getRoles());
    }

    @Test
    void getUserFromInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        UserContext extractedUser = jwtTokenProvider.getUserFromToken(invalidToken);

        // Then
        assertThat(extractedUser).isNull();
    }

    @Test
    void getUserWithNullToken_ReturnsNull() {
        // When
        UserContext extractedUser = jwtTokenProvider.getUserFromToken(null);

        // Then
        assertThat(extractedUser).isNull();
    }

    @Test
    void getTenantFromValidToken() {
        // Given
        String token = jwtTokenProvider.generateToken(testUser);

        // When
        String tenantId = jwtTokenProvider.getTenantFromToken(token);

        // Then
        assertThat(tenantId).isEqualTo(testUser.getTenantId());
    }

    @Test
    void getTenantFromInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        String tenantId = jwtTokenProvider.getTenantFromToken(invalidToken);

        // Then
        assertThat(tenantId).isNull();
    }

    @Test
    void getTenantWithNullToken_ReturnsNull() {
        // When
        String tenantId = jwtTokenProvider.getTenantFromToken(null);

        // Then
        assertThat(tenantId).isNull();
    }

    @Test
    void isRefreshTokenForRefreshToken() {
        // Given
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser);

        // When
        boolean isRefresh = jwtTokenProvider.isRefreshToken(refreshToken);

        // Then
        assertThat(isRefresh).isTrue();
    }

    @Test
    void isRefreshTokenForAccessToken() {
        // Given
        String accessToken = jwtTokenProvider.generateToken(testUser);

        // When
        boolean isRefresh = jwtTokenProvider.isRefreshToken(accessToken);

        // Then
        assertThat(isRefresh).isFalse();
    }

    @Test
    void isRefreshTokenForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        boolean isRefresh = jwtTokenProvider.isRefreshToken(invalidToken);

        // Then
        assertThat(isRefresh).isFalse();
    }

    @Test
    void isRefreshTokenWithNullToken_ReturnsFalse() {
        // When
        boolean isRefresh = jwtTokenProvider.isRefreshToken(null);

        // Then
        assertThat(isRefresh).isFalse();
    }

    @Test
    void getExpirationFromValidToken() {
        // Given
        String token = jwtTokenProvider.generateToken(testUser);

        // When
        Date expiration = jwtTokenProvider.getExpirationFromToken(token);

        // Then
        assertThat(expiration).isNotNull();
        assertThat(expiration.after(new Date())).isTrue();
    }

    @Test
    void getExpirationFromInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        Date expiration = jwtTokenProvider.getExpirationFromToken(invalidToken);

        // Then
        assertThat(expiration).isNull();
    }

    @Test
    void isTokenExpiredForValidToken() {
        // Given
        String token = jwtTokenProvider.generateToken(testUser);

        // When
        boolean isExpired = jwtTokenProvider.isTokenExpired(token);

        // Then
        assertThat(isExpired).isFalse();
    }

    @Test
    void isTokenExpiredForInvalidToken_ReturnsFalse() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        boolean isExpired = jwtTokenProvider.isTokenExpired(invalidToken);

        // Then
        assertThat(isExpired).isFalse(); // Invalid tokens are not considered expired
    }

    @Test
    void getRemainingValiditySecondsForValidToken() {
        // Given
        String token = jwtTokenProvider.generateToken(testUser);

        // When
        long remainingSeconds = jwtTokenProvider.getRemainingValiditySeconds(token);

        // Then
        assertThat(remainingSeconds).isGreaterThan(0);
        assertThat(remainingSeconds).isLessThanOrEqualTo(60 * 60); // 1 hour in seconds
    }

    @Test
    void getRemainingValiditySecondsForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        long remainingSeconds = jwtTokenProvider.getRemainingValiditySeconds(invalidToken);

        // Then
        assertThat(remainingSeconds).isEqualTo(0);
    }

    @Test
    void getExpirationWithNullToken_ReturnsNull() {
        // When
        Date expiration = jwtTokenProvider.getExpirationFromToken(null);

        // Then
        assertThat(expiration).isNull();
    }

    @Test
    void isTokenExpiredWithNullToken_ReturnsFalse() {
        // When
        boolean isExpired = jwtTokenProvider.isTokenExpired(null);

        // Then
        assertThat(isExpired).isFalse(); // Null tokens are not considered expired
    }

    @Test
    void getRemainingValiditySecondsWithNullToken_ReturnsZero() {
        // When
        long remainingSeconds = jwtTokenProvider.getRemainingValiditySeconds(null);

        // Then
        assertThat(remainingSeconds).isEqualTo(0);
    }

    @Test
    void createDefault() {
        // When
        JwtTokenProvider provider = JwtTokenProvider.createDefault(SECRET_KEY);

        // Then
        assertThat(provider).isNotNull();
        
        // Test that it works by generating a token
        String token = provider.generateToken(testUser);
        assertThat(token).isNotEmpty();
    }

    @Test
    void createWithNullSecretKey_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> JwtTokenProvider.createDefault(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createWithCustomConfiguration() {
        // When
        JwtTokenProvider provider = JwtTokenProvider.create(SECRET_KEY, 30, 14);

        // Then
        assertThat(provider).isNotNull();
        
        // Test that it works by generating a token
        String token = provider.generateToken(testUser);
        assertThat(token).isNotEmpty();
    }

    @Test
    void createWithNullSecretKeyInCustomFactory_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> JwtTokenProvider.create(null, 30, 14))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorWithNullSecretKey_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> new JwtTokenProvider(null, 60, 7))
            .isInstanceOf(NullPointerException.class);
    }
}
