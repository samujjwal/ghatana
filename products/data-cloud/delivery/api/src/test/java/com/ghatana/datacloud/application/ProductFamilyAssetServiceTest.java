package com.ghatana.datacloud.application;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ProductFamilyAssetService.
 *
 * <p>Tests schema validation, promotion gate enforcement, commit SHA binding,
 * environment validation, and repository interaction for asset management.
 *
 * @doc.type class
 * @doc.purpose Tests for ProductFamilyAssetService hardening
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ProductFamilyAssetService Tests")
class ProductFamilyAssetServiceTest extends EventloopTestBase {

    private FakeProductFamilyAssetRepository repository;
    private FakeMetricsCollector metrics;
    private ProductFamilyAssetService service;

    @BeforeEach
    void setUp() {
        repository = new FakeProductFamilyAssetRepository();
        metrics = new FakeMetricsCollector();
        service = new ProductFamilyAssetService(repository, metrics);
    }

    @Test
    @DisplayName("registers asset with valid schema")
    void shouldRegisterAsset() {
        ProductFamilyAssetService.ProductFamilyAsset asset = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("phr-consent-ui-component")
            .assetName("PHR Consent UI Component")
            .assetType("ui-component")
            .sourceProductId("phr")
            .description("Reusable consent management UI component")
            .maturityLevel("stable")
            .tenantId("tenant-123")
            .build();

        ProductFamilyAssetService.ProductFamilyAsset result = runPromise(() -> service.registerAsset(asset));

        assertThat(result.assetId()).isEqualTo("phr-consent-ui-component");
        assertThat(repository.lastUpserted).isNotNull();
        assertThat(metrics.counters).containsKey("product_family_asset.registered");
    }

    @Test
    @DisplayName("rejects invalid asset type")
    void shouldRejectInvalidAssetType() {
        ProductFamilyAssetService.ProductFamilyAsset asset = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("test-asset")
            .assetName("Test Asset")
            .assetType("invalid-type")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .tenantId("tenant-123")
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.registerAsset(asset)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid asset type");
    }

    @Test
    @DisplayName("rejects invalid maturity level")
    void shouldRejectInvalidMaturityLevel() {
        ProductFamilyAssetService.ProductFamilyAsset asset = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("test-asset")
            .assetName("Test Asset")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("invalid-level")
            .tenantId("tenant-123")
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.registerAsset(asset)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid maturity level");
    }

