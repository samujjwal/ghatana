/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.config.AepConfigurationValidator;
import com.ghatana.aep.config.EnvConfig;
import com.ghatana.aep.security.AepSecretManager;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ DI module for AEP operational infrastructure.
 *
 * <p>Wires the configuration validation and secret management subsystems:
 * <ul>
 *   <li>{@link AepConfigurationValidator} — validates all AEP environment variables
 *       at startup using semantic rules (formats, bounds, cross-dependencies, security).
 *       Throws {@link IllegalStateException} if the configuration is invalid, ensuring
 *       the application refuses to start with broken settings.</li>
 *   <li>{@link AepSecretManager} — multi-tier secret resolution (Kubernetes projected
 *       volume → HashiCorp Vault HTTP API → environment variable fallback). Always
 *       available as a singleton regardless of which runtime tier is active.</li>
 *   <li>{@link EnvConfig} — typed accessor layer over system environment variables.
 *       Shared as a singleton across all modules to prevent redundant {@link System#getenv}
 *       calls and keep env reading in one place.</li>
 * </ul>
 *
 * <h2>Required Modules</h2>
 * <p>This module has <em>no</em> required peer modules — it only reads the system
 * environment, making it suitable as the very first module in any injector construction.
 *
 * <h2>Startup Behaviour</h2>
 * <p>The {@link #envConfig()} provider runs configuration validation eagerly. Any
 * errors found by {@link AepConfigurationValidator} will cause the application to
 * abort startup with a descriptive message listing all problems. Warnings are logged
 * but do not prevent startup.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepOperationsModule(),   // validates config + wires secrets
 *     new AepCoreModule(),
 *     new AepObservabilityModule()
 * );
 * EnvConfig env         = injector.getInstance(EnvConfig.class);
 * AepSecretManager sec  = injector.getInstance(AepSecretManager.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for AEP configuration validation and secret management
 * @doc.layer product
 * @doc.pattern Module
 * @see AepConfigurationValidator
 * @see AepSecretManager
 */
public class AepOperationsModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(AepOperationsModule.class);

    /**
     * Provides a validated, singleton {@link EnvConfig} instance.
     *
     * <p>Validation runs synchronously at startup. Any configuration errors
     * are thrown as {@link IllegalStateException}, aborting the injection
     * process and preventing the application from starting with broken config.
     *
     * @return validated environment configuration
     */
    @Provides
    EnvConfig envConfig() {
        EnvConfig env = EnvConfig.fromSystem();
        AepConfigurationValidator validator = new AepConfigurationValidator(env, System.getenv());
        AepConfigurationValidator.ValidationResult result = validator.validate();

        // Log all warnings
        result.warnings().forEach(w -> log.warn("[Config] {}", w));

        // Abort on errors
        if (!result.isValid()) {
            result.errors().forEach(e -> log.error("[Config] {}", e));
            result.throwIfInvalid(); // throws IllegalStateException
        }

        return env;
    }

    /**
     * Provides the system-backed {@link AepSecretManager} singleton.
     *
     * <p>Resolution order for any secret key:
     * <ol>
     *   <li>Kubernetes projected volume ({@code /var/run/secrets/aep/<key>})</li>
     *   <li>HashiCorp Vault (when {@code VAULT_ADDR} is set)</li>
     *   <li>System environment variable fallback</li>
     * </ol>
     *
     * @return singleton secret manager
     */
    @Provides
    AepSecretManager aepSecretManager() {
        return AepSecretManager.fromSystem();
    }

    /**
     * Provides the {@link AepConfigurationValidator} for use in health checks
     * or periodic re-validation.
     *
     * @param env validated environment configuration
     * @return configuration validator
     */
    @Provides
    AepConfigurationValidator aepConfigurationValidator(EnvConfig env) {
        return new AepConfigurationValidator(env, System.getenv());
    }
}
