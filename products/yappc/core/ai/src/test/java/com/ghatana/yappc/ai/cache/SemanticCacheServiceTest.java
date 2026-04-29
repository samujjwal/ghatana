package com.ghatana.yappc.ai.cache;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link SemanticCacheService}.
 *
 * @doc.type class
 * @doc.purpose Verifies TTL, threshold, hit-ratio, and core cache semantics
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("SemanticCacheService")
class SemanticCacheServiceTest extends EventloopTestBase {

    private static final int DIM = 1536;

    private SemanticCacheService service;

    @BeforeEach
    void setUp() {
        service = new SemanticCacheService(SemanticCacheService.CacheConfig.defaults());
    }

    private static double[] unitVector(double v) {
        double[] e = new double[DIM];
        e[0] = v;
        return e;
    }

    @Nested
    @DisplayName("hitRatio()")
    class HitRatioTests {

        @Test
        @DisplayName("returns 0.0 when no lookups have been performed")
        void hitRatio_noLookups_returnsZero() {
            assertThat(service.hitRatio()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0.0 after a miss")
        void hitRatio_afterMiss_returnsZero() {
            runPromise(() -> service.lookup(unitVector(1.0), "ada-002", "test"));
            assertThat(service.hitRatio()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 1.0 after a single hit")
        void hitRatio_afterOneHit_returnsOne() {
            double[] embedding = unitVector(1.0);
            runPromise(() -> service.store("q", embedding, "response", "ada-002", "u1", "t1", Map.of()));
            Optional<SemanticCacheService.CacheHit> hit =
                    runPromise(() -> service.lookup(embedding, "ada-002", "feat"));
            assertThat(hit).isPresent();
            assertThat(service.hitRatio()).isEqualTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("returns 0.5 after one hit and one miss")
        void hitRatio_mixedHitsAndMisses_returnsCorrectRatio() {
            double[] stored = unitVector(1.0);
            double[] dissimilar = new double[DIM];
            dissimilar[1] = 1.0;
            runPromise(() -> service.store("q", stored, "resp", "ada-002", "u1", "t1", Map.of()));
            runPromise(() -> service.lookup(stored, "ada-002", "f"));
            runPromise(() -> service.lookup(dissimilar, "ada-002", "f"));
            assertThat(service.hitRatio()).isEqualTo(0.5, within(1e-9));
        }
    }

    @Nested
    @DisplayName("Similarity threshold")
    class ThresholdTests {

        @Test
        @DisplayName("default threshold is 0.95")
        void defaultThreshold() {
            assertThat(SemanticCacheService.CacheConfig.defaults().similarityThreshold())
                    .isEqualTo(0.95, within(1e-9));
        }

        @Test
        @DisplayName("lookup misses when vectors are orthogonal")
        void lookup_orthogonalVectors_returnsMiss() {
            double[] stored = unitVector(1.0);
            double[] dissimilar = new double[DIM];
            dissimilar[1] = 1.0;
            runPromise(() -> service.store("q", stored, "r", "ada-002", "u", "t", Map.of()));
            Optional<SemanticCacheService.CacheHit> result =
                    runPromise(() -> service.lookup(dissimilar, "ada-002", null));
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("TTL configuration")
    class TtlTests {

        @Test
        @DisplayName("default TTL is 24 hours")
        void defaultTtl() {
            assertThat(SemanticCacheService.CacheConfig.defaults().ttl())
                    .isEqualTo(java.time.Duration.ofHours(24));
        }

        @Test
        @DisplayName("entry is returned before TTL expires")
        void entry_withinTtl_isReturned() {
            double[] embedding = unitVector(1.0);
            runPromise(() -> service.store("q", embedding, "resp", "ada-002", "u", "t", Map.of()));
            Optional<SemanticCacheService.CacheHit> hit =
                    runPromise(() -> service.lookup(embedding, "ada-002", null));
            assertThat(hit).isPresent();
        }

        @Test
        @DisplayName("entry is treated as expired when TTL is zero")
        void entry_zeroTtl_treatedAsExpired() throws InterruptedException {
            SemanticCacheService zeroTtlService = new SemanticCacheService(
                    new SemanticCacheService.CacheConfig(0.95, 100, java.time.Duration.ZERO, false, null));
            double[] embedding = unitVector(1.0);
            runPromise(() -> zeroTtlService.store("q", embedding, "resp", "ada-002", "u", "t", Map.of()));
            Thread.sleep(1);
            Optional<SemanticCacheService.CacheHit> hit =
                    runPromise(() -> zeroTtlService.lookup(embedding, "ada-002", null));
            assertThat(hit).isEmpty();
        }
    }

    @Nested
    @DisplayName("getStats()")
    class StatsTests {

        @Test
        @DisplayName("hitRate in stats matches hitRatio() after hits and misses")
        void stats_hitRateConsistentWithHitRatio() {
            double[] embedding = unitVector(1.0);
            double[] dissimilar = new double[DIM];
            dissimilar[1] = 1.0;
            runPromise(() -> service.store("q", embedding, "r", "ada-002", "u", "t", Map.of()));
            runPromise(() -> service.lookup(embedding, "ada-002", "f"));
            runPromise(() -> service.lookup(dissimilar, "ada-002", "f"));
            SemanticCacheService.CacheStats stats = service.getStats();
            assertThat(stats.hitRate()).isEqualTo(service.hitRatio(), within(1e-9));
            assertThat(stats.totalHits()).isEqualTo(1L);
            assertThat(stats.totalMisses()).isEqualTo(1L);
        }
    }
}
