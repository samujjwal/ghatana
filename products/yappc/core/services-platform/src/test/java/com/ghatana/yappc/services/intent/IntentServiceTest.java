/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.services.intent;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IntentService implementations.
 */
class IntentServiceTest extends EventloopTestBase {

    private IntentService intentService;

    @BeforeEach
    void setUp() {
        intentService = new MockIntentService();
    }

    @Test
    @DisplayName("Should capture intent from user input")
    void testCaptureIntent() throws Exception {
        IntentInput input = new IntentInput("Create a REST API for user management");

        Promise<IntentSpec> promise = intentService.capture(input);
        IntentSpec spec = runPromise(() -> promise);

        assertThat(spec).isNotNull();
        assertThat(spec.description()).isEqualTo("Create a REST API for user management");
        assertThat(spec.intentType()).isEqualTo("API_CREATION");
    }

    @Test
    @DisplayName("Should analyze captured intent")
    void testAnalyzeIntent() throws Exception {
        IntentSpec spec = new IntentSpec(
            "Create a REST API for user management",
            "API_CREATION",
            java.util.Map.of("domain", "user-management")
        );

        Promise<IntentAnalysis> promise = intentService.analyze(spec);
        IntentAnalysis analysis = runPromise(() -> promise);

        assertThat(analysis).isNotNull();
        assertThat(analysis.feasible()).isTrue();
        assertThat(analysis.estimatedComplexity()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Should count intents")
    void testCountIntents() throws Exception {
        Promise<Long> promise = intentService.count();
        Long count = runPromise(() -> promise);

        assertThat(count).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Should handle complex intent with multiple requirements")
    void testComplexIntent() throws Exception {
        IntentInput input = new IntentInput(
            "Build a microservices architecture with authentication, database, and API gateway"
        );

        IntentSpec spec = runPromise(() -> intentService.capture(input));
        IntentAnalysis analysis = runPromise(() -> intentService.analyze(spec));

        assertThat(spec.intentType()).isEqualTo("ARCHITECTURE_DESIGN");
        assertThat(analysis.estimatedComplexity()).isEqualTo("HIGH");
        assertThat(analysis.requiredPhases()).contains("PLANNING", "DESIGN", "IMPLEMENTATION");
    }

    @Test
    @DisplayName("Should handle simple intent")
    void testSimpleIntent() throws Exception {
        IntentInput input = new IntentInput("Add a new field to User model");

        IntentSpec spec = runPromise(() -> intentService.capture(input));
        IntentAnalysis analysis = runPromise(() -> intentService.analyze(spec));

        assertThat(spec.intentType()).isEqualTo("MODEL_MODIFICATION");
        assertThat(analysis.estimatedComplexity()).isEqualTo("LOW");
    }

    // Mock implementations for testing

    static class MockIntentService implements IntentService {

        @Override
        public Promise<IntentSpec> capture(IntentInput input) {
            String intentType = determineIntentType(input.description());
            return Promise.of(new IntentSpec(
                input.description(),
                intentType,
                java.util.Map.of("source", "user-input")
            ));
        }

        @Override
        public Promise<IntentAnalysis> analyze(IntentSpec spec) {
            String complexity = estimateComplexity(spec.description());
            java.util.List<String> phases = determinePhases(spec.intentType());

            return Promise.of(new IntentAnalysis(
                true,
                complexity,
                phases,
                java.util.Map.of("recommendation", "Proceed with implementation")
            ));
        }

        @Override
        public Promise<Long> count() {
            return Promise.of(42L);
        }

        private String determineIntentType(String description) {
            String lower = description.toLowerCase();
            if (lower.contains("microservices") || lower.contains("architecture")) {
                return "ARCHITECTURE_DESIGN";
            } else if (lower.contains("rest api") || lower.contains("api")) {
                return "API_CREATION";
            } else if (lower.contains("field") || lower.contains("model")) {
                return "MODEL_MODIFICATION";
            }
            return "GENERAL";
        }

        private String estimateComplexity(String description) {
            String lower = description.toLowerCase();
            if (lower.contains("microservices") || lower.contains("architecture")) {
                return "HIGH";
            } else if (lower.contains("rest api") || lower.contains("service")) {
                return "MEDIUM";
            }
            return "LOW";
        }

        private java.util.List<String> determinePhases(String intentType) {
            return switch (intentType) {
                case "ARCHITECTURE_DESIGN" -> java.util.List.of("PLANNING", "DESIGN", "IMPLEMENTATION", "TESTING");
                case "API_CREATION" -> java.util.List.of("DESIGN", "IMPLEMENTATION", "TESTING");
                case "MODEL_MODIFICATION" -> java.util.List.of("IMPLEMENTATION", "TESTING");
                default -> java.util.List.of("IMPLEMENTATION");
            };
        }
    }
}
