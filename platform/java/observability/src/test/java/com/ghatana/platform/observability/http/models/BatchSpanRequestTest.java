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
    void setUp() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
    }

    @Test
    @DisplayName("Should serialize batch to JSON")
    void shouldSerializeBatchToJson() throws Exception { // GH-90000
        // Given
        SpanRequest span1 = new SpanRequest( // GH-90000
                "span-1", "trace-1", null, "op1", "service1",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:01Z"),
                1000L, "OK", Map.of(), Map.of() // GH-90000
        );
        SpanRequest span2 = new SpanRequest( // GH-90000
                "span-2", "trace-1", "span-1", "op2", "service1",
                Instant.parse("2024-01-01T00:00:01Z"),
                Instant.parse("2024-01-01T00:00:02Z"),
                1000L, "OK", Map.of(), Map.of() // GH-90000
        );
        BatchSpanRequest batch = new BatchSpanRequest(List.of(span1, span2)); // GH-90000

        // When
        String json = objectMapper.writeValueAsString(batch); // GH-90000

        // Then
        assertThat(json).contains("span-1");
        assertThat(json).contains("span-2");
        assertThat(json).contains("trace-1");
    }

    @Test
    @DisplayName("Should deserialize batch from JSON")
    void shouldDeserializeBatchFromJson() throws Exception { // GH-90000
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
        BatchSpanRequest batch = objectMapper.readValue(json, BatchSpanRequest.class); // GH-90000

        // Then
        assertThat(batch.getSpans()).hasSize(2); // GH-90000
        assertThat(batch.getSpans().get(0).getSpanId()).isEqualTo("span-1");
        assertThat(batch.getSpans().get(1).getSpanId()).isEqualTo("span-2");
        assertThat(batch.getSpans().get(1).getParentSpanId()).isEqualTo("span-1");
    }

    @Test
    @DisplayName("Should handle empty batch")
    void shouldHandleEmptyBatch() throws Exception { // GH-90000
        // Given
        String json = """
                {
                    "spans": []
                }
                """;

        // When
        BatchSpanRequest batch = objectMapper.readValue(json, BatchSpanRequest.class); // GH-90000

        // Then
        assertThat(batch.getSpans()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should handle large batch")
    void shouldHandleLargeBatch() { // GH-90000
        // Given
        List<SpanRequest> spans = java.util.stream.IntStream.range(0, 100) // GH-90000
                .mapToObj(i -> new SpanRequest( // GH-90000
                        "span-" + i,
                        "trace-1",
                        i > 0 ? "span-" + (i - 1) : null, // GH-90000
                        "op-" + i,
                        "service1",
                        Instant.now(), // GH-90000
                        Instant.now().plusMillis(100), // GH-90000
                        100L,
                        "OK",
                        Map.of("index", String.valueOf(i)), // GH-90000
                        Map.of() // GH-90000
                ))
                .toList(); // GH-90000
        BatchSpanRequest batch = new BatchSpanRequest(spans); // GH-90000

        // When/Then
        assertThat(batch.getSpans()).hasSize(100); // GH-90000
        assertThat(batch.getSpans().get(0).getTags()).containsEntry("index", "0"); // GH-90000
        assertThat(batch.getSpans().get(99).getTags()).containsEntry("index", "99"); // GH-90000
    }

    @Test
    @DisplayName("Should round-trip correctly")
    void shouldRoundTripCorrectly() throws Exception { // GH-90000
        // Given
        SpanRequest span = new SpanRequest( // GH-90000
                "span-1", "trace-1", null, "op1", "service1",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:01Z"),
                1000L, "OK",
                Map.of("key", "value"), // GH-90000
                Map.of("log", "logvalue") // GH-90000
        );
        BatchSpanRequest original = new BatchSpanRequest(List.of(span)); // GH-90000

        // When
        String json = objectMapper.writeValueAsString(original); // GH-90000
        BatchSpanRequest deserialized = objectMapper.readValue(json, BatchSpanRequest.class); // GH-90000

        // Then
        assertThat(deserialized.getSpans()).hasSize(1); // GH-90000
        assertThat(deserialized.getSpans().get(0).getSpanId()).isEqualTo("span-1");
        assertThat(deserialized.getSpans().get(0).getTags()).containsEntry("key", "value"); // GH-90000
    }
}
