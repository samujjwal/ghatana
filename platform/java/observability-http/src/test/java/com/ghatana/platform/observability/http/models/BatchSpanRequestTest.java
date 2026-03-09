package com.ghatana.platform.observability.http.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchSpanRequest Tests")
class BatchSpanRequestTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should serialize batch to JSON")
    void shouldSerializeBatchToJson() throws Exception {
        // Given
        SpanRequest span1 = new SpanRequest(
                "span-1", "trace-1", null, "op1", "service1",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:01Z"),
                1000L, "OK", Map.of(), Map.of()
        );
        SpanRequest span2 = new SpanRequest(
                "span-2", "trace-1", "span-1", "op2", "service1",
                Instant.parse("2024-01-01T00:00:01Z"),
                Instant.parse("2024-01-01T00:00:02Z"),
                1000L, "OK", Map.of(), Map.of()
        );
        BatchSpanRequest batch = new BatchSpanRequest(List.of(span1, span2));

        // When
        String json = objectMapper.writeValueAsString(batch);

        // Then
        assertThat(json).contains("span-1");
        assertThat(json).contains("span-2");
        assertThat(json).contains("trace-1");
    }

    @Test
    @DisplayName("Should deserialize batch from JSON")
    void shouldDeserializeBatchFromJson() throws Exception {
        // Given
        String json = """
                {
                    "spans": [
                        {
                            "spanId": "span-1",
                            "traceId": "trace-1",
                            "operationName": "op1",
                            "serviceName": "service1",
                            "startTime": "2024-01-01T00:00:00Z",
                            "endTime": "2024-01-01T00:00:01Z",
                            "status": "OK"
                        },
                        {
                            "spanId": "span-2",
                            "traceId": "trace-1",
                            "parentSpanId": "span-1",
                            "operationName": "op2",
                            "serviceName": "service1",
                            "startTime": "2024-01-01T00:00:01Z",
                            "endTime": "2024-01-01T00:00:02Z",
                            "status": "OK"
                        }
                    ]
                }
                """;

        // When
        BatchSpanRequest batch = objectMapper.readValue(json, BatchSpanRequest.class);

        // Then
        assertThat(batch.getSpans()).hasSize(2);
        assertThat(batch.getSpans().get(0).getSpanId()).isEqualTo("span-1");
        assertThat(batch.getSpans().get(1).getSpanId()).isEqualTo("span-2");
        assertThat(batch.getSpans().get(1).getParentSpanId()).isEqualTo("span-1");
    }

    @Test
    @DisplayName("Should handle empty batch")
    void shouldHandleEmptyBatch() throws Exception {
        // Given
        String json = """
                {
                    "spans": []
                }
                """;

        // When
        BatchSpanRequest batch = objectMapper.readValue(json, BatchSpanRequest.class);

        // Then
        assertThat(batch.getSpans()).isEmpty();
    }

    @Test
    @DisplayName("Should handle large batch")
    void shouldHandleLargeBatch() {
        // Given
        List<SpanRequest> spans = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> new SpanRequest(
                        "span-" + i,
                        "trace-1",
                        i > 0 ? "span-" + (i - 1) : null,
                        "op-" + i,
                        "service1",
                        Instant.now(),
                        Instant.now().plusMillis(100),
                        100L,
                        "OK",
                        Map.of("index", String.valueOf(i)),
                        Map.of()
                ))
                .toList();
        BatchSpanRequest batch = new BatchSpanRequest(spans);

        // When/Then
        assertThat(batch.getSpans()).hasSize(100);
        assertThat(batch.getSpans().get(0).getTags()).containsEntry("index", "0");
        assertThat(batch.getSpans().get(99).getTags()).containsEntry("index", "99");
    }

    @Test
    @DisplayName("Should round-trip correctly")
    void shouldRoundTripCorrectly() throws Exception {
        // Given
        SpanRequest span = new SpanRequest(
                "span-1", "trace-1", null, "op1", "service1",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:01Z"),
                1000L, "OK",
                Map.of("key", "value"),
                Map.of("log", "logvalue")
        );
        BatchSpanRequest original = new BatchSpanRequest(List.of(span));

        // When
        String json = objectMapper.writeValueAsString(original);
        BatchSpanRequest deserialized = objectMapper.readValue(json, BatchSpanRequest.class);

        // Then
        assertThat(deserialized.getSpans()).hasSize(1);
        assertThat(deserialized.getSpans().get(0).getSpanId()).isEqualTo("span-1");
        assertThat(deserialized.getSpans().get(0).getTags()).containsEntry("key", "value");
    }
}
