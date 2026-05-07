/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates AEP's runtime configuration map for semantic correctness.
 *
 * <p>This validator goes beyond simple key-presence checks and enforces:
 * <ul>
 *   <li>Database URL scheme must start with {@code jdbc:}.</li>
 *   <li>DB pool size must be between 1 and 200 (inclusive); a small pool in
 *       development emits a warning instead of an error.</li>
 *   <li>{@code EVENT_CLOUD_TRANSPORT} must be one of {@code eventlog}, {@code grpc},
 *       or {@code http}; each transport variant has its own endpoint validation.</li>
 *   <li>Kafka broker addresses must follow {@code host:port} format.</li>
 *   <li>AWS region identifiers must match the {@code ll-word-n} pattern.
 *       S3 bucket names must satisfy AWS naming rules (lowercase, 3–63 chars,
 *       no consecutive hyphens, no uppercase).</li>
 *   <li>{@code APP_ENV} must be one of {@code development}, {@code staging},
 *       or {@code production}.</li>
 *   <li>The consolidation interval (when set) must be ≥ 1.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * Map<String, String> env = System.getenv();
 * AepConfigurationValidator validator =
 *     new AepConfigurationValidator(EnvConfig.fromMap(env), env);
 * validator.validate().throwIfInvalid();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Semantic validation of AEP runtime configuration at startup
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepConfigurationValidator {

    // ── AWS region pattern: e.g. us-east-1, eu-central-1, ap-southeast-2 ────
    private static final Pattern AWS_REGION_PATTERN =
        Pattern.compile("^[a-z]{2,3}-[a-z]+-[0-9]$");

    // ── S3 bucket: 3-63 chars, lowercase letters/digits/hyphens,
    //    no leading/trailing hyphen, no consecutive hyphens ───────────────────
    private static final Pattern S3_BUCKET_PATTERN =
        Pattern.compile("^[a-z0-9][a-z0-9\\-]{1,61}[a-z0-9]$");

    // ── host:port — colon is required, port is 1-65535 ───────────────────────
    private static final Pattern HOST_PORT_PATTERN =
        Pattern.compile("^[^:]+:(\\d{1,5})$");

    private static final int DB_POOL_MAX = 200;
    private static final int DB_POOL_SMALL_WARNING_THRESHOLD = 5;

    private final EnvConfig envConfig;
    private final Map<String, String> env;

    /**
     * Creates a new validator.
     *
     * @param envConfig typed accessor over the raw map (must not be {@code null})
     * @param env       raw configuration map (must not be {@code null})
     */
    public AepConfigurationValidator(EnvConfig envConfig, Map<String, String> env) {
        if (envConfig == null) throw new NullPointerException("envConfig must not be null");
        if (env == null)       throw new NullPointerException("env must not be null");
        this.envConfig = envConfig;
        this.env       = env;
    }

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Runs all validation rules and returns a {@link ValidationResult}.
     *
     * <p>This method never throws; use {@link ValidationResult#throwIfInvalid()}
     * to convert a failed result into an exception.
     *
     * @return validation result containing errors and warnings
     */
    public ValidationResult validate() {
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateDatabase(errors, warnings);
        validateAppEnv(errors, warnings);
        validateTransport(errors, warnings);
        validateKafka(errors, warnings);
        validateSqs(errors, warnings);
        validateS3(errors, warnings);
        validateConsolidationInterval(errors, warnings);

        return new ValidationResult(errors, warnings);
    }

    // =========================================================================
    //  Static helpers (public — tested directly)
    // =========================================================================

    /**
     * Returns {@code true} when {@code url} is a syntactically valid HTTP or
     * HTTPS URL (scheme must be {@code http} or {@code https}).
     *
     * @param url the string to test; {@code null} returns {@code false}
     * @return {@code true} if valid
     */
    public static boolean isValidHttpUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns {@code true} when {@code hostPort} has the form {@code host:port}
     * where port is an integer in the range 1–65535.
     *
     * @param hostPort the string to test; {@code null} returns {@code false}
     * @return {@code true} if valid
     */
    public static boolean isValidHostPort(String hostPort) {
        if (hostPort == null || hostPort.isBlank()) return false;
        var m = HOST_PORT_PATTERN.matcher(hostPort);
        if (!m.matches()) return false;
        try {
            int port = Integer.parseInt(m.group(1));
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Returns {@code true} when {@code name} is a valid AWS S3 bucket name:
     * 3–63 lowercase alphanumeric characters or hyphens, no consecutive hyphens,
     * no uppercase letters, must start and end with a letter or digit.
     *
     * @param name the bucket name to test; {@code null} returns {@code false}
     * @return {@code true} if valid
     */
    public static boolean isValidS3BucketName(String name) {
        if (name == null || name.length() < 3 || name.length() > 63) return false;
        if (!S3_BUCKET_PATTERN.matcher(name).matches()) return false;
        // Reject consecutive hyphens (S3 rule)
        return !name.contains("--");
    }

    // =========================================================================
    //  Private validators
    // =========================================================================

    private void validateDatabase(List<String> errors, List<String> warnings) {
        // DB URL — must start with jdbc:
        String dbUrl = env.get("AEP_DB_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            errors.add("AEP_DB_URL is required but not set");
        } else if (!dbUrl.startsWith("jdbc:")) {
            errors.add("AEP_DB_URL must start with 'jdbc:' but was: " + dbUrl);
        }

        // DB username
        String dbUser = env.get("AEP_DB_USERNAME");
        if (dbUser == null || dbUser.isBlank()) {
            errors.add("AEP_DB_USERNAME is required but was blank or absent");
        }

        // DB password
        String dbPass = env.get("AEP_DB_PASSWORD");
        if (dbPass == null || dbPass.isBlank()) {
            errors.add("AEP_DB_PASSWORD is required but was blank or absent");
        } else {
            // Minimum length enforcement (lenient in dev, stricter in production)
            int minLength = envConfig.isDevelopment() ? 8 : 16;
            if (dbPass.length() < minLength) {
                errors.add("AEP_DB_PASSWORD must be at least " + minLength
                    + " characters (current length=" + dbPass.length() + ")");
            }
        }

        // Pool size
        String poolSizeStr = env.get("AEP_DB_POOL_SIZE");
        if (poolSizeStr != null && !poolSizeStr.isBlank()) {
            try {
                int poolSize = Integer.parseInt(poolSizeStr.trim());
                if (poolSize < 1 || poolSize > DB_POOL_MAX) {
                    errors.add("AEP_DB_POOL_SIZE must be between 1 and " + DB_POOL_MAX
                        + " (inclusive), but was " + poolSize);
                } else if (poolSize <= DB_POOL_SMALL_WARNING_THRESHOLD) {
                    warnings.add("AEP_DB_POOL_SIZE=" + poolSize
                        + " is very small; consider increasing it for production workloads");
                }
            } catch (NumberFormatException e) {
                errors.add("AEP_DB_POOL_SIZE is not a valid integer: " + poolSizeStr);
            }
        }
    }

    private void validateAppEnv(List<String> errors, List<String> warnings) {
        String appEnv = env.get("APP_ENV");
        if (appEnv == null || appEnv.isBlank()) {
            // Not present → default to production behaviour; treat as valid
            return;
        }
        if (!"development".equalsIgnoreCase(appEnv)
            && !"staging".equalsIgnoreCase(appEnv)
            && !"production".equalsIgnoreCase(appEnv)) {
            errors.add("APP_ENV must be one of development, staging, production but was: " + appEnv);
            return;
        }
        // Production security check: warn if DC URL is a localhost address
        if ("production".equalsIgnoreCase(appEnv)) {
            String dcUrl = env.getOrDefault("AEP_DC_BASE_URL", "");
            if (dcUrl.contains("localhost") || dcUrl.contains("127.0.0.1")) {
                warnings.add("APP_ENV=production but AEP_DC_BASE_URL points to localhost (" + dcUrl + "); "
                    + "this is a security concern in production");
            }
        }
    }

    private void validateTransport(List<String> errors, List<String> warnings) {
        String transport = env.get("EVENT_CLOUD_TRANSPORT");
        if (transport == null || transport.isBlank()) {
            // Not set → skip (may use default)
            return;
        }
        switch (transport.toLowerCase()) {
            case "eventlog" -> { /* valid, no endpoint required */ }
            case "grpc" -> {
                String endpoint = env.get("AEP_GRPC_ENDPOINT");
                if (endpoint == null || endpoint.isBlank()) {
                    errors.add("EVENT_CLOUD_TRANSPORT=grpc requires AEP_GRPC_ENDPOINT to be set");
                } else if (!isValidHostPort(endpoint)) {
                    errors.add("AEP_GRPC_ENDPOINT must be in host:port format (1-65535), but was: " + endpoint);
                }
            }
            case "http" -> {
                String endpoint = env.get("HTTP_INGRESS_ENDPOINT");
                if (endpoint == null || endpoint.isBlank()) {
                    errors.add("EVENT_CLOUD_TRANSPORT=http requires HTTP_INGRESS_ENDPOINT to be set");
                } else if (!isValidHttpUrl(endpoint)) {
                    errors.add("HTTP_INGRESS_ENDPOINT must be a valid http(s) URL, but was: " + endpoint);
                }
            }
            default ->
                errors.add("EVENT_CLOUD_TRANSPORT must be one of eventlog, grpc, http but was: " + transport);
        }
    }

    private void validateKafka(List<String> errors, List<String> warnings) {
        String brokers = env.get("KAFKA_BOOTSTRAP_SERVERS");
        if (brokers == null || brokers.isBlank()) return; // Kafka not configured

        // Validate each broker address
        String[] brokerList = brokers.split(",");
        for (String broker : brokerList) {
            String trimmed = broker.trim();
            if (!isValidHostPort(trimmed)) {
                errors.add("KAFKA_BOOTSTRAP_SERVERS contains invalid broker address '"
                    + trimmed + "'; expected host:port format");
            }
        }

        // Consumer group is recommended; emit warning if absent
        String consumerGroup = env.get("KAFKA_CONSUMER_GROUP");
        if (consumerGroup == null || consumerGroup.isBlank()) {
            warnings.add("KAFKA_CONSUMER_GROUP is not set; using default may cause consumer group conflicts");
        }
    }

    private void validateSqs(List<String> errors, List<String> warnings) {
        String sqsQueue  = env.get("SQS_QUEUE_NAME");
        String sqsRegion = env.get("SQS_REGION");
        if (sqsQueue == null && sqsRegion == null) return; // SQS not configured

        if (sqsRegion != null && !sqsRegion.isBlank()) {
            if (!AWS_REGION_PATTERN.matcher(sqsRegion).matches()) {
                errors.add("SQS_REGION is not a valid AWS region identifier: " + sqsRegion);
            }
        }
    }

    private void validateS3(List<String> errors, List<String> warnings) {
        String s3Bucket = env.get("S3_BUCKET");
        String s3Region = env.get("S3_REGION");
        if (s3Bucket == null && s3Region == null) return; // S3 not configured

        if (s3Bucket != null && !s3Bucket.isBlank()) {
            if (!isValidS3BucketName(s3Bucket)) {
                errors.add("S3_BUCKET '" + s3Bucket + "' is not a valid S3 bucket name; "
                    + "must be 3-63 lowercase alphanumeric characters or hyphens, "
                    + "no consecutive hyphens, no uppercase");
            }
        }
        if (s3Region != null && !s3Region.isBlank()) {
            if (!AWS_REGION_PATTERN.matcher(s3Region).matches()) {
                errors.add("S3_REGION is not a valid AWS region identifier: " + s3Region);
            }
        }
    }

    private void validateConsolidationInterval(List<String> errors, List<String> warnings) {
        String intervalStr = env.get("AEP_CONSOLIDATION_INTERVAL_HOURS");
        if (intervalStr == null || intervalStr.isBlank()) return;
        try {
            int interval = Integer.parseInt(intervalStr.trim());
            if (interval < 1) {
                errors.add("AEP_CONSOLIDATION_INTERVAL_HOURS must be >= 1, but was " + interval);
            }
        } catch (NumberFormatException e) {
            errors.add("AEP_CONSOLIDATION_INTERVAL_HOURS is not a valid integer: " + intervalStr);
        }
    }

    // =========================================================================
    //  ValidationResult
    // =========================================================================

    /**
     * Immutable result of a validation run.
     *
     * @doc.type class
     * @doc.purpose Carries errors and warnings from an AEP configuration validation
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public static final class ValidationResult {

        private final List<String> errors;
        private final List<String> warnings;

        ValidationResult(List<String> errors, List<String> warnings) {
            this.errors   = Collections.unmodifiableList(new ArrayList<>(errors));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        /**
         * Returns {@code true} when there are no validation errors.
         *
         * <p>Warnings do not affect validity.
         *
         * @return {@code true} if valid
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Returns the list of validation errors.
         *
         * @return unmodifiable list of error messages (never {@code null})
         */
        public List<String> errors() {
            return errors;
        }

        /**
         * Returns the list of validation warnings.
         *
         * @return unmodifiable list of warning messages (never {@code null})
         */
        public List<String> warnings() {
            return warnings;
        }

        /**
         * Throws {@link IllegalStateException} when there are validation errors.
         *
         * @throws IllegalStateException if {@link #isValid()} is {@code false},
         *                               with a message starting with
         *                               {@code "AEP configuration is invalid"}
         */
        public void throwIfInvalid() {
            if (!isValid()) {
                throw new IllegalStateException(
                    "AEP configuration is invalid. Errors: " + errors);
            }
        }

        @Override
        public String toString() {
            return "ValidationResult{valid=" + isValid()
                + ", errors=" + errors.size()
                + ", warnings=" + warnings.size()
                + '}';
        }
    }
}
