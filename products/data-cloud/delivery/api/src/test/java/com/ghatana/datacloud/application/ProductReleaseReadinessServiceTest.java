package com.ghatana.datacloud.application;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ProductReleaseReadinessService.
 *
 * <p>Tests validation logic, evidence freshness, commit SHA binding, environment binding,
 * and repository interaction for release readiness evidence production.
 *
 * @doc.type class
 * @doc.purpose Tests for ProductReleaseReadinessService hardening
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ProductReleaseReadinessService Tests")
class ProductReleaseReadinessServiceTest extends EventloopTestBase {

    private FakeProductReleaseReadinessRepository repository;
    private FakeMetricsCollector metrics;
    private ProductReleaseReadinessService service;

    @BeforeEach
    void setUp() {
        repository = new FakeProductReleaseReadinessRepository();
        metrics = new FakeMetricsCollector();
        service = new ProductReleaseReadinessService(repository, metrics);
    }

    @Test
    @DisplayName("produces release readiness with valid data")
    void shouldProduceReleaseReadiness() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .averageScore(0.95)
            .releaseTargetScore(0.90)
            .evidence(Map.of("build", Map.of("status", "passed")))
            .blockingGaps(List.of())
            .belowTargetDimensions(List.of())
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("production")
            .generatedAt(Instant.now())
            .build();

        ProductReleaseReadinessService.ProductReleaseReadiness result = runPromise(() -> service.produceReleaseReadiness(readiness));

