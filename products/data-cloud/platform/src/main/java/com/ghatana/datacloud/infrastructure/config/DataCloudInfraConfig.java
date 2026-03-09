package com.ghatana.datacloud.infrastructure.config;

import com.ghatana.datacloud.infrastructure.storage.BlobStorageConnectorConfig;
import com.ghatana.datacloud.infrastructure.storage.KeyValueConnectorConfig;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Centralized infrastructure configuration for Data Cloud.
 *
 * <p><b>Purpose</b><br>
 * Consolidates all infrastructure connector configurations into a single
 * configuration object. Supports loading from environment variables,
 * YAML files, or programmatic configuration.
 *
 * <p><b>Architecture Role</b><br>
 * - Central configuration hub for infrastructure layer
 * - Integrates with core ConfigLoader for YAML-based configuration
 * - Provides typed access to connector-specific configurations
 * - Supports environment variable overrides for deployment flexibility
 *
 * <p><b>Configuration Hierarchy</b><br>
 * <pre>
 * DataCloudInfraConfig
 *   ├── RedisConfig (KeyValueConnector)
 *   ├── S3Config (BlobStorageConnector)
 *   ├── ElasticsearchConfig (ElasticsearchIndex)
 *   └── DatabaseConfig (future: RelationalConnector)
 * </pre>
 *
 * <p><b>Environment Variables</b><br>
 * All configurations can be overridden via environment variables:
 * <ul>
 *   <li>Redis: {@code REDIS_HOST}, {@code REDIS_PORT}, {@code REDIS_PASSWORD}</li>
 *   <li>S3: {@code AWS_REGION}, {@code S3_BUCKET_NAME}, {@code S3_ENDPOINT}</li>
 *   <li>Elasticsearch: {@code ES_HOSTS}, {@code ES_USERNAME}, {@code ES_PASSWORD}</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Load from environment
 * DataCloudInfraConfig config = DataCloudInfraConfig.fromEnvironment();
 *
 * // Get connector-specific configs
 * KeyValueConnectorConfig redisConfig = config.getRedisConfig();
 * BlobStorageConnectorConfig s3Config = config.getS3Config();
 * ElasticsearchConfig esConfig = config.getElasticsearchConfig();
 *
 * // Programmatic configuration
 * DataCloudInfraConfig config = DataCloudInfraConfig.builder()
 *     .redis(KeyValueConnectorConfig.builder()
 *         .host("redis.prod.internal")
 *         .port(6379)
 *         .build())
 *     .s3(BlobStorageConnectorConfig.builder()
 *         .region("us-east-1")
 *         .bucketName("prod-data-bucket")
 *         .build())
 *     .elasticsearch(ElasticsearchConfig.builder()
 *         .hosts(List.of("es1:9200", "es2:9200"))
 *         .build())
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - all fields final. Safe to share across threads.
 *
 * @see KeyValueConnectorConfig
 * @see BlobStorageConnectorConfig
 * @see ElasticsearchConfig
 * @doc.type class
 * @doc.purpose Centralized infrastructure configuration for Data Cloud
 * @doc.layer product
 * @doc.pattern Value Object, Configuration
 */
public final class DataCloudInfraConfig {

    private final KeyValueConnectorConfig redisConfig;
    private final BlobStorageConnectorConfig s3Config;
    private final ElasticsearchConfig elasticsearchConfig;

    private DataCloudInfraConfig(Builder builder) {
        this.redisConfig = builder.redisConfig;
        this.s3Config = builder.s3Config;
        this.elasticsearchConfig = builder.elasticsearchConfig;
    }

    /**
     * Gets Redis configuration for KeyValueConnector.
     *
     * @return Redis configuration
     */
    public KeyValueConnectorConfig getRedisConfig() {
        return redisConfig;
    }

    /**
     * Gets S3 configuration for BlobStorageConnector.
     *
     * @return S3 configuration
     */
    public BlobStorageConnectorConfig getS3Config() {
        return s3Config;
    }

