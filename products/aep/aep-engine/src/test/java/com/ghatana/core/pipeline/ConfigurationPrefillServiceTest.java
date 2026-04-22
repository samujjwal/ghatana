/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("ConfigurationPrefillService [GH-90000]")
class ConfigurationPrefillServiceTest {

    private ConfigurationPrefillService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new DefaultConfigurationPrefillService(); // GH-90000
    }

    @Nested
    @DisplayName("suggestConfiguration() [GH-90000]")
    class SuggestConfigurationTests {

        @Test
        @DisplayName("suggests configuration for filter stage [GH-90000]")
        void suggestsConfigurationForFilterStage() { // GH-90000
            Map<String, Object> config = service.suggestConfiguration("filter", "transaction.created", Map.of()); // GH-90000

            assertThat(config).isNotEmpty(); // GH-90000
            assertThat(config).containsKey("removeNulls [GH-90000]");
            assertThat(config).containsKey("validateSchema [GH-90000]");
        }

        @Test
        @DisplayName("suggests configuration for detect stage [GH-90000]")
        void suggestsConfigurationForDetectStage() { // GH-90000
            Map<String, Object> config = service.suggestConfiguration("detect", "transaction.created", Map.of()); // GH-90000

            assertThat(config).isNotEmpty(); // GH-90000
            assertThat(config).containsKey("model [GH-90000]");
            assertThat(config).containsKey("threshold [GH-90000]");
        }

        @Test
        @DisplayName("customizes for transaction events [GH-90000]")
        void customizesForTransactionEvents() { // GH-90000
            Map<String, Object> config = service.suggestConfiguration("filter", "transaction.created", Map.of()); // GH-90000

            assertThat(config).containsKey("minAmount [GH-90000]");
            assertThat(config).containsKey("maxAmount [GH-90000]");
        }

        @Test
        @DisplayName("customizes for login events [GH-90000]")
        void customizesForLoginEvents() { // GH-90000
            Map<String, Object> config = service.suggestConfiguration("validate", "user.login", Map.of()); // GH-90000

            assertThat(config).containsKey("tokenType [GH-90000]");
            assertThat(config).containsKey("validateSignature [GH-90000]");
        }

        @Test
        @DisplayName("customizes for sensor events [GH-90000]")
        void customizesForSensorEvents() { // GH-90000
            Map<String, Object> config = service.suggestConfiguration("filter", "sensor.reading", Map.of()); // GH-90000

            assertThat(config).containsKey("method [GH-90000]");
            assertThat(config).containsKey("threshold [GH-90000]");
        }

        @Test
        @DisplayName("customizes based on industry context [GH-90000]")
        void customizesBasedOnIndustryContext() { // GH-90000
            Map<String, Object> context = Map.of("industry", "finance"); // GH-90000
            Map<String, Object> config = service.suggestConfiguration("detect", "transaction.created", context); // GH-90000

            assertThat(config.get("threshold [GH-90000]")).isEqualTo(0.9);
        }

        @Test
        @DisplayName("customizes for healthcare industry [GH-90000]")
        void customizesForHealthcareIndustry() { // GH-90000
            Map<String, Object> context = Map.of("industry", "healthcare"); // GH-90000
            Map<String, Object> config = service.suggestConfiguration("log", "patient.event", context); // GH-90000

            assertThat(config.get("includePayload [GH-90000]")).isEqualTo(false);
            assertThat(config.get("level [GH-90000]")).isEqualTo("warn [GH-90000]");
        }

        @Test
        @DisplayName("customizes for sensitivity context [GH-90000]")
        void customizesForSensitivityContext() { // GH-90000
            Map<String, Object> context = Map.of("sensitivity", "high"); // GH-90000
            Map<String, Object> config = service.suggestConfiguration("detect", "transaction.created", context); // GH-90000

            assertThat(config.get("threshold [GH-90000]")).isEqualTo(0.95);
        }

        @Test
        @DisplayName("returns empty config for unknown stage type [GH-90000]")
        void returnsEmptyConfigForUnknownStageType() { // GH-90000
            Map<String, Object> config = service.suggestConfiguration("unknown", "test.event", Map.of()); // GH-90000

            assertThat(config).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("calculates auto-configuration percentage [GH-90000]")
        void calculatesAutoConfigurationPercentage() { // GH-90000
            List<NaturalLanguagePipelineService.StageSpec> stages = List.of( // GH-90000
                new NaturalLanguagePipelineService.StageSpec( // GH-90000
                    "step-1", "filter", "Filter",
                    Map.of("removeNulls", true, "removeEmpty", true, "minAmount", 0.01), // GH-90000
                    List.of() // GH-90000
                )
            );

            double percentage = service.calculateAutoConfigurationPercentage(stages); // GH-90000
            assertThat(percentage).isGreaterThan(0.0); // GH-90000
        }

        @Test
        @DisplayName("returns 0 for empty stages [GH-90000]")
        void returns0ForEmptyStages() { // GH-90000
            double percentage = service.calculateAutoConfigurationPercentage(List.of()); // GH-90000
            assertThat(percentage).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("returns 0 for null stages [GH-90000]")
        void returns0ForNullStages() { // GH-90000
            double percentage = service.calculateAutoConfigurationPercentage(null); // GH-90000
            assertThat(percentage).isEqualTo(0.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("getConfidence() [GH-90000]")
    class GetConfidenceTests {

        @Test
        @DisplayName("returns confidence for default configs [GH-90000]")
        void returnsConfidenceForDefaultConfigs() { // GH-90000
            double confidence = service.getConfidence("filter", "transaction.created", "removeNulls"); // GH-90000

            assertThat(confidence).isEqualTo(0.7); // GH-90000
        }

        @Test
        @DisplayName("returns high confidence for transaction amount configs [GH-90000]")
        void returnsHighConfidenceForTransactionAmountConfigs() { // GH-90000
            double confidence = service.getConfidence("filter", "transaction.created", "minAmount"); // GH-90000

            assertThat(confidence).isEqualTo(0.9); // GH-90000
        }

        @Test
        @DisplayName("returns high confidence for sensor threshold configs [GH-90000]")
        void returnsHighConfidenceForSensorThresholdConfigs() { // GH-90000
            double confidence = service.getConfidence("detect", "sensor.reading", "threshold"); // GH-90000

            assertThat(confidence).isEqualTo(0.8); // GH-90000
        }

        @Test
        @DisplayName("returns low confidence for unknown configs [GH-90000]")
        void returnsLowConfidenceForUnknownConfigs() { // GH-90000
            double confidence = service.getConfidence("filter", "test.event", "unknownKey"); // GH-90000

            assertThat(confidence).isEqualTo(0.5); // GH-90000
        }

        @Test
        @DisplayName("confidence is between 0 and 1 [GH-90000]")
        void confidenceIsBetween0And1() { // GH-90000
            double confidence = service.getConfidence("filter", "test.event", "anyKey"); // GH-90000

            assertThat(confidence).isBetween(0.0, 1.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("ConfigSuggestion [GH-90000]")
    class ConfigSuggestionTests {

        @Test
        @DisplayName("config suggestion has required fields [GH-90000]")
        void configSuggestionHasRequiredFields() { // GH-90000
            ConfigurationPrefillService.ConfigSuggestion suggestion = new ConfigurationPrefillService.ConfigSuggestion( // GH-90000
                "key", "value", 0.8, "reason"
            );

            assertThat(suggestion.key()).isNotNull(); // GH-90000
            assertThat(suggestion.value()).isNotNull(); // GH-90000
            assertThat(suggestion.confidence()).isBetween(0.0, 1.0); // GH-90000
            assertThat(suggestion.reason()).isNotNull(); // GH-90000
        }
    }
}
