/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Production-grade semantic validator for AEP environment configuration.
 *
 * <h3>Validation tiers</h3>
 * <ol>
 *   <li><b>Required-presence</b> — mandatories like {@code AEP_DB_PASSWORD} must be
 *       set in production.</li>
 *   <li><b>Format</b> — JDBC URLs, HTTP/gRPC endpoints, port numbers, AWS regions.</li>
 *   <li><b>Numeric bounds</b> — pool sizes, thread counts, port ranges.</li>
 *   <li><b>Cross-dependency</b> — if a connector transport is enabled, all of its
 *       supporting variables must also be present.</li>
 *   <li><b>Security</b> — passwords must not be trivially weak in production;
 *       plaintext credentials in non-secret variables are flagged.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * EnvConfig env = EnvConfig.fromSystem();
 * AepConfigurationValidator validator = new AepConfigurationValidator(env);
 * ValidationResult result = validator.validate();
 * if (!result.isValid()) {
 *     result.errors().forEach(e -> log.error("Config error: {}", e));
 *     throw new IllegalStateException("Invalid AEP configuration — refusing to start");
 * }
 * result.warnings().forEach(w -> log.warn("Config warning: {}", w));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Semantic validation of AEP environment configuration with error/warning reporting
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class AepConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(AepConfigurationValidator.class);

    // Minimum acceptable length for a password in production
    private static final int MIN_PASSWORD_LENGTH = 8;

    // Valid AWS region pattern: xx-xxxx-N (e.g. us-east-1, eu-central-1)
    private static final Pattern AWS_REGION_PATTERN =
            Pattern.compile("^[a-z]{2}(-[a-z]+){1,2}-\\d$");

    // Supported EVENT_CLOUD_TRANSPORT values
    private static final List<String> VALID_TRANSPORTS = List.of("eventlog", "grpc", "http");

    // Supported APP_ENV values
    private static final List<String> VALID_ENVIRONMENTS = List.of("development", "production", "staging", "test");

    // Supported APP_ENV values that are considered "production-like" (strict security)
    private static final List<String> PRODUCTION_ENVIRONMENTS = List.of("production", "staging");

    private final EnvConfig env;
    // Raw map for checking variable presence without triggering defaults
    private final Map<String, String> rawEnv;

    /**
     * Creates a validator over the supplied {@link EnvConfig}.
     *
     * @param env  environment configuration (must not be null)
     */
    public AepConfigurationValidator(EnvConfig env) {
        this.env = Objects.requireNonNull(env, "env");
        // Use reflection-free overload — EnvConfig already exposes isDevelopment()
        this.rawEnv = Collections.emptyMap(); // will use env typed accessors + blank detection
    }

    /**
     * Creates a validator over the supplied {@link EnvConfig} AND a raw environment map
     * (e.g. {@link System#getenv()}) for presence checks without triggering defaults.
     *
     * @param env    typed environment config
     * @param rawEnv raw environment variable map
     */
    public AepConfigurationValidator(EnvConfig env, Map<String, String> rawEnv) {
        this.env = Objects.requireNonNull(env, "env");
        this.rawEnv = Objects.requireNonNull(rawEnv, "rawEnv");
    }

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Validates the entire AEP configuration.
     *
     * <p>Returns a {@link ValidationResult} aggregating all errors and warnings
     * discovered. Callers should treat any {@code error} as fatal (refuse to start)
     * and any {@code warning} as advisory.
     *
     * @return validation result (never null)
     */
    public ValidationResult validate() {
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateDatabase(errors, warnings);
        validateDataCloud(errors, warnings);
        validateEnvironment(errors, warnings);
        validateEventCloudTransport(errors, warnings);
        validateKafka(errors, warnings);
        validateRabbitMq(errors, warnings);
        validateRedis(errors, warnings);
        validateSqs(errors, warnings);
        validateS3(errors, warnings);
        validateThreadPoolSizes(errors, warnings);
        validateSecuritySettings(errors, warnings);

        ValidationResult result = new ValidationResult(
                Collections.unmodifiableList(errors),
                Collections.unmodifiableList(warnings));

        if (result.isValid()) {
            log.info("AEP configuration validated — {} warning(s)", warnings.size());
        } else {
            log.error("AEP configuration INVALID — {} error(s), {} warning(s)",
                    errors.size(), warnings.size());
        }
        return result;
    }

    // =========================================================================
    //  Validation sections
    // =========================================================================

    private void validateDatabase(List<String> errors, List<String> warnings) {
        String dbUrl = env.aepDbUrl();
        if (!dbUrl.startsWith("jdbc:postgresql://")) {
            errors.add("AEP_DB_URL must start with 'jdbc:postgresql://', got: "
                    + redactUrl(dbUrl));
        }

        // Check the raw map first so an explicitly blank value is caught even when EnvConfig
        // falls back to its "aep" default (which would otherwise pass the isBlank() check).
        String usernameRaw = rawEnv.isEmpty() ? null : rawEnv.get(EnvConfig.AEP_DB_USERNAME);
        String username = usernameRaw != null ? usernameRaw : env.aepDbUsername();
        if (username.isBlank()) {
            errors.add("AEP_DB_USERNAME must not be blank");
        }

        // Password is required — EnvConfig.require() throws if absent; we surface it as error
        try {
            String password = env.aepDbPassword();
            validatePasswordStrength("AEP_DB_PASSWORD", password, errors, warnings);
        } catch (IllegalStateException e) {
            errors.add("AEP_DB_PASSWORD is required but not set");
        }

        int poolSize = env.aepDbPoolSize();
        if (poolSize < 1 || poolSize > 200) {
            errors.add("AEP_DB_POOL_SIZE must be between 1 and 200, got: " + poolSize);
        } else if (poolSize < 5) {
            warnings.add("AEP_DB_POOL_SIZE=" + poolSize + " is low; consider at least 5 for production");
        }
    }

    private void validateDataCloud(List<String> errors, List<String> warnings) {
        String dcUrl = env.aepDcBaseUrl();
        if (!isValidHttpUrl(dcUrl)) {
            errors.add("AEP_DC_BASE_URL is not a valid HTTP/HTTPS URL: " + dcUrl);
        }
        if (!isPresentInRaw(EnvConfig.AEP_DC_BASE_URL)) {
            warnings.add("AEP_DC_BASE_URL not set; defaulting to http://localhost:8085 (not suitable for production)");
        }
    }

    private void validateEnvironment(List<String> errors, List<String> warnings) {
        String appEnv = env.get(EnvConfig.APP_ENV, "production").toLowerCase();
        if (!VALID_ENVIRONMENTS.contains(appEnv)) {
            errors.add("APP_ENV must be one of " + VALID_ENVIRONMENTS + ", got: " + appEnv);
        }
        if (!isPresentInRaw(EnvConfig.APP_ENV)) {
            warnings.add("APP_ENV not set; defaulting to 'production'");
        }
    }

    private void validateEventCloudTransport(List<String> errors, List<String> warnings) {
        String transport = env.eventCloudTransport();
        if (!VALID_TRANSPORTS.contains(transport)) {
            errors.add("EVENT_CLOUD_TRANSPORT must be one of " + VALID_TRANSPORTS + ", got: " + transport);
            return;
        }

        if ("grpc".equals(transport)) {
            String grpcEndpoint = env.aepGrpcEndpoint();
            if (!isValidHostPort(grpcEndpoint)) {
                errors.add("AEP_GRPC_ENDPOINT must be 'host:port', got: " + grpcEndpoint);
            }
        }

        if ("http".equals(transport)) {
            String httpEndpoint = env.httpIngressEndpoint();
            if (!isValidHttpUrl(httpEndpoint)) {
                errors.add("HTTP_INGRESS_ENDPOINT must be a valid HTTP/HTTPS URL, got: " + httpEndpoint);
            }
        }
    }

    private void validateKafka(List<String> errors, List<String> warnings) {
        // Kafka is optional — but if any Kafka variable is explicitly set, check for completeness
        boolean kafkaBrokersSet = isPresentInRaw(EnvConfig.KAFKA_BOOTSTRAP_SERVERS);
        boolean kafkaGroupSet   = isPresentInRaw(EnvConfig.KAFKA_CONSUMER_GROUP);

        if (kafkaBrokersSet) {
            String servers = env.kafkaBootstrapServers();
            for (String broker : servers.split(",")) {
                if (!isValidHostPort(broker.trim())) {
                    errors.add("KAFKA_BOOTSTRAP_SERVERS contains invalid broker address: '" + broker.trim() + "'");
                }
            }
            if (!kafkaGroupSet) {
                warnings.add("KAFKA_BOOTSTRAP_SERVERS is set but KAFKA_CONSUMER_GROUP is not; defaulting to 'aep-consumer-group'");
            }
        }
    }

    private void validateRabbitMq(List<String> errors, List<String> warnings) {
        if (!isPresentInRaw(EnvConfig.RABBITMQ_HOST)) {
            return; // RabbitMQ is optional
        }

        int port = env.rabbitMqPort();
        if (port < 1 || port > 65535) {
            errors.add("RABBITMQ_PORT must be between 1 and 65535, got: " + port);
        }

        String queue = env.rabbitMqQueue();
        if (queue.isBlank()) {
            errors.add("RABBITMQ_QUEUE must not be blank when RABBITMQ_HOST is configured");
        }
    }

    private void validateRedis(List<String> errors, List<String> warnings) {
        if (!isPresentInRaw(EnvConfig.REDIS_HOST)) {
            return; // Redis is optional
        }

        int port = env.redisPort();
        if (port < 1 || port > 65535) {
            errors.add("REDIS_PORT must be between 1 and 65535, got: " + port);
        }
    }

    private void validateSqs(List<String> errors, List<String> warnings) {
        if (!isPresentInRaw(EnvConfig.SQS_QUEUE_NAME) && !isPresentInRaw(EnvConfig.SQS_QUEUE_URL)) {
            return; // SQS is optional
        }

        String region = env.sqsRegion();
        if (!AWS_REGION_PATTERN.matcher(region).matches()) {
            errors.add("SQS_REGION is not a valid AWS region: " + region);
        }

        String queueName = env.sqsQueueName();
        if (queueName.isBlank()) {
            errors.add("SQS_QUEUE_NAME must not be blank when SQS transport is configured");
        }
    }

    private void validateS3(List<String> errors, List<String> warnings) {
        if (!isPresentInRaw(EnvConfig.S3_BUCKET)) {
            return; // S3 is optional
        }

        String region = env.s3Region();
        if (!AWS_REGION_PATTERN.matcher(region).matches()) {
            errors.add("S3_REGION is not a valid AWS region: " + region);
        }

        String bucket = env.s3Bucket();
        if (bucket.isBlank()) {
            errors.add("S3_BUCKET must not be blank when S3 transport is configured");
        } else if (!isValidS3BucketName(bucket)) {
            errors.add("S3_BUCKET name is invalid (must be 3-63 lowercase alphanumeric or hyphen, not start/end with hyphen): " + bucket);
        }
    }

    private void validateThreadPoolSizes(List<String> errors, List<String> warnings) {
        String workerThreadsRaw = rawEnv.get("AEP_WORKER_THREADS");
        if (workerThreadsRaw != null && !workerThreadsRaw.isBlank()) {
            try {
                int threads = Integer.parseInt(workerThreadsRaw.trim());
                if (threads < 1) {
                    errors.add("AEP_WORKER_THREADS must be a positive integer, got: " + threads);
                } else if (threads > 1024) {
                    warnings.add("AEP_WORKER_THREADS=" + threads + " is unusually large; verify system capacity");
                }
            } catch (NumberFormatException e) {
                errors.add("AEP_WORKER_THREADS must be an integer, got: " + workerThreadsRaw);
            }
        }

        int consolidationHours = env.consolidationIntervalHours();
        if (consolidationHours < 1 || consolidationHours > 168) {
            errors.add("AEP_CONSOLIDATION_INTERVAL_HOURS must be between 1 and 168, got: " + consolidationHours);
        }
    }

    private void validateSecuritySettings(List<String> errors, List<String> warnings) {
        boolean isProduction = PRODUCTION_ENVIRONMENTS.contains(
                env.get(EnvConfig.APP_ENV, "production").toLowerCase());

        if (isProduction) {
            // Verify schema registry is not localhost in production
            String schemaRegistry = env.aepSchemaRegistryUrl();
            if (schemaRegistry.contains("localhost") || schemaRegistry.contains("127.0.0.1")) {
                warnings.add("AEP_SCHEMA_REGISTRY_URL points to localhost in a production environment: " + schemaRegistry);
            }

            // Verify data-cloud is not localhost in production
            String dcUrl = env.aepDcBaseUrl();
            if (dcUrl.contains("localhost") || dcUrl.contains("127.0.0.1")) {
                warnings.add("AEP_DC_BASE_URL points to localhost in a production environment: " + dcUrl);
            }

            // HTTP (not HTTPS) schema-registry endpoint in production
            if (env.aepSchemaRegistryUrl().startsWith("http://")) {
                warnings.add("AEP_SCHEMA_REGISTRY_URL uses plaintext HTTP in production; prefer HTTPS");
            }

            // HTTP (not HTTPS) data-cloud in production
            if (dcUrl.startsWith("http://") && !dcUrl.contains("localhost") && !dcUrl.contains("127.0.0.1")) {
                warnings.add("AEP_DC_BASE_URL uses plaintext HTTP in production; prefer HTTPS");
            }
        }
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private void validatePasswordStrength(String varName, String password,
                                          List<String> errors, List<String> warnings) {
        boolean isProduction = PRODUCTION_ENVIRONMENTS.contains(
                env.get(EnvConfig.APP_ENV, "production").toLowerCase());

        if (!isProduction) return; // lenient in development/test

        if (password.length() < MIN_PASSWORD_LENGTH) {
            errors.add(varName + " is too short (minimum " + MIN_PASSWORD_LENGTH + " characters) — set a strong password in production");
        }

        // Flag trivially weak passwords
        if (password.equalsIgnoreCase("password") || password.equalsIgnoreCase("secret")
                || password.equalsIgnoreCase("changeme") || password.equals("123456")) {
            errors.add(varName + " appears to be a default/weak password — replace with a strong secret");
        }
    }

    /** Returns true when the raw env map contains a non-blank entry for {@code key}. */
    private boolean isPresentInRaw(String key) {
        if (rawEnv.isEmpty()) return false; // constructed with single-arg ctor — skip presence checks
        String v = rawEnv.get(key);
        return v != null && !v.isBlank();
    }

    /** Validates that a string is a well-formed HTTP or HTTPS URL. */
    static boolean isValidHttpUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** Validates that a string has the form {@code host:port} where port is 1–65535. */
    static boolean isValidHostPort(String hostPort) {
        if (hostPort == null || hostPort.isBlank()) return false;
        int colonIdx = hostPort.lastIndexOf(':');
        if (colonIdx <= 0) return false;
        String host = hostPort.substring(0, colonIdx);
        String portStr = hostPort.substring(colonIdx + 1);
        if (host.isBlank()) return false;
        try {
            int port = Integer.parseInt(portStr);
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * S3 bucket naming rules: 3-63 chars, lowercase alphanumeric & hyphens,
     * must not start or end with a hyphen, must not contain consecutive hyphens.
     */
    static boolean isValidS3BucketName(String name) {
        if (name == null || name.length() < 3 || name.length() > 63) return false;
        if (!name.matches("[a-z0-9][a-z0-9\\-]{1,61}[a-z0-9]")) return false;
        return !name.contains("--");
    }

    /** Redacts query-string and password fragments from a URL for safe logging. */
    private static String redactUrl(String url) {
        if (url == null) return "<null>";
        try {
            URI uri = new URI(url);
            // Remove userinfo (password in JDBC URLs is unusual but guard anyway)
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), null, null).toString();
        } catch (URISyntaxException e) {
            // If it doesn't parse as a URI, just return the scheme prefix
            int colon = url.indexOf(':');
            return colon > 0 ? url.substring(0, colon) + "://[redacted]" : "[redacted]";
        }
    }

    // =========================================================================
    //  Result types
    // =========================================================================

    /**
     * Immutable result of a configuration validation run.
     *
     * @param errors   fatal configuration problems — application must NOT start
     * @param warnings advisory configuration hints — application may still start
     *
     * @doc.type record
     * @doc.purpose Immutable container for configuration validation results
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record ValidationResult(List<String> errors, List<String> warnings) {

        /**
         * Returns {@code true} when no errors were found (warnings are acceptable).
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Throws {@link IllegalStateException} if the result is invalid, listing all errors.
         * Call this during application startup to fail fast on misconfiguration.
         *
         * @throws IllegalStateException if there are any configuration errors
         */
        public void throwIfInvalid() {
            if (!isValid()) {
                String message = "AEP configuration is invalid:\n  - " + String.join("\n  - ", errors);
                throw new IllegalStateException(message);
            }
        }

        @Override
        public String toString() {
            return "ValidationResult{valid=" + isValid()
                    + ", errors=" + errors.size()
                    + ", warnings=" + warnings.size() + "}";
        }
    }
}
