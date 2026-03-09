package com.ghatana.datacloud.infrastructure.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for BlobStorageConnector.
 *
 * <p><b>Purpose</b><br>
 * Immutable configuration value object for S3-compatible blob storage.
 * Supports AWS S3, MinIO, and other S3-compatible storage backends.
 * Consolidates connection parameters, bucket settings, and transfer configuration.
 *
 * <p><b>Architecture Role</b><br>
 * Configuration value object in infrastructure layer. Used by:
 * - {@link BlobStorageConnector} for S3 client setup
 * - Application configuration loading
 * - Environment-specific configuration overrides
 *
 * <p><b>Configuration Components</b><br>
 * <ul>
 *   <li><b>Connection Settings</b>: region, endpoint, credentials</li>
 *   <li><b>Bucket Settings</b>: bucketName, keyPrefix</li>
 *   <li><b>Transfer Settings</b>: multipartThreshold, presignedUrlExpiry</li>
 * </ul>
 *
 * <p><b>Environment Variables</b><br>
 * Configuration can be loaded from environment variables:
 * <ul>
 *   <li>{@code AWS_REGION} - AWS region (e.g., us-east-1)</li>
 *   <li>{@code AWS_ACCESS_KEY_ID} - AWS access key ID</li>
 *   <li>{@code AWS_SECRET_ACCESS_KEY} - AWS secret access key</li>
 *   <li>{@code S3_BUCKET_NAME} - S3 bucket name</li>
 *   <li>{@code S3_ENDPOINT} - Custom endpoint for MinIO/LocalStack</li>
 *   <li>{@code S3_KEY_PREFIX} - Key prefix for namespace isolation</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // AWS S3 configuration
 * BlobStorageConnectorConfig config = BlobStorageConnectorConfig.builder()
 *     .region("us-east-1")
 *     .bucketName("my-data-bucket")
 *     .keyPrefix("data-cloud/")
 *     .build();
 *
 * // MinIO configuration (local development)
 * BlobStorageConnectorConfig config = BlobStorageConnectorConfig.builder()
 *     .endpoint(URI.create("http://localhost:9000"))
 *     .accessKeyId("minioadmin")
 *     .secretAccessKey("minioadmin")
 *     .bucketName("local-bucket")
 *     .pathStyleAccess(true)
 *     .build();
 *
 * // From environment variables
 * BlobStorageConnectorConfig config = BlobStorageConnectorConfig.fromEnvironment();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - all fields final. Safe to share across threads.
 *
 * @see BlobStorageConnector
 * @doc.type class
 * @doc.purpose Configuration for BlobStorageConnector with S3 connection settings
 * @doc.layer product
 * @doc.pattern Value Object, Configuration
 */
public final class BlobStorageConnectorConfig {

    private final String region;
    private final URI endpoint;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String bucketName;
    private final String keyPrefix;
    private final boolean pathStyleAccess;
    private final long multipartThresholdBytes;
    private final Duration presignedUrlExpiry;
    private final Duration timeout;
    private final boolean encryptionEnabled;
    private final String encryptionKey;

    private BlobStorageConnectorConfig(Builder builder) {
        this.region = builder.region;
        this.endpoint = builder.endpoint;
        this.accessKeyId = builder.accessKeyId;
        this.secretAccessKey = builder.secretAccessKey;
        this.bucketName = builder.bucketName;
        this.keyPrefix = builder.keyPrefix;
        this.pathStyleAccess = builder.pathStyleAccess;
        this.multipartThresholdBytes = builder.multipartThresholdBytes;
        this.presignedUrlExpiry = builder.presignedUrlExpiry;
        this.timeout = builder.timeout;
        this.encryptionEnabled = builder.encryptionEnabled;
        this.encryptionKey = builder.encryptionKey;
    }

    /**
     * AWS region (e.g., us-east-1).
     *
     * @return region or null if using custom endpoint
     */
    public String getRegion() {
        return region;
    }

    /**
     * Custom endpoint URI for MinIO/LocalStack.
     *
     * @return endpoint URI or null for AWS S3
     */
    public URI getEndpoint() {
        return endpoint;
    }

