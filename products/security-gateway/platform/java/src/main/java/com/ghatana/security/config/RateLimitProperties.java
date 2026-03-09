package com.ghatana.security.config;

import io.activej.config.Config;
import io.activej.config.converter.ConfigConverters;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration properties for rate limiting settings.
 * 
 * <p>This class holds configuration options for rate limiting various operations
 * and endpoints in the application.</p>
 * 
 * <p>Example configuration (in application.yaml or application.properties):</p>
 * <pre>
 * security:
 *   rate-limit:
 *     enabled: true
 *     default:
 *       requests: 100
 *       duration: 1m
 *     endpoints:
 *       "/api/auth/login":
 *         requests: 5
 *         duration: 1m
 *       "/api/users":
 *         requests: 100
 *         duration: 1h
 *     ip-based: true
 *     user-based: true
 *     cache:
 *       enabled: true
 *       ttl: 1h
 * </pre>
 
 *
 * @doc.type class
 * @doc.purpose Rate limit properties
 * @doc.layer core
 * @doc.pattern Component
*/
public class RateLimitProperties {
    private final boolean enabled;
    private final RateLimitConfig defaultConfig;
    private final Map<String, RateLimitConfig> endpoints;
    private final boolean ipBased;
    private final boolean userBased;
    private final CacheProperties cache;
    
    /**
     * Creates a new RateLimitProperties instance from a Config object.
     * 
     * @param config The configuration source
     * @throws NullPointerException if config is null
     */
    public RateLimitProperties(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        this.enabled = Boolean.parseBoolean(config.get("enabled", "true"));
        this.defaultConfig = new RateLimitConfig(config.getChild("default"));
        this.ipBased = Boolean.parseBoolean(config.get("ip-based", "true"));
        this.userBased = Boolean.parseBoolean(config.get("user-based", "true"));
        this.cache = new CacheProperties(config.getChild("cache"));
        
        // Load endpoint-specific configurations
        Config endpointsConfig = config.getChild("endpoints");
        this.endpoints = new HashMap<>();
        
        for (String endpoint : endpointsConfig.getChildren().keySet()) {
            Config endpointConfig = endpointsConfig.getChild(endpoint);
            endpoints.put(endpoint, new RateLimitConfig(endpointConfig));
        }
    }
    
    /**
     * Creates a new RateLimitProperties instance from a Config object.
     * 
     * @param enabled The enabled flag
     * @param defaultConfig The default rate limit configuration
     * @param endpoints The endpoint-specific rate limit configurations
     * @param ipBased The IP-based rate limiting flag
     * @param userBased The user-based rate limiting flag
     * @param cache The cache configuration
     */
    public RateLimitProperties(boolean enabled, RateLimitConfig defaultConfig, Map<String, RateLimitConfig> endpoints, boolean ipBased, boolean userBased, CacheProperties cache) {
        this.enabled = enabled;
        this.defaultConfig = defaultConfig;
        this.endpoints = endpoints;
        this.ipBased = ipBased;
        this.userBased = userBased;
        this.cache = cache;
    }
    
    /**
     * Checks if rate limiting is enabled.
     * 
     * @return true if rate limiting is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the default rate limit configuration.
     * 
     * @return The default rate limit config
     */
    public RateLimitConfig getDefaultConfig() {
        return defaultConfig;
    }
    
    /**
     * Gets the rate limit configuration for a specific endpoint.
     * 
     * @param endpoint The endpoint path
     * @return The endpoint-specific config, or the default config if not found
     */
    public RateLimitConfig getConfigForEndpoint(String endpoint) {
        return endpoints.getOrDefault(endpoint, defaultConfig);
    }
    
    /**
     * Checks if IP-based rate limiting is enabled.
     * 
     * @return true if IP-based rate limiting is enabled, false otherwise
     */
    public boolean isIpBased() {
        return ipBased;
    }
    
    /**
     * Checks if user-based rate limiting is enabled.
     * 
     * @return true if user-based rate limiting is enabled, false otherwise
     */
    public boolean isUserBased() {
        return userBased;
    }
    
    /**
     * Gets the cache configuration for rate limiting.
     * 
     * @return The cache properties
     */
    public CacheProperties getCache() {
        return cache;
    }
    
    /**
     * Gets the number of requests allowed per second based on the default configuration.
     * 
     * @return The number of requests allowed per second
     */
    public double getRequestsPerSecond() {
        if (defaultConfig.getDuration().getSeconds() <= 0) {
            return 0;
        }
        return (double) defaultConfig.getRequests() / defaultConfig.getDuration().getSeconds();
    }
    
