package com.ghatana.platform.governance;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Provides hints to the storage layer about how to optimize
 * the storage and retrieval of events of a specific type.
 *
 * @doc.type class
 * @doc.purpose Storage optimization hints for event type storage and retrieval
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class StorageHints {
    private final String storageFormat;
    private final String compression;
    private final Map<String, String> properties;
    private final int ttlDays;
    private final boolean enableIndexing;
    private final boolean enableCaching;

    private StorageHints(Builder builder) {
        this.storageFormat = builder.storageFormat;
        this.compression = builder.compression;
        this.properties = Collections.unmodifiableMap(new HashMap<>(builder.properties));
        this.ttlDays = builder.ttlDays;
        this.enableIndexing = builder.enableIndexing;
        this.enableCaching = builder.enableCaching;
    }

    public String getStorageFormat() {
        return storageFormat;
    }

    public String getCompression() {
        return compression;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public int getTtlDays() {
        return ttlDays;
    }

    public boolean isIndexingEnabled() {
        return enableIndexing;
    }

    public boolean isCachingEnabled() {
        return enableCaching;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StorageHints that = (StorageHints) o;
        return ttlDays == that.ttlDays &&
            enableIndexing == that.enableIndexing &&
            enableCaching == that.enableCaching &&
            Objects.equals(storageFormat, that.storageFormat) &&
            Objects.equals(compression, that.compression) &&
            properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageFormat, compression, properties, ttlDays, enableIndexing, enableCaching);
    }

    @Override
    public String toString() {
        return "StorageHints{" +
            "storageFormat='" + storageFormat + '\'' +
            ", compression='" + compression + '\'' +
            ", properties=" + properties +
            ", ttlDays=" + ttlDays +
            ", enableIndexing=" + enableIndexing +
            ", enableCaching=" + enableCaching +
            '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StorageHints defaults() {
        return builder().build();
    }

    public static final class Builder {
        private String storageFormat = "JSON";
        private String compression = "GZIP";
        private final Map<String, String> properties = new HashMap<>();
        private int ttlDays = -1;
        private boolean enableIndexing = true;
        private boolean enableCaching = true;

        private Builder() {
        }

        public Builder withStorageFormat(String storageFormat) {
            this.storageFormat = Objects.requireNonNull(storageFormat, "Storage format cannot be null");
            return this;
        }

        public Builder withCompression(String compression) {
            this.compression = Objects.requireNonNull(compression, "Compression cannot be null");
            return this;
        }

        public Builder withProperty(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        public Builder withProperties(Map<String, String> properties) {
            this.properties.putAll(properties);
            return this;
        }

        public Builder withTtlDays(int ttlDays) {
            if (ttlDays < -1) {
                throw new IllegalArgumentException("TTL must be -1 or greater");
            }
            this.ttlDays = ttlDays;
            return this;
        }

        public Builder withIndexing(boolean enableIndexing) {
            this.enableIndexing = enableIndexing;
            return this;
        }

        public Builder withCaching(boolean enableCaching) {
            this.enableCaching = enableCaching;
            return this;
        }

        public StorageHints build() {
            return new StorageHints(this);
        }
    }
}
