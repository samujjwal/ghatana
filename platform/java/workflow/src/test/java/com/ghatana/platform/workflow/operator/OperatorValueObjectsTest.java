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
        void shouldBuildWithDefaults() {
            OperatorConfig config = OperatorConfig.builder().build();

            assertThat(config.getProcessingTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(config.getMaxBatchSize()).isEqualTo(1000);
            assertThat(config.getProperties()).isEmpty();
        }

        @Test
        @DisplayName("should build with custom properties")
        void shouldBuildWithProperties() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("windowSize", "60s")
                    .withProperty("partitionBy", "userId")
                    .withTimeout(Duration.ofSeconds(5))
                    .withMaxBatchSize(500)
                    .build();

            assertThat(config.getString("windowSize")).contains("60s");
            assertThat(config.getString("partitionBy")).contains("userId");
            assertThat(config.getProcessingTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(config.getMaxBatchSize()).isEqualTo(500);
        }

        @Test
        @DisplayName("should parse integer properties")
        void shouldParseInt() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("count", "42")
                    .build();

            assertThat(config.getInt("count")).contains(42);
            assertThat(config.getInt("missing")).isEmpty();
            assertThat(config.getInt("missing", 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("should parse long properties")
        void shouldParseLong() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("offset", "9999999999")
                    .build();

            assertThat(config.getLong("offset")).contains(9999999999L);
        }

        @Test
        @DisplayName("should parse boolean properties")
        void shouldParseBoolean() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("enabled", "true")
                    .withProperty("debug", "false")
                    .build();

            assertThat(config.getBoolean("enabled")).contains(true);
            assertThat(config.getBoolean("debug")).contains(false);
            assertThat(config.getBoolean("missing", true)).isTrue();
        }

        @Test
        @DisplayName("should parse duration with simple format")
        void shouldParseDurationSimple() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("interval", "5s")
                    .withProperty("window", "30m")
                    .withProperty("ttl", "1h")
                    .build();

            assertThat(config.getDuration("interval")).contains(Duration.ofSeconds(5));
            assertThat(config.getDuration("window")).contains(Duration.ofMinutes(30));
            assertThat(config.getDuration("ttl")).contains(Duration.ofHours(1));
        }

        @Test
        @DisplayName("should parse duration with ISO-8601 format")
        void shouldParseDurationIso() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("timeout", "PT10S")
                    .build();

            assertThat(config.getDuration("timeout")).contains(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("should return empty Optional for missing key")
        void shouldReturnEmptyForMissing() {
            OperatorConfig config = OperatorConfig.empty();

            assertThat(config.getString("nope")).isEmpty();
            assertThat(config.getInt("nope")).isEmpty();
            assertThat(config.getDuration("nope")).isEmpty();
        }

        @Test
        @DisplayName("should return immutable properties map")
        void shouldReturnImmutableMap() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("key", "value")
                    .build();

            assertThatThrownBy(() -> config.getProperties().put("new", "val"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should reject non-positive maxBatchSize")
        void shouldRejectInvalidBatchSize() {
            assertThatThrownBy(() -> OperatorConfig.builder().withMaxBatchSize(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> OperatorConfig.builder().withMaxBatchSize(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should support withProperties(Map)")
        void shouldSupportBulkProperties() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperties(Map.of("a", "1", "b", "2"))
                    .build();

            assertThat(config.getString("a")).contains("1");
            assertThat(config.getString("b")).contains("2");
        }
    }

    @Nested
    @DisplayName("OperatorResult")
    class OperatorResultTests {

        @Test
        @DisplayName("empty() should be successful with no outputs")
        void emptyShouldBeSuccessful() {
            OperatorResult result = OperatorResult.empty();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).isEmpty();
        }

        @Test
        @DisplayName("failed() should carry error message")
        void failedShouldCarryError() {
            OperatorResult result = OperatorResult.failed("something broke");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("something broke");
        }
    }
}
