package com.ghatana.core.ingestion.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TracingContext Tests")
class TracingContextTest {

    @Test
    @DisplayName("Should create TracingContext with valid values")
    void shouldCreateWithValidValues() {
        // When
        TracingContext ctx = new TracingContext("trace-123", "span-456", Optional.of("parent-789"));

        // Then
        assertThat(ctx).isNotNull();
        assertThat(ctx.traceId()).isEqualTo("trace-123");
        assertThat(ctx.spanId()).isEqualTo("span-456");
        assertThat(ctx.parentSpanId()).isPresent().contains("parent-789");
    }

    @Test
    @DisplayName("Should create new root trace context")
    void shouldCreateNewRootTrace() {
        // When
        TracingContext ctx = TracingContext.newTrace("trace-abc", "span-xyz");

        // Then
        assertThat(ctx.traceId()).isEqualTo("trace-abc");
        assertThat(ctx.spanId()).isEqualTo("span-xyz");
        assertThat(ctx.parentSpanId()).isEmpty();
    }

    @Test
    @DisplayName("Should create child span from parent")
    void shouldCreateChildSpan() {
        // Given
        TracingContext parent = TracingContext.newTrace("trace-123", "span-parent");

        // When
        TracingContext child = parent.childSpan("span-child");

        // Then
        assertThat(child.traceId()).isEqualTo("trace-123");  // Same trace ID
        assertThat(child.spanId()).isEqualTo("span-child");
        assertThat(child.parentSpanId()).isPresent().contains("span-parent");
    }

    @Test
    @DisplayName("Should throw exception for null traceId")
    void shouldThrowExceptionForNullTraceId() {
        // When/Then
        assertThatThrownBy(() -> new TracingContext(null, "span-123", Optional.empty()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("traceId required");
    }

    @Test
    @DisplayName("Should throw exception for null spanId")
    void shouldThrowExceptionForNullSpanId() {
        // When/Then
        assertThatThrownBy(() -> new TracingContext("trace-123", null, Optional.empty()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("spanId required");
    }

    @Test
    @DisplayName("Should throw exception for null parentSpanId Optional")
    void shouldThrowExceptionForNullParentSpanId() {
        // When/Then
        assertThatThrownBy(() -> new TracingContext("trace-123", "span-456", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("parentSpanId required");
    }

    @Test
    @DisplayName("Should be equal when all fields are equal")
    void shouldBeEqualWhenFieldsEqual() {
        // Given
        TracingContext ctx1 = TracingContext.newTrace("trace-123", "span-456");
        TracingContext ctx2 = TracingContext.newTrace("trace-123", "span-456");

        // Then
        assertThat(ctx1).isEqualTo(ctx2);
        assertThat(ctx1.hashCode()).isEqualTo(ctx2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when traceId differs")
    void shouldNotBeEqualWhenTraceIdDiffers() {
        // Given
        TracingContext ctx1 = TracingContext.newTrace("trace-123", "span-456");
        TracingContext ctx2 = TracingContext.newTrace("trace-789", "span-456");

        // Then
        assertThat(ctx1).isNotEqualTo(ctx2);
    }

    @Test
    @DisplayName("Should support multi-level span hierarchy")
    void shouldSupportMultiLevelHierarchy() {
        // Given - create 3-level hierarchy
        TracingContext root = TracingContext.newTrace("trace-123", "span-1");
        TracingContext level2 = root.childSpan("span-2");
        TracingContext level3 = level2.childSpan("span-3");

        // Then
        assertThat(root.parentSpanId()).isEmpty();
        assertThat(level2.parentSpanId()).contains("span-1");
        assertThat(level3.parentSpanId()).contains("span-2");
        assertThat(root.traceId()).isEqualTo(level2.traceId()).isEqualTo(level3.traceId());
    }

    @Test
    @DisplayName("Should handle UUID-format IDs")
    void shouldHandleUuidFormatIds() {
        // Given
        String uuidTrace = "550e8400-e29b-41d4-a716-446655440000";
        String uuidSpan = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

        // When
        TracingContext ctx = TracingContext.newTrace(uuidTrace, uuidSpan);

        // Then
        assertThat(ctx.traceId()).isEqualTo(uuidTrace);
        assertThat(ctx.spanId()).isEqualTo(uuidSpan);
    }
}
