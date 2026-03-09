package com.ghatana.yappc.core.recommendations;

/**
 * Canonical recommendation categories for cache tuning and optimization.
 * @doc.type enum
 * @doc.purpose Canonical recommendation categories for cache tuning and optimization.
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum CacheRecommendationType {
    ENABLE_CACHING,
    OPTIMIZE_CACHE_STRATEGY,
    INCREASE_CACHE_SIZE,
    REMOVE_CACHE_KEY,
    ADJUST_TTL,
    MEMORY_OPTIMIZATION,
    PROJECT_SPECIFIC_TUNING,
    SHARED_CACHE_OPTIMIZATION
}
