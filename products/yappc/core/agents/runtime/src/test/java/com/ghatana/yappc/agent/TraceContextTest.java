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
@DisplayName("TraceContext Tests")
class TraceContextTest {

  @Test
  @DisplayName("should create TraceContext with all fields")
  void shouldCreateTraceContext() { 
    String traceId = "trace-123";
    String spanId = "span-456";

    TraceContext context = new TraceContext(traceId, spanId); 

    assertThat(context.traceId()).isEqualTo(traceId); 
    assertThat(context.spanId()).isEqualTo(spanId); 
  }

  @Test
  @DisplayName("should create TraceContext with single traceId")
  void shouldCreateWithSingleTraceId() { 
    String traceId = "trace-123";

    TraceContext context = new TraceContext(traceId, null); 

    assertThat(context.traceId()).isEqualTo(traceId); 
    assertThat(context.spanId()).isNull(); 
  }

  @Test
  @DisplayName("should implement equals correctly")
  void shouldImplementEquals() { 
    TraceContext ctx1 = new TraceContext("trace-1", "span-1"); 
    TraceContext ctx2 = new TraceContext("trace-1", "span-1"); 
    TraceContext ctx3 = new TraceContext("trace-2", "span-2"); 

    assertThat(ctx1).isEqualTo(ctx2); 
    assertThat(ctx1).isNotEqualTo(ctx3); 
  }

  @Test
  @DisplayName("should handle null values")
  void shouldHandleNullValues() { 
    TraceContext context = new TraceContext(null, null); 

    assertThat(context.traceId()).isNull(); 
    assertThat(context.spanId()).isNull(); 
  }
}
