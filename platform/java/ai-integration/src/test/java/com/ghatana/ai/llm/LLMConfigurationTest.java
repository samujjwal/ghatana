package com.ghatana.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link LLMConfiguration}.
 *
 * Covers builder defaults, customization, null validation, and required fields.
 */
@DisplayName("LLMConfiguration")
class LLMConfigurationTest {

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("modelName defaults to gpt-3.5-turbo")
        void defaultModelName() {
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey("sk-test")
                    .build();
            assertThat(config.getModelName()).isEqualTo("gpt-3.5-turbo");
        }

        @Test
        @DisplayName("maxTokens defaults to 2048")
        void defaultMaxTokens() {
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey("sk-test")
                    .build();
            assertThat(config.getMaxTokens()).isEqualTo(2048);
        }

        @Test
        @DisplayName("temperature defaults to 0.7")
        void defaultTemperature() {
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey("sk-test")
                    .build();
            assertThat(config.getTemperature()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("timeoutSeconds defaults to 30")
        void defaultTimeoutSeconds() {
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey("sk-test")
                    .build();
            assertThat(config.getTimeoutSeconds()).isEqualTo(30);
        }

        @Test
        @DisplayName("maxRetries defaults to 3")
        void defaultMaxRetries() {
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey("sk-test")
                    .build();
            assertThat(config.getMaxRetries()).isEqualTo(3);
        }

        @Test
        @DisplayName("baseUrl and organization default to null")
        void nullableDefaults() {
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey("sk-test")
                    .build();
            assertThat(config.getBaseUrl()).isNull();
            assertThat(config.getOrganization()).isNull();
        }
    }

    @Nested
    @DisplayName("builder customization")
    class BuilderCustomization {

        @Test
        @DisplayName("all fields set correctly")
        void allFieldsSet() {
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey("sk-custom")
                    .baseUrl("https://api.custom.com")
                    .modelName("gpt-4")
                    .organization("org-123")
                    .maxTokens(4096)
                    .temperature(0.3)
                    .timeoutSeconds(60)
                    .maxRetries(5)
                    .build();

            assertThat(config.getApiKey()).isEqualTo("sk-custom");
            assertThat(config.getBaseUrl()).isEqualTo("https://api.custom.com");
            assertThat(config.getModelName()).isEqualTo("gpt-4");
            assertThat(config.getOrganization()).isEqualTo("org-123");
            assertThat(config.getMaxTokens()).isEqualTo(4096);
            assertThat(config.getTemperature()).isEqualTo(0.3);
            assertThat(config.getTimeoutSeconds()).isEqualTo(60);
            assertThat(config.getMaxRetries()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null apiKey throws NullPointerException")
        void nullApiKey() {
            assertThatThrownBy(() -> LLMConfiguration.builder()
                    .modelName("gpt-4")
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("apiKey");
        }

        @Test
        @DisplayName("null modelName throws NullPointerException")
        void nullModelName() {
            assertThatThrownBy(() -> LLMConfiguration.builder()
                    .apiKey("sk-test")
                    .modelName(null)
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("modelName");
        }
    }
}
