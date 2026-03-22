package com.ghatana.aiplatform.featurestore;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Feature value object representing a single ML feature.
 *
 * @doc.type class
 * @doc.purpose ML feature value object
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public final class Feature {

    private final String name;
    private final String entityId;
    private final double value;
    private final Instant timestamp;
    private final String version;
    private final Map<String, String> metadata;

    private Feature(Builder builder) {
        this.name = Objects.requireNonNull(builder.name);
        this.entityId = Objects.requireNonNull(builder.entityId);
        this.value = builder.value;
        this.timestamp = Objects.requireNonNull(builder.timestamp);
        this.version = builder.version != null ? builder.version : "v1";
        this.metadata = builder.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getEntityId() {
        return entityId;
    }

    public double getValue() {
        return value;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public static final class Builder {

        private String name;
        private String entityId;
        private double value;
        private Instant timestamp = Instant.now();
        private String version;
        private Map<String, String> metadata;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder value(double value) {
            this.value = value;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Feature build() {
            return new Feature(this);
        }
    }
}