    @Test
    @DisplayName("rejects invalid promotion status")
    void shouldRejectInvalidPromotionStatus() {
        ProductFamilyAssetService.ProductFamilyAsset asset = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("test-asset")
            .assetName("Test Asset")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .promotionStatus("invalid-status")
            .tenantId("tenant-123")
            .build();

        assertThatThrownBy(() -> runPromise(() -> service.registerAsset(asset)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid promotion status");
    }

    @Test
    @DisplayName("promotes asset with valid commit SHA and environment")
    void shouldPromoteAsset() {
        String assetId = "phr-consent-ui-component";
        String commitSha = "7f84bc08e9e4e6d7e209cb49a855f199f7c90347";
        String targetEnvironment = "production";

        repository.storedAsset = Optional.of(ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId(assetId)
            .assetName("PHR Consent UI Component")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .promotionStatus("approved")
            .tenantId("tenant-123")
            .build());

        repository.storedPolicy = Optional.of(new ProductFamilyAssetService.AssetPromotionPolicy(
            "policy-1", "policy-1", "Test Policy", "manual",
            "phr", List.of("data-cloud", "yappc"), List.of("ui-component"),
            "stable", null, Map.of(), true, "tenant-123", Instant.now(), Instant.now()
        ));

        ProductFamilyAssetService.AssetPromotionResult result = runPromise(() -> 
            service.promoteAsset(assetId, List.of("data-cloud"), "user-1", commitSha, targetEnvironment, "tenant-123"));

        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(repository.recordedHistory).hasSize(1);
        assertThat(repository.recordedHistory.get(0).commitSha()).isEqualTo(commitSha);
        assertThat(repository.recordedHistory.get(0).targetEnvironment()).isEqualTo(targetEnvironment);
    }

    @Test
    @DisplayName("rejects promotion with missing commit SHA")
    void shouldRejectPromotionWithoutCommitSha() {
        assertThatThrownBy(() -> runPromise(() -> 
            service.promoteAsset("asset-1", List.of("data-cloud"), "user-1", null, "production", "tenant-123")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Commit SHA must be present");
    }

    @Test
    @DisplayName("rejects promotion with invalid commit SHA format")
    void shouldRejectPromotionWithInvalidCommitSha() {
        assertThatThrownBy(() -> runPromise(() -> 
            service.promoteAsset("asset-1", List.of("data-cloud"), "user-1", "invalid-sha", "production", "tenant-123")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid commit SHA format");
    }

    @Test
    @DisplayName("rejects promotion with invalid target environment")
    void shouldRejectPromotionWithInvalidEnvironment() {
        assertThatThrownBy(() -> runPromise(() -> 
            service.promoteAsset("asset-1", List.of("data-cloud"), "user-1", 
                "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "invalid-env", "tenant-123")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid target environment");
    }

    @Test
    @DisplayName("fails promotion when asset not found")
    void shouldFailPromotionWhenAssetNotFound() {
        repository.storedAsset = Optional.empty();
        repository.storedPolicy = Optional.of(new ProductFamilyAssetService.AssetPromotionPolicy(
            "policy-1", "policy-1", "Test Policy", "manual",
            "phr", List.of("data-cloud"), List.of("ui-component"),
            "stable", null, Map.of(), true, "tenant-123", Instant.now(), Instant.now()
        ));

        ProductFamilyAssetService.AssetPromotionResult result = runPromise(() -> 
            service.promoteAsset("asset-1", List.of("data-cloud"), "user-1", 
                "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production", "tenant-123"));

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.message()).contains("Asset not found");
    }

    @Test
    @DisplayName("fails promotion when maturity level requirement not met")
    void shouldFailPromotionWhenMaturityLevelNotMet() {
        repository.storedAsset = Optional.of(ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("asset-1")
            .assetName("Test Asset")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("beta")
            .promotionStatus("approved")
            .tenantId("tenant-123")
            .build());

        repository.storedPolicy = Optional.of(new ProductFamilyAssetService.AssetPromotionPolicy(
            "policy-1", "policy-1", "Test Policy", "manual",
            "phr", List.of("data-cloud"), List.of("ui-component"),
            "stable", null, Map.of(), true, "tenant-123", Instant.now(), Instant.now()
        ));

        ProductFamilyAssetService.AssetPromotionResult result = runPromise(() -> 
            service.promoteAsset("asset-1", List.of("data-cloud"), "user-1", 
                "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production", "tenant-123"));

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.message()).contains("Maturity level");
    }

    @Test
    @DisplayName("fails promotion when target product not in allowed list")
    void shouldFailPromotionWhenTargetProductNotAllowed() {
        repository.storedAsset = Optional.of(ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("asset-1")
            .assetName("Test Asset")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .promotionStatus("approved")
            .tenantId("tenant-123")
            .build());

        repository.storedPolicy = Optional.of(new ProductFamilyAssetService.AssetPromotionPolicy(
            "policy-1", "policy-1", "Test Policy", "manual",
            "phr", List.of("data-cloud"), List.of("ui-component"),
            "stable", null, Map.of(), true, "tenant-123", Instant.now(), Instant.now()
        ));

        ProductFamilyAssetService.AssetPromotionResult result = runPromise(() -> 
            service.promoteAsset("asset-1", List.of("unallowed-product"), "user-1", 
                "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production", "tenant-123"));

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.message()).contains("not in allowed list");
    }

    @Test
    @DisplayName("fails promotion when asset type not in allowed list")
    void shouldFailPromotionWhenAssetTypeNotAllowed() {
        repository.storedAsset = Optional.of(ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("asset-1")
            .assetName("Test Asset")
            .assetType("service")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .promotionStatus("approved")
            .tenantId("tenant-123")
            .build());

        repository.storedPolicy = Optional.of(new ProductFamilyAssetService.AssetPromotionPolicy(
            "policy-1", "policy-1", "Test Policy", "manual",
            "phr", List.of("data-cloud"), List.of("ui-component"),
            "stable", null, Map.of(), true, "tenant-123", Instant.now(), Instant.now()
        ));

        ProductFamilyAssetService.AssetPromotionResult result = runPromise(() -> 
            service.promoteAsset("asset-1", List.of("data-cloud"), "user-1", 
                "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production", "tenant-123"));

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.message()).contains("Asset type");
    }

    @Test
    @DisplayName("retrieves asset by ID")
    void shouldRetrieveAsset() {
        ProductFamilyAssetService.ProductFamilyAsset asset = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("asset-1")
            .assetName("Test Asset")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .tenantId("tenant-123")
            .build();

        repository.storedAsset = Optional.of(asset);

        Optional<ProductFamilyAssetService.ProductFamilyAsset> result = runPromise(() -> service.getAsset("asset-1", "tenant-123"));

        assertThat(result).isPresent();
        assertThat(result.get().assetId()).isEqualTo("asset-1");
    }

    @Test
    @DisplayName("lists assets by source product")
    void shouldListAssetsBySource() {
        ProductFamilyAssetService.ProductFamilyAsset asset1 = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("asset-1")
            .assetName("Test Asset 1")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .tenantId("tenant-123")
            .build();

        ProductFamilyAssetService.ProductFamilyAsset asset2 = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("asset-2")
            .assetName("Test Asset 2")
            .assetType("service")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .tenantId("tenant-123")
            .build();

        repository.storedList = List.of(asset1, asset2);

        List<ProductFamilyAssetService.ProductFamilyAsset> result = runPromise(() -> service.listAssetsBySource("phr", "tenant-123"));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("lists promotable assets")
    void shouldListPromotableAssets() {
        ProductFamilyAssetService.ProductFamilyAsset asset = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("asset-1")
            .assetName("Test Asset")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .promotionStatus("approved")
            .tenantId("tenant-123")
            .build();

        repository.storedList = List.of(asset);

        List<ProductFamilyAssetService.ProductFamilyAsset> result = runPromise(() -> service.listPromotableAssets("tenant-123"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).promotionStatus()).isEqualTo("approved");
    }

    @Test
    @DisplayName("emits metrics on successful registration")
    void shouldEmitMetricsOnRegistration() {
        ProductFamilyAssetService.ProductFamilyAsset asset = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("test-asset")
            .assetName("Test Asset")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .tenantId("tenant-123")
            .build();

        runPromise(() -> service.registerAsset(asset));

        assertThat(metrics.counters).containsKey("product_family_asset.registered");
    }

    @Test
    @DisplayName("emits failure metrics on registration error")
    void shouldEmitFailureMetricsOnRegistrationError() {
        ProductFamilyAssetService.ProductFamilyAsset asset = ProductFamilyAssetService.ProductFamilyAsset.builder()
            .assetId("test-asset")
            .assetName("Test Asset")
            .assetType("ui-component")
            .sourceProductId("phr")
            .maturityLevel("stable")
            .tenantId("tenant-123")
            .build();

        repository.shouldFail = true;

        assertThatThrownBy(() -> runPromise(() -> service.registerAsset(asset)))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Repository error");

        assertThat(metrics.counters).containsKey("product_family_asset.register_failed");
    }

    // Fake implementations

    private static class FakeProductFamilyAssetRepository 
            implements ProductFamilyAssetService.ProductFamilyAssetRepository {
        
        ProductFamilyAssetService.ProductFamilyAsset lastUpserted;
        Optional<ProductFamilyAssetService.ProductFamilyAsset> storedAsset = Optional.empty();
        List<ProductFamilyAssetService.ProductFamilyAsset> storedList = List.of();
        Optional<ProductFamilyAssetService.AssetPromotionPolicy> storedPolicy = Optional.empty();
        List<ProductFamilyAssetService.AssetPromotionHistory> recordedHistory = new ArrayList<>();
        boolean shouldFail = false;

        @Override
        public Promise<ProductFamilyAssetService.ProductFamilyAsset> upsert(ProductFamilyAssetService.ProductFamilyAsset asset) {
            if (shouldFail) {
                return Promise.ofException(new RuntimeException("Repository error"));
            }
            lastUpserted = asset;
            return Promise.of(asset);
        }

        @Override
        public Promise<Optional<ProductFamilyAssetService.ProductFamilyAsset>> findById(String assetId, String tenantId) {
            return Promise.of(storedAsset);
        }

        @Override
        public Promise<List<ProductFamilyAssetService.ProductFamilyAsset>> findBySourceProduct(String sourceProductId, String tenantId) {
            return Promise.of(storedList);
        }

        @Override
        public Promise<List<ProductFamilyAssetService.ProductFamilyAsset>> findByPromotionStatus(String status, String tenantId) {
            return Promise.of(storedList);
        }

        @Override
        public Promise<Optional<ProductFamilyAssetService.AssetPromotionPolicy>> findActivePolicyForAsset(
                String assetId, String tenantId) {
            return Promise.of(storedPolicy);
        }

        @Override
        public Promise<Void> recordPromotionHistory(ProductFamilyAssetService.AssetPromotionHistory history) {
            recordedHistory.add(history);
            return Promise.of(null);
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
