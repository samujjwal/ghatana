package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StepRequest}.
 *
 * @doc.type class
 * @doc.purpose Verify StepRequest record behavior and factory methods
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("StepRequest Tests [GH-90000]")
class StepRequestTest {

  @Test
  @DisplayName("should create StepRequest with valid input and context [GH-90000]")
  void shouldCreateStepRequest() { // GH-90000
    String input = "test input";
    StepContext context = createTestContext(); // GH-90000

    StepRequest<String> request = new StepRequest<>(input, context); // GH-90000

    assertThat(request.input()).isEqualTo(input); // GH-90000
    assertThat(request.context()).isEqualTo(context); // GH-90000
  }

  @Test
  @DisplayName("should reject null input [GH-90000]")
  void shouldRejectNullInput() { // GH-90000
    StepContext context = createTestContext(); // GH-90000

    assertThatThrownBy(() -> new StepRequest<>(null, context)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("input cannot be null [GH-90000]");
  }

  @Test
  @DisplayName("should reject null context [GH-90000]")
  void shouldRejectNullContext() { // GH-90000
    String input = "test input";

    assertThatThrownBy(() -> new StepRequest<>(input, null)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("context cannot be null [GH-90000]");
  }

  @Test
  @DisplayName("should create StepRequest with default context using factory method [GH-90000]")
  void shouldCreateWithDefaultContext() { // GH-90000
    String stepId = "test-step";
    String input = "test input";

    StepRequest<String> request = StepRequest.of(stepId, input); // GH-90000

    assertThat(request.input()).isEqualTo(input); // GH-90000
    assertThat(request.context()).isNotNull(); // GH-90000
    assertThat(request.context().runId()).isEqualTo(stepId); // GH-90000
    assertThat(request.context().tenantId()).isEqualTo("system [GH-90000]");
  }

  @Test
  @DisplayName("should handle null stepId in factory method [GH-90000]")
  void shouldHandleNullStepIdInFactory() { // GH-90000
    StepRequest<String> request = StepRequest.of(null, "input"); // GH-90000

    assertThat(request.input()).isEqualTo("input [GH-90000]");
    assertThat(request.context().runId()).isNull(); // GH-90000
  }

  @Test
  @DisplayName("should handle null input in factory method [GH-90000]")
  void shouldHandleNullInputInFactory() { // GH-90000
    assertThatThrownBy(() -> StepRequest.of("stepId", null)) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("input cannot be null [GH-90000]");
  }

  private StepContext createTestContext() { // GH-90000
    return new StepContext( // GH-90000
        "tenant-1",
        "run-1",
        "phase-1",
        "config-1",
        new Budget(100L, 1.0, 60000L), // GH-90000
        null,
        null
    );
  }
}
