package com.ghatana.core.connectors;

import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.CorrelationId;
import com.ghatana.platform.types.identity.IdempotencyKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link IngestEvent}.
 *
 * Covers record construction, builder defaults, null validation,
 * immutable headers, read-only payload, and equality.
 */
@DisplayName("IngestEvent")
class IngestEventTest {

    private static final TenantId TENANT = TenantId.of("test-tenant");
    private static final ByteBuffer PAYLOAD = ByteBuffer.wrap("test-data".getBytes());

    private IngestEvent.Builder validBuilder() {
        return IngestEvent.builder()
                .tenantId(TENANT)
                .eventTypeName("user.created")
                .contentType(ContentType.JSON)
                .schemaUri("urn:schema:user:1.0")
                .payload("hello".getBytes());
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("eventTypeVersion defaults to 1.0.0")
        void defaultVersion() {
            IngestEvent event = validBuilder().build();
            assertThat(event.eventTypeVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("occurrenceTime defaults to now")
        void defaultOccurrenceTime() {
            Instant before = Instant.now();
            IngestEvent event = validBuilder().build();
            Instant after = Instant.now();
            assertThat(event.occurrenceTime()).isBetween(before, after);
        }

        @Test
        @DisplayName("headers defaults to empty map")
        void defaultHeaders() {
            IngestEvent event = validBuilder().build();
            assertThat(event.headers()).isEmpty();
        }

        @Test
        @DisplayName("correlationId defaults to empty")
        void defaultCorrelationId() {
            IngestEvent event = validBuilder().build();
            assertThat(event.correlationId()).isEmpty();
        }

        @Test
        @DisplayName("idempotencyKey defaults to empty")
        void defaultIdempotencyKey() {
            IngestEvent event = validBuilder().build();
            assertThat(event.idempotencyKey()).isEmpty();
        }

        @Test
        @DisplayName("partitionKey defaults to empty")
        void defaultPartitionKey() {
            IngestEvent event = validBuilder().build();
            assertThat(event.partitionKey()).isEmpty();
        }
    }

    @Nested
    @DisplayName("builder customization")
    class BuilderCustomization {

        @Test
        @DisplayName("all fields set correctly")
        void allFieldsSet() {
            Instant time = Instant.parse("2024-01-01T00:00:00Z");
            IngestEvent event = IngestEvent.builder()
                    .tenantId(TENANT)
                    .eventTypeName("order.placed")
                    .eventTypeVersion("2.0.0")
                    .occurrenceTime(time)
                    .headers(Map.of("source", "api"))
                    .contentType(ContentType.JSON)
                    .schemaUri("urn:schema:order:2.0")
                    .payload("order-data".getBytes())
                    .partitionKey("order-123")
                    .build();

            assertThat(event.tenantId()).isEqualTo(TENANT);
            assertThat(event.eventTypeName()).isEqualTo("order.placed");
            assertThat(event.eventTypeVersion()).isEqualTo("2.0.0");
            assertThat(event.occurrenceTime()).isEqualTo(time);
            assertThat(event.headers()).containsEntry("source", "api");
            assertThat(event.contentType()).isEqualTo(ContentType.JSON);
            assertThat(event.schemaUri()).isEqualTo("urn:schema:order:2.0");
            assertThat(event.partitionKey()).contains("order-123");
        }

        @Test
        @DisplayName("payload(byte[]) wraps as ByteBuffer")
        void payloadByteArray() {
            byte[] data = "hello".getBytes();
            IngestEvent event = validBuilder().payload(data).build();
            assertThat(event.payload()).isNotNull();
            assertThat(event.payload().remaining()).isEqualTo(data.length);
        }

        @Test
        @DisplayName("payload(ByteBuffer) accepted")
        void payloadByteBuffer() {
            ByteBuffer buf = ByteBuffer.wrap("test".getBytes());
            IngestEvent event = validBuilder().payload(buf).build();
            assertThat(event.payload()).isNotNull();
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void nullTenantId() {
            assertThatThrownBy(() -> validBuilder().tenantId(null).build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null eventTypeName throws NullPointerException")
        void nullEventTypeName() {
            assertThatThrownBy(() -> validBuilder().eventTypeName(null).build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null contentType throws NullPointerException")
        void nullContentType() {
            assertThatThrownBy(() -> validBuilder().contentType(null).build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null schemaUri throws NullPointerException")
        void nullSchemaUri() {
            assertThatThrownBy(() -> validBuilder().schemaUri(null).build())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null payload throws NullPointerException")
        void nullPayload() {
            assertThatThrownBy(() -> validBuilder().payload((ByteBuffer) null).build())
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("headers are defensively copied (immutable)")
        void headersImmutable() {
            Map<String, String> mutable = new HashMap<>();
            mutable.put("key", "value");
            IngestEvent event = validBuilder().headers(mutable).build();

            assertThatThrownBy(() -> event.headers().put("new", "val"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("payload is read-only after construction")
        void payloadReadOnly() {
            IngestEvent event = validBuilder().build();
            assertThat(event.payload().isReadOnly()).isTrue();
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("equal events are equal")
        void equalEvents() {
            Instant time = Instant.parse("2024-06-01T12:00:00Z");
            byte[] data = "same-data".getBytes();

            IngestEvent e1 = IngestEvent.builder()
                    .tenantId(TENANT)
                    .eventTypeName("test")
                    .eventTypeVersion("1.0.0")
                    .occurrenceTime(time)
                    .contentType(ContentType.JSON)
                    .schemaUri("urn:test")
                    .payload(data)
                    .build();

            IngestEvent e2 = IngestEvent.builder()
                    .tenantId(TENANT)
                    .eventTypeName("test")
                    .eventTypeVersion("1.0.0")
                    .occurrenceTime(time)
                    .contentType(ContentType.JSON)
                    .schemaUri("urn:test")
                    .payload(data)
                    .build();

            assertThat(e1).isEqualTo(e2);
        }
    }
}
