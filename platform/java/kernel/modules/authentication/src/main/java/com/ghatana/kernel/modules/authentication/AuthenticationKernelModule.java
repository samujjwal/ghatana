/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.authentication.service.AuthenticationService;
import com.ghatana.kernel.modules.authentication.service.AuthorizationService;
import com.ghatana.kernel.modules.authentication.service.TokenService;
import com.ghatana.kernel.modules.authentication.service.MfaService;
import com.ghatana.kernel.modules.authentication.service.OAuthService;
import io.activej.promise.Promise;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Generic Authentication Kernel Module.
 *
 * <p>Provides product-agnostic authentication, authorization, and token management
 * capabilities. This module contains NO finance-specific logic and can be reused
 * across all products in the Ghatana ecosystem.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>User authentication (password, MFA, SSO, OAuth)</li>
 *   <li>Role-based access control (RBAC)</li>
 *   <li>JWT token management and validation</li>
 *   <li>Session management</li>
 *   <li>Audit logging for authentication events</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Generic authentication kernel module - product-agnostic auth, RBAC, tokens
 * @doc.layer kernel
 * @doc.pattern Module
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class AuthenticationKernelModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationKernelModule.class);

    private AuthenticationService authService;
    private AuthorizationService authorizationService;
    private TokenService tokenService;
    private MfaService mfaService;
    private OAuthService oauthService;
    private KernelContext context;

    @Override
    public String getModuleId() {
        return "authentication";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            KernelCapability.Core.USER_AUTHENTICATION,
            KernelCapability.Core.SECURITY_FRAMEWORK,
            KernelCapability.Core.MULTI_FACTOR_AUTH,
            KernelCapability.Core.OAUTH_FRAMEWORK
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            KernelDependency.onCapability("config.management"),
            KernelDependency.onCapability("data.storage"),
            KernelDependency.onCapability("observability.framework")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Authentication module");
        this.context = context;

        // Initialize services with generic configuration
        this.authService = new AuthenticationService(context);
        this.authorizationService = new AuthorizationService(context);
        this.tokenService = new TokenService(context);
        this.mfaService = new MfaService(context);
        this.oauthService = new OAuthService(context);

        // Register services with kernel context
        context.registerService(AuthenticationService.class, authService);
        context.registerService(AuthorizationService.class, authorizationService);
        context.registerService(TokenService.class, tokenService);
        context.registerService(MfaService.class, mfaService);
        context.registerService(OAuthService.class, oauthService);

        log.info("Authentication module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Authentication module");

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            // Start authentication service
            authService.start();

            // Start authorization service
            authorizationService.start();

            // Start token service
            tokenService.start();

            // Start MFA service
            mfaService.start();

            // Start OAuth service
            oauthService.start();

            log.info("Authentication module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Authentication module");

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            // Stop services in reverse order
            if (oauthService != null) {
                oauthService.stop();
            }
            if (mfaService != null) {
                mfaService.stop();
            }
            if (tokenService != null) {
                tokenService.stop();
            }
            if (authorizationService != null) {
                authorizationService.stop();
            }
            if (authService != null) {
                authService.stop();
            }

            log.info("Authentication module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            boolean authHealthy = authService != null && authService.isHealthy();
            boolean authzHealthy = authorizationService != null && authorizationService.isHealthy();
            boolean tokenHealthy = tokenService != null && tokenService.isHealthy();
            boolean mfaHealthy = mfaService != null && mfaService.isHealthy();
            boolean oauthHealthy = oauthService != null && oauthService.isHealthy();

            boolean overallHealthy = authHealthy && authzHealthy && tokenHealthy && mfaHealthy && oauthHealthy;

            return overallHealthy 
                ? HealthStatus.healthy("All authentication services operational")
                : HealthStatus.unhealthy("Some authentication services degraded");
        } catch (Exception e) {
            log.error("Error checking authentication module health", e);
            return HealthStatus.unhealthy("Health check failed: " + e.getMessage());
        }
    }
}
