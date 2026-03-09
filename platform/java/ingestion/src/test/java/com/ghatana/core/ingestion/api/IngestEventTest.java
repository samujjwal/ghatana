package com.ghatana.core.ingestion.api;

import com.ghatana.core.connectors.IngestEvent;
import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.CorrelationId;
import com.ghatana.platform.types.identity.IdempotencyKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("IngestEvent Tests")
class IngestEventTest {

    @Test
    @DisplayName("Should create IngestEvent with builder - all required fields")
    void shouldCreateWithBuilderRequiredFields() {
        // Given
        TenantId tenantId = TenantId.random();
        String eventTypeName = "OrderCreated";
        String eventTypeVersion = "1.0.0";
        Instant occurrenceTime = Instant.now();
        ContentType contentType = ContentType.JSON;
        String schemaUri = "https://schemas.example.com/order-created/v1";
        ByteBuffer payload = ByteBuffer.wrap("{\"orderId\":\"123\"}".getBytes(StandardCharsets.UTF_8));

        // When
        IngestEvent event = IngestEvent.builder()
            .tenantId(tenantId)
            .eventTypeName(eventTypeName)
            .eventTypeVersion(eventTypeVersion)
            .occurrenceTime(occurrenceTime)
            .contentType(contentType)
            .schemaUri(schemaUri)
            .payload(payload)
            .build();

        // Then
        assertThat(event).isNotNull();
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.eventTypeName()).isEqualTo(eventTypeName);
        assertThat(event.eventTypeVersion()).isEqualTo(eventTypeVersion);
        assertThat(event.occurrenceTime()).isEqualTo(occurrenceTime);
        assertThat(event.headers()).isEmpty();
        assertThat(event.contentType()).isEqualTo(contentType);
        assertThat(event.schemaUri()).isEqualTo(schemaUri);
        assertThat(event.payload()).isEqualTo(payload.asReadOnlyBuffer());
        assertThat(event.correlationId()).isEmpty();
        assertThat(event.idempotencyKey()).isEmpty();
        assertThat(event.partitionKey()).isEmpty();
    }

    @Test
    @DisplayName("Should create IngestEvent with all fields including optional")
    void shouldCreateWithAllFields() {
        // Given
        TenantId tenantId = TenantId.random();
        String eventTypeName = "OrderCreated";
        String eventTypeVersion = "1.0.0";
        Instant occurrenceTime = Instant.now();
        Map<String, String> headers = Map.of("source", "api", "userId", "user-123");
        ContentType contentType = ContentType.PROTOBUF;
        String schemaUri = "https://schemas.example.com/order-created/v1";
        ByteBuffer payload = ByteBuffer.wrap("binary-data".getBytes(StandardCharsets.UTF_8));
        CorrelationId correlationId = CorrelationId.random();
        IdempotencyKey idempotencyKey = IdempotencyKey.random();
        String partitionKey = "partition-abc";

        // When
        IngestEvent event = IngestEvent.builder()
            .tenantId(tenantId)
            .eventTypeName(eventTypeName)
            .eventTypeVersion(eventTypeVersion)
            .occurrenceTime(occurrenceTime)
            .headers(headers)
            .contentType(contentType)
            .schemaUri(schemaUri)
            .payload(payload)
            .correlationId(correlationId)
            .idempotencyKey(idempotencyKey)
            .partitionKey(partitionKey)
            .build();

        // Then
        assertThat(event.headers()).containsExactlyInAnyOrderEntriesOf(headers);
        assertThat(event.correlationId()).isPresent().contains(correlationId);
        assertThat(event.idempotencyKey()).isPresent().contains(idempotencyKey);
        assertThat(event.partitionKey()).isPresent().contains(partitionKey);
    }

    @Test
    @DisplayName("Should use current time as default for occurrenceTime")
    void shouldUseCurrentTimeAsDefault() {
        // Given
        Instant before = Instant.now();

        // When
        IngestEvent event = IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .build();

        Instant after = Instant.now();

        // Then
        assertThat(event.occurrenceTime()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should accept byte array payload via convenience method")
    void shouldAcceptByteArrayPayload() {
        // Given
        byte[] payloadBytes = "test-data".getBytes(StandardCharsets.UTF_8);

        // When
        IngestEvent event = IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(payloadBytes)
            .build();

        // Then
        byte[] resultBytes = new byte[event.payload().remaining()];
        event.payload().duplicate().get(resultBytes);
        assertThat(resultBytes).isEqualTo(payloadBytes);
    }

    @Test
    @DisplayName("Should throw exception for null tenantId")
    void shouldThrowExceptionForNullTenantId() {
        // When/Then
        assertThatThrownBy(() -> IngestEvent.builder()
            .tenantId(null)
            .eventTypeName("Event")
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId required");
    }

    @Test
    @DisplayName("Should throw exception for null typeRef")
    void shouldThrowExceptionForNullEventTypeName() {
        // When/Then
        assertThatThrownBy(() -> IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName(null)
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("eventTypeName required");
    }

    @Test
    @DisplayName("Should throw exception for null occurrenceTime")
    void shouldThrowExceptionForNullOccurrenceTime() {
        // When/Then
        assertThatThrownBy(() -> IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .occurrenceTime(null)
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("occurrenceTime required");
    }

    @Test
    @DisplayName("Should throw exception for null contentType")
    void shouldThrowExceptionForNullContentType() {
        // When/Then
        assertThatThrownBy(() -> IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(null)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("contentType required");
    }

    @Test
    @DisplayName("Should throw exception for null schemaUri")
    void shouldThrowExceptionForNullSchemaUri() {
        // When/Then
        assertThatThrownBy(() -> IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(ContentType.JSON)
            .schemaUri(null)
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("schemaUri required");
    }

    @Test
    @DisplayName("Should throw exception for null payload")
    void shouldThrowExceptionForNullPayload() {
        // When/Then
        assertThatThrownBy(() -> IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload((ByteBuffer) null)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("payload required");
    }

    @Test
    @DisplayName("Should make defensive copy of headers")
    void shouldMakeDefensiveCopyOfHeaders() {
        // Given
        Map<String, String> mutableHeaders = new HashMap<>();
        mutableHeaders.put("key1", "value1");

        IngestEvent event = IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .headers(mutableHeaders)
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .build();

        // When - modify original map
        mutableHeaders.put("key2", "value2");

        // Then - event headers should not be affected
        assertThat(event.headers()).hasSize(1);
        assertThat(event.headers()).containsEntry("key1", "value1");
        assertThat(event.headers()).doesNotContainKey("key2");
    }

    @Test
    @DisplayName("Should return immutable headers map")
    void shouldReturnImmutableHeadersMap() {
        // Given
        IngestEvent event = IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .headers(Map.of("key", "value"))
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .build();

        // When/Then - trying to modify should throw exception
        assertThatThrownBy(() -> event.headers().put("new", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should return read-only ByteBuffer for payload")
    void shouldReturnReadOnlyPayload() {
        // Given
        ByteBuffer writablePayload = ByteBuffer.wrap("test".getBytes());

        IngestEvent event = IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(writablePayload)
            .build();

        // Then
        assertThat(event.payload().isReadOnly()).isTrue();
    }

    @Test
    @DisplayName("Should handle empty payload")
    void shouldHandleEmptyPayload() {
        // Given
        ByteBuffer emptyPayload = ByteBuffer.wrap(new byte[0]);

        // When
        IngestEvent event = IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(ContentType.BINARY)
            .schemaUri("schema")
            .payload(emptyPayload)
            .build();

        // Then
        assertThat(event.payload().remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle null correlationId gracefully")
    void shouldHandleNullCorrelationIdGracefully() {
        // When
        IngestEvent event = IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .correlationId(null)
            .build();

        // Then
        assertThat(event.correlationId()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null idempotencyKey gracefully")
    void shouldHandleNullIdempotencyKeyGracefully() {
        // When
        IngestEvent event = IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .idempotencyKey(null)
            .build();

        // Then
        assertThat(event.idempotencyKey()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null partitionKey gracefully")
    void shouldHandleNullPartitionKeyGracefully() {
        // When
        IngestEvent event = IngestEvent.builder()
            .tenantId(TenantId.random())
            .eventTypeName("Event")
            .contentType(ContentType.JSON)
            .schemaUri("schema")
            .payload(ByteBuffer.wrap("{}".getBytes()))
            .partitionKey(null)
            .build();

        // Then
        assertThat(event.partitionKey()).isEmpty();
    }
}
