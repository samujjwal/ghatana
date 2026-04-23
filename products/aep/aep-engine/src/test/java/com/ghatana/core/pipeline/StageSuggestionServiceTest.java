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
    void setUp() { // GH-90000
        service = new DefaultStageSuggestionService(); // GH-90000
    }

    @Nested
    @DisplayName("suggestStages()")
    class SuggestStagesTests {

        @Test
        @DisplayName("returns empty list for null event types")
        void returnsEmptyListForNullEventTypes() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(null, Map.of()); // GH-90000
            assertThat(suggestions).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty list for empty event types")
        void returnsEmptyListForEmptyEventTypes() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages(List.of(), Map.of()); // GH-90000
            assertThat(suggestions).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("suggests transaction stages for transaction events")
        void suggestsTransactionStagesForTransactionEvents() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages( // GH-90000
                List.of("transaction.created", "transaction.updated"), Map.of() // GH-90000
            );

            assertThat(suggestions).isNotEmpty(); // GH-90000
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("filter"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("detect"));
            assertThat(suggestions).anyMatch(s -> s.description().toLowerCase().contains("fraud"));
        }

        @Test
        @DisplayName("suggests auth stages for login events")
        void suggestsAuthStagesForLoginEvents() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages( // GH-90000
                List.of("user.login", "auth.token"), Map.of() // GH-90000
            );

            assertThat(suggestions).isNotEmpty(); // GH-90000
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("validate"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("detect"));
            assertThat(suggestions).anyMatch(s -> s.description().toLowerCase().contains("auth"));
        }

        @Test
        @DisplayName("suggests order stages for order events")
        void suggestsOrderStagesForOrderEvents() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages( // GH-90000
                List.of("order.created", "cart.updated"), Map.of() // GH-90000
            );

            assertThat(suggestions).isNotEmpty(); // GH-90000
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("validate"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("transform"));
            assertThat(suggestions).anyMatch(s -> s.description().toLowerCase().contains("order"));
        }

        @Test
        @DisplayName("suggests sensor stages for iot events")
        void suggestsSensorStagesForIotEvents() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages( // GH-90000
                List.of("sensor.reading", "iot.data"), Map.of() // GH-90000
            );

            assertThat(suggestions).isNotEmpty(); // GH-90000
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("filter"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("aggregate"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("detect"));
            assertThat(suggestions).anyMatch(s -> s.description().toLowerCase().contains("sensor"));
        }

        @Test
        @DisplayName("always includes utility stages")
        void alwaysIncludesUtilityStages() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages( // GH-90000
                List.of("any.event"), Map.of()
            );

            assertThat(suggestions).anyMatch(s -> s.stageType().equals("log"));
            assertThat(suggestions).anyMatch(s -> s.stageType().equals("metrics"));
        }

        @Test
        @DisplayName("confidence scores are between 0 and 1")
        void confidenceScoresAreBetween0And1() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages( // GH-90000
                List.of("transaction.created"), Map.of()
            );

            for (StageSuggestionService.StageSuggestion suggestion : suggestions) { // GH-90000
                assertThat(suggestion.confidence()).isBetween(0.0, 1.0); // GH-90000
            }
        }

        @Test
        @DisplayName("suggestions include configuration")
        void suggestionsIncludeConfiguration() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages( // GH-90000
                List.of("transaction.created"), Map.of()
            );

            for (StageSuggestionService.StageSuggestion suggestion : suggestions) { // GH-90000
                assertThat(suggestion.suggestedConfig()).isNotNull(); // GH-90000
            }
        }

        @Test
        @DisplayName("suggestions include dependencies")
        void suggestionsIncludeDependencies() { // GH-90000
            List<StageSuggestionService.StageSuggestion> suggestions = service.suggestStages( // GH-90000
                List.of("transaction.created"), Map.of()
            );

            for (StageSuggestionService.StageSuggestion suggestion : suggestions) { // GH-90000
                assertThat(suggestion.dependencies()).isNotNull(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("getStageTemplates()")
    class GetStageTemplatesTests {

        @Test
        @DisplayName("returns templates for valid stage type")
        void returnsTemplatesForValidStageType() { // GH-90000
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("filter");
            assertThat(templates).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty list for unknown stage type")
        void returnsEmptyListForUnknownStageType() { // GH-90000
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("unknown");
            assertThat(templates).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("templates have required fields")
        void templatesHaveRequiredFields() { // GH-90000
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("filter");
            
            for (StageSuggestionService.StageTemplate template : templates) { // GH-90000
                assertThat(template.id()).isNotNull(); // GH-90000
                assertThat(template.name()).isNotNull(); // GH-90000
                assertThat(template.description()).isNotNull(); // GH-90000
                assertThat(template.stageType()).isNotNull(); // GH-90000
                assertThat(template.defaultConfig()).isNotNull(); // GH-90000
                assertThat(template.applicableEventTypes()).isNotNull(); // GH-90000
            }
        }

        @Test
        @DisplayName("filter templates include transaction and sensor filters")
        void filterTemplatesIncludeTransactionAndSensorFilters() { // GH-90000
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("filter");
            
            assertThat(templates).anyMatch(t -> t.id().equals("filter-transaction"));
            assertThat(templates).anyMatch(t -> t.id().equals("filter-sensor"));
        }

        @Test
        @DisplayName("detect templates include fraud and anomaly detection")
        void detectTemplatesIncludeFraudAndAnomalyDetection() { // GH-90000
            List<StageSuggestionService.StageTemplate> templates = service.getStageTemplates("detect");
            
            assertThat(templates).anyMatch(t -> t.id().equals("detect-fraud"));
            assertThat(templates).anyMatch(t -> t.id().equals("detect-anomaly"));
        }
    }
}
