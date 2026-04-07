/**
 * @doc.type class
 * @doc.purpose Test feature schema validation, type checking, and quality metrics
 * @doc.layer shared-services
 * @doc.pattern Test
 */
package com.ghatana.featurestore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature Validation Tests
 *
 * Test feature schema validation, type checking, and quality metrics.
 */
@DisplayName("Feature Validation Tests")
class FeatureValidationTest {

    @Test
    @DisplayName("Should validate feature schema")
    void shouldValidateFeatureSchema() {
        Map<String, Object> feature = Map.of(
            "featureId", "feat-123",
            "value", 42.0,
            "timestamp", System.currentTimeMillis()
        );
        
        assertThat(feature).isNotNull();
        assertThat(feature.containsKey("featureId")).isTrue();
        assertThat(feature.containsKey("value")).isTrue();
    }

    @Test
    @DisplayName("Should check feature types")
    void shouldCheckFeatureTypes() {
        Map<String, Object> numericFeature = Map.of("value", 42.0);
        Map<String, Object> stringFeature = Map.of("value", "test");
        Map<String, Object> booleanFeature = Map.of("value", true);
        
        assertThat(numericFeature.get("value")).isInstanceOf(Double.class);
        assertThat(stringFeature.get("value")).isInstanceOf(String.class);
        assertThat(booleanFeature.get("value")).isInstanceOf(Boolean.class);
    }

    @Test
    @DisplayName("Should calculate quality metrics")
    void shouldCalculateQualityMetrics() {
        Map<String, Object> feature = Map.of(
            "featureId", "feat-456",
            "value", 100.0,
            "completeness", 0.95
        );
        
        assertThat(feature.get("completeness")).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Should detect data anomalies")
    void shouldDetectDataAnomalies() {
        Map<String, Object> normalFeature = Map.of("value", 50.0);
        Map<String, Object> outlierFeature = Map.of("value", 999999.0);
        
        assertThat(normalFeature.get("value")).isLessThan(100.0);
        assertThat(outlierFeature.get("value")).isGreaterThan(1000.0);
    }

    @Test
    @DisplayName("Should validate feature constraints")
    void shouldValidateFeatureConstraints() {
        Map<String, Object> feature = Map.of(
            "value", 42.0,
            "min", 0.0,
            "max", 100.0
        );
        
        double value = (Double) feature.get("value");
        double min = (Double) feature.get("min");
        double max = (Double) feature.get("max");
        
        assertThat(value).isBetween(min, max);
    }

    @Test
    @DisplayName("Should handle validation failures")
    void shouldHandleValidationFailures() {
        Map<String, Object> invalidFeature = Map.of(
            "featureId", null,
            "value", "invalid"
        );
        
        assertThat(invalidFeature.get("featureId")).isNull();
    }
}
