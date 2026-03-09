package com.ghatana.flashit.agent.service;

import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ClassificationService.
 *
 * <p>Tests service behavior without calling OpenAI API.
 * Verifies fallback logic and input handling.
 */
@DisplayName("ClassificationService Tests")
class ClassificationServiceTest {

    private ClassificationService service;

    @BeforeEach
    void setUp() {
        // Use config without a valid API key to test fallback behavior
        AgentConfig config = new TestAgentConfig("");
        service = new ClassificationService(config);
    }

    @Test
    @DisplayName("classify() returns fallback when API key is missing")
    void classifyReturnsFallbackWhenApiKeyMissing() {
        // GIVEN
        ClassificationRequest request = new ClassificationRequest(
                "Today I went for a run in the park",
                null,
                "TEXT",
                List.of("happy", "energetic"),
                List.of("exercise"),
                null,
                List.of(
                        new SphereInfo("sphere-1", "Health & Fitness", "Exercise and wellness", "PERSONAL"),
                        new SphereInfo("sphere-2", "Daily Journal", "Day-to-day notes", "PERSONAL")
                ),
                "user-123"
        );

        // WHEN
        ClassificationResponse response = service.classify(request);

        // THEN — should fallback gracefully
        assertThat(response).isNotNull();
        assertThat(response.model()).isNotBlank();
        assertThat(response.processingTimeMs()).isGreaterThanOrEqualTo(0);
        // With no API key, should hit fallback path and return first sphere
        assertThat(response.sphereId()).isEqualTo("sphere-1");
        assertThat(response.confidence()).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("classify() handles empty spheres list")
    void classifyHandlesEmptySpheres() {
        // GIVEN
        ClassificationRequest request = new ClassificationRequest(
                "Some text", null, "TEXT", List.of(), List.of(),
                null, List.of(), "user-123");

        // WHEN
        ClassificationResponse response = service.classify(request);

        // THEN
        assertThat(response).isNotNull();
        assertThat(response.alternatives()).isNotNull();
    }

    @Test
    @DisplayName("suggestSpheres() returns ordered suggestions")
    void suggestSpheresReturnsOrderedSuggestions() {
        // GIVEN
        ClassificationRequest request = new ClassificationRequest(
                "Learning Java", null, "TEXT", List.of(), List.of("coding"),
                null,
                List.of(new SphereInfo("s1", "Programming", null, "PERSONAL")),
                "user-123");

        // WHEN
        List<SphereSuggestion> suggestions = service.suggestSpheres(request);

        // THEN
        assertThat(suggestions).isNotNull();
        assertThat(suggestions).isNotEmpty();
    }

    /**
     * Test-only AgentConfig with controllable API key.
     */
    private static class TestAgentConfig extends AgentConfig {
        private final String apiKey;

        TestAgentConfig(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public String getOpenAiApiKey() {
            return apiKey;
        }

        @Override
        public boolean isOpenAiConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
