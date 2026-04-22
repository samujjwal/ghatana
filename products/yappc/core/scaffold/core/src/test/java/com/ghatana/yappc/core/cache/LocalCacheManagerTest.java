/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
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
    void testSimpleConfiguration() { // GH-90000
        // Test simplified configuration (memory-only, no statistics) // GH-90000
        YappcCacheManager cache = LocalCacheManager.createSimple(); // GH-90000

        CachedArtifact artifact =
                new CachedArtifact("test content", "text/plain", Map.of("key", "value")); // GH-90000

        // Put and get
        runPromise(() -> cache.put("test-key", artifact)); // GH-90000

        Optional<CachedArtifact> result = cache.get("test-key [GH-90000]");
        assertTrue(result.isPresent()); // GH-90000
        assertEquals("test content", result.get().getContent()); // GH-90000
        assertEquals("text/plain", result.get().getContentType()); // GH-90000

        // Test invalidation
        assertTrue(cache.invalidate("test-key [GH-90000]"));
        assertFalse(cache.get("test-key [GH-90000]").isPresent());
    }

    @Test
    void testDefaultConfiguration() { // GH-90000
        // Test default configuration with disk persistence and statistics
        CacheConfiguration config =
                CacheConfiguration.builder().cacheDirectory(tempDir.resolve("test-cache [GH-90000]")).build();

        YappcCacheManager cache = new LocalCacheManager(config); // GH-90000

        CachedArtifact artifact =
                new CachedArtifact( // GH-90000
                        "persistent content", "application/json", Map.of("type", "test")); // GH-90000

        // Put and get
        runPromise(() -> cache.put("persistent-key", artifact)); // GH-90000

        Optional<CachedArtifact> result = cache.get("persistent-key [GH-90000]");
        assertTrue(result.isPresent()); // GH-90000
        assertEquals("persistent content", result.get().getContent()); // GH-90000

        // Test statistics
        CacheStatistics stats = cache.getStatistics(); // GH-90000
        assertEquals(1, stats.getHitCount()); // GH-90000
        assertEquals(0, stats.getMissCount()); // GH-90000
        assertEquals(1.0, stats.getHitRate(), 0.001); // GH-90000
    }

    @Test
    void testCustomConfiguration() { // GH-90000
        // Test custom configuration
        CacheConfiguration config =
                CacheConfiguration.builder() // GH-90000
                        .persistToDisk(false) // GH-90000
                        .enableStatistics(true) // GH-90000
                        .maxMemoryEntries(10) // GH-90000
                        .defaultTtl(Duration.ofMinutes(30)) // GH-90000
                        .asyncMode(false) // GH-90000
                        .build(); // GH-90000

        YappcCacheManager cache = new LocalCacheManager(config); // GH-90000

        CachedArtifact artifact =
                new CachedArtifact("custom config content", "text/xml", Map.of("config", "custom")); // GH-90000

        runPromise(() -> cache.put("custom-key", artifact)); // GH-90000

        Optional<CachedArtifact> result = cache.get("custom-key [GH-90000]");
        assertTrue(result.isPresent()); // GH-90000
        assertEquals("custom config content", result.get().getContent()); // GH-90000

        // Test statistics are enabled
        CacheStatistics stats = cache.getStatistics(); // GH-90000
        assertTrue(stats.getHitCount() > 0); // GH-90000
        assertTrue(stats.getHitRate() > 0); // GH-90000
    }

    @Test
    void testBackwardCompatibility() { // GH-90000
        // Test backward compatibility with old constructors
        YappcCacheManager defaultCache = new LocalCacheManager(); // GH-90000
        assertNotNull(defaultCache); // GH-90000

        YappcCacheManager pathCache = new LocalCacheManager(tempDir.resolve("path-cache [GH-90000]"));
        assertNotNull(pathCache); // GH-90000

        // Both should work the same way
        CachedArtifact artifact =
                new CachedArtifact("backward compatible", "text/plain", Map.of("test", "backward")); // GH-90000

        runPromise(() -> defaultCache.put("test1", artifact)); // GH-90000
        runPromise(() -> pathCache.put("test2", artifact)); // GH-90000

        assertTrue(defaultCache.get("test1 [GH-90000]").isPresent());
        assertTrue(pathCache.get("test2 [GH-90000]").isPresent());
    }

    @Test
    void testCacheInvalidation() { // GH-90000
        YappcCacheManager cache = LocalCacheManager.createSimple(); // GH-90000

        CachedArtifact artifact1 = new CachedArtifact("content1", "text/plain", Map.of()); // GH-90000
        CachedArtifact artifact2 = new CachedArtifact("content2", "text/plain", Map.of()); // GH-90000

        runPromise(() -> cache.put("key1", artifact1)); // GH-90000
        runPromise(() -> cache.put("key2", artifact2)); // GH-90000

        // Both should be present
        assertTrue(cache.get("key1 [GH-90000]").isPresent());
        assertTrue(cache.get("key2 [GH-90000]").isPresent());

        // Invalidate all
        cache.invalidateAll(); // GH-90000

        // Both should be gone
        assertFalse(cache.get("key1 [GH-90000]").isPresent());
        assertFalse(cache.get("key2 [GH-90000]").isPresent());
    }
}
