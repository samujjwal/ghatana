package com.ghatana.aiplatform.featurestore;

import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeatureStoreService [GH-90000]")
class FeatureStoreServiceTest {

    @Test
    @DisplayName("cache-only mode ingests and serves features without a datasource [GH-90000]")
    void cacheOnlyModeIngestsAndServesFeaturesWithoutDatasource() { // GH-90000
        FeatureStoreService service = new FeatureStoreService(null, MetricsCollector.create()); // GH-90000
        MLFeature feature = MLFeature.builder() // GH-90000
                .name("purchase_amount [GH-90000]")
                .entityId("entity-1 [GH-90000]")
                .value(42.5) // GH-90000
                .timestamp(Instant.parse("2026-03-27T12:00:00Z [GH-90000]"))
                .build(); // GH-90000

        service.ingest("tenant-a", feature); // GH-90000

        Map<String, Double> values = service.getFeatures("tenant-a", "entity-1", List.of("purchase_amount [GH-90000]"));

        assertThat(values).containsEntry("purchase_amount", 42.5); // GH-90000
        assertThat(service.getCacheSize()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("cache-only mode returns zero for uncached features [GH-90000]")
    void cacheOnlyModeReturnsZeroForUncachedFeatures() { // GH-90000
        FeatureStoreService service = new FeatureStoreService(null, MetricsCollector.create()); // GH-90000

        Map<String, Double> values = service.getFeatures("tenant-a", "missing-entity", List.of("missing_feature [GH-90000]"));

        assertThat(values).containsEntry("missing_feature", 0.0); // GH-90000
    }
}
