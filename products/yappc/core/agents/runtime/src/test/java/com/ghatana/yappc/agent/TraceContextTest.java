package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TraceContext}.
 *
 * @doc.type class
 * @doc.purpose Verify TraceContext record behavior
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("TraceContext Tests [GH-90000]")
class TraceContextTest {

  @Test
  @DisplayName("should create TraceContext with all fields [GH-90000]")
  void shouldCreateTraceContext() { // GH-90000
    String traceId = "trace-123";
    String spanId = "span-456";

    TraceContext context = new TraceContext(traceId, spanId); // GH-90000

    assertThat(context.traceId()).isEqualTo(traceId); // GH-90000
    assertThat(context.spanId()).isEqualTo(spanId); // GH-90000
  }

  @Test
  @DisplayName("should create TraceContext with single traceId [GH-90000]")
  void shouldCreateWithSingleTraceId() { // GH-90000
    String traceId = "trace-123";

    TraceContext context = new TraceContext(traceId, null); // GH-90000

    assertThat(context.traceId()).isEqualTo(traceId); // GH-90000
    assertThat(context.spanId()).isNull(); // GH-90000
  }

  @Test
  @DisplayName("should implement equals correctly [GH-90000]")
  void shouldImplementEquals() { // GH-90000
    TraceContext ctx1 = new TraceContext("trace-1", "span-1"); // GH-90000
    TraceContext ctx2 = new TraceContext("trace-1", "span-1"); // GH-90000
    TraceContext ctx3 = new TraceContext("trace-2", "span-2"); // GH-90000

    assertThat(ctx1).isEqualTo(ctx2); // GH-90000
    assertThat(ctx1).isNotEqualTo(ctx3); // GH-90000
  }

  @Test
  @DisplayName("should handle null values [GH-90000]")
  void shouldHandleNullValues() { // GH-90000
    TraceContext context = new TraceContext(null, null); // GH-90000

    assertThat(context.traceId()).isNull(); // GH-90000
    assertThat(context.spanId()).isNull(); // GH-90000
  }
}
