/**
 * Platform configuration management: loading, merging, and validating service configuration.
 *
 * <h2>Environment Variable Naming Convention (CFG-001)</h2>
 *
 * <p>All environment variables consumed by a service <strong>must</strong> use
 * {@code SCREAMING_SNAKE_CASE} and be namespaced by a service-specific prefix
 * so that variables from different services are unambiguous in shared environments:</p>
 *
 * <pre>
 * AUTH_JWT_SECRET            ← JWT_SECRET owned by auth-gateway
 * AUTH_JWT_EXPIRY_MS
 * AUTH_DB_HOST
 * AUTH_DB_PORT
 * AUTH_DB_POOL_MIN_IDLE
 * AI_INFER_PORT
 * AI_INFER_MODEL_TIMEOUT_MS
 * DATA_CLOUD_DB_HOST
 * </pre>
 *
 * <p>Common cross-service variables (e.g. {@code PORT}, {@code LOG_LEVEL}) may omit the
 * prefix only when they are intentionally shared and documented as canonical.</p>
 *
 * <h2>Standard Usage Pattern</h2>
 *
 * <pre>{@code
 * // Bootstrap once in the service module/launcher — not on every call site:
 * ConfigManager config = ConfigManager.createDefault("my-service");
 *
 * // Required values — fail fast at startup if absent:
 * String secret = config.getString("JWT_SECRET")
 *     .orElseThrow(() -> new IllegalStateException("JWT_SECRET must be set"));
 *
 * // Optional values with safe defaults:
 * int port = config.getInt("PORT").orElse(8080);
 * long expiryMs = config.getLong("JWT_EXPIRY_MS").orElse(3_600_000L);
 * boolean jdbcCreds = config.getBoolean("USE_JDBC_CREDENTIALS").orElse(false);
 * }</pre>
 *
 * <h2>Configuration Validation (CFG-002)</h2>
 *
 * <p>Validate all required configuration at service startup, before the HTTP server
 * binds. Never silently default for secrets or critical infrastructure URLs:</p>
 *
 * <pre>{@code
 * // Acceptable pattern — centralized startup validation:
 * private static void validateConfig(ConfigManager config) {
 *     config.getString("JWT_SECRET")
 *         .filter(s -> !s.isBlank())
 *         .orElseThrow(() -> new IllegalStateException("JWT_SECRET must be non-blank"));
 *     config.getString("DB_HOST")
 *         .orElseThrow(() -> new IllegalStateException("DB_HOST is required"));
 * }
 * }</pre>
 *
 * <p>Use {@link com.ghatana.platform.config.validation.ConfigValidator} for declarative
 * constraint expressions when the touched module already uses it.</p>
 *
 * <h2>Source Priority (highest to lowest)</h2>
 * <ol>
 * <li>System properties ({@code -Dkey=value})</li>
 * <li>Environment variables</li>
 * <li>YAML file ({@code config/&lt;service&gt;.yml})</li>
 * <li>In-memory defaults registered programmatically</li>
 * </ol>
 */
package com.ghatana.platform.config;
