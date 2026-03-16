/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent.dispatch;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * In-memory catalog-backed agent dispatcher using a Guava {@link Cache}.
 *
 * <p>Maintains a bounded, time-expiring cache of {@link AgentRegistryRecord}
 * entries keyed by {@code agentId}. Records are seeded either from YAML-based
 * agent definitions or from the persistent registry via
 * {@link #registerFromRegistry(AgentRegistryRecord)}.
 *
 * <p><b>Design rationale</b><br>
 * The previous implementation used a raw {@code ConcurrentHashMap}, which
 * offered no eviction, no statistics, and no capacity bounding. This class
 * replaces that with a Guava {@code Cache} to gain:
 * <ul>
 *   <li>Automatic eviction after {@code MAX_SIZE} entries (LRU).</li>
 *   <li>TTL-based expiration to reflect heartbeat timeouts.</li>
 *   <li>Built-in hit/miss statistics for observability.</li>
 * </ul>
 *
 * <p>Subclasses (e.g. {@link RegistryReadThroughDispatcher}) may extend
 * {@link #resolve} to add fallback load-on-miss behavior.
 *
 * @doc.type class
 * @doc.purpose Cache-based agent dispatcher seeded from catalog or JDBC registry
 * @doc.layer product
 * @doc.pattern Service, Cache
 */
public class CatalogAgentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CatalogAgentDispatcher.class);

    /** Maximum number of agent records held in the local catalog cache. */
    static final long MAX_SIZE = 1_024;

    /** Time-to-live per cache entry; mirrors the heartbeat timeout of 30 s. */
    static final long TTL_MINUTES = 10;

    /**
     * Guava cache that replaces the previous {@code ConcurrentHashMap<String, TypedAgent>}
     * construction.
     *
     * <p>Key: {@code agentId}. Value: {@link AgentRegistryRecord} (never {@code null}).
     */
    private final Cache<String, AgentRegistryRecord> cache;

    /**
     * Creates a dispatcher with default capacity ({@value #MAX_SIZE} entries,
     * {@value #TTL_MINUTES}-minute TTL) and statistics recording enabled.
     */
    public CatalogAgentDispatcher() {
        this(MAX_SIZE, TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Creates a dispatcher with a custom capacity and TTL — intended for tests.
     *
     * @param maxSize  maximum number of cached entries
     * @param ttl      expiry duration
     * @param ttlUnit  expiry time unit
     */
    public CatalogAgentDispatcher(long maxSize, long ttl, TimeUnit ttlUnit) {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl, ttlUnit)
                .recordStats()
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Catalog management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Seeds the local catalog cache from a persistent registry record.
     *
     * <p>Calling this method replaces any existing entry for the same
     * {@code agentId}, making it safe to call on registry refresh events.
     *
     * @param record the registry entry to seed into the cache (never {@code null})
     */
    public void registerFromRegistry(@NotNull AgentRegistryRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        cache.put(record.agentId(), record);
        log.debug("Registered agent '{}' (type={}, tenant={}) into local catalog cache",
                record.agentId(), record.agentType(), record.tenantId());
    }

    /**
     * Removes all catalog entries for the given tenant, e.g. on tenant
     * suspension.
     *
     * @param tenantId the tenant whose entries should be evicted
     */
    public void evictTenant(@NotNull String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        List<String> toEvict = cache.asMap().entrySet().stream()
                .filter(e -> tenantId.equals(e.getValue().tenantId()))
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toList());
        cache.invalidateAll(toEvict);
        log.info("Evicted {} catalog entries for tenant '{}'", toEvict.size(), tenantId);
    }

    /**
     * Invalidates a single agent entry, forcing a refresh on the next
     * {@link #resolve} call (or its subclass override).
     *
     * @param agentId the agent to invalidate
     */
    public void invalidate(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        cache.invalidate(agentId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dispatch
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves an agent from the local catalog cache.
     *
     * <p>Returns {@code Optional.empty()} on a cache miss. Subclasses may
     * override this method to add a fallback (e.g. JDBC read-through).
     *
     * @param agentId  the unique agent identifier
     * @param tenantId the tenant scope — used by subclass overrides for security
     * @return the cached {@link AgentRegistryRecord}, or empty if not found
     */
    @NotNull
    public Optional<AgentRegistryRecord> resolve(@NotNull String agentId,
                                                 @NotNull String tenantId) {
        Objects.requireNonNull(agentId,  "agentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Optional.ofNullable(cache.getIfPresent(agentId));
    }

    /**
     * Returns all cached entries that advertise the given capability, scoped
     * to the specified tenant.
     *
     * @param capability the capability to match (case-sensitive)
     * @param tenantId   the tenant scope
     * @return list of matching records (may be empty, never {@code null})
     */
    @NotNull
    public List<AgentRegistryRecord> resolveByCapability(@NotNull String capability,
                                                         @NotNull String tenantId) {
        return cache.asMap().values().stream()
                .filter(r -> tenantId.equals(r.tenantId()) && r.hasCapability(capability))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Observability
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the current number of entries held by the local cache.
     *
     * @return cached entry count (approximate due to eviction)
     */
    public long cachedEntryCount() {
        return cache.size();
    }

    /**
     * Returns Guava cache statistics (hit count, miss count, eviction count,
     * etc.) for use in metrics reporting.
     *
     * @return cache statistics snapshot
     */
    @NotNull
    public CacheStats cacheStats() {
        return cache.stats();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Package-private cache access (for subclass read-through implementations)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Direct cache access for subclasses that need to populate the cache after
     * a successful read-through (e.g. {@link RegistryReadThroughDispatcher}).
     *
     * @param agentId the key
     * @param record  the value to store
     */
    protected void cacheEntry(@NotNull String agentId, @NotNull AgentRegistryRecord record) {
        cache.put(agentId, record);
    }

    /**
     * Checks whether the given agentId is currently present in the local cache
     * without triggering a load.
     *
     * @param agentId the agent identifier
     * @return {@code true} when the entry is cached
     */
    protected boolean isCached(@NotNull String agentId) {
        return cache.getIfPresent(agentId) != null;
    }
}
