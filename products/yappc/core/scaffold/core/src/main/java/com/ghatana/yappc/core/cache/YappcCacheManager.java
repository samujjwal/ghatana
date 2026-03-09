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

import java.util.Optional;
import io.activej.promise.Promise;

/**
 * Day 22: Cache manager interface for YAPPC caching system. Defines common operations for both
 * local and remote cache implementations.
 *
 * @doc.type interface
 * @doc.purpose Day 22: Cache manager interface for YAPPC caching system. Defines common operations for both
 * @doc.layer platform
 * @doc.pattern Manager
 */
public interface YappcCacheManager {

    /**
 * Retrieves a cached artifact by key. */
    Optional<CachedArtifact> get(String key);

    /**
 * Stores an artifact in the cache. */
    Promise<Void> put(String key, CachedArtifact artifact);

    /**
 * Invalidates a cache entry by key. */
    boolean invalidate(String key);

    /**
 * Invalidates all cache entries. */
    void invalidateAll();

    /**
 * Gets cache statistics. */
    CacheStatistics getStatistics();
}
