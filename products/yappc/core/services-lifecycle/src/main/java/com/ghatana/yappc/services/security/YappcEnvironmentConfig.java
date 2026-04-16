/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Security — Environment Configuration Validator
 */
package com.ghatana.yappc.services.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Startup-time environment configuration validator for YAPPC services.
 *
 * <p>Validates that all required environment variables are present, non-empty, and
 * safe for production. Fail-fast: any validation error throws
 * {@link YappcEnvironmentConfigException} with a descriptive message, preventing the
 * service from starting in a misconfigured state.
 *
 * <p><b>Variables Validated</b></p>
 * <ul>
 *   <li>{@code YAPPC_API_KEYS} — must be set; must not be the insecure default
 *       {@code dev-key} when running in production mode</li>
 *   <li>{@code YAPPC_DB_URL} — required for all services that use the database</li>
 *   <li>{@code YAPPC_DB_USER} — required alongside {@code YAPPC_DB_URL}</li>
 *   <li>{@code YAPPC_DB_PASSWORD} — required alongside {@code YAPPC_DB_URL}</li>
 *   <li>{@code YAPPC_TENANT_ID} — required to prevent cross-tenant data leaks</li>
 * </ul>
 *
 * <p><b>Usage</b></p>
 * <pre>{@code
 * // In service launcher or DI module configure():
 * YappcEnvironmentConfig.validate();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Fail-fast startup validation of required YAPPC environment variables
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class YappcEnvironmentConfig {

    private static final Logger log = LoggerFactory.getLogger(YappcEnvironmentConfig.class);

    /** Environment variable that carries the YAPPC API key set. */
    public static final String API_KEYS_ENV = "YAPPC_API_KEYS";

    /** Insecure default key that must not be used in production. */
    public static final String INSECURE_DEFAULT_KEY = "dev-key";

    /** Environment variable indicating the runtime profile (e.g., {@code production}, {@code dev}). */
    public static final String PROFILE_ENV = "YAPPC_PROFILE";

    /** Database URL environment variable. */
    public static final String DB_URL_ENV = "YAPPC_DB_URL";

    /** Agent runtime AI mode: {@code required} or {@code stub}. */
    public static final String AGENT_LLM_MODE_ENV = "YAPPC_AGENT_LLM_MODE";

    /** OpenTelemetry OTLP endpoint required for production AI observability. */
    public static final String OTEL_EXPORTER_OTLP_ENDPOINT_ENV = "OTEL_EXPORTER_OTLP_ENDPOINT";

    /** Anthropic provider API key. */
    public static final String ANTHROPIC_API_KEY_ENV = "ANTHROPIC_API_KEY";

    /** Anthropic provider model name. */
    public static final String ANTHROPIC_MODEL_ENV = "ANTHROPIC_MODEL";

    /** OpenAI provider API key. */
    public static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";

    /** OpenAI provider model name. */
    public static final String OPENAI_MODEL_ENV = "OPENAI_MODEL";

    /** Ollama provider host. */
    public static final String OLLAMA_HOST_ENV = "OLLAMA_HOST";

    /** Ollama provider model name. */
    public static final String OLLAMA_MODEL_ENV = "OLLAMA_MODEL";

    /** Database user environment variable. */
    public static final String DB_USER_ENV = "YAPPC_DB_USER";

    /** Database password environment variable. */
    public static final String DB_PASSWORD_ENV = "YAPPC_DB_PASSWORD";

    /** Tenant ID environment variable used when no request-scoped tenant context is present. */
    public static final String TENANT_ID_ENV = "YAPPC_TENANT_ID";

    private YappcEnvironmentConfig() {}

    /**
     * Validates all required environment variables using the real {@link System#getenv()} map.
     *
     * @throws YappcEnvironmentConfigException if any validation fails
     */
    public static void validate() {
        validate(System.getenv());
    }

    /**
     * Validates environment variables from the supplied map (allows testing with injected env).
     *
     * @param env environment variable map to validate
     * @throws YappcEnvironmentConfigException if any validation fails
     */
    public static void validate(Map<String, String> env) {
        List<String> errors = new ArrayList<>();

        validateApiKeys(env, errors);
        validateAgentLlmMode(env, errors);
        validateProductionAiObservability(env, errors);
        validateDatabase(env, errors);
        validateTenantId(env, errors);

        if (!errors.isEmpty()) {
            String message = "YAPPC environment configuration is invalid:\n  - " +
                    String.join("\n  - ", errors);
            log.error(message);
            throw new YappcEnvironmentConfigException(message);
        }

        log.info("YappcEnvironmentConfig: all required environment variables are present and valid");
    }

    /**
     * Returns a {@link ValidationResult} without throwing, allowing callers to inspect
     * individual errors for health-check endpoints.
     *
     * @param env environment variable map to validate
     * @return validation result with all failures collected
     */
    public static ValidationResult check(Map<String, String> env) {
        List<String> errors = new ArrayList<>();
        validateApiKeys(env, errors);
        validateAgentLlmMode(env, errors);
        validateProductionAiObservability(env, errors);
        validateDatabase(env, errors);
        validateTenantId(env, errors);
        return new ValidationResult(errors);
    }

    // ─── Validators ───────────────────────────────────────────────────────────

    private static void validateApiKeys(Map<String, String> env, List<String> errors) {
        String apiKeys = env.get(API_KEYS_ENV);

        if (apiKeys == null || apiKeys.isBlank()) {
            errors.add(API_KEYS_ENV + " must be set — configure with one or more comma-separated API keys");
            return;
        }

        // Reject the insecure default key in production mode
        String profile = env.getOrDefault(PROFILE_ENV, "dev").toLowerCase();
        if ("production".equals(profile) || "prod".equals(profile)) {
            Set<String> keys = Set.of(apiKeys.split(","));
            if (keys.stream().map(String::trim).anyMatch(INSECURE_DEFAULT_KEY::equals)) {
                errors.add(API_KEYS_ENV + " must not contain the insecure default key '" +
                        INSECURE_DEFAULT_KEY + "' in production mode (YAPPC_PROFILE=" + profile + ")");
            }
        }
    }

    private static void validateAgentLlmMode(Map<String, String> env, List<String> errors) {
        String configuredMode = env.getOrDefault(AGENT_LLM_MODE_ENV, "required").trim().toLowerCase();
        if (!"required".equals(configuredMode) && !"stub".equals(configuredMode)) {
            errors.add(AGENT_LLM_MODE_ENV + " must be one of: required, stub");
            return;
        }

        String profile = env.getOrDefault(PROFILE_ENV, "dev").toLowerCase();
        if (("production".equals(profile) || "prod".equals(profile)) && "stub".equals(configuredMode)) {
            errors.add(AGENT_LLM_MODE_ENV + "=stub is not allowed in production mode (" + PROFILE_ENV + "=" + profile + ")");
        }
    }

    private static void validateProductionAiObservability(Map<String, String> env, List<String> errors) {
        if (!isProductionProfile(env) || !isRequiredAiMode(env)) {
            return;
        }

        validateConfiguredAiProvider(env, errors);

        if (isBlankOrAbsent(env, OTEL_EXPORTER_OTLP_ENDPOINT_ENV)) {
            errors.add(OTEL_EXPORTER_OTLP_ENDPOINT_ENV
                    + " must be set when production AI runtime is enabled so traces and provider metrics are exported");
        }
    }

    private static void validateConfiguredAiProvider(Map<String, String> env, List<String> errors) {
        boolean hasConfiguredProvider = false;

        hasConfiguredProvider |= validateProvider(env, errors,
                ANTHROPIC_API_KEY_ENV, ANTHROPIC_MODEL_ENV, "Anthropic");
        hasConfiguredProvider |= validateProvider(env, errors,
                OPENAI_API_KEY_ENV, OPENAI_MODEL_ENV, "OpenAI");
        hasConfiguredProvider |= validateProvider(env, errors,
                OLLAMA_HOST_ENV, OLLAMA_MODEL_ENV, "Ollama");

        if (!hasConfiguredProvider) {
            errors.add("Production AI runtime requires at least one configured provider: "
                    + ANTHROPIC_API_KEY_ENV + " (+ " + ANTHROPIC_MODEL_ENV + "), "
                    + OPENAI_API_KEY_ENV + " (+ " + OPENAI_MODEL_ENV + "), or "
                    + OLLAMA_HOST_ENV + " (+ " + OLLAMA_MODEL_ENV + ")");
        }
    }

    private static boolean validateProvider(
            Map<String, String> env,
            List<String> errors,
            String credentialKey,
            String modelKey,
            String providerName) {
        if (isBlankOrAbsent(env, credentialKey)) {
            return false;
        }
        if (isBlankOrAbsent(env, modelKey)) {
            errors.add(providerName + " AI provider is configured via " + credentialKey
                    + " but missing required model variable " + modelKey);
            return false;
        }
        return true;
    }

    private static boolean isProductionProfile(Map<String, String> env) {
        String profile = env.getOrDefault(PROFILE_ENV, "dev").toLowerCase();
        return "production".equals(profile) || "prod".equals(profile);
    }

    private static boolean isRequiredAiMode(Map<String, String> env) {
        return !"stub".equalsIgnoreCase(env.getOrDefault(AGENT_LLM_MODE_ENV, "required").trim());
    }

    private static void validateDatabase(Map<String, String> env, List<String> errors) {
        String dbUrl = env.get(DB_URL_ENV);

        if (dbUrl == null || dbUrl.isBlank()) {
            // DB connection is optional for some lightweight services; warn but do not fail
            log.warn("YappcEnvironmentConfig: {} not set — database-backed features will be unavailable", DB_URL_ENV);
            return;
        }

        // When DB_URL is set, credentials become required
        if (isBlankOrAbsent(env, DB_USER_ENV)) {
            errors.add(DB_USER_ENV + " must be set when " + DB_URL_ENV + " is configured");
        }
        if (isBlankOrAbsent(env, DB_PASSWORD_ENV)) {
            errors.add(DB_PASSWORD_ENV + " must be set when " + DB_URL_ENV + " is configured");
        }
    }

    private static void validateTenantId(Map<String, String> env, List<String> errors) {
        String tenantId = env.get(TENANT_ID_ENV);
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("YappcEnvironmentConfig: {} not set — tenant ID must be provided via X-Tenant-ID header or JWT claims. " +
                    "Set this variable for service-to-service calls that require a default tenant context.", TENANT_ID_ENV);
        }
    }

    private static boolean isBlankOrAbsent(Map<String, String> env, String key) {
        String value = env.get(key);
        return value == null || value.isBlank();
    }

    // ─── Supporting types ─────────────────────────────────────────────────────

    /**
     * Immutable result of a non-throwing environment validation check.
     *
     * @param errors list of validation error messages; empty means valid
     */
    public record ValidationResult(List<String> errors) {

        /** {@code true} iff there are no validation errors. */
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * Thrown when startup environment validation fails.
     *
     * <p>Carries the full list of detected misconfigurations so operators can fix
     * all problems at once rather than discover them one by one.
     */
    public static final class YappcEnvironmentConfigException extends RuntimeException {

        public YappcEnvironmentConfigException(String message) {
            super(message);
        }
    }
}