    /**
     * Gets the burst capacity for rate limiting, which is the same as the maximum number of requests
     * allowed in the duration window.
     * 
     * @return The burst capacity
     */
    public int getBurstCapacity() {
        return defaultConfig.getRequests();
    }
    
    @Override
    public String toString() {
        return "RateLimitProperties{" +
                "enabled=" + enabled +
                ", defaultConfig=" + defaultConfig +
                ", endpoints=" + endpoints +
                ", ipBased=" + ipBased +
                ", userBased=" + userBased +
                ", cache=" + cache +
                '}';
    }
    
    /**
     * Creates a new RateLimitProperties instance from a Config object.
     * 
     * @param config The configuration source
     * @return A new RateLimitProperties instance
     */
    public static RateLimitProperties fromConfig(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        return new RateLimitProperties(
            config.get(ConfigConverters.ofBoolean(), "enabled", true),
            RateLimitConfig.fromConfig(config.getChild("default")),
            parseEndpointConfigs(config.getChild("endpoints")),
            config.get(ConfigConverters.ofBoolean(), "ip-based", true),
            config.get(ConfigConverters.ofBoolean(), "user-based", true),
            CacheProperties.fromConfig(config.getChild("cache"))
        );
    }
    
    private static Map<String, RateLimitConfig> parseEndpointConfigs(Config endpointsConfig) {
        Map<String, RateLimitConfig> endpoints = new HashMap<>();
        
        for (String endpoint : endpointsConfig.getChildren().keySet()) {
            Config endpointConfig = endpointsConfig.getChild(endpoint);
            endpoints.put(endpoint, new RateLimitConfig(endpointConfig));
        }
        
        return endpoints;
    }
    
    /**
     * Rate limit configuration for a specific endpoint or the default.
     */
    public static class RateLimitConfig {
        private final int requests;
        private final Duration duration;
        
        public RateLimitConfig(Config config) {
            this.requests = Integer.parseInt(config.get("requests", "100"));
            this.duration = Duration.parse("PT" + config.get("duration", "1m")
                .replaceAll("([0-9]+)s", "$1S")
                .replaceAll("([0-9]+)m", "$1M")
                .replaceAll("([0-9]+)h", "$1H")
                .replaceAll("([0-9]+)d", "$1D"));
        }
        
        /**
         * Creates a new RateLimitConfig instance from a Config object.
         * 
         * @param config The configuration source
         * @return A new RateLimitConfig instance
         */
        public static RateLimitConfig fromConfig(Config config) {
            return new RateLimitConfig(config);
        }
        
        /**
         * Gets the maximum number of requests allowed in the duration window.
         * 
         * @return The maximum number of requests
         */
        public int getRequests() {
            return requests;
        }
        
        /**
         * Gets the duration window for rate limiting.
         * 
         * @return The duration window
         */
        public Duration getDuration() {
            return duration;
        }
        
        @Override
        public String toString() {
            return requests + " per " + duration;
        }
    }
    
    /**
     * Cache configuration for rate limiting.
     */
    public static class CacheProperties {
        private final boolean enabled;
        private final Duration ttl;
        
        public CacheProperties(Config config) {
            this.enabled = Boolean.parseBoolean(config.get("enabled", "true"));
            String ttlStr = config.get("ttl", "1h");
            this.ttl = Duration.parse("PT" + ttlStr
                .replaceAll("([0-9]+)s", "$1S")
                .replaceAll("([0-9]+)m", "$1M")
                .replaceAll("([0-9]+)h", "$1H")
                .replaceAll("([0-9]+)d", "$1D"));
        }
        
        /**
         * Creates a new CacheProperties instance from a Config object.
         * 
         * @param config The configuration source
         * @return A new CacheProperties instance
         */
        public static CacheProperties fromConfig(Config config) {
            return new CacheProperties(config);
        }
        
        /**
         * Checks if rate limit caching is enabled.
         * 
         * @return true if caching is enabled, false otherwise
         */
        public boolean isEnabled() {
            return enabled;
        }
        
        /**
         * Gets the time-to-live for rate limit entries in the cache.
         * 
         * @return The TTL duration
         */
        public Duration getTtl() {
            return ttl;
        }
        
        @Override
        public String toString() {
            return "CacheProperties{" +
                    "enabled=" + enabled +
                    ", ttl=" + ttl +
                    '}';
        }
    }
}
