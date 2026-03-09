package com.ghatana.platform.workflow.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OperatorConfig} value object.
 *
 * @doc.type class
 * @doc.purpose OperatorConfig builder and accessor tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("OperatorConfig")
class OperatorConfigTest {

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should build config with properties")
        void shouldBuildWithProperties() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("key1", "value1")
                    .withProperty("key2", "42")
                    .build();

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("should support timeout configuration")
        void shouldSupportTimeout() {
            OperatorConfig config = OperatorConfig.builder()
                    .withTimeout(Duration.ofSeconds(30))
                    .build();

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("should support max batch size")
        void shouldSupportMaxBatchSize() {
            OperatorConfig config = OperatorConfig.builder()
                    .withMaxBatchSize(100)
                    .build();

            assertThat(config).isNotNull();
        }
    }

    @Nested
    @DisplayName("type-safe accessors")
    class TypeSafeAccessors {

        @Test
        @DisplayName("should retrieve string property")
        void shouldRetrieveStringProperty() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("name", "test-operator")
                    .build();

            assertThat(config.getString("name")).contains("test-operator");
        }

        @Test
        @DisplayName("should return empty for missing property")
        void shouldReturnEmptyForMissing() {
            OperatorConfig config = OperatorConfig.builder().build();

            assertThat(config.getString("nonexistent")).isEmpty();
        }
    }
}
