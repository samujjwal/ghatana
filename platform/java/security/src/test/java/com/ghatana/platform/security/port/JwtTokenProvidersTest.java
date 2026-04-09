package com.ghatana.platform.security.port;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verify canonical JWT provider factories expose working port instances
 * @doc.layer platform
 * @doc.pattern Unit Test
 */
@DisplayName("JwtTokenProviders Tests")
class JwtTokenProvidersTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("fromSharedSecret should return a working JwtTokenProvider port")
    void fromSharedSecretShouldReturnWorkingPort() {
        JwtTokenProvider provider = JwtTokenProviders.fromSharedSecret(SECRET, 60_000L);

        String token = provider.createToken("user-123", List.of("USER"), Map.of("tenantId", "tenant-a"));

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserIdFromToken(token)).contains("user-123");
        assertThat(provider.extractClaims(token)).hasValueSatisfying(
            claims -> assertThat(claims).containsEntry("tenantId", "tenant-a")
        );
    }
}
