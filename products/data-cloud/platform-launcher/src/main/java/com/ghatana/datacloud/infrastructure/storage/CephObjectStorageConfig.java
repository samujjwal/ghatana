package com.ghatana.datacloud.infrastructure.storage;

import java.net.URI;
import java.util.Objects;

/**
 * Immutable configuration for {@link CephObjectStorageConnector}.
 *
 * <p>Ceph RADOS Gateway (RGW) is S3-compatible. This config points the AWS SDK v2
 * S3 client at a Ceph RGW endpoint using path-style access (required by RGW).
 *
 * <pre>{@code
 * CephObjectStorageConfig config = CephObjectStorageConfig.builder()
 *     .endpoint("http://ceph-rgw.storage.svc.cluster.local:7480")
 *     .accessKey("my-access-key")
 *     .secretKey("my-secret-key")
 *     .bucket("datacloud-entities")
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable configuration for Ceph RGW object storage connector
 * @doc.layer product
 * @doc.pattern ValueObject, Configuration
 */
public record CephObjectStorageConfig(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region
) {
    public CephObjectStorageConfig {
        Objects.requireNonNull(endpoint,   "endpoint required");
        Objects.requireNonNull(accessKey,  "accessKey required");
        Objects.requireNonNull(secretKey,  "secretKey required");
        Objects.requireNonNull(bucket,     "bucket required");
        region = (region != null && !region.isBlank()) ? region : "us-east-1";
        // Validate endpoint URI
        try { URI.create(endpoint); } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("endpoint must be a valid URI: " + endpoint, e);
        }
    }

    public URI endpointUri() {
        return URI.create(endpoint);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String region = "us-east-1";

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder accessKey(String accessKey) {
            this.accessKey = accessKey;
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public CephObjectStorageConfig build() {
            return new CephObjectStorageConfig(endpoint, accessKey, secretKey, bucket, region);
        }
    }
}
