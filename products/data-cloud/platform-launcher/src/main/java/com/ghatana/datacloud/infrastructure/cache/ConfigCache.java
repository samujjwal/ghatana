package com.ghatana.datacloud.infrastructure.cache;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration cache port interface for content API settings.
 *
 * <p><b>Purpose</b><br>
 * Defines caching contract for frequently-accessed configuration data including
 * policy settings, template definitions, and scoring dimension configurations.
 * Provides TTL-based expiration and tenant-scoped cache keys.
 *
 * <p><b>Cache Key Structure</b><br>
 * Keys follow pattern: {tenant}:{type}:{id}
 * - Policy: tenant-123:policy:PROFANITY
 * - Template: tenant-123:template:email-template
 * - Dimension: tenant-123:dimension:completeness
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ConfigCache cache = new RedisConfigCache(jedisPool, metrics);
 *
 * // Store policy configuration
 * cache.putPolicy("tenant-123", "PROFANITY", policyConfig, Duration.ofMinutes(10))
 *     .then(v -> System.out.println("Cached"));
 *
 * // Retrieve policy configuration
 * cache.getPolicy("tenant-123", "PROFANITY")
 *     .then(optConfig -> optConfig.ifPresent(c -> process(c)));
 *
 * // Invalidate on update
 * cache.invalidatePolicy("tenant-123", "PROFANITY");
 * }</pre>
 *
 * <p><b>Implementation Requirements</b><br>
 * - All operations must be Promise-based async
 * - Cache keys must include tenant ID for isolation
 * - TTL must be enforced (default 10 minutes)
 * - Failures should log but not block operations
 * - Metrics should track hit/miss/eviction rates
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe and support concurrent access.
 *
 * @see RedisCon figCache
 * @doc.type interface
 * @doc.purpose Configuration caching port for content API
 * @doc.layer infrastructure
 * @doc.pattern Port
 */
public interface ConfigCache {

    /**
     * Default TTL for cached configurations (10 minutes).
     */
    Duration DEFAULT_TTL = Duration.ofMinutes(10);

    // ===== Policy Configuration Cache =====

    /**
     * Stores policy configuration in cache with TTL.
     *
     * @param tenantId   the tenant ID for isolation
     * @param policyType the policy type (PROFANITY, PII, SPAM, etc.)
     * @param config     the policy configuration map
     * @param ttl        the time-to-live duration
     * @return promise that completes when stored
     * @throws NullPointerException if any parameter is null
     */
    Promise<Void> putPolicy(String tenantId, String policyType, Object config, Duration ttl);

    /**
     * Retrieves policy configuration from cache.
     *
     * @param tenantId   the tenant ID
     * @param policyType the policy type
     * @return promise of optional configuration (empty if not cached)
     * @throws NullPointerException if any parameter is null
     */
    Promise<Optional<Object>> getPolicy(String tenantId, String policyType);

    /**
     * Invalidates (removes) policy configuration from cache.
     *
     * @param tenantId   the tenant ID
     * @param policyType the policy type
     * @return promise that completes when invalidated
     * @throws NullPointerException if any parameter is null
     */
    Promise<Void> invalidatePolicy(String tenantId, String policyType);

    // ===== Template Configuration Cache =====

    /**
     * Stores template definition in cache with TTL.
     *
     * @param tenantId   the tenant ID for isolation
     * @param templateId the template identifier
     * @param template   the template definition
     * @param ttl        the time-to-live duration
     * @return promise that completes when stored
     * @throws NullPointerException if any parameter is null
     */
    Promise<Void> putTemplate(String tenantId, String templateId, Object template, Duration ttl);

    /**
     * Retrieves template definition from cache.
     *
     * @param tenantId   the tenant ID
     * @param templateId the template identifier
     * @return promise of optional template (empty if not cached)
     * @throws NullPointerException if any parameter is null
     */
    Promise<Optional<Object>> getTemplate(String tenantId, String templateId);

    /**
     * Invalidates (removes) template definition from cache.
     *
     * @param tenantId   the tenant ID
     * @param templateId the template identifier
     * @return promise that completes when invalidated
     * @throws NullPointerException if any parameter is null
     */
    Promise<Void> invalidateTemplate(String tenantId, String templateId);

    // ===== Scoring Dimension Configuration Cache =====

    /**
     * Stores scoring dimension configuration in cache with TTL.
     *
     * @param tenantId  the tenant ID for isolation
     * @param dimension the dimension name (completeness, consistency, etc.)
     * @param config    the dimension configuration
     * @param ttl       the time-to-live duration
     * @return promise that completes when stored
     * @throws NullPointerException if any parameter is null
     */
    Promise<Void> putDimension(String tenantId, String dimension, Object config, Duration ttl);

    /**
     * Retrieves scoring dimension configuration from cache.
     *
     * @param tenantId  the tenant ID
     * @param dimension the dimension name
     * @return promise of optional configuration (empty if not cached)
     * @throws NullPointerException if any parameter is null
     */
    Promise<Optional<Object>> getDimension(String tenantId, String dimension);

    /**
     * Invalidates (removes) scoring dimension configuration from cache.
     *
     * @param tenantId  the tenant ID
     * @param dimension the dimension name
     * @return promise that completes when invalidated
     * @throws NullPointerException if any parameter is null
     */
    Promise<Void> invalidateDimension(String tenantId, String dimension);

    // ===== Bulk Operations =====

    /**
     * Invalidates all cached configurations for a tenant.
     *
     * <p>Useful when tenant is deleted or bulk configuration changes occur.
     *
     * @param tenantId the tenant ID
     * @return promise that completes when all invalidated
     * @throws NullPointerException if tenantId is null
     */
    Promise<Void> invalidateAllForTenant(String tenantId);

    /**
     * Clears entire cache (all tenants, all configurations).
     *
     * <p>Use with caution - intended for testing or emergency cache flush.
     *
     * @return promise that completes when cache cleared
     */
    Promise<Void> clearAll();
}
