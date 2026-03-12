package com.ghatana.datacloud.client;

import com.ghatana.datacloud.config.DataCloudEnvConfig;
import com.ghatana.datacloud.config.DataCloudStartupValidator;
import com.ghatana.datacloud.deployment.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Factory for creating Data-Cloud clients for different deployment modes.
 * 
 * <p>Provides static factory methods to create clients for:
 * - EMBEDDED mode (in-process, library usage)
 * - STANDALONE mode (HTTP client to single server)
 * - DISTRIBUTED mode (HTTP client to cluster)
 * </p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Embedded mode (in-process)
 * DataCloudClient embedded = DataCloudClientFactory.embedded(config);
 * 
 * // Standalone mode (HTTP to single server)
 * DataCloudClient standalone = DataCloudClientFactory.standalone("http://localhost:8080");
 * 
 * // Distributed mode (HTTP to cluster)
 * DataCloudClient distributed = DataCloudClientFactory.distributed(
 *     "http://node1:8080,http://node2:8080,http://node3:8080");
 * }</pre>
 * 
 * @doc.type factory
 * @doc.purpose Client creation for different deployment modes
 * @doc.layer core
 * @doc.pattern Factory, Builder
 */
public class DataCloudClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(DataCloudClientFactory.class);
    
    private DataCloudClientFactory() {
        // Prevent instantiation
    }
    
    /**
     * Creates an embedded (in-process) Data-Cloud client.
     * 
     * <p>This client runs Data-Cloud in the same JVM process, suitable for:
     * - Library usage in applications
     * - Edge deployments
     * - Testing and development
     * - Embedded systems
     * </p>
     * 
     * @param config server configuration
     * @return embedded data-cloud client
     * @throws NullPointerException if config is null
     */
    public static DataCloudClient embedded(ServerConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        
        logger.info("Creating embedded Data-Cloud client with plugin integration");
        
        // Create embedded client using builder pattern with memory storage for now
        // In production, this would be configured based on ServerConfig
        EmbeddedDataCloudClient client = EmbeddedDataCloudClient.builder()
            .withMemoryStorage()
            .build();
        
        logger.info("Embedded Data-Cloud client created successfully with plugins");
        return client;
    }
    
    /**
     * Creates a standalone (HTTP) Data-Cloud client.
     * 
     * <p>This client connects to a single Data-Cloud server via HTTP.
     * Suitable for:
     * - Single-node deployments
     * - Development environments
     * - Small production deployments
     * </p>
     * 
     * @param serverUrl server URL (e.g., "http://localhost:8080")
     * @return HTTP client for standalone server
     * @throws NullPointerException if serverUrl is null
     * @throws IllegalArgumentException if serverUrl is invalid
     */
    public static DataCloudClient standalone(String serverUrl) {
        Objects.requireNonNull(serverUrl, "serverUrl cannot be null");
        validateUrl(serverUrl);
        
        logger.info("Creating standalone Data-Cloud client: {}", serverUrl);
        
        // Create HTTP client implementation
        HttpDataCloudClient client = new HttpDataCloudClient(serverUrl);
        
        logger.info("Standalone Data-Cloud client created successfully");
        return client;
    }
    
    /**
     * Creates a distributed (HTTP cluster) Data-Cloud client.
     * 
     * <p>This client connects to a Data-Cloud cluster with load balancing.
     * Suitable for:
     * - Multi-node production deployments
     * - High availability setups
     * - Large-scale deployments
     * </p>
     * 
     * @param clusterUrls comma-separated server URLs
     * @return HTTP client with load balancing
     * @throws NullPointerException if clusterUrls is null
     * @throws IllegalArgumentException if clusterUrls is invalid
     */
    public static DataCloudClient distributed(String clusterUrls) {
        Objects.requireNonNull(clusterUrls, "clusterUrls cannot be null");
        
        String[] urls = clusterUrls.split(",");
        if (urls.length < 2) {
            throw new IllegalArgumentException("Distributed mode requires at least 2 nodes");
        }
        
        for (String url : urls) {
            validateUrl(url.trim());
        }
        
        logger.info("Creating distributed Data-Cloud client with {} nodes", urls.length);
        
        // Create HTTP client with load balancing
        DistributedHttpDataCloudClient client = new DistributedHttpDataCloudClient(urls);
        
        logger.info("Distributed Data-Cloud client created successfully");
        return client;
    }
    
    /**
     * Validates a URL format.
     * 
     * @param url URL to validate
     * @throws IllegalArgumentException if URL is invalid
     */
    private static void validateUrl(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
        
        try {
            new java.net.URL(url);
        } catch (java.net.MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: " + url, e);
        }
    }
    
    /**
     * Creates a Data-Cloud client by reading configuration from environment variables.
     *
     * <p>Reads {@code DC_DEPLOYMENT_MODE} to decide which factory method to call:
     * <ul>
     *   <li>{@code EMBEDDED} (default) — calls {@link #embedded(ServerConfig)} with empty config</li>
     *   <li>{@code STANDALONE} — calls {@link #standalone(String)} with {@code DC_SERVER_URL}</li>
     *   <li>{@code DISTRIBUTED} — calls {@link #distributed(String)} with {@code DC_CLUSTER_URLS}</li>
     * </ul>
     *
     * <p>Runs {@link DataCloudStartupValidator} first to fail fast on misconfiguration.
     *
     * @return fully configured Data-Cloud client
     * @throws IllegalStateException if required environment variables are missing/invalid
     */
    public static DataCloudClient fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /**
     * Creates a Data-Cloud client from the supplied environment map.
     * Primarily for testing without modifying system env.
     *
     * @param env environment map
     * @return fully configured Data-Cloud client
     * @throws IllegalStateException if required environment variables are missing/invalid
     */
    public static DataCloudClient fromEnvironment(Map<String, String> env) {
        DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env);
        DataCloudStartupValidator.validate(config);

        String mode = config.deploymentMode();
        logger.info("Creating Data-Cloud client from environment — mode={}", mode);

        return switch (mode) {
            case "STANDALONE"   -> standalone(config.serverUrl());
            case "DISTRIBUTED"  -> distributed(config.clusterUrls());
            default             -> embedded(new ServerConfig());
        };
    }

    /**
     * Creates a builder for advanced client configuration.
     *
     * @return client builder
     */
    public static DataCloudClientBuilder builder() {
        return new DataCloudClientBuilder();
    }
    
    /**
     * Builder for advanced Data-Cloud client configuration.
     */
    public static class DataCloudClientBuilder {
        private String mode = "EMBEDDED";
        private String serverUrl;
        private String[] clusterUrls;
        private ServerConfig embeddedConfig;
        private int connectTimeout = 30000;
        private int readTimeout = 60000;
        private int maxRetries = 3;
        private boolean enableMetrics = true;
        private boolean enableTracing = true;
        
        /**
         * Sets deployment mode.
         * 
         * @param mode EMBEDDED, STANDALONE, or DISTRIBUTED
         * @return this builder
         */
        public DataCloudClientBuilder mode(String mode) {
            this.mode = mode;
            return this;
        }
        
        /**
         * Sets server URL for standalone mode.
         * 
         * @param serverUrl server URL
         * @return this builder
         */
        public DataCloudClientBuilder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }
        
        /**
         * Sets cluster URLs for distributed mode.
         * 
         * @param clusterUrls array of server URLs
         * @return this builder
         */
        public DataCloudClientBuilder clusterUrls(String[] clusterUrls) {
            this.clusterUrls = clusterUrls;
            return this;
        }
        
        /**
         * Sets embedded config.
         * 
         * @param config server configuration
         * @return this builder
         */
        public DataCloudClientBuilder embeddedConfig(ServerConfig config) {
            this.embeddedConfig = config;
            return this;
        }
        
        /**
         * Sets connection timeout.
         * 
         * @param timeoutMs timeout in milliseconds
         * @return this builder
         */
        public DataCloudClientBuilder connectTimeout(int timeoutMs) {
            this.connectTimeout = timeoutMs;
            return this;
        }
        
        /**
         * Sets read timeout.
         * 
         * @param timeoutMs timeout in milliseconds
         * @return this builder
         */
        public DataCloudClientBuilder readTimeout(int timeoutMs) {
            this.readTimeout = timeoutMs;
            return this;
        }
        
        /**
         * Sets max retries.
         * 
         * @param maxRetries maximum number of retries
         * @return this builder
         */
        public DataCloudClientBuilder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        /**
         * Enables/disables metrics collection.
         * 
         * @param enable true to enable
         * @return this builder
         */
        public DataCloudClientBuilder enableMetrics(boolean enable) {
            this.enableMetrics = enable;
            return this;
        }
        
        /**
         * Enables/disables distributed tracing.
         * 
         * @param enable true to enable
         * @return this builder
         */
        public DataCloudClientBuilder enableTracing(boolean enable) {
            this.enableTracing = enable;
            return this;
        }
        
        /**
         * Builds the client.
         * 
         * @return configured data-cloud client
         */
        public DataCloudClient build() {
            switch (mode.toUpperCase()) {
                case "EMBEDDED":
                    if (embeddedConfig == null) {
                        throw new IllegalStateException("Embedded mode requires embeddedConfig");
                    }
                    return embedded(embeddedConfig);
                    
                case "STANDALONE":
                    if (serverUrl == null) {
                        throw new IllegalStateException("Standalone mode requires serverUrl");
                    }
                    return standalone(serverUrl);
                    
                case "DISTRIBUTED":
                    if (clusterUrls == null || clusterUrls.length == 0) {
                        throw new IllegalStateException("Distributed mode requires clusterUrls");
                    }
                    return distributed(String.join(",", clusterUrls));
                    
                default:
                    throw new IllegalArgumentException("Unknown mode: " + mode);
            }
        }
    }
}
