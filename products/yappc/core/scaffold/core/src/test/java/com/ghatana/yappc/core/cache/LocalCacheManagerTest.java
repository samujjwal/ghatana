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

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles local cache manager test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class LocalCacheManagerTest extends EventloopTestBase {

    @TempDir Path tempDir;

    @Test
    void testSimpleConfiguration() {
        // Test simplified configuration (memory-only, no statistics)
        YappcCacheManager cache = LocalCacheManager.createSimple();

        CachedArtifact artifact =
                new CachedArtifact("test content", "text/plain", Map.of("key", "value"));

        // Put and get
        runPromise(() -> cache.put("test-key", artifact));

        Optional<CachedArtifact> result = cache.get("test-key");
        assertTrue(result.isPresent());
        assertEquals("test content", result.get().getContent());
        assertEquals("text/plain", result.get().getContentType());

        // Test invalidation
        assertTrue(cache.invalidate("test-key"));
        assertFalse(cache.get("test-key").isPresent());
    }

    @Test
    void testDefaultConfiguration() {
        // Test default configuration with disk persistence and statistics
        CacheConfiguration config =
                CacheConfiguration.builder().cacheDirectory(tempDir.resolve("test-cache")).build();

        YappcCacheManager cache = new LocalCacheManager(config);

        CachedArtifact artifact =
                new CachedArtifact(
                        "persistent content", "application/json", Map.of("type", "test"));

        // Put and get
        runPromise(() -> cache.put("persistent-key", artifact));

        Optional<CachedArtifact> result = cache.get("persistent-key");
        assertTrue(result.isPresent());
        assertEquals("persistent content", result.get().getContent());

        // Test statistics
        CacheStatistics stats = cache.getStatistics();
        assertEquals(1, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
        assertEquals(1.0, stats.getHitRate(), 0.001);
    }

    @Test
    void testCustomConfiguration() {
        // Test custom configuration
        CacheConfiguration config =
                CacheConfiguration.builder()
                        .persistToDisk(false)
                        .enableStatistics(true)
                        .maxMemoryEntries(10)
                        .defaultTtl(Duration.ofMinutes(30))
                        .asyncMode(false)
                        .build();

        YappcCacheManager cache = new LocalCacheManager(config);

        CachedArtifact artifact =
                new CachedArtifact("custom config content", "text/xml", Map.of("config", "custom"));

        runPromise(() -> cache.put("custom-key", artifact));

        Optional<CachedArtifact> result = cache.get("custom-key");
        assertTrue(result.isPresent());
        assertEquals("custom config content", result.get().getContent());

        // Test statistics are enabled
        CacheStatistics stats = cache.getStatistics();
        assertTrue(stats.getHitCount() > 0);
        assertTrue(stats.getHitRate() > 0);
    }

    @Test
    void testBackwardCompatibility() {
        // Test backward compatibility with old constructors
        YappcCacheManager defaultCache = new LocalCacheManager();
        assertNotNull(defaultCache);

        YappcCacheManager pathCache = new LocalCacheManager(tempDir.resolve("path-cache"));
        assertNotNull(pathCache);

        // Both should work the same way
        CachedArtifact artifact =
                new CachedArtifact("backward compatible", "text/plain", Map.of("test", "backward"));

        runPromise(() -> defaultCache.put("test1", artifact));
        runPromise(() -> pathCache.put("test2", artifact));

        assertTrue(defaultCache.get("test1").isPresent());
        assertTrue(pathCache.get("test2").isPresent());
    }

    @Test
    void testCacheInvalidation() {
        YappcCacheManager cache = LocalCacheManager.createSimple();

        CachedArtifact artifact1 = new CachedArtifact("content1", "text/plain", Map.of());
        CachedArtifact artifact2 = new CachedArtifact("content2", "text/plain", Map.of());

        runPromise(() -> cache.put("key1", artifact1));
        runPromise(() -> cache.put("key2", artifact2));

        // Both should be present
        assertTrue(cache.get("key1").isPresent());
        assertTrue(cache.get("key2").isPresent());

        // Invalidate all
        cache.invalidateAll();

        // Both should be gone
        assertFalse(cache.get("key1").isPresent());
        assertFalse(cache.get("key2").isPresent());
    }
}
