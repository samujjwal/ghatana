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
 * Tests for NaturalLanguagePipelineService.
 *
 * @doc.type class
 * @doc.purpose Test natural language pipeline generation
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("NaturalLanguagePipelineService [GH-90000]")
class NaturalLanguagePipelineServiceTest {

    private NaturalLanguagePipelineService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new DefaultNaturalLanguagePipelineService(); // GH-90000
    }

    @Nested
    @DisplayName("generatePipeline() [GH-90000]")
    class GeneratePipelineTests {

        @Test
        @DisplayName("generates pipeline from fraud detection description [GH-90000]")
        void generatesPipelineFromFraudDetectionDescription() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create fraud detection pipeline for transactions",
                Map.of() // GH-90000
            );

            assertThat(spec.name()).contains("Fraud [GH-90000]");
            assertThat(spec.eventType()).isEqualTo("transaction.created [GH-90000]");
            assertThat(spec.stages()).isNotEmpty(); // GH-90000
            assertThat(spec.stages()).anyMatch(s -> s.type().equals("detect [GH-90000]"));
        }

        @Test
        @DisplayName("generates pipeline from login security description [GH-90000]")
        void generatesPipelineFromLoginSecurityDescription() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create authentication and security pipeline for login events",
                Map.of() // GH-90000
            );

            assertThat(spec.name()).contains("Authentication [GH-90000]");
            assertThat(spec.eventType()).isEqualTo("user.login [GH-90000]");
            assertThat(spec.stages()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("generates pipeline from sensor monitoring description [GH-90000]")
        void generatesPipelineFromSensorMonitoringDescription() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create sensor monitoring pipeline with aggregation",
                Map.of() // GH-90000
            );

            assertThat(spec.name()).contains("Sensor [GH-90000]");
            assertThat(spec.eventType()).isEqualTo("sensor.reading [GH-90000]");
            assertThat(spec.stages()).isNotEmpty(); // GH-90000
            assertThat(spec.stages()).anyMatch(s -> s.type().equals("aggregate [GH-90000]"));
        }

        @Test
        @DisplayName("uses eventType from context if provided [GH-90000]")
        void usesEventTypeFromContextIfProvided() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create detection pipeline",
                Map.of("eventType", "custom.event") // GH-90000
            );

            assertThat(spec.eventType()).isEqualTo("custom.event [GH-90000]");
        }

        @Test
        @DisplayName("includes validation stage [GH-90000]")
        void includesValidationStage() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create pipeline",
                Map.of() // GH-90000
            );

            assertThat(spec.stages()).anyMatch(s -> s.type().equals("validate [GH-90000]"));
        }

        @Test
        @DisplayName("includes logging stage [GH-90000]")
        void includesLoggingStage() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create pipeline",
                Map.of() // GH-90000
            );

            assertThat(spec.stages()).anyMatch(s -> s.type().equals("log [GH-90000]"));
        }

        @Test
        @DisplayName("generates stage dependencies [GH-90000]")
        void generatesStageDependencies() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create fraud detection pipeline",
                Map.of() // GH-90000
            );

            // Validate stage should be first (no dependencies) // GH-90000
            assertThat(spec.stages().get(0).dependencies()).isEmpty(); // GH-90000
            
            // Later stages should have dependencies
            if (spec.stages().size() > 1) { // GH-90000
                assertThat(spec.stages().get(1).dependencies()).isNotEmpty(); // GH-90000
            }
        }

        @Test
        @DisplayName("handles empty description gracefully [GH-90000]")
        void handlesEmptyDescriptionGracefully() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "",
                Map.of() // GH-90000
            );

            assertThat(spec.name()).isEqualTo("Generated Pipeline [GH-90000]");
            assertThat(spec.stages()).isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("validateDescription() [GH-90000]")
    class ValidateDescriptionTests {

        @Test
        @DisplayName("validates correct description [GH-90000]")
        void validatesCorrectDescription() { // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription( // GH-90000
                "Create fraud detection pipeline for transactions"
            );

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.errors()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("rejects null description [GH-90000]")
        void rejectsNullDescription() { // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription(null); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).contains("Description cannot be empty [GH-90000]");
        }

        @Test
        @DisplayName("rejects empty description [GH-90000]")
        void rejectsEmptyDescription() { // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription(" [GH-90000]");

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).contains("Description cannot be empty [GH-90000]");
        }

        @Test
        @DisplayName("warns on short description [GH-90000]")
        void warnsOnShortDescription() { // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription("test [GH-90000]");

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.warnings()).anyMatch(w -> w.contains("too short [GH-90000]"));
        }

        @Test
        @DisplayName("warns on very long description [GH-90000]")
        void warnsOnVeryLongDescription() { // GH-90000
            String longDesc = "a".repeat(600); // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription(longDesc); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.warnings()).anyMatch(w -> w.contains("very long [GH-90000]"));
        }
    }

    @Nested
    @DisplayName("PipelineSpec [GH-90000]")
    class PipelineSpecTests {

        @Test
        @DisplayName("spec has required fields [GH-90000]")
        void specHasRequiredFields() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = new NaturalLanguagePipelineService.PipelineSpec( // GH-90000
                "Test Pipeline",
                "Test Description",
                "test.event",
                List.of() // GH-90000
            );

            assertThat(spec.name()).isNotNull(); // GH-90000
            assertThat(spec.description()).isNotNull(); // GH-90000
            assertThat(spec.eventType()).isNotNull(); // GH-90000
            assertThat(spec.stages()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("StageSpec [GH-90000]")
    class StageSpecTests {

        @Test
        @DisplayName("stage spec has required fields [GH-90000]")
        void stageSpecHasRequiredFields() { // GH-90000
            NaturalLanguagePipelineService.StageSpec spec = new NaturalLanguagePipelineService.StageSpec( // GH-90000
                "step-1",
                "validate",
                "Validation",
                Map.of("check", true), // GH-90000
                List.of() // GH-90000
            );

            assertThat(spec.id()).isNotNull(); // GH-90000
            assertThat(spec.type()).isNotNull(); // GH-90000
            assertThat(spec.name()).isNotNull(); // GH-90000
            assertThat(spec.config()).isNotNull(); // GH-90000
            assertThat(spec.dependencies()).isNotNull(); // GH-90000
        }
    }
}
