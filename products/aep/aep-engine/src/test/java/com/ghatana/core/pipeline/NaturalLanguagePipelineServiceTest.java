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
@DisplayName("NaturalLanguagePipelineService")
class NaturalLanguagePipelineServiceTest {

    private NaturalLanguagePipelineService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new DefaultNaturalLanguagePipelineService(); // GH-90000
    }

    @Nested
    @DisplayName("generatePipeline()")
    class GeneratePipelineTests {

        @Test
        @DisplayName("generates pipeline from fraud detection description")
        void generatesPipelineFromFraudDetectionDescription() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create fraud detection pipeline for transactions",
                Map.of() // GH-90000
            );

            assertThat(spec.name()).contains("Fraud");
            assertThat(spec.eventType()).isEqualTo("transaction.created");
            assertThat(spec.stages()).isNotEmpty(); // GH-90000
            assertThat(spec.stages()).anyMatch(s -> s.type().equals("detect"));
        }

        @Test
        @DisplayName("generates pipeline from login security description")
        void generatesPipelineFromLoginSecurityDescription() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create authentication and security pipeline for login events",
                Map.of() // GH-90000
            );

            assertThat(spec.name()).contains("Authentication");
            assertThat(spec.eventType()).isEqualTo("user.login");
            assertThat(spec.stages()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("generates pipeline from sensor monitoring description")
        void generatesPipelineFromSensorMonitoringDescription() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create sensor monitoring pipeline with aggregation",
                Map.of() // GH-90000
            );

            assertThat(spec.name()).contains("Sensor");
            assertThat(spec.eventType()).isEqualTo("sensor.reading");
            assertThat(spec.stages()).isNotEmpty(); // GH-90000
            assertThat(spec.stages()).anyMatch(s -> s.type().equals("aggregate"));
        }

        @Test
        @DisplayName("uses eventType from context if provided")
        void usesEventTypeFromContextIfProvided() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create detection pipeline",
                Map.of("eventType", "custom.event") // GH-90000
            );

            assertThat(spec.eventType()).isEqualTo("custom.event");
        }

        @Test
        @DisplayName("includes validation stage")
        void includesValidationStage() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create pipeline",
                Map.of() // GH-90000
            );

            assertThat(spec.stages()).anyMatch(s -> s.type().equals("validate"));
        }

        @Test
        @DisplayName("includes logging stage")
        void includesLoggingStage() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "Create pipeline",
                Map.of() // GH-90000
            );

            assertThat(spec.stages()).anyMatch(s -> s.type().equals("log"));
        }

        @Test
        @DisplayName("generates stage dependencies")
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
        @DisplayName("handles empty description gracefully")
        void handlesEmptyDescriptionGracefully() { // GH-90000
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline( // GH-90000
                "",
                Map.of() // GH-90000
            );

            assertThat(spec.name()).isEqualTo("Generated Pipeline");
            assertThat(spec.stages()).isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("validateDescription()")
    class ValidateDescriptionTests {

        @Test
        @DisplayName("validates correct description")
        void validatesCorrectDescription() { // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription( // GH-90000
                "Create fraud detection pipeline for transactions"
            );

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.errors()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("rejects null description")
        void rejectsNullDescription() { // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription(null); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).contains("Description cannot be empty");
        }

        @Test
        @DisplayName("rejects empty description")
        void rejectsEmptyDescription() { // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription("");

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).contains("Description cannot be empty");
        }

        @Test
        @DisplayName("warns on short description")
        void warnsOnShortDescription() { // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription("test");

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.warnings()).anyMatch(w -> w.contains("too short"));
        }

        @Test
        @DisplayName("warns on very long description")
        void warnsOnVeryLongDescription() { // GH-90000
            String longDesc = "a".repeat(600); // GH-90000
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription(longDesc); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.warnings()).anyMatch(w -> w.contains("very long"));
        }
    }

    @Nested
    @DisplayName("PipelineSpec")
    class PipelineSpecTests {

        @Test
        @DisplayName("spec has required fields")
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
    @DisplayName("StageSpec")
    class StageSpecTests {

        @Test
        @DisplayName("stage spec has required fields")
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
