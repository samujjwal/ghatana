package com.ghatana.finance.ai;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fraud model endpoint configuration resolved from model metadata.
 *
 * @doc.type class
 * @doc.purpose Encapsulates finance fraud model endpoint and timeout settings
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class FraudModelEndpointConfig {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final String endpoint;
    private final Duration timeout;
    private final String modelVersion;

    public FraudModelEndpointConfig(String endpoint, Duration timeout, String modelVersion) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        this.endpoint = endpoint;
        this.timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
        this.modelVersion = modelVersion == null || modelVersion.isBlank() ? null : modelVersion;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public static FraudModelEndpointConfig fromMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        String endpoint = firstString(metadata, "endpoint", "fraud_endpoint", "prediction_endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }

        long timeoutMillis = firstLong(
            metadata,
            DEFAULT_TIMEOUT.toMillis(),
            "timeout_ms",
            "fraud_timeout_ms",
            "prediction_timeout_ms"
        );

        String modelVersion = firstString(metadata, "model_version", "modelVersion", "version");

        return new FraudModelEndpointConfig(endpoint, Duration.ofMillis(timeoutMillis), modelVersion);
    }

    private static String firstString(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static long firstLong(Map<String, Object> metadata, long fallback, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof Number number) {
                long parsed = number.longValue();
                return parsed > 0L ? parsed : fallback;
            }
            if (value instanceof String stringValue) {
                try {
                    long parsed = Long.parseLong(stringValue);
                    return parsed > 0L ? parsed : fallback;
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
        }
        return fallback;
    }
}
