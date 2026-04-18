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
 * Tests for StageSuggestionService.
 *
 * @doc.type class
 * @doc.purpose Test stage suggestion service
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("StageSuggestionService")
class StageSuggestionServiceTest {

    private StageSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new DefaultStageSuggestionService();
    }

    @Nested
    @DisplayName("suggestStages()")
    class SuggestStagesTests {

        @Test
        @DisplayName("returns empty list for null event types")
        void returnsEmptyListForNullEventTypes() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(null, Map.of());
            assertThat(suggestions).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for empty event types")
        void returnsEmptyListForEmptyEventTypes() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(List.of(), Map.of());
            assertThat(suggestions).isEmpty();
        }

        @Test
        @DisplayName("suggests transaction stages for transaction events")
        void suggestsTransactionStagesForTransactionEvents() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(
                List.of("transaction.created", "transaction.updated"), Map.of()
            );

            assertThat(suggestions).isNotEmpty();
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("filter"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("detect"));
            assertThat(suggestions).anyMatch(s -> s.description().toLowerCase().contains("fraud"));
        }

        @Test
        @DisplayName("suggests auth stages for login events")
        void suggestsAuthStagesForLoginEvents() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(
                List.of("user.login", "auth.token"), Map.of()
            );

            assertThat(suggestions).isNotEmpty();
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("validate"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("detect"));
            assertThat(suggestions).anyMatch(s -> s.description().toLowerCase().contains("auth"));
        }

        @Test
        @DisplayName("suggests order stages for order events")
        void suggestsOrderStagesForOrderEvents() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(
                List.of("order.created", "cart.updated"), Map.of()
            );

            assertThat(suggestions).isNotEmpty();
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("validate"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("transform"));
            assertThat(suggestions).anyMatch(s -> s.description().toLowerCase().contains("order"));
        }

        @Test
        @DisplayName("suggests sensor stages for iot events")
        void suggestsSensorStagesForIotEvents() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(
                List.of("sensor.reading", "iot.data"), Map.of()
            );

            assertThat(suggestions).isNotEmpty();
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("filter"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("aggregate"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("detect"));
            assertThat(suggestions).anyMatch(s -> s.description().toLowerCase().contains("sensor"));
        }

        @Test
        @DisplayName("always includes utility stages")
        void alwaysIncludesUtilityStages() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(
                List.of("any.event"), Map.of()
            );

            assertThat(suggestions).anyMatch(s -> s.stageType().equals("log"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("metrics"));
        }

        @Test
        @DisplayName("confidence scores are between 0 and 1")
        void confidenceScoresAreBetween0And1() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(
                List.of("transaction.created"), Map.of()
            );

            for (StageSuggestionService.StageSuggestion suggestion : suggestions) {
                assertThat(suggestion.confidence()).isBetween(0.0, 1.0);
            }
        }

        @Test
        @DisplayName("suggestions include configuration")
        void suggestionsIncludeConfiguration() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(
                List.of("transaction.created"), Map.of()
            );

            for (StageSuggestionService.StageSuggestion suggestion : suggestions) {
                assertThat(suggestion.suggestedConfig()).isNotNull();
            }
        }

        @Test
        @DisplayName("suggestions include dependencies")
        void suggestionsIncludeDependencies() {
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(
                List.of("transaction.created"), Map.of()
            );

            for (StageSuggestionService.StageSuggestion suggestion : suggestions) {
                assertThat(suggestion.dependencies()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("getStageTemplates()")
    class GetStageTemplatesTests {

        @Test
        @DisplayName("returns templates for valid stage type")
        void returnsTemplatesForValidStageType() {
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("filter");
            assertThat(templates).isNotEmpty();
        }

        @Test
        @DisplayName("returns empty list for unknown stage type")
        void returnsEmptyListForUnknownStageType() {
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("unknown");
            assertThat(templates).isEmpty();
        }

        @Test
        @DisplayName("templates have required fields")
        void templatesHaveRequiredFields() {
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("filter");
            
            for (StageSuggestionService.StageTemplate template : templates) {
                assertThat(template.id()).isNotNull();
                assertThat(template.name()).isNotNull();
                assertThat(template.description()).isNotNull();
                assertThat(template.stageType()).isNotNull();
                assertThat(template.defaultConfig()).isNotNull();
                assertThat(template.applicableEventTypes()).isNotNull();
            }
        }

        @Test
        @DisplayName("filter templates include transaction and sensor filters")
        void filterTemplatesIncludeTransactionAndSensorFilters() {
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("filter");
            
            assertThat(templates).anyMatch(t -> t.id().equals("filter-transaction"));
            assertThat(templates).anyMatch(t -> t.id().equals("filter-sensor"));
        }

        @Test
        @DisplayName("detect templates include fraud and anomaly detection")
        void detectTemplatesIncludeFraudAndAnomalyDetection() {
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("detect");
            
            assertThat(templates).anyMatch(t -> t.id().equals("detect-fraud"));
            assertThat(templates).anyMatch(t -> t.id().equals("detect-anomaly"));
        }
    }
}
