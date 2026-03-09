package com.ghatana.flashit.agent.service;

import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NLPService.
 */
@DisplayName("NLPService Tests")
class NLPServiceTest {

    private NLPService service;

    @BeforeEach
    void setUp() {
        service = new NLPService(new TestAgentConfig(""));
    }

    @Test
    @DisplayName("extractEntities() returns empty list on API failure")
    void extractEntitiesReturnsEmptyOnFailure() {
        // GIVEN
        NLPRequest request = new NLPRequest(
                "moment-1", "I visited Paris with John yesterday",
                "user-123", List.of("entities"));

        // WHEN
        NLPResponse response = service.extractEntities(request);

        // THEN
        assertThat(response).isNotNull();
        assertThat(response.entities()).isNotNull();
        assertThat(response.momentId()).isEqualTo("moment-1");
    }

    @Test
    @DisplayName("analyzeSentiment() returns neutral fallback on API failure")
    void analyzeSentimentReturnsNeutralFallback() {
        // GIVEN
        NLPRequest request = new NLPRequest(
                "moment-1", "The weather is fine today",
                "user-123", List.of("sentiment"));

        // WHEN
        NLPResponse response = service.analyzeSentiment(request);

        // THEN
        assertThat(response).isNotNull();
        assertThat(response.sentiment()).isNotNull();
        assertThat(response.sentiment().label()).isEqualTo("neutral");
    }

    @Test
    @DisplayName("detectMood() returns neutral fallback on API failure")
    void detectMoodReturnsNeutralFallback() {
        // GIVEN
        NLPRequest request = new NLPRequest(
                "moment-1", "Just another ordinary day",
                "user-123", List.of("mood"));

        // WHEN
        NLPResponse response = service.detectMood(request);

        // THEN
        assertThat(response).isNotNull();
        assertThat(response.mood()).isNotNull();
        assertThat(response.mood().primaryMood()).isEqualTo("neutral");
    }

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
            return false;
        }
    }
}
