package com.ghatana.services.featurestore;

import com.ghatana.aiplatform.featurestore.MLFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeatureStoreIngestLauncher")
class FeatureStoreIngestLauncherTest {

    @Test
    @DisplayName("extractFeatures uses deterministic ordering and sanitizes feature names")
    void extractFeaturesUsesDeterministicOrderingAndSanitizesFeatureNames() {
        Instant timestamp = Instant.parse("2026-03-27T14:15:16Z");
        Map<String, Object> payload = new HashMap<>();
        payload.put("z-score", 7);
        payload.put("amount", 12.5);
        payload.put("active?", true);

        List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("entity-7", payload, timestamp);

        assertThat(features)
                .extracting(MLFeature::getName)
                .containsExactly("active_", "amount", "z_score", "hour_of_day", "day_of_week");
        assertThat(features)
                .extracting(MLFeature::getValue)
                .containsExactly(1.0, 12.5, 7.0, 14.0, 5.0);
        assertThat(features)
                .extracting(MLFeature::getTimestamp)
                .containsOnly(timestamp);
    }

    @Test
    @DisplayName("extractFeatures reuses one fallback entity id for all derived features")
    void extractFeaturesReusesOneFallbackEntityIdForAllDerivedFeatures() {
        Instant timestamp = Instant.parse("2026-03-27T00:00:00Z");
        Map<String, Object> payload = Map.of("segment", "gold");

        List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures(null, payload, timestamp);

        assertThat(features).hasSize(3);
        assertThat(features)
                .extracting(MLFeature::getEntityId)
                .doesNotContainNull()
                .containsOnly(features.get(0).getEntityId());
        assertThat(features.get(0).getValue()).isEqualTo((double) ("gold".hashCode() & 0x7FFFFFFF));
    }
}