    /**
     * AWS access key ID.
     *
     * @return access key ID or null for default credentials
     */
    public String getAccessKeyId() {
        return accessKeyId;
    }

    /**
     * AWS secret access key.
     *
     * @return secret access key or null for default credentials
     */
    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    /**
     * S3 bucket name.
     *
     * @return bucket name (required)
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * Key prefix for namespace isolation.
     *
     * @return key prefix (default: "dc:blob:")
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Whether to use path-style access (required for MinIO).
     *
     * @return true for path-style, false for virtual-hosted style
     */
    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    /**
     * Threshold for multipart uploads in bytes.
     *
     * @return threshold in bytes (default: 5MB)
     */
    public long getMultipartThresholdBytes() {
        return multipartThresholdBytes;
    }

    /**
     * Expiry duration for presigned URLs.
     *
     * @return expiry duration (default: 1 hour)
     */
    public Duration getPresignedUrlExpiry() {
        return presignedUrlExpiry;
    }

    /**
     * Connection and operation timeout.
     *
     * @return timeout duration (default: 30 seconds)
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Whether encryption is enabled for stored data.
     *
     * @return true if encryption is enabled
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    /**
     * Base64-encoded encryption key for AES-256-GCM encryption.
     *
     * @return encryption key or null if encryption is disabled
     */
    public String getEncryptionKey() {
        return encryptionKey;
    }

    /**
     * Creates a new builder with default values.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with this config's values.
     *
     * @return builder with current values
     */
    public Builder toBuilder() {
        return new Builder()
                .region(region)
                .endpoint(endpoint)
                .accessKeyId(accessKeyId)
                .secretAccessKey(secretAccessKey)
                .bucketName(bucketName)
                .keyPrefix(keyPrefix)
                .pathStyleAccess(pathStyleAccess)
                .multipartThresholdBytes(multipartThresholdBytes)
                .presignedUrlExpiry(presignedUrlExpiry)
                .timeout(timeout)
                .encryptionEnabled(encryptionEnabled)
                .encryptionKey(encryptionKey);
    }

    /**
     * Creates configuration from environment variables.
     * Falls back to defaults if environment variables are not set.
     *
     * @return configuration from environment
     */
    public static BlobStorageConnectorConfig fromEnvironment() {
        Builder builder = builder();

        String region = System.getenv("AWS_REGION");
        if (region != null && !region.isEmpty()) {
            builder.region(region);
        }

        String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        if (accessKeyId != null && !accessKeyId.isEmpty()) {
            builder.accessKeyId(accessKeyId);
        }

        String secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        if (secretAccessKey != null && !secretAccessKey.isEmpty()) {
            builder.secretAccessKey(secretAccessKey);
        }

        String bucketName = System.getenv("S3_BUCKET_NAME");
        if (bucketName != null && !bucketName.isEmpty()) {
            builder.bucketName(bucketName);
        }

        String endpoint = System.getenv("S3_ENDPOINT");
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpoint(URI.create(endpoint));
        }

        String keyPrefix = System.getenv("S3_KEY_PREFIX");
        if (keyPrefix != null && !keyPrefix.isEmpty()) {
            builder.keyPrefix(keyPrefix);
        }

        String pathStyle = System.getenv("S3_PATH_STYLE_ACCESS");
        if ("true".equalsIgnoreCase(pathStyle)) {
            builder.pathStyleAccess(true);
        }

        String encryptionEnabled = System.getenv("S3_ENCRYPTION_ENABLED");
        if ("true".equalsIgnoreCase(encryptionEnabled)) {
            builder.encryptionEnabled(true);
        }

        String encryptionKey = System.getenv("S3_ENCRYPTION_KEY");
        if (encryptionKey != null && !encryptionKey.isEmpty()) {
            builder.encryptionKey(encryptionKey);
        }

