package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ValidationResult} convenience factories.
 *
 * @doc.type class
 * @doc.purpose Verify validation result helpers preserve expected semantics
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("ValidationResult Tests")
class ValidationResultTest {

  @Test
  @DisplayName("success should produce valid result with no errors")
  void successShouldProduceValidResult() {
    ValidationResult result = ValidationResult.success();

    assertThat(result.ok()).isTrue();
    assertThat(result.isValid()).isTrue();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  @DisplayName("fail should preserve all supplied error messages")
  void failShouldPreserveErrors() {
    ValidationResult result = ValidationResult.fail("missing id", "invalid phase");

    assertThat(result.ok()).isFalse();
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).containsExactly("missing id", "invalid phase");
  }
}
