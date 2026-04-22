package com.ghatana.datacloud.profile.cache;

import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Profile Cache Manager - Caches collection storage profiles for fast access.
 *
 * <p>This service provides caching for:
 * <ul>
 *   <li>Profile metadata by tenant and collection</li>
 *   <li>Active profile lookups</li>
 *   <li>Backend availability checks</li>
 * </ul>
 *
 * <p><b>Cache Strategy</b><br>
 * <ol>
 *   <li>Profiles are cached indefinitely (no expiration for metadata)</li>
 *   <li>Cache invalidation occurs on profile updates/deletions</li>
 *   <li>Thread-safe using ConcurrentHashMap</li>
 *   <li>Statistics tracking for cache hits/misses</li>
 * </ol>
 *
 * <p><b>Thread Safety</b><br>
 * All operations are thread-safe via ConcurrentHashMap and atomic counters.
 *
 * @doc.type class
 * @doc.purpose Profile caching service
 * @doc.layer product
 * @doc.pattern Cache Manager
 */
public class ProfileCacheManager {

    private static final Logger log = LoggerFactory.getLogger(ProfileCacheManager.class);

    private static final AtomicReference<ProfileCacheManager> INSTANCE = new AtomicReference<>();

    // Cache key format: "tenantId:collectionName"
    private final Map<String, CachedProfile> profileCache = new ConcurrentHashMap<>();
    
    // Cache for active profiles by tenant: tenantId -> collectionName -> profile
    private final Map<String, Map<String, CollectionStorageProfile>> activeProfilesCache = new ConcurrentHashMap<>();

    // Cache statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheInvalidations = new AtomicLong(0);

    private ProfileCacheManager() {
        log.info("ProfileCacheManager initialized");
    }

    /**
     * Gets the singleton instance.
     */
    public static ProfileCacheManager getInstance() {
        ProfileCacheManager instance = INSTANCE.get();
        if (instance == null) {
            instance = new ProfileCacheManager();
            INSTANCE.compareAndSet(null, instance);
        }
        return instance;
    }

    // ========================================================================
    // Cache Operations
    // ========================================================================

    /**
     * Caches a profile for a tenant and collection.
     *
     * @param profile profile to cache
     */
    public void cacheProfile(CollectionStorageProfile profile) {
        if (profile == null) {
            log.warn("Cannot cache null profile");
            return;
        }

        String key = makeKey(profile.getTenantId(), profile.getCollectionName());
        CachedProfile cached = new CachedProfile(profile, Instant.now());
        profileCache.put(key, cached);

        // Update active profiles cache if profile is active
        if (profile.getIsActive()) {
            activeProfilesCache
                .computeIfAbsent(profile.getTenantId(), k -> new ConcurrentHashMap<>())
                .put(profile.getCollectionName(), profile);
        } else {
            // Remove from active cache if inactive
            Map<String, CollectionStorageProfile> tenantCache = activeProfilesCache.get(profile.getTenantId());
            if (tenantCache != null) {
                tenantCache.remove(profile.getCollectionName());
            }
        }

        log.debug("Cached profile [tenant={}, collection={}]", profile.getTenantId(), profile.getCollectionName());
    }

    /**
     * Gets a cached profile by tenant and collection.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return cached profile or empty if not found
     */
    public Optional<CollectionStorageProfile> getProfile(String tenantId, String collectionName) {
        String key = makeKey(tenantId, collectionName);
        CachedProfile cached = profileCache.get(key);

        if (cached != null) {
            cacheHits.incrementAndGet();
            return Optional.of(cached.profile());
        }

        cacheMisses.incrementAndGet();
        return Optional.empty();
    }

    /**
     * Gets an active profile by tenant and collection.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return cached active profile or empty if not found
     */
    public Optional<CollectionStorageProfile> getActiveProfile(String tenantId, String collectionName) {
        Map<String, CollectionStorageProfile> tenantCache = activeProfilesCache.get(tenantId);
        if (tenantCache != null) {
            CollectionStorageProfile profile = tenantCache.get(collectionName);
            if (profile != null && profile.getIsActive()) {
                cacheHits.incrementAndGet();
                return Optional.of(profile);
            }
        }
        cacheMisses.incrementAndGet();
        return Optional.empty();
    }

