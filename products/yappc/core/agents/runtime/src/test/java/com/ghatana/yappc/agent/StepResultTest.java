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
@DisplayName("StepResult Tests")
class StepResultTest {

  @Test
  @DisplayName("success factory should create successful result")
  void successFactoryShouldCreateSuccessfulResult() { 
    String output = "result";
    Map<String, Object> metadata = Map.of("key", "value"); 
    Instant start = Instant.now(); 
    Instant end = start.plusMillis(100); 

    StepResult<String> result = StepResult.success(output, metadata, start, end); 

    assertThat(result.status()).isEqualTo(StepResult.Status.SUCCESS); 
    assertThat(result.output()).isEqualTo(output); 
    assertThat(result.success()).isTrue(); 
    assertThat(result.isSuccess()).isTrue(); 
    assertThat(result.errors()).isEmpty(); 
    assertThat(result.warnings()).isEmpty(); 
  }

  @Test
  @DisplayName("failed factory should create failed result")
  void failedFactoryShouldCreateFailedResult() { 
    List<String> errors = List.of("error1", "error2"); 
    Map<String, Object> metadata = Map.of(); 
    Instant start = Instant.now(); 
    Instant end = start.plusMillis(100); 

    StepResult<String> result = StepResult.failed(errors, metadata, start, end); 

    assertThat(result.status()).isEqualTo(StepResult.Status.FAILED); 
    assertThat(result.output()).isNull(); 
    assertThat(result.success()).isFalse(); 
    assertThat(result.isSuccess()).isFalse(); 
    assertThat(result.errors()).containsExactlyElementsOf(errors); 
  }

  @Test
  @DisplayName("waitingReview factory should create waiting result")
  void waitingReviewFactoryShouldCreateWaitingResult() { 
    String output = "partial result";
    Map<String, Object> metadata = Map.of(); 
    Instant start = Instant.now(); 
    Instant end = start.plusMillis(100); 

    StepResult<String> result = StepResult.waitingReview(output, metadata, start, end); 

    assertThat(result.status()).isEqualTo(StepResult.Status.WAITING_REVIEW); 
    assertThat(result.output()).isEqualTo(output); 
    assertThat(result.success()).isFalse(); 
    assertThat(result.errors()).isEmpty(); 
  }

  @Test
  @DisplayName("metrics should return metadata or empty map")
  void metricsShouldReturnMetadata() { 
    Map<String, Object> metadata = Map.of("key", "value"); 
    Instant now = Instant.now(); 

    StepResult<String> result = StepResult.success("output", metadata, now, now); 

    assertThat(result.metrics()).isEqualTo(metadata); 
  }

  @Test
  @DisplayName("metrics should return empty map when metadata is null")
  void metricsShouldReturnEmptyMapWhenNull() { 
    Instant now = Instant.now(); 
    StepResult<String> result = new StepResult<>( 
        StepResult.Status.SUCCESS, "output", List.of(), List.of(), null, now, now); 

    assertThat(result.metrics()).isEmpty(); 
  }

  @Test
  @DisplayName("durationMs should calculate correct duration")
  void durationMsShouldCalculateCorrectDuration() { 
    Instant start = Instant.now(); 
    Instant end = start.plusMillis(150); 
    StepResult<String> result = StepResult.success("output", Map.of(), start, end); 

    assertThat(result.durationMs()).isEqualTo(150L); 
  }

  @Test
  @DisplayName("durationMs should return zero when timestamps are null")
  void durationMsShouldReturnZeroWhenNull() { 
    StepResult<String> result = StepResult.success("output", Map.of(), null, null); 

    assertThat(result.durationMs()).isZero(); 
  }

  @Test
  @DisplayName("durationMs should return zero when start is null")
  void durationMsShouldReturnZeroWhenStartNull() { 
    Instant end = Instant.now(); 
    StepResult<String> result = StepResult.success("output", Map.of(), null, end); 

    assertThat(result.durationMs()).isZero(); 
  }

  @Test
  @DisplayName("durationMs should return zero when end is null")
  void durationMsShouldReturnZeroWhenEndNull() { 
    Instant start = Instant.now(); 
    StepResult<String> result = StepResult.success("output", Map.of(), start, null); 

    assertThat(result.durationMs()).isZero(); 
  }

  @Test
  @DisplayName("status enum should have all required values")
  void statusEnumShouldHaveAllValues() { 
    assertThat(StepResult.Status.values()) 
        .containsExactly(StepResult.Status.SUCCESS, StepResult.Status.FAILED, StepResult.Status.WAITING_REVIEW); 
  }
}
