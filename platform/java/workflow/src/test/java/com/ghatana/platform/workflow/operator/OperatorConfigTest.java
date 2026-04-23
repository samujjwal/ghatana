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
        void shouldBuildWithProperties() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperty("key1", "value1") // GH-90000
                    .withProperty("key2", "42") // GH-90000
                    .build(); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should support timeout configuration")
        void shouldSupportTimeout() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withTimeout(Duration.ofSeconds(30)) // GH-90000
                    .build(); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should support max batch size")
        void shouldSupportMaxBatchSize() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withMaxBatchSize(100) // GH-90000
                    .build(); // GH-90000

            assertThat(config).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("type-safe accessors")
    class TypeSafeAccessors {

        @Test
        @DisplayName("should retrieve string property")
        void shouldRetrieveStringProperty() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperty("name", "test-operator") // GH-90000
                    .build(); // GH-90000

            assertThat(config.getString("name")).contains("test-operator");
        }

        @Test
        @DisplayName("should return empty for missing property")
        void shouldReturnEmptyForMissing() { // GH-90000
            OperatorConfig config = OperatorConfig.builder().build(); // GH-90000

            assertThat(config.getString("nonexistent")).isEmpty();
        }
    }
}