        return builder.build();
    }

    @Override
    public String toString() {
        return "BlobStorageConnectorConfig{" +
                "region='" + region + '\'' +
                ", endpoint=" + endpoint +
                ", bucketName='" + bucketName + '\'' +
                ", keyPrefix='" + keyPrefix + '\'' +
                ", pathStyleAccess=" + pathStyleAccess +
                ", multipartThresholdBytes=" + multipartThresholdBytes +
                ", presignedUrlExpiry=" + presignedUrlExpiry +
                ", timeout=" + timeout +
                ", encryptionEnabled=" + encryptionEnabled +
                ", encryptionKey=" + (encryptionKey != null ? "***" : "null") +
                '}';
    }

    /**
     * Builder for BlobStorageConnectorConfig.
     */
    public static final class Builder {
        private String region = "us-east-1";
        private URI endpoint;
        private String accessKeyId;
        private String secretAccessKey;
        private String bucketName = "data-cloud";
        private String keyPrefix = "dc:blob:";
        private boolean pathStyleAccess = false;
        private long multipartThresholdBytes = 5 * 1024 * 1024; // 5MB
        private Duration presignedUrlExpiry = Duration.ofHours(1);
        private Duration timeout = Duration.ofSeconds(30);
        private boolean encryptionEnabled = false;
        private String encryptionKey;

        private Builder() {
        }

        /**
         * Sets the AWS region.
         *
         * @param region AWS region (e.g., us-east-1)
         * @return this builder
         */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /**
         * Sets a custom endpoint for MinIO/LocalStack.
         *
         * @param endpoint endpoint URI
         * @return this builder
         */
        public Builder endpoint(URI endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the AWS access key ID.
         *
         * @param accessKeyId access key ID
         * @return this builder
         */
        public Builder accessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
            return this;
        }

        /**
         * Sets the AWS secret access key.
         *
         * @param secretAccessKey secret access key
         * @return this builder
         */
        public Builder secretAccessKey(String secretAccessKey) {
            this.secretAccessKey = secretAccessKey;
            return this;
        }

        /**
         * Sets the S3 bucket name.
         *
         * @param bucketName bucket name (required)
         * @return this builder
         */
        public Builder bucketName(String bucketName) {
            this.bucketName = Objects.requireNonNull(bucketName, "bucketName");
            return this;
        }

        /**
         * Sets the key prefix for namespace isolation.
         *
         * @param keyPrefix key prefix
         * @return this builder
         */
        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix != null ? keyPrefix : "dc:blob:";
            return this;
        }

        /**
         * Sets whether to use path-style access (required for MinIO).
         *
         * @param pathStyleAccess true for path-style
         * @return this builder
         */
        public Builder pathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
            return this;
        }

        /**
         * Sets the threshold for multipart uploads.
         *
         * @param bytes threshold in bytes
         * @return this builder
         */
        public Builder multipartThresholdBytes(long bytes) {
            if (bytes < 0) {
                throw new IllegalArgumentException("multipartThresholdBytes cannot be negative");
            }
            this.multipartThresholdBytes = bytes;
            return this;
        }

        /**
         * Sets the expiry duration for presigned URLs.
         *
         * @param expiry expiry duration
         * @return this builder
         */
        public Builder presignedUrlExpiry(Duration expiry) {
            this.presignedUrlExpiry = expiry != null ? expiry : Duration.ofHours(1);
            return this;
        }

        /**
         * Sets the connection and operation timeout.
         *
         * @param timeout timeout duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout != null ? timeout : Duration.ofSeconds(30);
            return this;
        }

        /**
         * Enables or disables encryption for stored data.
         *
         * @param enabled true to enable encryption
         * @return this builder
         */
        public Builder encryptionEnabled(boolean enabled) {
            this.encryptionEnabled = enabled;
            return this;
        }

        /**
         * Sets the base64-encoded encryption key for AES-256-GCM encryption.
         *
         * @param encryptionKey base64-encoded 256-bit key
         * @return this builder
         */
        public Builder encryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return immutable configuration
         */
        public BlobStorageConnectorConfig build() {
            return new BlobStorageConnectorConfig(this);
        }
    }
}