    /**
     * Gets Elasticsearch configuration.
     *
     * @return Elasticsearch configuration
     */
    public ElasticsearchConfig getElasticsearchConfig() {
        return elasticsearchConfig;
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
     * Creates configuration from environment variables.
     * Falls back to defaults if environment variables are not set.
     *
     * @return configuration from environment
     */
    public static DataCloudInfraConfig fromEnvironment() {
        return builder()
                .redis(KeyValueConnectorConfig.fromEnvironment())
                .s3(BlobStorageConnectorConfig.fromEnvironment())
                .elasticsearch(ElasticsearchConfig.fromEnvironment())
                .build();
    }

    /**
     * Creates a builder initialized with this config's values.
     *
     * @return builder with current values
     */
    public Builder toBuilder() {
        return new Builder()
                .redis(redisConfig)
                .s3(s3Config)
                .elasticsearch(elasticsearchConfig);
    }

    @Override
    public String toString() {
        return "DataCloudInfraConfig{" +
                "redisConfig=" + redisConfig +
                ", s3Config=" + s3Config +
                ", elasticsearchConfig=" + elasticsearchConfig +
                '}';
    }

    /**
     * Builder for DataCloudInfraConfig.
     */
    public static final class Builder {
        private KeyValueConnectorConfig redisConfig = KeyValueConnectorConfig.builder().build();
        private BlobStorageConnectorConfig s3Config = BlobStorageConnectorConfig.builder().build();
        private ElasticsearchConfig elasticsearchConfig = ElasticsearchConfig.builder().build();

        private Builder() {
        }

        /**
         * Sets Redis configuration.
         *
         * @param config Redis configuration
         * @return this builder
         */
        public Builder redis(KeyValueConnectorConfig config) {
            this.redisConfig = config != null ? config : KeyValueConnectorConfig.builder().build();
            return this;
        }

        /**
         * Sets S3 configuration.
         *
         * @param config S3 configuration
         * @return this builder
         */
        public Builder s3(BlobStorageConnectorConfig config) {
            this.s3Config = config != null ? config : BlobStorageConnectorConfig.builder().build();
            return this;
        }

        /**
         * Sets Elasticsearch configuration.
         *
         * @param config Elasticsearch configuration
         * @return this builder
         */
        public Builder elasticsearch(ElasticsearchConfig config) {
            this.elasticsearchConfig = config != null ? config : ElasticsearchConfig.builder().build();
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return immutable configuration
         */
        public DataCloudInfraConfig build() {
            return new DataCloudInfraConfig(this);
        }
    }

    /**
     * Elasticsearch configuration.
     */
    public static final class ElasticsearchConfig {
        private final List<String> hosts;
        private final String username;
        private final String password;
        private final String indexPrefix;
        private final Duration timeout;
        private final boolean sslEnabled;

        private ElasticsearchConfig(ElasticsearchConfigBuilder builder) {
            this.hosts = builder.hosts;
            this.username = builder.username;
            this.password = builder.password;
            this.indexPrefix = builder.indexPrefix;
            this.timeout = builder.timeout;
            this.sslEnabled = builder.sslEnabled;
        }

        public List<String> getHosts() {
            return hosts;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getIndexPrefix() {
            return indexPrefix;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public boolean isSslEnabled() {
            return sslEnabled;
        }

        public static ElasticsearchConfigBuilder builder() {
            return new ElasticsearchConfigBuilder();
        }

        public static ElasticsearchConfig fromEnvironment() {
            ElasticsearchConfigBuilder builder = builder();

            String hosts = System.getenv("ES_HOSTS");
            if (hosts != null && !hosts.isEmpty()) {
                builder.hosts(List.of(hosts.split(",")));
            }

            String username = System.getenv("ES_USERNAME");
            if (username != null && !username.isEmpty()) {
                builder.username(username);
            }

            String password = System.getenv("ES_PASSWORD");
            if (password != null && !password.isEmpty()) {
                builder.password(password);
            }

            String indexPrefix = System.getenv("ES_INDEX_PREFIX");
            if (indexPrefix != null && !indexPrefix.isEmpty()) {
                builder.indexPrefix(indexPrefix);
            }

            String sslEnabled = System.getenv("ES_SSL_ENABLED");
            if ("true".equalsIgnoreCase(sslEnabled)) {
                builder.sslEnabled(true);
            }

            return builder.build();
        }

        @Override
        public String toString() {
            return "ElasticsearchConfig{" +
                    "hosts=" + hosts +
                    ", indexPrefix='" + indexPrefix + '\'' +
                    ", sslEnabled=" + sslEnabled +
                    '}';
        }

        public static final class ElasticsearchConfigBuilder {
            private List<String> hosts = List.of("localhost:9200");
            private String username;
            private String password;
            private String indexPrefix = "datacloud";
            private Duration timeout = Duration.ofSeconds(30);
            private boolean sslEnabled = false;

            private ElasticsearchConfigBuilder() {
            }

            public ElasticsearchConfigBuilder hosts(List<String> hosts) {
                this.hosts = hosts != null && !hosts.isEmpty() ? hosts : List.of("localhost:9200");
                return this;
            }

            public ElasticsearchConfigBuilder username(String username) {
                this.username = username;
                return this;
            }

            public ElasticsearchConfigBuilder password(String password) {
                this.password = password;
                return this;
            }

            public ElasticsearchConfigBuilder indexPrefix(String indexPrefix) {
                this.indexPrefix = indexPrefix != null ? indexPrefix : "datacloud";
                return this;
            }

            public ElasticsearchConfigBuilder timeout(Duration timeout) {
                this.timeout = timeout != null ? timeout : Duration.ofSeconds(30);
                return this;
            }

            public ElasticsearchConfigBuilder sslEnabled(boolean sslEnabled) {
                this.sslEnabled = sslEnabled;
                return this;
            }

            public ElasticsearchConfig build() {
                return new ElasticsearchConfig(this);
            }
        }
    }
}
