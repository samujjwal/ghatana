/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.auth;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.auth.dto.*;
import com.ghatana.yappc.api.common.JsonUtils;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authentication Controller - Handles user authentication and token management.
 *
 * <p>Provides handler methods for:
 * <ul>
 *   <li>User login with JWT token generation</li>
 *   <li>User registration</li>
 *   <li>Token refresh for session extension</li>
 *   <li>User logout and token revocation</li>
 *   <li>Current user profile retrieval</li>
 *   <li>Password reset workflows</li>
 * </ul>
 *
 * <p>Routes (wired in ApiApplication):
 * <pre>
 * POST /api/auth/login     - Authenticate and get tokens
 * POST /api/auth/register  - Create new user account
 * POST /api/auth/logout    - Revoke tokens
 * POST /api/auth/refresh   - Refresh access token
 * GET  /api/auth/profile   - Get current user profile
 * POST /api/auth/reset     - Request password reset
 * POST /api/auth/reset/confirm - Confirm password reset
 * </pre>
 *
 * <p>Security:
 * <ul>
 *   <li>JWT tokens with 1-hour expiration</li>
 *   <li>Refresh tokens with 7-day expiration</li>
 *   <li>BCrypt password hashing</li>
 *   <li>Rate limiting on login attempts</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Authentication and token management
 * @doc.layer product
 * @doc.pattern Controller
 */
