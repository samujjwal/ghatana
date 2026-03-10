/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - Authentication Controller
 * 
 * Provides authentication endpoints including login, logout, token refresh,
 * and user management. Integrates with JWT token provider and security middleware.
 */

package com.ghatana.yappc.api.security;

import com.ghatana.yappc.api.common.JsonUtils;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ghatana.yappc.api.security.HttpResponseFactory.*;

/**
 * Authentication controller for YAPPC API.
 * 
 * Endpoints:
 * - POST /api/auth/login - User authentication
 * - POST /api/auth/refresh - Token refresh
 * - POST /api/auth/logout - User logout
 * - GET /api/auth/profile - Current user info
 * - POST /api/auth/register - User registration
  *
 * @doc.type class
 * @doc.purpose auth controller
 * @doc.layer product
 * @doc.pattern Controller
 */
public class AuthController {
    
    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final SecurityConfig securityConfig;
    
    // In-memory token blacklist for logout (in production, use Redis)
    private final Map<String, Instant> tokenBlacklist = new ConcurrentHashMap<>();
    
    public AuthController(@NotNull JwtTokenProvider jwtTokenProvider,
                          @NotNull UserService userService,
                          @NotNull SecurityConfig securityConfig) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.securityConfig = securityConfig;
    }
    
    /**
     * Authenticates a user and returns JWT tokens.
     */
    public Promise<HttpResponse> login(@NotNull HttpRequest request) {
        try {
            return JsonUtils.parseBody(request, LoginRequest.class)
                .then(loginRequest -> userService.authenticate(loginRequest.getEmail(), loginRequest.getPassword())
                    .then(user -> {
                        if (user == null) {
                            return Promise.of(of401("Invalid credentials"));
                        }
                        
                        // Generate tokens
                        String accessToken = jwtTokenProvider.generateToken(user);
                        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
                        
                        LOG.info("User {} logged in successfully", user.getUserId());
                        
                        return Promise.of(ok200(Map.of(
                            "accessToken", accessToken,
                            "refreshToken", refreshToken,
                            "tokenType", "Bearer",
                            "expiresIn", jwtTokenProvider.getRemainingValiditySeconds(accessToken),
                            "user", Map.of(
                                "id", user.getUserId(),
                                "email", user.getEmail(),
                                "name", user.getUserName(),
                                "roles", user.getRoles(),
                                "tenantId", user.getTenantId()
                            ),
                            "timestamp", System.currentTimeMillis()
                        )));
                    }))
                .then(Promise::of, e -> {
                    LOG.error("Login error", e);
                    return Promise.of(of400("Invalid request format"));
                });
        } catch (Exception e) {
            LOG.error("Login request error", e);
            return Promise.of(of400("Invalid request format"));
        }
    }
    
    /**
     * Refreshes an access token using a refresh token.
     */
    public Promise<HttpResponse> refreshToken(@NotNull HttpRequest request) {
        try {
            return JsonUtils.parseBody(request, RefreshTokenRequest.class)
                .then(refreshRequest -> {
                    String refreshToken = refreshRequest.getRefreshToken();
                    
                    // Check if token is blacklisted
                    if (tokenBlacklist.containsKey(refreshToken)) {
                        return Promise.of(PlatformCompatibility.createJsonResponse(401, "{\"error\":\"Token has been revoked\",\"timestamp\":" + System.currentTimeMillis() + "}"));
                    }
                    
                    // Validate refresh token
                    return jwtTokenProvider.validateToken(refreshToken)
                        .then(valid -> {
                            if (!valid || !jwtTokenProvider.isRefreshToken(refreshToken)) {
                                return Promise.of(of401("Invalid refresh token"));
                            }
                            
                            // Get user from token and generate new access token
                            UserContext user = jwtTokenProvider.getUserFromToken(refreshToken);
                            if (user == null) {
                                return Promise.of(of401("Cannot extract user from token"));
                            }
                            
                            // Generate new access token
                            String newAccessToken = jwtTokenProvider.generateToken(user);
                            
                            LOG.info("Access token refreshed for user {}", user.getUserId());
                            
                            return Promise.of(ok200(Map.of(
                                "accessToken", newAccessToken,
                                "tokenType", "Bearer",
                                "expiresIn", jwtTokenProvider.getRemainingValiditySeconds(newAccessToken),
                                "timestamp", System.currentTimeMillis()
                            )));
                        });
                })
                .then(Promise::of, e -> {
                    LOG.error("Token refresh error", e);
                    return Promise.of(of400("Invalid request format"));
                });
        } catch (Exception e) {
            LOG.error("Refresh request error", e);
            return Promise.of(of400("Invalid request format"));
        }
    }
    
    /**
     * Logs out a user by adding the token to the blacklist.
     */
    public Promise<HttpResponse> logout(@NotNull HttpRequest request) {
        try {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Promise.of(of400("Missing Authorization header"));
            }
            
            String accessToken = authHeader.substring(7);
            
            // Add token to blacklist
            tokenBlacklist.put(accessToken, Instant.now());
            
            LOG.info("User logged out successfully");
            
            return Promise.of(ok200(Map.of(
                "message", "Logged out successfully",
                "timestamp", System.currentTimeMillis()
            )));
            
        } catch (Exception e) {
            LOG.error("Logout error", e);
            return Promise.of(of500("Internal server error"));
        }
    }
    
    /**
     * Gets the current user's information.
     */
    public Promise<HttpResponse> getCurrentUser(@NotNull HttpRequest request) {
        try {
            UserContext user = PlatformCompatibility.getAttached(request, UserContext.class);
            if (user == null) {
                return Promise.of(of401("User not authenticated"));
            }
            
            return Promise.of(ok200(Map.of(
                "user", Map.of(
                    "id", user.getUserId(),
                    "email", user.getEmail(),
                    "name", user.getUserName(),
                    "roles", user.getRoles(),
                    "tenantId", user.getTenantId(),
                    "permissions", user.getPermissions().stream()
                        .map(p -> Map.of(
                            "pathPattern", p.getPathPattern(),
                            "methods", p.getMethods(),
                            "description", p.getDescription()
                        ))
                        .toList()
                ),
                "timestamp", System.currentTimeMillis()
            )));
            
        } catch (Exception e) {
            LOG.error("Get current user error", e);
            return Promise.of(of500("Internal server error"));
        }
    }
    
    /**
     * Registers a new user.
     */
    public Promise<HttpResponse> register(@NotNull HttpRequest request) {
        try {
            return JsonUtils.parseBody(request, RegisterRequest.class)
                .then(registerRequest -> {
                    // Validate registration data
                    if (registerRequest.getEmail() == null || registerRequest.getPassword() == null) {
                        return Promise.of(of400("Email and password are required"));
                    }
                    
                    // Create user
                    return userService.createUser(
                            registerRequest.getEmail(),
                            registerRequest.getPassword(),
                            registerRequest.getUserName(),
                            registerRequest.getTenantId(),
                            registerRequest.getRoles()
                        ).then(user -> {
                            if (user == null) {
                                return Promise.of(of409("User already exists"));
                            }
                            
                            LOG.info("User {} registered successfully", user.getUserId());
                            
                            return Promise.of(of201(Map.of(
                                "message", "User registered successfully",
                                "user", Map.of(
                                    "id", user.getUserId(),
                                    "email", user.getEmail(),
                                    "name", user.getUserName(),
                                    "roles", user.getRoles(),
                                    "tenantId", user.getTenantId()
                                ),
                                "timestamp", System.currentTimeMillis()
                            )));
                        });
                })
                .then(Promise::of, e -> {
                    LOG.error("Registration error", e);
                    return Promise.of(of400("Invalid request format"));
                });
        } catch (Exception e) {
            LOG.error("Registration request error", e);
            return Promise.of(of400("Invalid request format"));
        }
    }
    
    /**
     * Cleans up expired tokens from the blacklist.
     */
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        tokenBlacklist.entrySet().removeIf(entry -> 
            entry.getValue().plusSeconds(3600).isBefore(now) // Remove after 1 hour
        );
    }
    
    // Request DTOs
    public static class LoginRequest {
        private String email;
        private String password;
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class RefreshTokenRequest {
        private String refreshToken;
        
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }
    
    public static class RegisterRequest {
        private String email;
        private String password;
        private String userName;
        private String tenantId;
        private java.util.List<String> roles;
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public java.util.List<String> getRoles() { return roles; }
        public void setRoles(java.util.List<String> roles) { this.roles = roles; }
    }
}
