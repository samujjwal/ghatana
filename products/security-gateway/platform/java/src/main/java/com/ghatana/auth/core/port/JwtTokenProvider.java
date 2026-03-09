package com.ghatana.auth.core.port;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;
import io.activej.promise.Promise;

import java.time.Duration;

/**
 * Port interface for async JWT token operations with tenant isolation.
 *
 * <p>This is the product-level async JWT port used by the security-gateway module.
 * Implementations wrap cryptographic operations in ActiveJ Promises.
 *
 * @doc.type interface
 * @doc.purpose Async JWT token operations port
 * @doc.layer product
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface JwtTokenProvider {

    Promise<String> generateToken(TenantId tenantId, UserPrincipal principal, Duration ttl);

    Promise<String> generateRefreshToken(TenantId tenantId, UserPrincipal principal, Duration ttl);

    Promise<JwtClaims> validateToken(TenantId tenantId, String token);

    Promise<Boolean> revokeToken(TenantId tenantId, String tokenId);

    Promise<Integer> revokeAllTokensForUser(TenantId tenantId, String userId);

    Promise<Boolean> isTokenRevoked(TenantId tenantId, String tokenId);

    /**
     * Exception thrown when JWT validation fails.
     */
    class JwtValidationException extends RuntimeException {
        public JwtValidationException(String message) {
            super(message);
        }

        public JwtValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
