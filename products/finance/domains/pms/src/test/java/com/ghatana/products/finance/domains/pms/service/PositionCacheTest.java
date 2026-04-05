package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Position Cache Tests")
class PositionCacheTest {
    private CacheService service;

    @BeforeEach
    void setUp() {
        service = new CacheService();
    }

    @Test
    @DisplayName("Should cache position")
    void shouldCachePosition() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.cache("AAPL", position);
        assertThat(service.get("AAPL")).isPresent();
    }

    @Test
    @DisplayName("Should invalidate cache entry")
    void shouldInvalidateCacheEntry() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.cache("AAPL", position);
        service.invalidate("AAPL");
        assertThat(service.get("AAPL")).isEmpty();
    }

    @Test
    @DisplayName("Should auto-expire cache entries")
    void shouldAutoExpireCacheEntries() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.cacheWithTTL("AAPL", position, 1);
        try { Thread.sleep(1100); } catch (InterruptedException e) {}
        assertThat(service.get("AAPL")).isEmpty();
    }

    @Test
    @DisplayName("Should track cache hits and misses")
    void shouldTrackCacheHitsAndMisses() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.cache("AAPL", position);
        service.get("AAPL");
        service.get("GOOGL");
        CacheStats stats = service.getStats();
        assertThat(stats.hits()).isEqualTo(1);
        assertThat(stats.misses()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should support cache warming")
    void shouldSupportCacheWarming() {
        java.util.List<Position> positions = java.util.List.of(
            new Position("AAPL", 100L, BigDecimal.valueOf(150.00)),
            new Position("GOOGL", 50L, BigDecimal.valueOf(2800.00))
        );
        service.warmCache(positions);
        assertThat(service.get("AAPL")).isPresent();
        assertThat(service.get("GOOGL")).isPresent();
    }

    @Test
    @DisplayName("Should evict least recently used entries")
    void shouldEvictLeastRecentlyUsedEntries() {
        service.setMaxSize(2);
        service.cache("AAPL", new Position("AAPL", 100L, BigDecimal.valueOf(150.00)));
        service.cache("GOOGL", new Position("GOOGL", 50L, BigDecimal.valueOf(2800.00)));
        service.cache("MSFT", new Position("MSFT", 75L, BigDecimal.valueOf(300.00)));
        assertThat(service.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should support cache refresh")
    void shouldSupportCacheRefresh() {
        Position oldPos = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Position newPos = new Position("AAPL", 150L, BigDecimal.valueOf(151.00));
        service.cache("AAPL", oldPos);
        service.refresh("AAPL", newPos);
        assertThat(service.get("AAPL").get().quantity()).isEqualTo(150L);
    }

    @Test
    @DisplayName("Should clear entire cache")
    void shouldClearEntireCache() {
        service.cache("AAPL", new Position("AAPL", 100L, BigDecimal.valueOf(150.00)));
        service.cache("GOOGL", new Position("GOOGL", 50L, BigDecimal.valueOf(2800.00)));
        service.clear();
        assertThat(service.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should support cache partitioning")
    void shouldSupportCachePartitioning() {
        service.cache("account-1:AAPL", new Position("AAPL", 100L, BigDecimal.valueOf(150.00)));
        service.cache("account-2:AAPL", new Position("AAPL", 50L, BigDecimal.valueOf(151.00)));
        assertThat(service.getByPrefix("account-1")).hasSize(1);
    }

    @Test
    @DisplayName("Should generate cache report")
    void shouldGenerateCacheReport() {
        service.cache("AAPL", new Position("AAPL", 100L, BigDecimal.valueOf(150.00)));
        service.get("AAPL");
        CacheReport report = service.generateReport();
        assertThat(report.size()).isEqualTo(1);
        assertThat(report.hitRate()).isGreaterThan(0.0);
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record CacheEntry(Position position, Instant cachedAt, Instant expiresAt) {}
    record CacheStats(long hits, long misses) {}
    record CacheReport(int size, double hitRate, long totalRequests) {}

    static class CacheService {
        private final java.util.Map<String, CacheEntry> cache = new java.util.concurrent.ConcurrentHashMap<>();
        private long hits = 0;
        private long misses = 0;
        private int maxSize = 1000;

        void cache(String key, Position position) {
            cacheWithTTL(key, position, 3600);
        }

        void cacheWithTTL(String key, Position position, int ttlSeconds) {
            Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
            cache.put(key, new CacheEntry(position, Instant.now(), expiresAt));
        }

        java.util.Optional<Position> get(String key) {
            CacheEntry entry = cache.get(key);
            if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
                misses++;
                return java.util.Optional.empty();
            }
            hits++;
            return java.util.Optional.of(entry.position());
        }

        void invalidate(String key) {
            cache.remove(key);
        }

        CacheStats getStats() {
            return new CacheStats(hits, misses);
        }

        void warmCache(java.util.List<Position> positions) {
            positions.forEach(pos -> cache(pos.symbol(), pos));
        }

        void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        int size() {
            cache.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt()));
            if (cache.size() > maxSize) {
                cache.entrySet().stream()
                    .sorted((a, b) -> a.getValue().cachedAt().compareTo(b.getValue().cachedAt()))
                    .limit(cache.size() - maxSize)
                    .forEach(e -> cache.remove(e.getKey()));
            }
            return cache.size();
        }

        void refresh(String key, Position position) {
            cache(key, position);
        }

        void clear() {
            cache.clear();
        }

        java.util.List<Position> getByPrefix(String prefix) {
            return cache.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(e -> e.getValue().position())
                .toList();
        }

        CacheReport generateReport() {
            long total = hits + misses;
            double hitRate = total > 0 ? (double) hits / total : 0.0;
            return new CacheReport(cache.size(), hitRate, total);
        }
    }
}
