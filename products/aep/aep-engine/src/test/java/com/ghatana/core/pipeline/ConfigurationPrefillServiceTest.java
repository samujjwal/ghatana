/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ConfigurationPrefillService.
 *
 * @doc.type class
 * @doc.purpose Test configuration prefill service
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ConfigurationPrefillService")
class ConfigurationPrefillServiceTest {

    private ConfigurationPrefillService service;

    @BeforeEach
    void setUp() {
        service = new DefaultConfigurationPrefillService();
    }

    @Nested
    @DisplayName("suggestConfiguration()")
    class SuggestConfigurationTests {

        @Test
        @DisplayName("suggests configuration for filter stage")
        void suggestsConfigurationForFilterStage() {
            Map<String, Object> config = service.suggestConfiguration("filter", "transaction.created", Map.of());

            assertThat(config).isNotEmpty();
            assertThat(config).containsKey("removeNulls");
            assertThat(config).containsKey("validateSchema");
        }

        @Test
        @DisplayName("suggests configuration for detect stage")
        void suggestsConfigurationForDetectStage() {
            Map<String, Object> config = service.suggestConfiguration("detect", "transaction.created", Map.of());

            assertThat(config).isNotEmpty();
            assertThat(config).containsKey("model");
            assertThat(config).containsKey("threshold");
        }

        @Test
        @DisplayName("customizes for transaction events")
        void customizesForTransactionEvents() {
            Map<String, Object> config = service.suggestConfiguration("filter", "transaction.created", Map.of());

            assertThat(config).containsKey("minAmount");
            assertThat(config).containsKey("maxAmount");
        }

        @Test
        @DisplayName("customizes for login events")
        void customizesForLoginEvents() {
            Map<String, Object> config = service.suggestConfiguration("validate", "user.login", Map.of());

            assertThat(config).containsKey("tokenType");
            assertThat(config).containsKey("validateSignature");
        }

        @Test
        @DisplayName("customizes for sensor events")
        void customizesForSensorEvents() {
            Map<String, Object> config = service.suggestConfiguration("filter", "sensor.reading", Map.of());

            assertThat(config).containsKey("method");
            assertThat(config).containsKey("threshold");
        }

        @Test
        @DisplayName("customizes based on industry context")
        void customizesBasedOnIndustryContext() {
            Map<String, Object> context = Map.of("industry", "finance");
            Map<String, Object> config = service.suggestConfiguration("detect", "transaction.created", context);

            assertThat(config.get("threshold")).isEqualTo(0.9);
        }

        @Test
        @DisplayName("customizes for healthcare industry")
        void customizesForHealthcareIndustry() {
            Map<String, Object> context = Map.of("industry", "healthcare");
            Map<String, Object> config = service.suggestConfiguration("log", "patient.event", context);

            assertThat(config.get("includePayload")).isEqualTo(false);
            assertThat(config.get("level")).isEqualTo("warn");
        }

        @Test
        @DisplayName("customizes for sensitivity context")
        void customizesForSensitivityContext() {
            Map<String, Object> context = Map.of("sensitivity", "high");
            Map<String, Object> config = service.suggestConfiguration("detect", "transaction.created", context);

            assertThat(config.get("threshold")).isEqualTo(0.95);
        }

        @Test
        @DisplayName("returns empty config for unknown stage type")
        void returnsEmptyConfigForUnknownStageType() {
            Map<String, Object> config = service.suggestConfiguration("unknown", "test.event", Map.of());

            assertThat(config).isEmpty();
        }

        @Test
        @DisplayName("calculates auto-configuration percentage")
        void calculatesAutoConfigurationPercentage() {
            List<NaturalLanguagePipelineService.StageSpec> stages = List.of(
                new NaturalLanguagePipelineService.StageSpec(
                    "step-1", "filter", "Filter",
                    Map.of("removeNulls", true, "removeEmpty", true, "minAmount", 0.01),
                    List.of()
                )
            );

            double percentage = service.calculateAutoConfigurationPercentage(stages);
            assertThat(percentage).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("returns 0 for empty stages")
        void returns0ForEmptyStages() {
            double percentage = service.calculateAutoConfigurationPercentage(List.of());
            assertThat(percentage).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0 for null stages")
        void returns0ForNullStages() {
            double percentage = service.calculateAutoConfigurationPercentage(null);
            assertThat(percentage).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("getConfidence()")
    class GetConfidenceTests {

        @Test
        @DisplayName("returns confidence for default configs")
        void returnsConfidenceForDefaultConfigs() {
            double confidence = service.getConfidence("filter", "transaction.created", "removeNulls");

            assertThat(confidence).isEqualTo(0.7);
        }

        @Test
        @DisplayName("returns high confidence for transaction amount configs")
        void returnsHighConfidenceForTransactionAmountConfigs() {
            double confidence = service.getConfidence("filter", "transaction.created", "minAmount");

            assertThat(confidence).isEqualTo(0.9);
        }

        @Test
        @DisplayName("returns high confidence for sensor threshold configs")
        void returnsHighConfidenceForSensorThresholdConfigs() {
            double confidence = service.getConfidence("detect", "sensor.reading", "threshold");

            assertThat(confidence).isEqualTo(0.8);
        }

        @Test
        @DisplayName("returns low confidence for unknown configs")
        void returnsLowConfidenceForUnknownConfigs() {
            double confidence = service.getConfidence("filter", "test.event", "unknownKey");

            assertThat(confidence).isEqualTo(0.5);
        }

        @Test
        @DisplayName("confidence is between 0 and 1")
        void confidenceIsBetween0And1() {
            double confidence = service.getConfidence("filter", "test.event", "anyKey");

            assertThat(confidence).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("ConfigSuggestion")
    class ConfigSuggestionTests {

        @Test
        @DisplayName("config suggestion has required fields")
        void configSuggestionHasRequiredFields() {
            ConfigurationPrefillService.ConfigSuggestion suggestion = new ConfigurationPrefillService.ConfigSuggestion(
                "key", "value", 0.8, "reason"
            );

            assertThat(suggestion.key()).isNotNull();
            assertThat(suggestion.value()).isNotNull();
            assertThat(suggestion.confidence()).isBetween(0.0, 1.0);
            assertThat(suggestion.reason()).isNotNull();
        }
    }
}
