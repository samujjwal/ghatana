/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    void setUp() {
        service = new DefaultNaturalLanguagePipelineService();
    }

    @Nested
    @DisplayName("generatePipeline()")
    class GeneratePipelineTests {

        @Test
        @DisplayName("generates pipeline from fraud detection description")
        void generatesPipelineFromFraudDetectionDescription() {
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline(
                "Create fraud detection pipeline for transactions",
                Map.of()
            );

            assertThat(spec.name()).contains("Fraud");
            assertThat(spec.eventType()).isEqualTo("transaction.created");
            assertThat(spec.stages()).isNotEmpty();
            assertThat(spec.stages()).anyMatch(s -> s.type().equals("detect"));
        }

        @Test
        @DisplayName("generates pipeline from login security description")
        void generatesPipelineFromLoginSecurityDescription() {
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline(
                "Create authentication and security pipeline for login events",
                Map.of()
            );

            assertThat(spec.name()).contains("Authentication");
            assertThat(spec.eventType()).isEqualTo("user.login");
            assertThat(spec.stages()).isNotEmpty();
        }

        @Test
        @DisplayName("generates pipeline from sensor monitoring description")
        void generatesPipelineFromSensorMonitoringDescription() {
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline(
                "Create sensor monitoring pipeline with aggregation",
                Map.of()
            );

            assertThat(spec.name()).contains("Sensor");
            assertThat(spec.eventType()).isEqualTo("sensor.reading");
            assertThat(spec.stages()).isNotEmpty();
            assertThat(spec.stages()).anyMatch(s -> s.type().equals("aggregate"));
        }

        @Test
        @DisplayName("uses eventType from context if provided")
        void usesEventTypeFromContextIfProvided() {
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline(
                "Create detection pipeline",
                Map.of("eventType", "custom.event")
            );

            assertThat(spec.eventType()).isEqualTo("custom.event");
        }

        @Test
        @DisplayName("includes validation stage")
        void includesValidationStage() {
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline(
                "Create pipeline",
                Map.of()
            );

            assertThat(spec.stages()).anyMatch(s -> s.type().equals("validate"));
        }

        @Test
        @DisplayName("includes logging stage")
        void includesLoggingStage() {
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline(
                "Create pipeline",
                Map.of()
            );

            assertThat(spec.stages()).anyMatch(s -> s.type().equals("log"));
        }

        @Test
        @DisplayName("generates stage dependencies")
        void generatesStageDependencies() {
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline(
                "Create fraud detection pipeline",
                Map.of()
            );

            // Validate stage should be first (no dependencies)
            assertThat(spec.stages().get(0).dependencies()).isEmpty();
            
            // Later stages should have dependencies
            if (spec.stages().size() > 1) {
                assertThat(spec.stages().get(1).dependencies()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("handles empty description gracefully")
        void handlesEmptyDescriptionGracefully() {
            NaturalLanguagePipelineService.PipelineSpec spec = service.generatePipeline(
                "",
                Map.of()
            );

            assertThat(spec.name()).isEqualTo("Generated Pipeline");
            assertThat(spec.stages()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("validateDescription()")
    class ValidateDescriptionTests {

        @Test
        @DisplayName("validates correct description")
        void validatesCorrectDescription() {
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription(
                "Create fraud detection pipeline for transactions"
            );

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("rejects null description")
        void rejectsNullDescription() {
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription(null);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).contains("Description cannot be empty");
        }

        @Test
        @DisplayName("rejects empty description")
        void rejectsEmptyDescription() {
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription("");

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).contains("Description cannot be empty");
        }

        @Test
        @DisplayName("warns on short description")
        void warnsOnShortDescription() {
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription("test");

            assertThat(result.valid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("too short"));
        }

        @Test
        @DisplayName("warns on very long description")
        void warnsOnVeryLongDescription() {
            String longDesc = "a".repeat(600);
            NaturalLanguagePipelineService.ValidationResult result = service.validateDescription(longDesc);

            assertThat(result.valid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("very long"));
        }
    }

    @Nested
    @DisplayName("PipelineSpec")
    class PipelineSpecTests {

        @Test
        @DisplayName("spec has required fields")
        void specHasRequiredFields() {
            NaturalLanguagePipelineService.PipelineSpec spec = new NaturalLanguagePipelineService.PipelineSpec(
                "Test Pipeline",
                "Test Description",
                "test.event",
                List.of()
            );

            assertThat(spec.name()).isNotNull();
            assertThat(spec.description()).isNotNull();
            assertThat(spec.eventType()).isNotNull();
            assertThat(spec.stages()).isNotNull();
        }
    }

    @Nested
    @DisplayName("StageSpec")
    class StageSpecTests {

        @Test
        @DisplayName("stage spec has required fields")
        void stageSpecHasRequiredFields() {
            NaturalLanguagePipelineService.StageSpec spec = new NaturalLanguagePipelineService.StageSpec(
                "step-1",
                "validate",
                "Validation",
                Map.of("check", true),
                List.of()
            );

            assertThat(spec.id()).isNotNull();
            assertThat(spec.type()).isNotNull();
            assertThat(spec.name()).isNotNull();
            assertThat(spec.config()).isNotNull();
            assertThat(spec.dependencies()).isNotNull();
        }
    }
}
