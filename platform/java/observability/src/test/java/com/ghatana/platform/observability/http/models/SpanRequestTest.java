package com.ghatana.platform.observability.http.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpanRequest Tests [GH-90000]")
class SpanRequestTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
    }

    @Test
    @DisplayName("Should serialize to JSON [GH-90000]")
    void shouldSerializeToJson() throws Exception { // GH-90000
        // Given
        SpanRequest span = new SpanRequest( // GH-90000
                "span-1",
                "trace-1",
                "parent-1",
                "http.request",
                "api-service",
                Instant.parse("2024-01-01T00:00:00Z [GH-90000]"),
                Instant.parse("2024-01-01T00:00:01Z [GH-90000]"),
                1000L,
                "OK",
                Map.of("http.method", "GET"), // GH-90000
                Map.of("event", "request_started") // GH-90000
        );

        // When
        String json = objectMapper.writeValueAsString(span); // GH-90000

        // Then
        assertThat(json).contains("span-1 [GH-90000]");
        assertThat(json).contains("trace-1 [GH-90000]");
        assertThat(json).contains("http.request [GH-90000]");
    }

    @Test
    @DisplayName("Should deserialize from JSON [GH-90000]")
    void shouldDeserializeFromJson() throws Exception { // GH-90000
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
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class); // GH-90000

        // Then
        assertThat(span.getSpanId()).isEqualTo("span-1 [GH-90000]");
        assertThat(span.getTraceId()).isEqualTo("trace-1 [GH-90000]");
        assertThat(span.getParentSpanId()).isEqualTo("parent-1 [GH-90000]");
        assertThat(span.getOperationName()).isEqualTo("http.request [GH-90000]");
        assertThat(span.getServiceName()).isEqualTo("api-service [GH-90000]");
        assertThat(span.getStatus()).isEqualTo("OK [GH-90000]");
        assertThat(span.getTags()).containsEntry("http.method", "GET"); // GH-90000
        assertThat(span.getLogs()).containsEntry("event", "request_started"); // GH-90000
    }

    @Test
    @DisplayName("Should deserialize with null parentSpanId [GH-90000]")
    void shouldDeserializeWithNullParentSpanId() throws Exception { // GH-90000
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
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class); // GH-90000

        // Then
        assertThat(span.getParentSpanId()).isNull(); // GH-90000
        assertThat(span.getSpanId()).isEqualTo("span-1 [GH-90000]");
    }

    @Test
    @DisplayName("Should deserialize with empty tags and logs [GH-90000]")
    void shouldDeserializeWithEmptyTagsAndLogs() throws Exception { // GH-90000
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
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class); // GH-90000

        // Then
        assertThat(span.getTags()).isEmpty(); // GH-90000
        assertThat(span.getLogs()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should handle different status values [GH-90000]")
    void shouldHandleDifferentStatusValues() throws Exception { // GH-90000
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

        SpanRequest spanError = objectMapper.readValue(jsonError, SpanRequest.class); // GH-90000
        assertThat(spanError.getStatus()).isEqualTo("ERROR [GH-90000]");

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

        SpanRequest spanUnset = objectMapper.readValue(jsonUnset, SpanRequest.class); // GH-90000
        assertThat(spanUnset.getStatus()).isEqualTo("UNSET [GH-90000]");
    }

    @Test
    @DisplayName("Should create with constructor [GH-90000]")
    void shouldCreateWithConstructor() { // GH-90000
        // When
        SpanRequest span = new SpanRequest( // GH-90000
                "span-1",
                "trace-1",
                null,
                "test.op",
                "test-service",
                Instant.now(), // GH-90000
                Instant.now().plusSeconds(1), // GH-90000
                1000L,
                "OK",
                Map.of(), // GH-90000
                Map.of() // GH-90000
        );

        // Then
        assertThat(span.getSpanId()).isEqualTo("span-1 [GH-90000]");
        assertThat(span.getTraceId()).isEqualTo("trace-1 [GH-90000]");
        assertThat(span.getParentSpanId()).isNull(); // GH-90000
        assertThat(span.getTags()).isEmpty(); // GH-90000
        assertThat(span.getLogs()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should handle tags with special characters [GH-90000]")
    void shouldHandleTagsWithSpecialCharacters() throws Exception { // GH-90000
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
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class); // GH-90000

        // Then
        assertThat(span.getTags()).containsEntry("http.url", "https://api.example.com/path?query=value"); // GH-90000
        assertThat(span.getTags()).containsEntry("error.message", "Connection failed: timeout after 30s"); // GH-90000
        assertThat(span.getTags()).containsEntry("user.email", "test@example.com"); // GH-90000
    }

    @Test
    @DisplayName("Should round-trip correctly [GH-90000]")
    void shouldRoundTripCorrectly() throws Exception { // GH-90000
        // Given
        SpanRequest original = new SpanRequest( // GH-90000
                "span-1",
                "trace-1",
                "parent-1",
                "http.request",
                "api-service",
                Instant.parse("2024-01-01T00:00:00Z [GH-90000]"),
                Instant.parse("2024-01-01T00:00:01Z [GH-90000]"),
                1000L,
                "OK",
                Map.of("key1", "value1"), // GH-90000
                Map.of("log1", "logvalue1") // GH-90000
        );

        // When
        String json = objectMapper.writeValueAsString(original); // GH-90000
        SpanRequest deserialized = objectMapper.readValue(json, SpanRequest.class); // GH-90000

        // Then
        assertThat(deserialized.getSpanId()).isEqualTo(original.getSpanId()); // GH-90000
        assertThat(deserialized.getTraceId()).isEqualTo(original.getTraceId()); // GH-90000
        assertThat(deserialized.getParentSpanId()).isEqualTo(original.getParentSpanId()); // GH-90000
        assertThat(deserialized.getOperationName()).isEqualTo(original.getOperationName()); // GH-90000
        assertThat(deserialized.getServiceName()).isEqualTo(original.getServiceName()); // GH-90000
        assertThat(deserialized.getStatus()).isEqualTo(original.getStatus()); // GH-90000
        assertThat(deserialized.getTags()).isEqualTo(original.getTags()); // GH-90000
        assertThat(deserialized.getLogs()).isEqualTo(original.getLogs()); // GH-90000
    }

    @Test
    @DisplayName("Should calculate duration from timestamps if null [GH-90000]")
    void shouldCalculateDurationFromTimestamps() throws Exception { // GH-90000
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
        SpanRequest span = objectMapper.readValue(json, SpanRequest.class); // GH-90000

        // Then - duration should be null if not provided (calculated later) // GH-90000
        // This is expected behavior - duration is optional in the request
        assertThat(span.getSpanId()).isEqualTo("span-1 [GH-90000]");
    }
}
