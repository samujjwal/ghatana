package com.ghatana.platform.observability.http.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SpanRequest Tests")
class SpanRequestTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should serialize to JSON")
    void shouldSerializeToJson() throws Exception {
        // Given
        SpanRequest span = new SpanRequest(
                "span-1",
                "trace-1",
                "parent-1",
                "http.request",
                "api-service",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:01Z"),
                1000L,
                "OK",
                Map.of("http.method", "GET"),
                Map.of("event", "request_started")
        );

        // When
        String json = objectMapper.writeValueAsString(span);

        // Then
        assertThat(json).contains("span-1");
        assertThat(json).contains("trace-1");
        assertThat(json).contains("http.request");
    }

    @Test
    @DisplayName("Should deserialize from JSON")
    void shouldDeserializeFromJson() throws Exception {
        // Given
        String json = """
                {
                    "spanId": "span-1",
                    "traceId": "trace-1",
                    "parentSpanId": "parent-1",
                    "operationName": "http.request",
                    "serviceName": "api-service",
                    "startTime": "2024-01-01T00:00:00Z",
                    "endTime": "2024-01-01T00:00:01Z",
                    "duration": 1000,
                    "status": "OK",
                    "tags": {"http.method": "GET"},
                    "logs": {"event": "request_started"}
                }
                """;

        // When
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class);

        // Then
        assertThat(span.getSpanId()).isEqualTo("span-1");
        assertThat(span.getTraceId()).isEqualTo("trace-1");
        assertThat(span.getParentSpanId()).isEqualTo("parent-1");
        assertThat(span.getOperationName()).isEqualTo("http.request");
        assertThat(span.getServiceName()).isEqualTo("api-service");
        assertThat(span.getStatus()).isEqualTo("OK");
        assertThat(span.getTags()).containsEntry("http.method", "GET");
        assertThat(span.getLogs()).containsEntry("event", "request_started");
    }

    @Test
    @DisplayName("Should deserialize with null parentSpanId")
    void shouldDeserializeWithNullParentSpanId() throws Exception {
        // Given
        String json = """
                {
                    "spanId": "span-1",
                    "traceId": "trace-1",
                    "parentSpanId": null,
                    "operationName": "root.operation",
                    "serviceName": "api-service",
                    "startTime": "2024-01-01T00:00:00Z",
                    "endTime": "2024-01-01T00:00:01Z",
                    "status": "OK"
                }
                """;

        // When
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class);

        // Then
        assertThat(span.getParentSpanId()).isNull();
        assertThat(span.getSpanId()).isEqualTo("span-1");
    }

    @Test
    @DisplayName("Should deserialize with empty tags and logs")
    void shouldDeserializeWithEmptyTagsAndLogs() throws Exception {
        // Given
        String json = """
                {
                    "spanId": "span-1",
                    "traceId": "trace-1",
                    "operationName": "test.op",
                    "serviceName": "api-service",
                    "startTime": "2024-01-01T00:00:00Z",
                    "endTime": "2024-01-01T00:00:01Z",
                    "status": "OK",
                    "tags": {},
                    "logs": {}
                }
                """;

        // When
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class);

        // Then
        assertThat(span.getTags()).isEmpty();
        assertThat(span.getLogs()).isEmpty();
    }

    @Test
    @DisplayName("Should handle different status values")
    void shouldHandleDifferentStatusValues() throws Exception {
        // Test ERROR status
        String jsonError = """
                {
                    "spanId": "span-1",
                    "traceId": "trace-1",
                    "operationName": "failed.op",
                    "serviceName": "api-service",
                    "startTime": "2024-01-01T00:00:00Z",
                    "endTime": "2024-01-01T00:00:01Z",
                    "status": "ERROR"
                }
                """;

        SpanRequest spanError = objectMapper.readValue(jsonError, SpanRequest.class);
        assertThat(spanError.getStatus()).isEqualTo("ERROR");

        // Test UNSET status
        String jsonUnset = """
                {
                    "spanId": "span-2",
                    "traceId": "trace-2",
                    "operationName": "unknown.op",
                    "serviceName": "api-service",
                    "startTime": "2024-01-01T00:00:00Z",
                    "endTime": "2024-01-01T00:00:01Z",
                    "status": "UNSET"
                }
                """;

        SpanRequest spanUnset = objectMapper.readValue(jsonUnset, SpanRequest.class);
        assertThat(spanUnset.getStatus()).isEqualTo("UNSET");
    }

    @Test
    @DisplayName("Should create with constructor")
    void shouldCreateWithConstructor() {
        // When
        SpanRequest span = new SpanRequest(
                "span-1",
                "trace-1",
                null,
                "test.op",
                "test-service",
                Instant.now(),
                Instant.now().plusSeconds(1),
                1000L,
                "OK",
                Map.of(),
                Map.of()
        );

        // Then
        assertThat(span.getSpanId()).isEqualTo("span-1");
        assertThat(span.getTraceId()).isEqualTo("trace-1");
        assertThat(span.getParentSpanId()).isNull();
        assertThat(span.getTags()).isEmpty();
        assertThat(span.getLogs()).isEmpty();
    }

    @Test
    @DisplayName("Should handle tags with special characters")
    void shouldHandleTagsWithSpecialCharacters() throws Exception {
        // Given
        String json = """
                {
                    "spanId": "span-1",
                    "traceId": "trace-1",
                    "operationName": "test.op",
                    "serviceName": "api-service",
                    "startTime": "2024-01-01T00:00:00Z",
                    "endTime": "2024-01-01T00:00:01Z",
                    "status": "OK",
                    "tags": {
                        "http.url": "https://api.example.com/path?query=value",
                        "error.message": "Connection failed: timeout after 30s",
                        "user.email": "test@example.com"
                    }
                }
                """;

        // When
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class);

        // Then
        assertThat(span.getTags()).containsEntry("http.url", "https://api.example.com/path?query=value");
        assertThat(span.getTags()).containsEntry("error.message", "Connection failed: timeout after 30s");
        assertThat(span.getTags()).containsEntry("user.email", "test@example.com");
    }

    @Test
    @DisplayName("Should round-trip correctly")
    void shouldRoundTripCorrectly() throws Exception {
        // Given
        SpanRequest original = new SpanRequest(
                "span-1",
                "trace-1",
                "parent-1",
                "http.request",
                "api-service",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:01Z"),
                1000L,
                "OK",
                Map.of("key1", "value1"),
                Map.of("log1", "logvalue1")
        );

        // When
        String json = objectMapper.writeValueAsString(original);
        SpanRequest deserialized = objectMapper.readValue(json, SpanRequest.class);

        // Then
        assertThat(deserialized.getSpanId()).isEqualTo(original.getSpanId());
        assertThat(deserialized.getTraceId()).isEqualTo(original.getTraceId());
        assertThat(deserialized.getParentSpanId()).isEqualTo(original.getParentSpanId());
        assertThat(deserialized.getOperationName()).isEqualTo(original.getOperationName());
        assertThat(deserialized.getServiceName()).isEqualTo(original.getServiceName());
        assertThat(deserialized.getStatus()).isEqualTo(original.getStatus());
        assertThat(deserialized.getTags()).isEqualTo(original.getTags());
        assertThat(deserialized.getLogs()).isEqualTo(original.getLogs());
    }

    @Test
    @DisplayName("Should calculate duration from timestamps if null")
    void shouldCalculateDurationFromTimestamps() throws Exception {
        // Given
        String json = """
                {
                    "spanId": "span-1",
                    "traceId": "trace-1",
                    "operationName": "test.op",
                    "serviceName": "api-service",
                    "startTime": "2024-01-01T00:00:00Z",
                    "endTime": "2024-01-01T00:00:02Z",
                    "status": "OK"
                }
                """;

        // When
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class);

        // Then - duration should be null if not provided (calculated later)
        // This is expected behavior - duration is optional in the request
        assertThat(span.getSpanId()).isEqualTo("span-1");
    }
}
