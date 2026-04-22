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
@DisplayName("LLMConfiguration [GH-90000]")
class LLMConfigurationTest {

    @Nested
    @DisplayName("builder defaults [GH-90000]")
    class BuilderDefaults {

        @Test
        @DisplayName("modelName defaults to gpt-3.5-turbo [GH-90000]")
        void defaultModelName() { // GH-90000
            LLMConfiguration config = LLMConfiguration.builder() // GH-90000
                    .apiKey("sk-test [GH-90000]")
                    .build(); // GH-90000
            assertThat(config.getModelName()).isEqualTo("gpt-3.5-turbo [GH-90000]");
        }

        @Test
        @DisplayName("maxTokens defaults to 2048 [GH-90000]")
        void defaultMaxTokens() { // GH-90000
            LLMConfiguration config = LLMConfiguration.builder() // GH-90000
                    .apiKey("sk-test [GH-90000]")
                    .build(); // GH-90000
            assertThat(config.getMaxTokens()).isEqualTo(2048); // GH-90000
        }

        @Test
        @DisplayName("temperature defaults to 0.7 [GH-90000]")
        void defaultTemperature() { // GH-90000
            LLMConfiguration config = LLMConfiguration.builder() // GH-90000
                    .apiKey("sk-test [GH-90000]")
                    .build(); // GH-90000
            assertThat(config.getTemperature()).isEqualTo(0.7); // GH-90000
        }

        @Test
        @DisplayName("timeoutSeconds defaults to 30 [GH-90000]")
        void defaultTimeoutSeconds() { // GH-90000
            LLMConfiguration config = LLMConfiguration.builder() // GH-90000
                    .apiKey("sk-test [GH-90000]")
                    .build(); // GH-90000
            assertThat(config.getTimeoutSeconds()).isEqualTo(30); // GH-90000
        }

        @Test
        @DisplayName("maxRetries defaults to 3 [GH-90000]")
        void defaultMaxRetries() { // GH-90000
            LLMConfiguration config = LLMConfiguration.builder() // GH-90000
                    .apiKey("sk-test [GH-90000]")
                    .build(); // GH-90000
            assertThat(config.getMaxRetries()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("baseUrl and organization default to null [GH-90000]")
        void nullableDefaults() { // GH-90000
            LLMConfiguration config = LLMConfiguration.builder() // GH-90000
                    .apiKey("sk-test [GH-90000]")
                    .build(); // GH-90000
            assertThat(config.getBaseUrl()).isNull(); // GH-90000
            assertThat(config.getOrganization()).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder customization [GH-90000]")
    class BuilderCustomization {

        @Test
        @DisplayName("all fields set correctly [GH-90000]")
        void allFieldsSet() { // GH-90000
            LLMConfiguration config = LLMConfiguration.builder() // GH-90000
                    .apiKey("sk-custom [GH-90000]")
                    .baseUrl("https://api.custom.com [GH-90000]")
                    .modelName("gpt-4 [GH-90000]")
                    .organization("org-123 [GH-90000]")
                    .maxTokens(4096) // GH-90000
                    .temperature(0.3) // GH-90000
                    .timeoutSeconds(60) // GH-90000
                    .maxRetries(5) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getApiKey()).isEqualTo("sk-custom [GH-90000]");
            assertThat(config.getBaseUrl()).isEqualTo("https://api.custom.com [GH-90000]");
            assertThat(config.getModelName()).isEqualTo("gpt-4 [GH-90000]");
            assertThat(config.getOrganization()).isEqualTo("org-123 [GH-90000]");
            assertThat(config.getMaxTokens()).isEqualTo(4096); // GH-90000
            assertThat(config.getTemperature()).isEqualTo(0.3); // GH-90000
            assertThat(config.getTimeoutSeconds()).isEqualTo(60); // GH-90000
            assertThat(config.getMaxRetries()).isEqualTo(5); // GH-90000
        }
    }

    @Nested
    @DisplayName("null validation [GH-90000]")
    class NullValidation {

        @Test
        @DisplayName("null apiKey throws NullPointerException [GH-90000]")
        void nullApiKey() { // GH-90000
            assertThatThrownBy(() -> LLMConfiguration.builder() // GH-90000
                    .modelName("gpt-4 [GH-90000]")
                    .build()) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("apiKey [GH-90000]");
        }

        @Test
        @DisplayName("null modelName throws NullPointerException [GH-90000]")
        void nullModelName() { // GH-90000
            assertThatThrownBy(() -> LLMConfiguration.builder() // GH-90000
                    .apiKey("sk-test [GH-90000]")
                    .modelName(null) // GH-90000
                    .build()) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("modelName [GH-90000]");
        }
    }
}
