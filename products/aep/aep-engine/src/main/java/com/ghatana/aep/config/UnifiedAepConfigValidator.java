/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.config;

import com.ghatana.aep.Aep;

import java.util.Map;
import java.util.Objects;

/**
 * Unified entry point for all AEP configuration validation (AEP-001).
 *
 * <p>Previously, AEP configuration validation was split between two independent
 * validator classes:
 * <ul>
 *   <li>{@link AepConfigValidator} — validates the typed {@link Aep.AepConfig} Java record
 *       (anomaly thresholds, pipeline limits, idempotency settings, consent config).</li>
 *   <li>{@link AepConfigurationValidator} — validates the runtime environment variable map
 *       (JDBC URLs, transport types, Kafka brokers, AWS regions, APP_ENV).</li>
 * </ul>
 *
 * <p>These serve distinct but complementary validation contexts. This class coordinates
 * them through a single entry point, ensuring consistent application and preventing callers
 * from inadvertently skipping one of the two validation passes.
 *
 * <h2>Validation Contexts</h2>
 * <dl>
 *   <dt>API config validation</dt>
 *   <dd>Use {@link #validateApiConfig(Aep.AepConfig)} when creating an engine instance
 *       via {@link Aep#create(Aep.AepConfig)}. Validates typed Java values.</dd>
 *   <dt>Environment config validation</dt>
 *   <dd>Use {@link #validateEnvConfig(Map)} at application startup to validate env-sourced
 *       configuration before the engine is constructed.</dd>
 *   <dt>Full validation</dt>
 *   <dd>Use {@link #validateAll(Aep.AepConfig, Map)} when both are available.</dd>
 * </dl>
 *
 * <p>Usage:
 * <pre>{@code
 * // At engine-creation time (API config only)
 * UnifiedAepConfigValidator.validateApiConfig(config);
 *
 * // At startup (env config only)
 * UnifiedAepConfigValidator.validateEnvConfig(System.getenv());
 *
 * // Combined    
 * UnifiedAepConfigValidator.validateAll(config, System.getenv());
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Consolidated entry point coordinating API and env-map configuration validation
 * @doc.layer product
 * @doc.pattern Facade
 * @since 1.2.0
 */
public final class UnifiedAepConfigValidator {

    private UnifiedAepConfigValidator() {}

    /**
     * Validates a typed {@link Aep.AepConfig} instance.
     *
     * <p>Delegates to {@link AepConfigValidator}. Throws immediately on the first
     * detected batch of violations.
     *
     * @param config API-level configuration to validate; must not be {@code null}
     * @throws IllegalArgumentException if any constraint is violated
     * @throws NullPointerException     if {@code config} is {@code null}
     */
    public static void validateApiConfig(Aep.AepConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        AepConfigValidator.validate(config);
    }

    /**
     * Validates an environment variable map.
     *
     * <p>Delegates to {@link AepConfigurationValidator}. Throws on the first detected
     * batch of errors; warnings are surfaced via structured logging inside the delegate.
     *
     * @param env raw environment / properties map to validate; must not be {@code null}
     * @throws IllegalStateException if any required key is missing or has an invalid value
     * @throws NullPointerException  if {@code env} is {@code null}
     */
    public static void validateEnvConfig(Map<String, String> env) {
        Objects.requireNonNull(env, "env must not be null");
        EnvConfig envConfig = EnvConfig.fromMap(env);
        AepConfigurationValidator validator = new AepConfigurationValidator(envConfig, env);
        validator.validate().throwIfInvalid();
    }

    /**
     * Runs both API and environment config validations in one call.
     *
     * <p>API config is validated first; if it fails, env config is not evaluated.
     *
     * @param apiConfig typed API configuration; must not be {@code null}
     * @param env       environment variable map; must not be {@code null}
     * @throws IllegalArgumentException if API config constraints are violated
     * @throws IllegalStateException    if env config constraints are violated
     * @throws NullPointerException     if either argument is {@code null}
     */
    public static void validateAll(Aep.AepConfig apiConfig, Map<String, String> env) {
        validateApiConfig(apiConfig);
        validateEnvConfig(env);
    }
}
