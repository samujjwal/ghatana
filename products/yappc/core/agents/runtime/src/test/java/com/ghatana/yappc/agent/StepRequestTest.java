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
@DisplayName("StepRequest Tests")
class StepRequestTest {

  @Test
  @DisplayName("should create StepRequest with valid input and context")
  void shouldCreateStepRequest() { 
    String input = "test input";
    StepContext context = createTestContext(); 

    StepRequest<String> request = new StepRequest<>(input, context); 

    assertThat(request.input()).isEqualTo(input); 
    assertThat(request.context()).isEqualTo(context); 
  }

  @Test
  @DisplayName("should reject null input")
  void shouldRejectNullInput() { 
    StepContext context = createTestContext(); 

    assertThatThrownBy(() -> new StepRequest<>(null, context)) 
        .isInstanceOf(IllegalArgumentException.class) 
        .hasMessageContaining("input cannot be null");
  }

  @Test
  @DisplayName("should reject null context")
  void shouldRejectNullContext() { 
    String input = "test input";

    assertThatThrownBy(() -> new StepRequest<>(input, null)) 
        .isInstanceOf(IllegalArgumentException.class) 
        .hasMessageContaining("context cannot be null");
  }

  @Test
  @DisplayName("should create StepRequest with default context using factory method")
  void shouldCreateWithDefaultContext() { 
    String stepId = "test-step";
    String input = "test input";

    StepRequest<String> request = StepRequest.of(stepId, input); 

    assertThat(request.input()).isEqualTo(input); 
    assertThat(request.context()).isNotNull(); 
    assertThat(request.context().runId()).isEqualTo(stepId); 
    assertThat(request.context().tenantId()).isEqualTo("system");
  }

  @Test
  @DisplayName("should handle null stepId in factory method")
  void shouldHandleNullStepIdInFactory() { 
    StepRequest<String> request = StepRequest.of(null, "input"); 

    assertThat(request.input()).isEqualTo("input");
    assertThat(request.context().runId()).isNull(); 
  }

  @Test
  @DisplayName("should handle null input in factory method")
  void shouldHandleNullInputInFactory() { 
    assertThatThrownBy(() -> StepRequest.of("stepId", null)) 
        .isInstanceOf(IllegalArgumentException.class) 
        .hasMessageContaining("input cannot be null");
  }

  private StepContext createTestContext() { 
    return new StepContext( 
        "tenant-1",
        "run-1",
        "phase-1",
        "config-1",
        new Budget(100L, 1.0, 60000L), 
        null,
        null
    );
  }
}
