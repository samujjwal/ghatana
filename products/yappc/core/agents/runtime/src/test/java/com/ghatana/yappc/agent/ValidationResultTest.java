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
  void successShouldProduceValidResult() { // GH-90000
    ValidationResult result = ValidationResult.success(); // GH-90000

    assertThat(result.ok()).isTrue(); // GH-90000
    assertThat(result.isValid()).isTrue(); // GH-90000
    assertThat(result.errors()).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("fail should preserve all supplied error messages")
  void failShouldPreserveErrors() { // GH-90000
    ValidationResult result = ValidationResult.fail("missing id", "invalid phase"); // GH-90000

    assertThat(result.ok()).isFalse(); // GH-90000
    assertThat(result.isValid()).isFalse(); // GH-90000
    assertThat(result.errors()).containsExactly("missing id", "invalid phase"); // GH-90000
  }
}
