/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for authentication and authorization providers.
 *
 * <p>Provides JWT providers, authentication service/controller, authorization service/controller,
 * and user repository.
 *
 * @doc.type class
 * @doc.purpose Authentication and authorization DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 */
public class AuthModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(AuthModule.class);

  /** Provides UserRepository for authentication persistence. */
  @Provides
  com.ghatana.yappc.api.auth.repository.UserRepository userRepository() {
    logger.info("Creating DataCloudUserRepository");
    return new com.ghatana.yappc.api.auth.repository.DataCloudUserRepository();
  }

  /** Provides JwtTokenProvider for token creation and validation. */
  @Provides
  com.ghatana.platform.security.jwt.JwtTokenProvider jwtTokenProvider() {
    String secretKey = System.getenv("JWT_SECRET_KEY");
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET_KEY environment variable is required but not set. "
              + "Must be at least 32 characters.");
    }
    long validityMs = 3_600_000L; // 1 hour
    logger.info("Creating JwtTokenProvider with {}ms validity", validityMs);
    return new com.ghatana.platform.security.jwt.JwtTokenProvider(secretKey, validityMs);
  }

  /**
   * Provides local YAPPC JwtTokenProvider for SecurityMiddleware.
   *
   * @doc.type class
   * @doc.purpose YAPPC-scoped JWT token provider for middleware auth
   * @doc.layer api
   * @doc.pattern Provider
   */
  @Provides
  com.ghatana.yappc.api.security.JwtTokenProvider yappcJwtTokenProvider() {
    String secretKey = System.getenv("JWT_SECRET_KEY");
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET_KEY environment variable is required but not set. "
              + "Must be at least 32 characters.");
    }
    long tokenValidityMinutes = 60L; // 1 hour
    long refreshValidityDays = 30L;
    logger.info("Creating YAPPC JwtTokenProvider (validity={}min)", tokenValidityMinutes);
    return new com.ghatana.yappc.api.security.JwtTokenProvider(
        secretKey, tokenValidityMinutes, refreshValidityDays);
  }

  /** Provides AuthenticationService for login/register/token management. */
  @Provides
  com.ghatana.yappc.api.auth.AuthenticationService authenticationService(
      com.ghatana.yappc.api.auth.repository.UserRepository userRepository,
      com.ghatana.platform.security.jwt.JwtTokenProvider jwtTokenProvider) {
    logger.info("Creating AuthenticationService");
    return new com.ghatana.yappc.api.auth.AuthenticationService(userRepository, jwtTokenProvider);
  }

  /** Provides AuthenticationController for authentication endpoints. */
  @Provides
  com.ghatana.yappc.api.auth.AuthenticationController authenticationController(
      com.ghatana.yappc.api.auth.AuthenticationService authenticationService) {
    logger.info("Creating AuthenticationController");
    return new com.ghatana.yappc.api.auth.AuthenticationController(authenticationService);
  }

  /** Provides AuthorizationController for auth endpoints. */
  @Provides
  com.ghatana.yappc.api.auth.AuthorizationController authorizationController(
      com.ghatana.platform.security.rbac.SyncAuthorizationService authorizationService) {
    logger.info("Creating AuthorizationController with SyncAuthorizationService");
    return new com.ghatana.yappc.api.auth.AuthorizationController(authorizationService);
  }
}
