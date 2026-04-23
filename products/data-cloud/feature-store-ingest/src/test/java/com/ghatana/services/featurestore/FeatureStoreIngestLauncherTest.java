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
    void extractFeaturesUsesDeterministicOrderingAndSanitizesFeatureNames() { // GH-90000
        Instant timestamp = Instant.parse("2026-03-27T14:15:16Z");
        Map<String, Object> payload = new HashMap<>(); // GH-90000
        payload.put("z-score", 7); // GH-90000
        payload.put("amount", 12.5); // GH-90000
        payload.put("active?", true); // GH-90000

        List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures("entity-7", payload, timestamp); // GH-90000

        assertThat(features) // GH-90000
                .extracting(MLFeature::getName) // GH-90000
                .containsExactly("active_", "amount", "z_score", "hour_of_day", "day_of_week"); // GH-90000
        assertThat(features) // GH-90000
                .extracting(MLFeature::getValue) // GH-90000
                .containsExactly(1.0, 12.5, 7.0, 14.0, 5.0); // GH-90000
        assertThat(features) // GH-90000
                .extracting(MLFeature::getTimestamp) // GH-90000
                .containsOnly(timestamp); // GH-90000
    }

    @Test
    @DisplayName("extractFeatures reuses one fallback entity id for all derived features")
    void extractFeaturesReusesOneFallbackEntityIdForAllDerivedFeatures() { // GH-90000
        Instant timestamp = Instant.parse("2026-03-27T00:00:00Z");
        Map<String, Object> payload = Map.of("segment", "gold"); // GH-90000

        List<MLFeature> features = FeatureStoreIngestLauncher.extractFeatures(null, payload, timestamp); // GH-90000

        assertThat(features).hasSize(3); // GH-90000
        assertThat(features) // GH-90000
                .extracting(MLFeature::getEntityId) // GH-90000
                .doesNotContainNull() // GH-90000
                .containsOnly(features.get(0).getEntityId()); // GH-90000
        assertThat(features.get(0).getValue()).isEqualTo((double) ("gold".hashCode() & 0x7FFFFFFF)); // GH-90000
    }
}