public class AuthenticationController {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationController.class);

    private final AuthenticationService authService;

    /**
     * Creates an AuthenticationController.
     *
     * @param authService the authentication service
     */
    public AuthenticationController(AuthenticationService authService) {
        this.authService = Objects.requireNonNull(authService, "authService is required");
    }

    /**
     * POST /api/auth/login
     * Authenticate user and generate JWT token.
     *
     * @param request LoginRequest with username/email and password
     * @return LoginResponse with access token, refresh token, and user info
     */
    public Promise<HttpResponse> login(HttpRequest request) {
        Promise<HttpResponse> promise =
                JsonUtils.parseBody(request, LoginRequest.class)
                        .then(
                                loginRequest -> {
                                    log.info("Login attempt for user: {}", loginRequest.username());
                                    return authService.authenticate(loginRequest.username(), loginRequest.password())
                                            .map(
                                                    authResult -> {
                                                        if (!authResult.success()) {
                                                            log.warn(
                                                                    "Login failed for user: {} - {}",
                                                                    loginRequest.username(),
                                                                    authResult.error());
                                                            return ApiResponse.unauthorized(authResult.error());
                                                        }

                                                        LoginResponse response =
                                                                new LoginResponse(
                                                                        authResult.accessToken(),
                                                                        authResult.refreshToken(),
                                                                        authResult.tokenType(),
                                                                        authResult.expiresIn(),
                                                                        authResult.user());
                                                        return ApiResponse.ok(response);
                                                    });
                                });

        return ApiResponse.wrap(promise);
    }

    /**
     * POST /api/auth/register
     * Register a new user account.
     *
     * @param request RegisterRequest with user details
     * @return Success response or error
     */
    public Promise<HttpResponse> register(HttpRequest request) {
        Promise<HttpResponse> promise =
                JsonUtils.parseBody(request, RegisterRequest.class)
                        .then(
                                registerRequest -> {
                                    log.info("Registration attempt for user: {}", registerRequest.username());
                                    return authService.register(
                                            registerRequest.username(),
                                            registerRequest.email(),
                                            registerRequest.password(),
                                            registerRequest.firstName(),
                                            registerRequest.lastName())
                                            .map(
                                                    authResult -> {
                                                        if (!authResult.success()) {
                                                            log.warn(
                                                                    "Registration failed for user: {} - {}",
                                                                    registerRequest.username(),
                                                                    authResult.error());
                                                            return ApiResponse.conflict(authResult.error());
                                                        }
                                                        log.info("Registration successful for user: {}",
                                                                registerRequest.username());
                                                        return ApiResponse.created(
                                                                Map.of("message", "Registration successful",
                                                                       "userId", authResult.user().id()));
                                                    });
                                });
        return ApiResponse.wrap(promise);
    }

    /**
     * POST /api/auth/logout
     * Logout user and revoke tokens.
     *
     * @param request HttpRequest with Authorization header
     * @return Success response
     */
    public Promise<HttpResponse> logout(HttpRequest request) {
        Optional<String> token = extractBearerToken(request);
        
        if (token.isEmpty()) {
            return Promise.of(ApiResponse.unauthorized("Missing authorization token"));
        }

        Promise<HttpResponse> promise =
                authService.logout(token.get())
                        .map(
                                success -> {
                                    if (!success) {
                                        log.warn("Logout failed - invalid token");
                                        return ApiResponse.unauthorized("Invalid token");
                                    }
                                    log.info("User logged out successfully");
                                    return ApiResponse.ok(Map.of("message", "Logged out successfully"));
                                });

        return ApiResponse.wrap(promise);
    }

    /**
     * POST /api/auth/refresh
     * Refresh access token using refresh token.
     *
     * @param request RefreshTokenRequest
     * @return New access token and refresh token
     */
    public Promise<HttpResponse> refresh(HttpRequest request) {
        Promise<HttpResponse> promise =
                JsonUtils.parseBody(request, RefreshTokenRequest.class)
                        .then(
                                refreshRequest ->
                                        authService.refreshToken(refreshRequest.refreshToken())
                                                .map(
                                                        authResult -> {
                                                            if (!authResult.success()) {
                                                                log.warn("Token refresh failed: {}", authResult.error());
                                                                return ApiResponse.unauthorized(authResult.error());
                                                            }

                                                            RefreshTokenResponse response =
                                                                    new RefreshTokenResponse(
                                                                            authResult.accessToken(),
                                                                            authResult.refreshToken(),
                                                                            authResult.tokenType(),
                                                                            authResult.expiresIn());
                                                            return ApiResponse.ok(response);
                                                        }));
        return ApiResponse.wrap(promise);
    }

    /**
     * GET /api/auth/profile
     * Get current authenticated user profile.
     *
     * @param request HttpRequest with Authorization header
     * @return UserProfile
     */
    public Promise<HttpResponse> getProfile(HttpRequest request) {
        Optional<String> token = extractBearerToken(request);
        
        if (token.isEmpty()) {
            return Promise.of(ApiResponse.unauthorized("Missing authorization token"));
        }

        Promise<HttpResponse> promise =
                authService.getCurrentUser(token.get())
                        .map(
                                userOptional ->
                                        userOptional
                                                .<HttpResponse>map(ApiResponse::ok)
                                                .orElseGet(() -> ApiResponse.notFound("User not found")));

        return ApiResponse.wrap(promise);
    }

    /**
     * POST /api/auth/reset
     * Request password reset email.
     *
     * @param request PasswordResetRequest with email
     * @return Success message
     */
    public Promise<HttpResponse> requestPasswordReset(HttpRequest request) {
        Promise<HttpResponse> promise =
                JsonUtils.parseBody(request, PasswordResetRequest.class)
                        .then(
                                resetRequest ->
                                        authService.requestPasswordReset(resetRequest.email())
                                                .map(
                                                        ignored -> {
                                                            log.info(
                                                                    "Password reset requested for: {}",
                                                                    resetRequest.email());
                                                            return ApiResponse.ok(
                                                                    Map.of("message", "Password reset email sent"));
                                                        }));

        return ApiResponse.wrap(promise);
    }

    /**
     * POST /api/auth/reset/confirm
     * Confirm password reset with token and new password.
     *
     * @param request PasswordResetConfirmRequest
     * @return Success response
     */
    public Promise<HttpResponse> confirmPasswordReset(HttpRequest request) {
        Promise<HttpResponse> promise =
                JsonUtils.parseBody(request, PasswordResetConfirmRequest.class)
                        .then(
                                confirmRequest ->
                                        authService
                                                .confirmPasswordReset(
                                                        confirmRequest.token(), confirmRequest.newPassword())
                                                .map(
                                                        success -> {
                                                            if (!success) {
                                                                log.warn("Password reset confirmation failed");
                                                                return ApiResponse.badRequest(
                                                                        "Invalid or expired reset token");
                                                            }
                                                            log.info("Password reset confirmed successfully");
                                                            return ApiResponse.ok(
                                                                    Map.of(
                                                                            "message",
                                                                            "Password reset successful"));
                                                        }));

        return ApiResponse.wrap(promise);
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private Optional<String> extractBearerToken(HttpRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        if (!header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return Optional.of(header.substring("Bearer ".length()));
    }
}
