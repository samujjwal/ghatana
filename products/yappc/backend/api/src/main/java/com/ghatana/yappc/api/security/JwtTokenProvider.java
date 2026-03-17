/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - JWT Token Provider
 * 
 * Provides JWT token generation, validation, and user extraction services.
 * Supports multiple signing algorithms and token claims.
 */

package com.ghatana.yappc.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

    private final com.ghatana.platform.security.port.JwtTokenProvider accessTokenProvider;
    private final com.ghatana.platform.security.port.JwtTokenProvider refreshTokenProvider;
    
    public JwtTokenProvider(@NotNull String secretKey, 
                           long tokenValidityInMinutes,
                           long refreshTokenValidityInDays) {
        this.accessTokenProvider = new com.ghatana.platform.security.jwt.JwtTokenProvider(
            secretKey,
            tokenValidityInMinutes * 60_000L
        );
        this.refreshTokenProvider = new com.ghatana.platform.security.jwt.JwtTokenProvider(
            secretKey,
            refreshTokenValidityInDays * 24L * 60L * 60L * 1_000L
        );
    }
    
    /**
     * Generates a JWT token for the given user.
     */
    public String generateToken(@NotNull UserContext user) {
        return accessTokenProvider.createToken(
            user.getUserId(),
            user.getRoles(),
            buildUserClaims(user, false)
        );
    }
    
    /**
     * Generates a refresh token for the given user.
     */
    public String generateRefreshToken(@NotNull UserContext user) {
        return refreshTokenProvider.createToken(
            user.getUserId(),
            user.getRoles(),
            buildUserClaims(user, true)
        );
    }
    
    /**
     * Validates a JWT token using ActiveJ Promise (non-blocking).
     */
    public Promise<Boolean> validateToken(@NotNull String token) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                return accessTokenProvider.validateToken(token);
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
            Map<String, Object> claims = extractClaims(token);
            if (claims == null) {
                return null;
            }

            List<String> roles = extractRoles(claims.get("roles"));
            List<Permission> permissions = extractPermissions(claims.get("permissions"));
            String userId = asString(claims.get("sub"));
            if (userId == null || userId.isBlank()) {
                return null;
            }
            
            return UserContext.builder()
                .userId(userId)
                .email(defaultString(asString(claims.get("email")), "unknown@ghatana.local"))
                .userName(defaultString(asString(claims.get("userName")), userId))
                .tenantId(defaultString(asString(claims.get("tenantId")), "default"))
                .roles(roles)
                .permissions(permissions)
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
            Map<String, Object> claims = extractClaims(token);
            return claims == null ? null : asString(claims.get("tenantId"));
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
            Map<String, Object> claims = extractClaims(token);
            return claims != null && "refresh".equals(asString(claims.get("type")));
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
            Map<String, Object> claims = extractClaims(token);
            if (claims == null) {
                return null;
            }
            Object expRaw = claims.get("exp");
            if (expRaw instanceof Date date) {
                return date;
            }
            if (expRaw instanceof Number num) {
                return new Date(num.longValue() * 1_000L);
            }
            return null;
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

    @Nullable
    private Map<String, Object> extractClaims(@NotNull String token) {
        try {
            return accessTokenProvider.extractClaims(token).orElse(null);
        } catch (Exception ignored) {
            try {
                return refreshTokenProvider.extractClaims(token).orElse(null);
            } catch (Exception ex) {
                LOG.debug("Failed to extract claims", ex);
                return null;
            }
        }
    }

    private Map<String, Object> buildUserClaims(@NotNull UserContext user, boolean refreshToken) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("email", user.getEmail());
        claims.put("tenantId", user.getTenantId());
        claims.put("userName", user.getUserName());
        claims.put("permissions", user.getPermissions().stream().map(permission -> {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("pathPattern", permission.getPathPattern());
            serialized.put("methods", permission.getMethods());
            serialized.put("description", permission.getDescription());
            return serialized;
        }).toList());
        if (refreshToken) {
            claims.put("type", "refresh");
        }
        return claims;
    }

    private List<String> extractRoles(@Nullable Object rawRoles) {
        if (!(rawRoles instanceof List<?> roleList)) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (Object role : roleList) {
            if (role instanceof String roleString && !roleString.isBlank()) {
                roles.add(roleString);
            }
        }
        return roles;
    }

    private List<Permission> extractPermissions(@Nullable Object rawPermissions) {
        if (!(rawPermissions instanceof List<?> permissionList)) {
            return List.of();
        }
        List<Permission> permissions = new ArrayList<>();
        for (Object rawPermission : permissionList) {
            if (!(rawPermission instanceof Map<?, ?> map)) {
                continue;
            }
            String pathPattern = asString(map.get("pathPattern"));
            if (pathPattern == null || pathPattern.isBlank()) {
                continue;
            }
            List<String> methods = extractRoles(map.get("methods"));
            String description = defaultString(asString(map.get("description")), "");
            permissions.add(new Permission(pathPattern, methods, description));
        }
        return permissions;
    }

    @Nullable
    private String asString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return String.valueOf(value);
    }

    private String defaultString(@Nullable String value, @NotNull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
