package com.ghatana.platform.security.auth.impl;

import com.ghatana.platform.security.auth.AuthenticationProvider;
import com.ghatana.platform.security.auth.Credentials;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JWT-based authentication provider that validates JWT tokens.
 * 
 * <p>This provider validates JWT tokens and extracts user information from them.
 * It can be used as a standalone provider or as part of a chain with other providers.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create the JWT authentication provider
 * JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(jwtTokenProvider);
 * 
 * // Optionally, chain with another provider for token generation
 * JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtTokenProvider, delegateProvider);
 * }</pre>
 
 *
 * @doc.type class
 * @doc.purpose Jwt authentication provider
 * @doc.layer core
 * @doc.pattern Provider
*/
public class JwtAuthenticationProvider implements AuthenticationProvider {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationProvider.class);
    
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationProvider delegateProvider;
    
    /**
     * Creates a new JWT authentication provider that only validates tokens.
     * 
     * @param jwtTokenProvider The JWT token provider instance
     * @throws NullPointerException if jwtTokenProvider is null
     */
    public JwtAuthenticationProvider(JwtTokenProvider jwtTokenProvider) {
        this(jwtTokenProvider, null);
    }
    
    /**
     * Creates a new JWT authentication provider that can also generate tokens.
     * 
     * @param jwtTokenProvider The JWT token provider instance
     * @param delegateProvider The underlying provider to use for initial authentication
     * @throws NullPointerException if jwtTokenProvider is null
     */
    public JwtAuthenticationProvider(JwtTokenProvider jwtTokenProvider, AuthenticationProvider delegateProvider) {
        this.jwtTokenProvider = Objects.requireNonNull(jwtTokenProvider, "JwtTokenProvider cannot be null");
        this.delegateProvider = delegateProvider;
    }
    
    @Override
    public Promise<Optional<User>> authenticate(Credentials credentials) {
        if (!supports(credentials.getType())) {
            return Promise.of(Optional.empty());
        }
        
        try {
            // If we have a delegate provider and the credentials are not a token,
            // delegate to it and generate a token if authentication succeeds
            if (delegateProvider != null && !(credentials instanceof TokenCredentials)) {
                return delegateProvider.authenticate(credentials)
                    .map(userOpt -> userOpt.map(user -> {
                        // Generate a token via the port interface (userId + roles)
                        List<String> roles = user.getRoles().stream().toList();
                        String token = jwtTokenProvider.createToken(user.getUsername(), roles, null);
                        return user.toBuilder()
                            .authToken(token)
                            .build();
                    }));
            }
            
            // Handle token validation
            if (credentials instanceof TokenCredentials) {
                String token = ((TokenCredentials) credentials).getToken();
                if (!jwtTokenProvider.validateToken(token)) {
                    return Promise.of(Optional.empty());
                }
                String userId = jwtTokenProvider.getUserIdFromToken(token).orElse(null);
                if (userId == null) {
                    return Promise.of(Optional.empty());
                }
                User user = User.builder()
                    .userId(userId)
                    .username(userId)
                    .authenticated(true)
                    .authToken(token)
                    .build();
                // CORR-03 fix: build one builder, add all roles, call build() once
                List<String> tokenRoles = jwtTokenProvider.getRolesFromToken(token);
                User userWithRoles = user.toBuilder().addRoles(tokenRoles).build();
                return Promise.of(Optional.of(userWithRoles));
            }
            
            return Promise.of(Optional.empty());
            
        } catch (RuntimeException e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
            return Promise.of(Optional.empty());
        } catch (Exception e) {
            log.error("Unexpected error during JWT authentication", e);
            return Promise.ofException(e);
        }
    }
    
    @Override
    public boolean supports(String type) {
        return "token".equals(type) || (delegateProvider != null && delegateProvider.supports(type));
    }
    
    @Override
    public int getPriority() {
        return 100; // Higher priority than most other providers
    }
    
    /**
     * Generates a JWT token for the specified user.
     * 
     * @param user The user to generate a token for
     * @return A JWT token string
     * @throws NullPointerException if user is null
     */
    public String generateToken(User user) {
        List<String> roles = user.getRoles().stream().toList();
        return jwtTokenProvider.createToken(user.getUsername(), roles, null);
    }
    
    /**
     * Refreshes a JWT token by extending its expiration time.
     * 
     * @param token The token to refresh
     * @return A new JWT token with extended expiration
     * @throws RuntimeException if the token is invalid
     */
    public String refreshToken(String token) {
        String userId = jwtTokenProvider.getUserIdFromToken(token).orElseThrow(() -> new RuntimeException("Invalid token"));
        List<String> roles = jwtTokenProvider.getRolesFromToken(token);
        return jwtTokenProvider.createToken(userId, roles, null);
    }
    
    /**
     * Gets the remaining validity time of a token in milliseconds.
     * Extracts the expiration claim from the token and subtracts current time.
     * 
     * @param token The token to check
     * @return The remaining validity time in milliseconds, or -1 if the token is expired or invalid
     */
    public long getRemainingValidity(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            return -1L;
        }
        return jwtTokenProvider.extractClaims(token)
            .map(claims -> {
                Object exp = claims.get("exp");
                if (exp instanceof Number) {
                    long expiryMs = ((Number) exp).longValue() * 1000L;
                    long remaining = expiryMs - new Date().getTime();
                    return remaining > 0 ? remaining : -1L;
                }
                return -1L;
            })
            .orElse(-1L);
    }
}
