package com.ghatana.stt.core.security;

import com.ghatana.platform.security.port.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JWT token validator that delegates to the platform's canonical
 * {@link JwtTokenProvider} — eliminating the hand-rolled HMAC implementation.
 *
 * <p>Adapts the platform token provider to the local {@link JwtValidator} interface
 * for backward compatibility with existing STT service security code.
 *
 * @doc.type class
 * @doc.purpose JWT token validation via platform provider
 * @doc.layer security
 * @doc.pattern Adapter
 */
public final class JwtTokenValidator implements JwtValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenValidator.class);

    private final JwtTokenProvider tokenProvider;

    /**
     * Creates a JWT validator backed by the platform token provider.
     *
     * @param tokenProvider the platform JWT token provider
     */
    public JwtTokenValidator(JwtTokenProvider tokenProvider) {
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
        LOG.info("JWT validator initialized using platform JwtTokenProvider");
    }

    @Override
    public JwtClaims validate(String token) throws JwtValidationException {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException("Token is empty");
        }

        // Delegate validation to the platform provider
        if (!tokenProvider.validateToken(token)) {
            throw new JwtValidationException("Token validation failed (invalid or expired)");
        }

        // Extract claims using the platform provider
        Optional<String> userId = tokenProvider.getUserIdFromToken(token);
        if (userId.isEmpty() || userId.get().isBlank()) {
            throw new JwtValidationException("Token missing subject (userId)");
        }

        List<String> roles = tokenProvider.getRolesFromToken(token);
        Optional<Map<String, Object>> claims = tokenProvider.extractClaims(token);

        long expiresAt = 0;
        if (claims.isPresent()) {
            Object exp = claims.get().get("exp");
            if (exp instanceof Number num) {
                expiresAt = num.longValue();
            }
        }

        return new JwtClaims(userId.get(), new HashSet<>(roles), expiresAt);
    }

    /**
     * Builder for JwtTokenValidator.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder that constructs the platform JWT provider and wraps it.
     */
    public static class Builder {
        private JwtTokenProvider tokenProvider;

        /**
         * Sets the platform token provider directly.
         *
         * @param tokenProvider platform JWT token provider
         * @return this builder
         */
        public Builder tokenProvider(JwtTokenProvider tokenProvider) {
            this.tokenProvider = tokenProvider;
            return this;
        }

        /**
         * Builds the validator.
         *
         * @return configured JwtTokenValidator
         */
        public JwtTokenValidator build() {
            Objects.requireNonNull(tokenProvider, "tokenProvider is required");
            return new JwtTokenValidator(tokenProvider);
        }
    }
}
