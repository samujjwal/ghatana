package com.ghatana.datacloud.config.model;

import java.util.Objects;

/**
 * Unique key for identifying configuration entries in the cache.
 *
 * <p>
 * ConfigKey combines tenant ID and resource name to provide a unique, immutable
 * key for configuration lookups.
 *
 * @doc.type record
 * @doc.purpose Unique identifier for cached configuration entries
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record ConfigKey(
        ConfigType type,
        String tenantId,
        String name
        ) {

    /**
     * Creates a ConfigKey with validation.
     */
    public ConfigKey   {
        Objects.requireNonNull(type, "Config type cannot be null");
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
    }

    /**
     * Create a key for a collection configuration.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return config key for the collection
     */
    public static ConfigKey collection(String tenantId, String collectionName) {
        return new ConfigKey(ConfigType.COLLECTION, tenantId, collectionName);
    }

    /**
     * Create a key for an event collection configuration.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return config key for the event collection
     */
    public static ConfigKey eventCollection(String tenantId, String collectionName) {
        return new ConfigKey(ConfigType.EVENT_COLLECTION, tenantId, collectionName);
    }

    /**
     * Create a key for a plugin configuration.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return config key for the plugin
     */
    public static ConfigKey plugin(String tenantId, String pluginName) {
        return new ConfigKey(ConfigType.PLUGIN, tenantId, pluginName);
    }

    /**
     * Create a key for a storage profile configuration.
     *
     * @param tenantId tenant identifier
     * @param profileName profile name
     * @return config key for the storage profile
     */
    public static ConfigKey storageProfile(String tenantId, String profileName) {
        return new ConfigKey(ConfigType.STORAGE_PROFILE, tenantId, profileName);
    }

    /**
     * Create a key for a policy configuration.
     *
     * @param tenantId tenant identifier
     * @param policyName policy name
     * @return config key for the policy
     */
    public static ConfigKey policy(String tenantId, String policyName) {
        return new ConfigKey(ConfigType.POLICY, tenantId, policyName);
    }

    /**
     * Create a key for a routing configuration.
     *
     * @param tenantId tenant identifier
     * @param routingName routing config name (often collection name)
     * @return config key for the routing config
     */
    public static ConfigKey routing(String tenantId, String routingName) {
        return new ConfigKey(ConfigType.ROUTING_RULE, tenantId, routingName);
    }

    /**
     * Get a cache key string representation.
     *
     * @return cache key string
     */
    public String toCacheKey() {
        return type.name() + ":" + tenantId + ":" + name;
    }

    @Override
    public String toString() {
        return toCacheKey();
    }

    /**
     * Types of configuration that can be cached.
     */
    public enum ConfigType {
        COLLECTION,
        EVENT_COLLECTION,
        PLUGIN,
        STORAGE_PROFILE,
        POLICY,
        ROUTING_RULE
    }
}
