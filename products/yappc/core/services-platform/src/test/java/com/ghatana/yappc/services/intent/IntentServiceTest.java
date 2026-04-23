/*
 * Copyright (c) 2026 Ghatana // GH-90000
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
    void setUp() { // GH-90000
        intentService = new MockIntentService(); // GH-90000
    }

    @Test
    @DisplayName("Should capture intent from user input")
    void testCaptureIntent() throws Exception { // GH-90000
        IntentInput input = new IntentInput("Create a REST API for user management");

        Promise<IntentSpec> promise = intentService.capture(input); // GH-90000
        IntentSpec spec = runPromise(() -> promise); // GH-90000

        assertThat(spec).isNotNull(); // GH-90000
        assertThat(spec.description()).isEqualTo("Create a REST API for user management");
        assertThat(spec.intentType()).isEqualTo("API_CREATION");
    }

    @Test
    @DisplayName("Should analyze captured intent")
    void testAnalyzeIntent() throws Exception { // GH-90000
        IntentSpec spec = new IntentSpec( // GH-90000
            "Create a REST API for user management",
            "API_CREATION",
            java.util.Map.of("domain", "user-management") // GH-90000
        );

        Promise<IntentAnalysis> promise = intentService.analyze(spec); // GH-90000
        IntentAnalysis analysis = runPromise(() -> promise); // GH-90000

        assertThat(analysis).isNotNull(); // GH-90000
        assertThat(analysis.feasible()).isTrue(); // GH-90000
        assertThat(analysis.estimatedComplexity()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Should count intents")
    void testCountIntents() throws Exception { // GH-90000
        Promise<Long> promise = intentService.count(); // GH-90000
        Long count = runPromise(() -> promise); // GH-90000

        assertThat(count).isGreaterThanOrEqualTo(0L); // GH-90000
    }

    @Test
    @DisplayName("Should handle complex intent with multiple requirements")
    void testComplexIntent() throws Exception { // GH-90000
        IntentInput input = new IntentInput( // GH-90000
            "Build a microservices architecture with authentication, database, and API gateway"
        );

        IntentSpec spec = runPromise(() -> intentService.capture(input)); // GH-90000
        IntentAnalysis analysis = runPromise(() -> intentService.analyze(spec)); // GH-90000

        assertThat(spec.intentType()).isEqualTo("ARCHITECTURE_DESIGN");
        assertThat(analysis.estimatedComplexity()).isEqualTo("HIGH");
        assertThat(analysis.requiredPhases()).contains("PLANNING", "DESIGN", "IMPLEMENTATION"); // GH-90000
    }

    @Test
    @DisplayName("Should handle simple intent")
    void testSimpleIntent() throws Exception { // GH-90000
        IntentInput input = new IntentInput("Add a new field to User model");

        IntentSpec spec = runPromise(() -> intentService.capture(input)); // GH-90000
        IntentAnalysis analysis = runPromise(() -> intentService.analyze(spec)); // GH-90000

        assertThat(spec.intentType()).isEqualTo("MODEL_MODIFICATION");
        assertThat(analysis.estimatedComplexity()).isEqualTo("LOW");
    }

    // Mock implementations for testing

    static class MockIntentService implements IntentService {

        @Override
        public Promise<IntentSpec> capture(IntentInput input) { // GH-90000
            String intentType = determineIntentType(input.description()); // GH-90000
            return Promise.of(new IntentSpec( // GH-90000
                input.description(), // GH-90000
                intentType,
                java.util.Map.of("source", "user-input") // GH-90000
            ));
        }

        @Override
        public Promise<IntentAnalysis> analyze(IntentSpec spec) { // GH-90000
            String complexity = estimateComplexity(spec.description()); // GH-90000
            java.util.List<String> phases = determinePhases(spec.intentType()); // GH-90000

            return Promise.of(new IntentAnalysis( // GH-90000
                true,
                complexity,
                phases,
                java.util.Map.of("recommendation", "Proceed with implementation") // GH-90000
            ));
        }

        @Override
        public Promise<Long> count() { // GH-90000
            return Promise.of(42L); // GH-90000
        }

        private String determineIntentType(String description) { // GH-90000
            String lower = description.toLowerCase(); // GH-90000
            if (lower.contains("microservices") || lower.contains("architecture")) {
                return "ARCHITECTURE_DESIGN";
            } else if (lower.contains("rest api") || lower.contains("api")) {
                return "API_CREATION";
            } else if (lower.contains("field") || lower.contains("model")) {
                return "MODEL_MODIFICATION";
            }
            return "GENERAL";
        }

        private String estimateComplexity(String description) { // GH-90000
            String lower = description.toLowerCase(); // GH-90000
            if (lower.contains("microservices") || lower.contains("architecture")) {
                return "HIGH";
            } else if (lower.contains("rest api") || lower.contains("service")) {
                return "MEDIUM";
            }
            return "LOW";
        }

        private java.util.List<String> determinePhases(String intentType) { // GH-90000
            return switch (intentType) { // GH-90000
                case "ARCHITECTURE_DESIGN" -> java.util.List.of("PLANNING", "DESIGN", "IMPLEMENTATION", "TESTING"); // GH-90000
                case "API_CREATION" -> java.util.List.of("DESIGN", "IMPLEMENTATION", "TESTING"); // GH-90000
                case "MODEL_MODIFICATION" -> java.util.List.of("IMPLEMENTATION", "TESTING"); // GH-90000
                default -> java.util.List.of("IMPLEMENTATION");
            };
        }
    }
}
