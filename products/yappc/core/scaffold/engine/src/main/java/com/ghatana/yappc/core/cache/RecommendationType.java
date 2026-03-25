/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.yappc.core.cache;

/**
 * Types of cache recommendations that can be made by the AI cache policy tuner.
 *
 * @doc.type enum
 * @doc.purpose Types of cache recommendations for policy tuning
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum RecommendationType {
    /**
     * Enable caching for tasks that are not currently cached
     */
    ENABLE_CACHING,
    /**
     * Optimize the cache strategy for better performance
     */
    OPTIMIZE_CACHE_STRATEGY,
    /**
     * Increase the cache size to improve hit rate
     */
    INCREASE_CACHE_SIZE,
    /**
     * Remove cache key that has poor hit rate
     */
    REMOVE_CACHE_KEY,
    /**
     * Adjust time-to-live (TTL) settings
     */
    ADJUST_TTL,
    /**
     * Optimize memory usage patterns
     */
    MEMORY_OPTIMIZATION,
    /**
     * Apply project-specific tuning recommendations
     */
    PROJECT_SPECIFIC_TUNING,
    /**
     * Optimize shared cache usage across projects
     */
    SHARED_CACHE_OPTIMIZATION
}