    /**
     * Invalidates a cached profile by tenant and collection.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     */
    public void invalidateProfile(String tenantId, String collectionName) {
        String key = makeKey(tenantId, collectionName);
        profileCache.remove(key);
        
        Map<String, CollectionStorageProfile> tenantCache = activeProfilesCache.get(tenantId);
        if (tenantCache != null) {
            tenantCache.remove(collectionName);
        }

        cacheInvalidations.incrementAndGet();
        log.debug("Invalidated profile cache [tenant={}, collection={}]", tenantId, collectionName);
    }

    /**
     * Invalidates all profiles for a tenant.
     *
     * @param tenantId tenant identifier
     * @return number of profiles invalidated
     */
    public int invalidateTenantProfiles(String tenantId) {
        int count = 0;
        String prefix = tenantId + ":";

        Iterator<Map.Entry<String, CachedProfile>> it = profileCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CachedProfile> entry = it.next();
            if (entry.getKey().startsWith(prefix)) {
                it.remove();
                count++;
            }
        }

        activeProfilesCache.remove(tenantId);
        cacheInvalidations.addAndGet(count);
        log.info("Invalidated {} profile cache entries for tenant {}", count, tenantId);

        return count;
    }

    /**
     * Clears all cached profiles.
     */
    public void clearAll() {
        int size = profileCache.size();
        profileCache.clear();
        activeProfilesCache.clear();
        cacheInvalidations.addAndGet(size);
        log.info("Cleared all profile cache entries (count={})", size);
    }

    /**
     * Gets all cached profiles for a tenant.
     *
     * @param tenantId tenant identifier
     * @return list of cached profiles
     */
    public List<CollectionStorageProfile> getTenantProfiles(String tenantId) {
        List<CollectionStorageProfile> profiles = new ArrayList<>();
        String prefix = tenantId + ":";

        for (CachedProfile cached : profileCache.values()) {
            if (cached.profile().getTenantId().equals(tenantId)) {
                profiles.add(cached.profile());
            }
        }

        return Collections.unmodifiableList(profiles);
    }

    /**
     * Gets all active profiles for a tenant.
     *
     * @param tenantId tenant identifier
     * @return list of active profiles
     */
    public List<CollectionStorageProfile> getActiveTenantProfiles(String tenantId) {
        Map<String, CollectionStorageProfile> tenantCache = activeProfilesCache.get(tenantId);
        if (tenantCache == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(tenantCache.values()));
    }

    // ========================================================================
    // Cache Statistics
    // ========================================================================

    /**
     * Gets cache statistics.
     *
     * @return cache statistics
     */
    public CacheStatistics getStatistics() {
        long totalRequests = cacheHits.get() + cacheMisses.get();
        double hitRate = totalRequests > 0 ? (double) cacheHits.get() / totalRequests : 0.0;

        return new CacheStatistics(
            profileCache.size(),
            cacheHits.get(),
            cacheMisses.get(),
            cacheInvalidations.get(),
            hitRate
        );
    }

    /**
     * Resets cache statistics.
     */
    public void resetStatistics() {
        cacheHits.set(0);
        cacheMisses.set(0);
        cacheInvalidations.set(0);
        log.info("Reset profile cache statistics");
    }

    /**
     * Gets the current cache size.
     *
     * @return number of cached profiles
     */
    public int size() {
        return profileCache.size();
    }

    /**
     * Checks if a profile is cached.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return true if profile is cached
     */
    public boolean isCached(String tenantId, String collectionName) {
        return profileCache.containsKey(makeKey(tenantId, collectionName));
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private String makeKey(String tenantId, String collectionName) {
        return tenantId + ":" + collectionName;
    }

    // ========================================================================
    // Inner Classes
    // ========================================================================

    /**
     * Cached profile with metadata.
     */
    private record CachedProfile(
        CollectionStorageProfile profile,
        Instant cachedAt
    ) {}

    /**
     * Cache statistics.
     */
    public record CacheStatistics(
        int cacheSize,
        long hits,
        long misses,
        long invalidations,
        double hitRate
    ) {}
}
