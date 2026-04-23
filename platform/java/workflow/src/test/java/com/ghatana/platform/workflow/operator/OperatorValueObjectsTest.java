package com.ghatana.platform.workflow.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OperatorConfig} and {@link OperatorResult}.
 *
 * @doc.type class
 * @doc.purpose Operator configuration and result value-object tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("Operator Value Objects")
class OperatorValueObjectsTest {

    @Nested
    @DisplayName("OperatorConfig")
    class OperatorConfigTests {

        @Test
        @DisplayName("should build with defaults")
        void shouldBuildWithDefaults() { // GH-90000
            OperatorConfig config = OperatorConfig.builder().build(); // GH-90000

            assertThat(config.getProcessingTimeout()).isEqualTo(Duration.ofSeconds(30)); // GH-90000
            assertThat(config.getMaxBatchSize()).isEqualTo(1000); // GH-90000
            assertThat(config.getProperties()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should build with custom properties")
        void shouldBuildWithProperties() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperty("windowSize", "60s") // GH-90000
                    .withProperty("partitionBy", "userId") // GH-90000
                    .withTimeout(Duration.ofSeconds(5)) // GH-90000
                    .withMaxBatchSize(500) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getString("windowSize")).contains("60s");
            assertThat(config.getString("partitionBy")).contains("userId");
            assertThat(config.getProcessingTimeout()).isEqualTo(Duration.ofSeconds(5)); // GH-90000
            assertThat(config.getMaxBatchSize()).isEqualTo(500); // GH-90000
        }

        @Test
        @DisplayName("should parse integer properties")
        void shouldParseInt() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperty("count", "42") // GH-90000
                    .build(); // GH-90000

            assertThat(config.getInt("count")).contains(42);
            assertThat(config.getInt("missing")).isEmpty();
            assertThat(config.getInt("missing", 10)).isEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("should parse long properties")
        void shouldParseLong() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperty("offset", "9999999999") // GH-90000
                    .build(); // GH-90000

            assertThat(config.getLong("offset")).contains(9999999999L);
        }

        @Test
        @DisplayName("should parse boolean properties")
        void shouldParseBoolean() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperty("enabled", "true") // GH-90000
                    .withProperty("debug", "false") // GH-90000
                    .build(); // GH-90000

            assertThat(config.getBoolean("enabled")).contains(true);
            assertThat(config.getBoolean("debug")).contains(false);
            assertThat(config.getBoolean("missing", true)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should parse duration with simple format")
        void shouldParseDurationSimple() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperty("interval", "5s") // GH-90000
                    .withProperty("window", "30m") // GH-90000
                    .withProperty("ttl", "1h") // GH-90000
                    .build(); // GH-90000

            assertThat(config.getDuration("interval")).contains(Duration.ofSeconds(5));
            assertThat(config.getDuration("window")).contains(Duration.ofMinutes(30));
            assertThat(config.getDuration("ttl")).contains(Duration.ofHours(1));
        }

        @Test
        @DisplayName("should parse duration with ISO-8601 format")
        void shouldParseDurationIso() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperty("timeout", "PT10S") // GH-90000
                    .build(); // GH-90000

            assertThat(config.getDuration("timeout")).contains(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("should return empty Optional for missing key")
        void shouldReturnEmptyForMissing() { // GH-90000
            OperatorConfig config = OperatorConfig.empty(); // GH-90000

            assertThat(config.getString("nope")).isEmpty();
            assertThat(config.getInt("nope")).isEmpty();
            assertThat(config.getDuration("nope")).isEmpty();
        }

        @Test
        @DisplayName("should return immutable properties map")
        void shouldReturnImmutableMap() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperty("key", "value") // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> config.getProperties().put("new", "val")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("should reject non-positive maxBatchSize")
        void shouldRejectInvalidBatchSize() { // GH-90000
            assertThatThrownBy(() -> OperatorConfig.builder().withMaxBatchSize(0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
            assertThatThrownBy(() -> OperatorConfig.builder().withMaxBatchSize(-1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("should support withProperties(Map)")
        void shouldSupportBulkProperties() { // GH-90000
            OperatorConfig config = OperatorConfig.builder() // GH-90000
                    .withProperties(Map.of("a", "1", "b", "2")) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getString("a")).contains("1");
            assertThat(config.getString("b")).contains("2");
        }
    }

    @Nested
    @DisplayName("OperatorResult")
    class OperatorResultTests {

        @Test
        @DisplayName("empty() should be successful with no outputs")
        void emptyShouldBeSuccessful() { // GH-90000
            OperatorResult result = OperatorResult.empty(); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("failed() should carry error message")
        void failedShouldCarryError() { // GH-90000
            OperatorResult result = OperatorResult.failed("something broke");

            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()).isEqualTo("something broke");
        }
    }
}
