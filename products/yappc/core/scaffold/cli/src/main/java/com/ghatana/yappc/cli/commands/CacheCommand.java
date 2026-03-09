/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
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

package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.cache.YappcCacheManager;
import com.ghatana.yappc.core.cache.CacheStatistics;
import com.ghatana.yappc.core.cache.LocalCacheManager;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache management CLI commands. Provides cache statistics, status, and maintenance operations. */
@CommandLine.Command(
        name = "cache",
        description = "Cache management operations",
        subcommands = {
            CacheCommand.StatsCommand.class,
            CacheCommand.ClearCommand.class,
            CacheCommand.StatusCommand.class
        })
/**
 * CacheCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose CacheCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class CacheCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(CacheCommand.class);

    @Override
    public Integer call() throws Exception {
        log.info("🗃️  YAPPC Cache Management");
        log.info("");
        log.info("Available commands:");
        log.info("  stats   - Show cache statistics");
        log.info("  status  - Show cache status");
        log.info("  clear   - Clear cache entries");
        log.info("");
        log.info("Use 'yappc cache <command> --help' for more information");

        return 0;
    }

    @CommandLine.Command(name = "stats", description = "Show detailed cache statistics")
    static class StatsCommand implements Callable<Integer> {

        @CommandLine.Option(
                names = {"--json"},
                description = "Output statistics in JSON format")
        private boolean jsonOutput;

        @Override
        public Integer call() throws Exception {
            YappcCacheManager cacheManager = LocalCacheManager.createSimple();
            CacheStatistics stats = cacheManager.getStatistics();

            if (jsonOutput) {
                outputJsonStats(stats);
            } else {
                outputConsoleStats(stats);
            }

            return 0;
        }

        private void outputConsoleStats(CacheStatistics stats) {
            log.info("📊 Cache Statistics");
            log.info("─".repeat(50));
            log.info("");;

            // Request Statistics
            log.info("🎯 Request Statistics:");
            log.info(String.format("   Hits:        %,d", stats.getHitCount()));
            log.info(String.format("   Misses:      %,d", stats.getMissCount()));
            log.info(String.format("   Total:       %,d", stats.getRequestCount()));
            if (stats.getRequestCount() > 0) {
                log.info(String.format("   Hit Rate:    %.2f%%", stats.getHitRate() * 100));
                log.info(String.format("   Miss Rate:   %.2f%%", stats.getMissRate() * 100));
            }
            log.info("");;

            // Size Statistics
            log.info("💾 Size Statistics:");
            log.info(String.format("   Memory Entries: %,d", stats.getEstimatedSize()));
            log.info(String.format("   Disk Entries:   %,d", stats.getDiskEntries()));
            log.info("   Disk Size:      {}", formatBytes(stats.getDiskSizeBytes()));
            log.info("");;

            // Performance Statistics
            log.info("⚡ Performance Statistics:");
            log.info(String.format("   Evictions:      %,d", stats.getEvictionCount()));
            log.info("   Total Load:     {}", formatDuration(stats.getTotalLoadTime()));
            log.info("   Avg Load:       {}", formatDuration(stats.getAverageLoadPenalty()));

            // Performance Summary
            log.info("");;
            log.info("📈 Summary:");
            if (stats.getRequestCount() == 0) {
                log.info("   No cache requests recorded yet");
            } else if (stats.getHitRate() > 0.8) {
                log.info("   ✅ Excellent cache performance");
            } else if (stats.getHitRate() > 0.6) {
                log.info("   👍 Good cache performance");
            } else if (stats.getHitRate() > 0.4) {
                log.info("   ⚠️  Moderate cache performance");
            } else {
                log.info("   🔴 Poor cache performance - consider cache tuning");
            }
        }

        private void outputJsonStats(CacheStatistics stats) {
            String json =
                    String.format(
                            """
                {
                  "requests": {
                    "hits": %d,
                    "misses": %d,
                    "total": %d,
                    "hitRate": %.4f,
                    "missRate": %.4f
                  },
                  "size": {
                    "memoryEntries": %d,
                    "diskEntries": %d,
                    "diskSizeBytes": %d
                  },
                  "performance": {
                    "evictions": %d,
                    "totalLoadTimeNanos": %d,
                    "averageLoadPenaltyNanos": %d
                  }
                }""",
                            stats.getHitCount(),
                            stats.getMissCount(),
                            stats.getRequestCount(),
                            stats.getHitRate(),
                            stats.getMissRate(),
                            stats.getEstimatedSize(),
                            stats.getDiskEntries(),
                            stats.getDiskSizeBytes(),
                            stats.getEvictionCount(),
                            stats.getTotalLoadTime().toNanos(),
                            stats.getAverageLoadPenalty().toNanos());
            log.info("{}", json);
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024)
                return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }

        private String formatDuration(java.time.Duration duration) {
            long nanos = duration.toNanos();
            if (nanos < 1_000) return nanos + " ns";
            if (nanos < 1_000_000) return String.format("%.2f μs", nanos / 1_000.0);
            if (nanos < 1_000_000_000) return String.format("%.2f ms", nanos / 1_000_000.0);
            return String.format("%.2f s", nanos / 1_000_000_000.0);
        }
    }

    @CommandLine.Command(name = "clear", description = "Clear cache entries")
    static class ClearCommand implements Callable<Integer> {

        @CommandLine.Option(
                names = {"--all"},
                description = "Clear all cache entries")
        private boolean clearAll;

        @CommandLine.Option(
                names = {"--key"},
                description = "Clear specific cache entry by key")
        private String key;

        @Override
        public Integer call() throws Exception {
            YappcCacheManager cacheManager = LocalCacheManager.createSimple();

            if (clearAll) {
                log.info("🗑️  Clearing all cache entries...");
                cacheManager.invalidateAll();
                log.info("✅ All cache entries cleared");
            } else if (key != null) {
                log.info("🗑️  Clearing cache entry: {}...", key);
                boolean removed = cacheManager.invalidate(key);
                if (removed) {
                    log.info("✅ Cache entry cleared");
                } else {
                    log.info("⚠️  Cache entry not found");
                }
            } else {
                log.error("❌ Must specify either --all or --key <key>");
                return 1;
            }

            return 0;
        }
    }

    @CommandLine.Command(name = "status", description = "Show cache status and health")
    static class StatusCommand implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            YappcCacheManager cacheManager = LocalCacheManager.createSimple();
            CacheStatistics stats = cacheManager.getStatistics();

            log.info("🗃️  Cache Status");
            log.info("─".repeat(30));
            log.info("");;

            // Basic Status
            log.info("Status:           {}", stats.getRequestCount() > 0 ? "🟢 Active" : "🔵 Idle");
            log.info(String.format("Memory Entries:   %,d", stats.getEstimatedSize()));
            log.info(String.format("Requests:         %,d", stats.getRequestCount()));

            if (stats.getRequestCount() > 0) {
                log.info(String.format("Hit Rate:         %.1f%%", stats.getHitRate() * 100));

                // Health Assessment
                String health;
                if (stats.getHitRate() > 0.8) {
                    health = "🟢 Excellent";
                } else if (stats.getHitRate() > 0.6) {
                    health = "🟡 Good";
                } else {
                    health = "🔴 Poor";
                }
                log.info("Health:           {}", health);
            } else {
                log.info("Hit Rate:         N/A");
                log.info("Health:           🔵 No data");
            }

            log.info("");;
            log.info("💡 Use 'yappc cache stats' for detailed statistics");

            return 0;
        }
    }
}
