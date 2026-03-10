/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - JWT Token Provider
 * 
 * Provides JWT token generation, validation, and user extraction services.
 * Supports multiple signing algorithms and token claims.
 */

package com.ghatana.yappc.api.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.activej.promise.Promise;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * JWT token provider for authentication and authorization.
 * 
 * Features:
 * - Token generation and validation
 * - User context extraction
 * - Token refresh support
 * - Multiple signing algorithms
 * - Custom claims support
  *
 * @doc.type class
 * @doc.purpose jwt token provider
 * @doc.layer product
 * @doc.pattern Service
 */
public class JwtTokenProvider {
    
    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenProvider.class);
    
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private final SecretKey signingKey;
    private final JwtParser jwtParser;
    private final long tokenValidityInMinutes;
    private final long refreshTokenValidityInDays;
    
    public JwtTokenProvider(@NotNull String secretKey, 
                           long tokenValidityInMinutes,
                           long refreshTokenValidityInDays) {
        this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes());
        this.jwtParser = Jwts.parser()
            .verifyWith(this.signingKey)
            .build();
        this.tokenValidityInMinutes = tokenValidityInMinutes;
        this.refreshTokenValidityInDays = refreshTokenValidityInDays;
    }
    
    /**
     * Generates a JWT token for the given user.
     */
    public String generateToken(@NotNull UserContext user) {
        Instant now = Instant.now();
        Instant validity = now.plus(tokenValidityInMinutes, ChronoUnit.MINUTES);
        
        return Jwts.builder()
            .setSubject(user.getUserId())
            .claim("email", user.getEmail())
            .claim("roles", user.getRoles())
            .claim("permissions", user.getPermissions())
            .claim("tenantId", user.getTenantId())
            .claim("userName", user.getUserName())
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(validity))
            .signWith(signingKey, SignatureAlgorithm.HS512)
            .compact();
    }
    
    /**
     * Generates a refresh token for the given user.
     */
    public String generateRefreshToken(@NotNull UserContext user) {
        Instant now = Instant.now();
        Instant validity = now.plus(refreshTokenValidityInDays, ChronoUnit.DAYS);
        
        return Jwts.builder()
            .setSubject(user.getUserId())
            .claim("type", "refresh")
            .claim("tenantId", user.getTenantId())
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(validity))
            .signWith(signingKey, SignatureAlgorithm.HS512)
            .compact();
    }
    
    /**
     * Validates a JWT token using ActiveJ Promise (non-blocking).
     */
    public Promise<Boolean> validateToken(@NotNull String token) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                jwtParser.parseClaimsJws(token);
                return true;
            } catch (ExpiredJwtException e) {
                LOG.warn("JWT token expired: {}", e.getMessage());
                return false;
            } catch (UnsupportedJwtException e) {
                LOG.warn("Unsupported JWT token: {}", e.getMessage());
                return false;
            } catch (MalformedJwtException e) {
                LOG.warn("Malformed JWT token: {}", e.getMessage());
                return false;
            } catch (SecurityException e) {
                LOG.warn("Invalid JWT signature: {}", e.getMessage());
                return false;
            } catch (IllegalArgumentException e) {
                LOG.warn("Invalid JWT token: {}", e.getMessage());
                return false;
            } catch (Exception e) {
                LOG.error("JWT validation error", e);
                return false;
            }
        });
    }
    
    /**
     * Extracts user context from a valid JWT token.
     */
    @Nullable
    public UserContext getUserFromToken(@NotNull String token) {
        try {
            Claims claims = jwtParser.parseClaimsJws(token).getBody();
            
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            
            @SuppressWarnings("unchecked")
            List<Permission> permissions = claims.get("permissions", List.class);
            
            return UserContext.builder()
                .userId(claims.getSubject())
                .email(claims.get("email", String.class))
                .userName(claims.get("userName", String.class))
                .tenantId(claims.get("tenantId", String.class))
                .roles(roles)
                .permissions(permissions.stream()
                    .map(p -> (Permission) p)
                    .toList())
                .build();
                
        } catch (Exception e) {
            LOG.error("Failed to extract user from token", e);
            return null;
        }
    }
    
    /**
     * Extracts tenant ID from a valid JWT token.
     */
    @Nullable
    public String getTenantFromToken(@NotNull String token) {
        try {
            Claims claims = jwtParser.parseClaimsJws(token).getBody();
            return claims.get("tenantId", String.class);
        } catch (Exception e) {
            LOG.error("Failed to extract tenant from token", e);
            return null;
        }
    }
    
    /**
     * Checks if a token is a refresh token.
     */
    public boolean isRefreshToken(@NotNull String token) {
        try {
            Claims claims = jwtParser.parseClaimsJws(token).getBody();
            return "refresh".equals(claims.get("type"));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Refreshes an access token using a refresh token (non-blocking via ActiveJ Promise).
     */
    public Promise<String> refreshToken(@NotNull String refreshToken) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            if (!isRefreshToken(refreshToken)) {
                throw new IllegalArgumentException("Invalid refresh token");
            }
            
            UserContext user = getUserFromToken(refreshToken);
            if (user == null) {
                throw new IllegalArgumentException("Cannot extract user from refresh token");
            }
            
            return generateToken(user);
        });
    }
    
    /**
     * Gets the expiration time from a token.
     */
    @Nullable
    public Date getExpirationFromToken(@NotNull String token) {
        try {
            Claims claims = jwtParser.parseClaimsJws(token).getBody();
            return claims.getExpiration();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Checks if a token is expired.
     */
    public boolean isTokenExpired(@NotNull String token) {
        Date expiration = getExpirationFromToken(token);
        return expiration != null && expiration.before(new Date());
    }
    
    /**
     * Gets the remaining validity time in seconds.
     */
    public long getRemainingValiditySeconds(@NotNull String token) {
        Date expiration = getExpirationFromToken(token);
        if (expiration == null) {
            return 0;
        }
        
        long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }
    
    /**
     * Creates a token provider with default configuration.
     */
    public static JwtTokenProvider createDefault(@NotNull String secretKey) {
        return new JwtTokenProvider(secretKey, 60, 7); // 1 hour access, 7 days refresh
    }
    
    /**
     * Creates a token provider with custom configuration.
     */
    public static JwtTokenProvider create(@NotNull String secretKey,
                                         long tokenValidityMinutes,
                                         long refreshTokenValidityDays) {
        return new JwtTokenProvider(secretKey, tokenValidityMinutes, refreshTokenValidityDays);
    }
}
