package com.ghatana.datacloud.profile.composition;

import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optimized profile composer for efficient profile composition and merging.
 *
 * <p>This service provides:
 * <ul>
 *   <li>Profile composition from base profiles and overrides</li>
 *   <li>Cached composition results for fast access</li>
 *   <li>Profile inheritance and hierarchy support</li>
 *   <li>Backend configuration merging</li>
 *   <li>Priority-based profile resolution</li>
 * </ul>
 *
 * <p><b>Composition Strategy</b><br>
 * <ol>
 *   <li>Start with base profile (if provided)</li>
 *   <li>Apply tenant-specific overrides</li>
 *   <li>Apply collection-specific overrides</li>
 *   <li>Merge backend configurations (overrides take precedence)</li>
 *   <li>Combine fallback backends (union of all sources)</li>
 * </ol>
 *
 * <p><b>Thread Safety</b><br>
 * All operations are thread-safe via ConcurrentHashMap.
 *
 * @doc.type class
 * @doc.purpose Optimized profile composition service
 * @doc.layer product
 * @doc.pattern Composition Service
 */
public class OptimizedProfileComposer {

    private static final Logger log = LoggerFactory.getLogger(OptimizedProfileComposer.class);

    private static final AtomicReference<OptimizedProfileComposer> INSTANCE = new AtomicReference<>();

    // Cache for composed profiles: "baseId:tenantId:collectionName" -> ComposedProfile
    private final Map<String, ComposedProfile> compositionCache = new ConcurrentHashMap<>();

    // Cache for profile merges: "profileId1:profileId2" -> MergedProfile
    private final Map<String, CollectionStorageProfile> mergeCache = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong compositionCount = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    private OptimizedProfileComposer() {
        log.info("OptimizedProfileComposer initialized");
    }

    /**
     * Gets the singleton instance.
     */
    public static OptimizedProfileComposer getInstance() {
        OptimizedProfileComposer instance = INSTANCE.get();
        if (instance == null) {
            instance = new OptimizedProfileComposer();
            INSTANCE.compareAndSet(null, instance);
        }
        return instance;
    }

    // ========================================================================
    // Composition Operations
    // ========================================================================

    /**
     * Composes a profile from base profile and overrides.
     *
     * @param baseProfile base profile (optional)
     * @param overrides override profile
     * @return composed profile
     */
    public CollectionStorageProfile compose(
            CollectionStorageProfile baseProfile,
            CollectionStorageProfile overrides) {
        if (baseProfile == null) {
            return overrides;
        }
        if (overrides == null) {
            return baseProfile;
        }

        String cacheKey = makeMergeKey(baseProfile.getId(), overrides.getId());
        CollectionStorageProfile cached = mergeCache.get(cacheKey);

        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        CollectionStorageProfile composed = mergeProfiles(baseProfile, overrides);
        mergeCache.put(cacheKey, composed);
        compositionCount.incrementAndGet();

        log.debug("Composed profile [base={}, overrides={}]", 
            baseProfile.getId(), overrides.getId());

        return composed;
    }

    /**
     * Composes multiple profiles with priority ordering.
     *
     * <p>Profiles are merged in order of priority (lower number = higher priority).
     * Later profiles override earlier ones.
     *
     * @param profiles list of profiles to compose
     * @return composed profile
     */
    public CollectionStorageProfile composeMultiple(List<CollectionStorageProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("Profiles list cannot be null or empty");
        }

        if (profiles.size() == 1) {
            return profiles.get(0);
        }

        // Sort by priority
        List<CollectionStorageProfile> sorted = new ArrayList<>(profiles);
        sorted.sort(Comparator.comparingInt(CollectionStorageProfile::getPriorityOrder));

