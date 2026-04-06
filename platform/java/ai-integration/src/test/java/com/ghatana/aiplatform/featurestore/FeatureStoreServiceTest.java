package com.ghatana.aiplatform.featurestore;

import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeatureStoreService")
class FeatureStoreServiceTest {

    @Test
    @DisplayName("cache-only mode ingests and serves features without a datasource")
    void cacheOnlyModeIngestsAndServesFeaturesWithoutDatasource() {
        FeatureStoreService service = new FeatureStoreService(null, MetricsCollector.create());
        MLFeature feature = MLFeature.builder()
                .name("purchase_amount")
                .entityId("entity-1")
                .value(42.5)
                .timestamp(Instant.parse("2026-03-27T12:00:00Z"))
                .build();

        service.ingest("tenant-a", feature);

        Map<String, Double> values = service.getFeatures("tenant-a", "entity-1", List.of("purchase_amount"));

        assertThat(values).containsEntry("purchase_amount", 42.5);
        assertThat(service.getCacheSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("cache-only mode returns zero for uncached features")
    void cacheOnlyModeReturnsZeroForUncachedFeatures() {
        FeatureStoreService service = new FeatureStoreService(null, MetricsCollector.create());

        Map<String, Double> values = service.getFeatures("tenant-a", "missing-entity", List.of("missing_feature"));

        assertThat(values).containsEntry("missing_feature", 0.0);
    }
}