        assertThat(result.productId()).isEqualTo("phr");
        assertThat(result.releaseVerdict()).isEqualTo("pass");
        assertThat(repository.lastUpserted).isNotNull();
        assertThat(metrics.counters).containsKey("product_release_readiness.produced");
    }

    @Test
    @DisplayName("rejects stale evidence older than 24 hours")
    void shouldRejectStaleEvidence() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("production")
            .generatedAt(Instant.now().minus(Duration.ofHours(25)))
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.produceReleaseReadiness(readiness)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Evidence is stale");
    }

    @Test
    @DisplayName("rejects missing commit SHA")
    void shouldRejectMissingCommitSha() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha(null)
            .evidenceEnvironment("production")
            .generatedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.produceReleaseReadiness(readiness)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Commit SHA must be present");
    }

    @Test
    @DisplayName("rejects invalid commit SHA format")
    void shouldRejectInvalidCommitShaFormat() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha("invalid-sha")
            .evidenceEnvironment("production")
            .generatedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.produceReleaseReadiness(readiness)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid commit SHA format");
    }

    @Test
    @DisplayName("rejects evidence environment mismatch")
    void shouldRejectEvidenceEnvironmentMismatch() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("staging")
            .generatedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.produceReleaseReadiness(readiness)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match release target");
    }

    @Test
    @DisplayName("rejects invalid release target")
    void shouldRejectInvalidReleaseTarget() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("invalid")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("invalid")
            .generatedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.produceReleaseReadiness(readiness)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid release target");
    }

    @Test
    @DisplayName("rejects invalid release verdict")
    void shouldRejectInvalidReleaseVerdict() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("invalid")
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("production")
            .generatedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.produceReleaseReadiness(readiness)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid release verdict");
    }

    @Test
    @DisplayName("rejects score outside valid range")
    void shouldRejectInvalidScoreRange() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .averageScore(1.5)
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("production")
            .generatedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.produceReleaseReadiness(readiness)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Average score must be between 0 and 1");
    }

    @Test
    @DisplayName("retrieves release readiness by product version and target")
    void shouldRetrieveReleaseReadiness() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("production")
            .generatedAt(Instant.now())
            .build();

        repository.storedReadiness = Optional.of(readiness);

        Optional<ProductReleaseReadinessService.ProductReleaseReadiness> result = runPromise(() -> 
            service.getReleaseReadiness("phr", "1.0.0", "production", "tenant-123"));

        assertThat(result).isPresent();
        assertThat(result.get().productId()).isEqualTo("phr");
    }

    @Test
    @DisplayName("lists all release readiness for product")
    void shouldListReleaseReadiness() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness1 = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("production")
            .generatedAt(Instant.now())
            .build();

        ProductReleaseReadinessService.ProductReleaseReadiness readiness2 = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("staging")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("staging")
            .generatedAt(Instant.now())
            .build();

        repository.storedList = List.of(readiness1, readiness2);

        List<ProductReleaseReadinessService.ProductReleaseReadiness> result = runPromise(() -> service.listReleaseReadiness("phr", "tenant-123"));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("emits metrics on successful production")
    void shouldEmitMetricsOnSuccess() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("production")
            .generatedAt(Instant.now())
            .build();

        runPromise(() -> service.produceReleaseReadiness(readiness));

        assertThat(metrics.counters).containsKey("product_release_readiness.produced");
        assertThat(metrics.counters.get("product_release_readiness.produced")).isGreaterThan(0);
    }

    @Test
    @DisplayName("emits failure metrics on production error")
    void shouldEmitFailureMetricsOnError() {
        ProductReleaseReadinessService.ProductReleaseReadiness readiness = ProductReleaseReadinessService.ProductReleaseReadiness.builder()
            .productId("phr")
            .productVersion("1.0.0")
            .releaseTarget("production")
            .releaseVerdict("pass")
            .tenantId("tenant-123")
            .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
            .evidenceEnvironment("production")
            .generatedAt(Instant.now())
            .build();

        repository.shouldFail = true;

        assertThatThrownBy(() -> runPromise(() -> service.produceReleaseReadiness(readiness)))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Repository error");

        assertThat(metrics.counters).containsKey("product_release_readiness.produce_failed");
    }

    // Fake implementations

    private static class FakeProductReleaseReadinessRepository 
            implements ProductReleaseReadinessService.ProductReleaseReadinessRepository {
        
        ProductReleaseReadinessService.ProductReleaseReadiness lastUpserted;
        Optional<ProductReleaseReadinessService.ProductReleaseReadiness> storedReadiness = Optional.empty();
        List<ProductReleaseReadinessService.ProductReleaseReadiness> storedList = List.of();
        boolean shouldFail = false;

        @Override
        public Promise<ProductReleaseReadinessService.ProductReleaseReadiness> upsert(ProductReleaseReadinessService.ProductReleaseReadiness readiness) {
            if (shouldFail) {
                return Promise.ofException(new RuntimeException("Repository error"));
            }
            lastUpserted = readiness;
            return Promise.of(readiness);
        }

        @Override
        public Promise<Optional<ProductReleaseReadinessService.ProductReleaseReadiness>> findByProductVersionAndTarget(
                String productId, String productVersion, String releaseTarget, String tenantId) {
            return Promise.of(storedReadiness);
        }

        @Override
        public Promise<List<ProductReleaseReadinessService.ProductReleaseReadiness>> findByProductId(String productId, String tenantId) {
            return Promise.of(storedList);
        }

        @Override
        public Promise<List<ProductReleaseReadinessService.ProductReleaseReadiness>> findByTenant(String tenantId) {
            return Promise.of(storedList);
        }

        @Override
        public Promise<Void> deleteById(String id, String tenantId) {
            storedList = storedList.stream().filter(item -> !id.equals(item.id())).toList();
            return Promise.complete();
        }
    }

    private static class FakeMetricsCollector implements MetricsCollector {
        final Map<String, Integer> counters = new ConcurrentHashMap<>();

        @Override
        public void increment(String metricName, double amount, Map<String, String> tags) {
            counters.merge(metricName, (int) amount, Integer::sum);
        }

        @Override
        public void recordError(String metricName, Exception e, Map<String, String> tags) {
            counters.merge(metricName, 1, Integer::sum);
        }

        @Override
        public void incrementCounter(String metricName, String... keyValues) {
            counters.merge(metricName, 1, Integer::sum);
        }

        @Override
        public io.micrometer.core.instrument.MeterRegistry getMeterRegistry() {
            return null;
        }
    }
}
