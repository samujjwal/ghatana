package com.ghatana.security.config;

import io.activej.config.Config;
import io.activej.config.converter.ConfigConverters;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Configuration properties for key management settings.
 * 
 * <p>This class holds configuration options for managing cryptographic keys,
 * including key rotation policies, storage backends, and access controls.</p>
 * 
 * <p>Example configuration (in application.yaml or application.properties):</p>
 * <pre>
 * security:
 *   key-management:
 *     provider: "aws-kms"
 *     region: "us-east-1"
 *     key-rotation:
 *       enabled: true
 *       interval: 30d
 *       retention: 90d
 *     cache:
 *       enabled: true
 *       ttl: 1h
 *       max-size: 1000
 *     access-control:
 *       admin-roles: ["admin", "security-admin"]
 *       read-roles: ["user", "service-account"]
 * </pre>
 
 *
 * @doc.type class
 * @doc.purpose Key management properties
 * @doc.layer core
 * @doc.pattern Component
*/
public class KeyManagementProperties {
    private final String provider;
    private final String region;
    private final KeyRotationProperties keyRotation;
    private final CacheProperties cache;
    private final AccessControlProperties accessControl;
    private final Config config;
    
    /**
     * Creates a new KeyManagementProperties instance from a Config object.
     * 
     * @param config The configuration source
     * @throws NullPointerException if config is null
     */
    public KeyManagementProperties(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        this.config = config;
        this.provider = config.get("provider", "");
        this.region = config.get("region", "");
        this.keyRotation = new KeyRotationProperties(config.getChild("key-rotation"));
        this.cache = new CacheProperties(config.getChild("cache"));
        this.accessControl = new AccessControlProperties(config.getChild("access-control"));
    }
    
    /**
     * Gets the key management service provider.
     * 
     * @return The provider name (e.g., "aws-kms", "hashicorp-vault", "local")
     */
    public String getProvider() {
        return provider;
    }
    
    /**
     * Gets the region for the key management service.
     * 
     * @return The region (e.g., "us-east-1")
     */
    public String getRegion() {
        return region;
    }
    
    /**
     * Gets the key rotation configuration.
     * 
     * @return Key rotation properties
     */
    public KeyRotationProperties getKeyRotation() {
        return keyRotation;
    }
    
    /**
     * Gets the key caching configuration.
     * 
     * @return Cache properties
     */
    public CacheProperties getCache() {
        return cache;
    }
    
    /**
     * Gets the access control configuration.
     * 
     * @return Access control properties
     */
    public AccessControlProperties getAccessControl() {
        return accessControl;
    }
    
    /**
     * Checks if initial key generation is enabled.
     * 
     * @return true if initial key generation is enabled, false otherwise
     */
    public boolean isGenerateInitialKey() {
        return config.get(ConfigConverters.ofBoolean(), "generate-initial-key", false);
    }

    /**
     * Gets the number of initial keys to generate.
     * 
     * @return The number of initial keys
     */
    public int getInitialKeyCount() {

        return config.get(ConfigConverters.ofInteger(), "initial-key-count", 1);
    }

    /**
     * Gets the size of the keys.
     * 
     * @return The key size
     */
    public int getKeySize() {
        return config.get(ConfigConverters.ofInteger(), "key-size", 256);
    }

    /**
     * Gets the algorithm used for key generation.
     * 
     * @return The key algorithm
     */
    public String getKeyAlgorithm() {
        return config.get("key-algorithm", "RSA");
    }

    /**
     * Gets the configured keys.
     * 
     * @return A map of configured keys
     */
    public Map<String, KeyConfig> getConfiguredKeys() {
        return config.getChild("configured-keys").getChildren().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> KeyConfig.fromConfig(e.getValue())
            ));
    }
    
    @Override
    public String toString() {
        return "KeyManagementProperties{" +
                "provider='" + provider + '\'' +
                ", region='" + region + '\'' +
                ", keyRotation=" + keyRotation +
                ", cache=" + cache +
                ", accessControl=" + accessControl +
                '}';
    }
    
    /**
     * Key rotation configuration.
     */
    public static class KeyRotationProperties {
        private final boolean enabled;
        private final Duration interval;
        private final Duration retention;
        
        public KeyRotationProperties(Config config) {
            this.enabled = Boolean.parseBoolean(config.get("enabled", "true"));
            this.interval = Duration.parse(config.get("interval", "P30D"));
            this.retention = Duration.parse(config.get("retention", "P90D"));
        }
        
        /**
         * Checks if automatic key rotation is enabled.
         * 
         * @return true if key rotation is enabled, false otherwise
         */
        public boolean isEnabled() {
            return enabled;
        }
        
        /**
         * Gets the interval between key rotations.
         * 
         * @return The rotation interval
         */
        public Duration getInterval() {
            return interval;
        }
        
        /**
         * Gets the retention period for old keys after rotation.
         * 
         * @return The retention period
         */
        public Duration getRetention() {
            return retention;
        }
        
        @Override
        public String toString() {
            return "KeyRotationProperties{" +
                    "enabled=" + enabled +
                    ", interval=" + interval +
                    ", retention=" + retention +
                    '}';
        }
    }
    
    /**
     * Cache configuration for keys.
     */
    public static class CacheProperties {
        private final boolean enabled;
        private final Duration ttl;
        private final long maxSize;
        
        public CacheProperties(Config config) {
            this.enabled = Boolean.parseBoolean(config.get("enabled", "true"));
            this.ttl = Duration.parse(config.get("ttl", "PT1H"));
            this.maxSize = Long.parseLong(config.get("max-size", "1000"));
        }
        
        /**
         * Checks if key caching is enabled.
         * 
         * @return true if caching is enabled, false otherwise
         */
        public boolean isEnabled() {
            return enabled;
        }
        
        /**
         * Gets the time-to-live for cached keys.
         * 
         * @return The TTL duration
         */
        public Duration getTtl() {
            return ttl;
        }
        
        /**
         * Gets the maximum number of keys to cache.
         * 
         * @return The maximum cache size
         */
        public long getMaxSize() {
            return maxSize;
        }
        
        @Override
        public String toString() {
            return "CacheProperties{" +
                    "enabled=" + enabled +
                    ", ttl=" + ttl +
                    ", maxSize=" + maxSize +
                    '}';
        }
    }
    
    /**
     * Access control configuration for key management.
     */
    public static class AccessControlProperties {
        private final String[] adminRoles;
        private final String[] readRoles;
        
        public AccessControlProperties(Config config) {
            this.adminRoles = config.get("admin-roles", "").split(",");
            this.readRoles = config.get("read-roles", "").split(",");
        }
        
        /**
         * Gets the roles that have administrative access to key management.
         * 
         * @return Array of admin role names
         */
        public String[] getAdminRoles() {
            return adminRoles.clone();
        }
        
        /**
         * Gets the roles that have read-only access to key management.
         * 
         * @return Array of read role names
         */
        public String[] getReadRoles() {
            return readRoles.clone();
        }
        
        @Override
        public String toString() {
            return "AccessControlProperties{" +
                    "adminRoles=" + String.join(",", adminRoles) +
                    ", readRoles=" + String.join(",", readRoles) +
                    '}';
        }
    }
}
