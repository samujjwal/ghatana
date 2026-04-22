package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StepResult}.
 *
 * @doc.type class
 * @doc.purpose Verify StepResult record behavior and factory methods
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("StepResult Tests [GH-90000]")
class StepResultTest {

  @Test
  @DisplayName("success factory should create successful result [GH-90000]")
  void successFactoryShouldCreateSuccessfulResult() { // GH-90000
    String output = "result";
    Map<String, Object> metadata = Map.of("key", "value"); // GH-90000
    Instant start = Instant.now(); // GH-90000
    Instant end = start.plusMillis(100); // GH-90000

    StepResult<String> result = StepResult.success(output, metadata, start, end); // GH-90000

    assertThat(result.status()).isEqualTo(StepResult.Status.SUCCESS); // GH-90000
    assertThat(result.output()).isEqualTo(output); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
    assertThat(result.isSuccess()).isTrue(); // GH-90000
    assertThat(result.errors()).isEmpty(); // GH-90000
    assertThat(result.warnings()).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("failed factory should create failed result [GH-90000]")
  void failedFactoryShouldCreateFailedResult() { // GH-90000
    List<String> errors = List.of("error1", "error2"); // GH-90000
    Map<String, Object> metadata = Map.of(); // GH-90000
    Instant start = Instant.now(); // GH-90000
    Instant end = start.plusMillis(100); // GH-90000

    StepResult<String> result = StepResult.failed(errors, metadata, start, end); // GH-90000

    assertThat(result.status()).isEqualTo(StepResult.Status.FAILED); // GH-90000
    assertThat(result.output()).isNull(); // GH-90000
    assertThat(result.success()).isFalse(); // GH-90000
    assertThat(result.isSuccess()).isFalse(); // GH-90000
    assertThat(result.errors()).containsExactlyElementsOf(errors); // GH-90000
  }

  @Test
  @DisplayName("waitingReview factory should create waiting result [GH-90000]")
  void waitingReviewFactoryShouldCreateWaitingResult() { // GH-90000
    String output = "partial result";
    Map<String, Object> metadata = Map.of(); // GH-90000
    Instant start = Instant.now(); // GH-90000
    Instant end = start.plusMillis(100); // GH-90000

    StepResult<String> result = StepResult.waitingReview(output, metadata, start, end); // GH-90000

    assertThat(result.status()).isEqualTo(StepResult.Status.WAITING_REVIEW); // GH-90000
    assertThat(result.output()).isEqualTo(output); // GH-90000
    assertThat(result.success()).isFalse(); // GH-90000
    assertThat(result.errors()).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("metrics should return metadata or empty map [GH-90000]")
  void metricsShouldReturnMetadata() { // GH-90000
    Map<String, Object> metadata = Map.of("key", "value"); // GH-90000
    Instant now = Instant.now(); // GH-90000

    StepResult<String> result = StepResult.success("output", metadata, now, now); // GH-90000

    assertThat(result.metrics()).isEqualTo(metadata); // GH-90000
  }

  @Test
  @DisplayName("metrics should return empty map when metadata is null [GH-90000]")
  void metricsShouldReturnEmptyMapWhenNull() { // GH-90000
    Instant now = Instant.now(); // GH-90000
    StepResult<String> result = new StepResult<>( // GH-90000
        StepResult.Status.SUCCESS, "output", List.of(), List.of(), null, now, now); // GH-90000

    assertThat(result.metrics()).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("durationMs should calculate correct duration [GH-90000]")
  void durationMsShouldCalculateCorrectDuration() { // GH-90000
    Instant start = Instant.now(); // GH-90000
    Instant end = start.plusMillis(150); // GH-90000
    StepResult<String> result = StepResult.success("output", Map.of(), start, end); // GH-90000

    assertThat(result.durationMs()).isEqualTo(150L); // GH-90000
  }

  @Test
  @DisplayName("durationMs should return zero when timestamps are null [GH-90000]")
  void durationMsShouldReturnZeroWhenNull() { // GH-90000
    StepResult<String> result = StepResult.success("output", Map.of(), null, null); // GH-90000

    assertThat(result.durationMs()).isZero(); // GH-90000
  }

  @Test
  @DisplayName("durationMs should return zero when start is null [GH-90000]")
  void durationMsShouldReturnZeroWhenStartNull() { // GH-90000
    Instant end = Instant.now(); // GH-90000
    StepResult<String> result = StepResult.success("output", Map.of(), null, end); // GH-90000

    assertThat(result.durationMs()).isZero(); // GH-90000
  }

  @Test
  @DisplayName("durationMs should return zero when end is null [GH-90000]")
  void durationMsShouldReturnZeroWhenEndNull() { // GH-90000
    Instant start = Instant.now(); // GH-90000
    StepResult<String> result = StepResult.success("output", Map.of(), start, null); // GH-90000

    assertThat(result.durationMs()).isZero(); // GH-90000
  }

  @Test
  @DisplayName("status enum should have all required values [GH-90000]")
  void statusEnumShouldHaveAllValues() { // GH-90000
    assertThat(StepResult.Status.values()) // GH-90000
        .containsExactly(StepResult.Status.SUCCESS, StepResult.Status.FAILED, StepResult.Status.WAITING_REVIEW); // GH-90000
  }
}