        // Merge sequentially
        CollectionStorageProfile result = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            result = compose(result, sorted.get(i));
        }

        compositionCount.incrementAndGet();
        return result;
    }

    /**
     * Composes profile with tenant and collection overrides.
     *
     * @param baseProfile base profile
     * @param tenantProfile tenant-specific overrides
     * @param collectionProfile collection-specific overrides
     * @return composed profile
     */
    public CollectionStorageProfile composeWithOverrides(
            CollectionStorageProfile baseProfile,
            CollectionStorageProfile tenantProfile,
            CollectionStorageProfile collectionProfile) {
        CollectionStorageProfile result = baseProfile;

        if (tenantProfile != null) {
            result = compose(result, tenantProfile);
        }

        if (collectionProfile != null) {
            result = compose(result, collectionProfile);
        }

        return result;
    }

    // ========================================================================
    // Cache Operations
    // ========================================================================

    /**
     * Invalidates composition cache for specific profiles.
     *
     * @param profileIds profile IDs to invalidate
     */
    public void invalidateCompositionCache(String... profileIds) {
        int count = 0;
        for (String profileId : profileIds) {
            Iterator<Map.Entry<String, ComposedProfile>> it = compositionCache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ComposedProfile> entry = it.next();
                if (entry.getKey().contains(profileId)) {
                    it.remove();
                    count++;
                }
            }

            Iterator<Map.Entry<String, CollectionStorageProfile>> mergeIt = mergeCache.entrySet().iterator();
            while (mergeIt.hasNext()) {
                Map.Entry<String, CollectionStorageProfile> entry = mergeIt.next();
                if (entry.getKey().contains(profileId)) {
                    mergeIt.remove();
                    count++;
                }
            }
        }

        log.debug("Invalidated {} composition cache entries", count);
    }

    /**
     * Clears all composition caches.
     */
    public void clearAllCaches() {
        int compositionSize = compositionCache.size();
        int mergeSize = mergeCache.size();
        compositionCache.clear();
        mergeCache.clear();
        log.info("Cleared composition caches (composition={}, merge={})", 
            compositionSize, mergeSize);
    }

    // ========================================================================
    // Statistics
    // ========================================================================

    /**
     * Gets composition statistics.
     *
     * @return composition statistics
     */
    public CompositionStatistics getStatistics() {
        long totalRequests = cacheHits.get() + cacheMisses.get();
        double hitRate = totalRequests > 0 ? (double) cacheHits.get() / totalRequests : 0.0;

        return new CompositionStatistics(
            compositionCache.size(),
            mergeCache.size(),
            compositionCount.get(),
            cacheHits.get(),
            cacheMisses.get(),
            hitRate
        );
    }

    /**
     * Resets statistics.
     */
    public void resetStatistics() {
        compositionCount.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        log.info("Reset composition statistics");
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private String makeMergeKey(String profileId1, String profileId2) {
        return profileId1 + ":" + profileId2;
    }

    private CollectionStorageProfile mergeProfiles(
            CollectionStorageProfile base,
            CollectionStorageProfile override) {
        // Merge backend configurations
        Map<String, Object> mergedConfig = new HashMap<>(base.getBackendConfig());
        mergedConfig.putAll(override.getBackendConfig());

        // Merge fallback backends (union, deduplicated)
        Set<String> mergedFallbacks = new LinkedHashSet<>(base.getFallbackBackendIds());
        mergedFallbacks.addAll(override.getFallbackBackendIds());
        List<String> fallbackList = new ArrayList<>(mergedFallbacks);

        // Override values from override profile
        String primaryBackendId = override.getPrimaryBackendId() != null 
            ? override.getPrimaryBackendId() 
            : base.getPrimaryBackendId();

        Boolean isActive = override.getIsActive() != null 
            ? override.getIsActive() 
            : base.getIsActive();

        Integer priorityOrder = override.getPriorityOrder() != null 
            ? override.getPriorityOrder() 
            : base.getPriorityOrder();

        Instant updatedAt = Instant.now();

        return CollectionStorageProfile.builder()
            .id(override.getId())
            .tenantId(override.getTenantId())
            .collectionName(override.getCollectionName())
            .storageProfileId(override.getStorageProfileId())
            .primaryBackendId(primaryBackendId)
            .fallbackBackendIds(fallbackList)
            .backendConfig(mergedConfig)
            .isActive(isActive)
            .priorityOrder(priorityOrder)
            .createdAt(base.getCreatedAt())
            .updatedAt(updatedAt)
            .build();
    }

    // ========================================================================
    // Inner Classes
    // ========================================================================

    /**
     * Composed profile metadata.
     */
    private record ComposedProfile(
        CollectionStorageProfile profile,
        List<String> sourceProfileIds,
        Instant composedAt
    ) {}

    /**
     * Composition statistics.
     */
    public record CompositionStatistics(
        int compositionCacheSize,
        int mergeCacheSize,
        long totalCompositions,
        long cacheHits,
        long cacheMisses,
        double hitRate
    ) {}
}
