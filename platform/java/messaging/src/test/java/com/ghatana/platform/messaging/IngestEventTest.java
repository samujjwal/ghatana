package com.ghatana.core.connectors;

import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
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
@DisplayName("IngestEvent [GH-90000]")
class IngestEventTest {

    private static final TenantId TENANT = TenantId.of("test-tenant [GH-90000]");
    private static final ByteBuffer PAYLOAD = ByteBuffer.wrap("test-data".getBytes()); // GH-90000

    private IngestEvent.Builder validBuilder() { // GH-90000
        return IngestEvent.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .eventTypeName("user.created [GH-90000]")
                .contentType(ContentType.JSON) // GH-90000
                .schemaUri("urn:schema:user:1.0 [GH-90000]")
                .payload("hello".getBytes()); // GH-90000
    }

    @Nested
    @DisplayName("builder defaults [GH-90000]")
    class BuilderDefaults {

        @Test
        @DisplayName("eventTypeVersion defaults to 1.0.0 [GH-90000]")
        void defaultVersion() { // GH-90000
            IngestEvent event = validBuilder().build(); // GH-90000
            assertThat(event.eventTypeVersion()).isEqualTo("1.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("occurrenceTime defaults to now [GH-90000]")
        void defaultOccurrenceTime() { // GH-90000
            Instant before = Instant.now(); // GH-90000
            IngestEvent event = validBuilder().build(); // GH-90000
            Instant after = Instant.now(); // GH-90000
            assertThat(event.occurrenceTime()).isBetween(before, after); // GH-90000
        }

        @Test
        @DisplayName("headers defaults to empty map [GH-90000]")
        void defaultHeaders() { // GH-90000
            IngestEvent event = validBuilder().build(); // GH-90000
            assertThat(event.headers()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("correlationId defaults to empty [GH-90000]")
        void defaultCorrelationId() { // GH-90000
            IngestEvent event = validBuilder().build(); // GH-90000
            assertThat(event.correlationId()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("idempotencyKey defaults to empty [GH-90000]")
        void defaultIdempotencyKey() { // GH-90000
            IngestEvent event = validBuilder().build(); // GH-90000
            assertThat(event.idempotencyKey()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("partitionKey defaults to empty [GH-90000]")
        void defaultPartitionKey() { // GH-90000
            IngestEvent event = validBuilder().build(); // GH-90000
            assertThat(event.partitionKey()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder customization [GH-90000]")
    class BuilderCustomization {

        @Test
        @DisplayName("all fields set correctly [GH-90000]")
        void allFieldsSet() { // GH-90000
            Instant time = Instant.parse("2024-01-01T00:00:00Z [GH-90000]");
            IngestEvent event = IngestEvent.builder() // GH-90000
                    .tenantId(TENANT) // GH-90000
                    .eventTypeName("order.placed [GH-90000]")
                    .eventTypeVersion("2.0.0 [GH-90000]")
                    .occurrenceTime(time) // GH-90000
                    .headers(Map.of("source", "api")) // GH-90000
                    .contentType(ContentType.JSON) // GH-90000
                    .schemaUri("urn:schema:order:2.0 [GH-90000]")
                    .payload("order-data".getBytes()) // GH-90000
                    .partitionKey("order-123 [GH-90000]")
                    .build(); // GH-90000

            assertThat(event.tenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(event.eventTypeName()).isEqualTo("order.placed [GH-90000]");
            assertThat(event.eventTypeVersion()).isEqualTo("2.0.0 [GH-90000]");
            assertThat(event.occurrenceTime()).isEqualTo(time); // GH-90000
            assertThat(event.headers()).containsEntry("source", "api"); // GH-90000
            assertThat(event.contentType()).isEqualTo(ContentType.JSON); // GH-90000
            assertThat(event.schemaUri()).isEqualTo("urn:schema:order:2.0 [GH-90000]");
            assertThat(event.partitionKey()).contains("order-123 [GH-90000]");
        }

        @Test
        @DisplayName("payload(byte[]) wraps as ByteBuffer [GH-90000]")
        void payloadByteArray() { // GH-90000
            byte[] data = "hello".getBytes(); // GH-90000
            IngestEvent event = validBuilder().payload(data).build(); // GH-90000
            assertThat(event.payload()).isNotNull(); // GH-90000
            assertThat(event.payload().remaining()).isEqualTo(data.length); // GH-90000
        }

        @Test
        @DisplayName("payload(ByteBuffer) accepted [GH-90000]")
        void payloadByteBuffer() { // GH-90000
            ByteBuffer buf = ByteBuffer.wrap("test".getBytes()); // GH-90000
            IngestEvent event = validBuilder().payload(buf).build(); // GH-90000
            assertThat(event.payload()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("null validation [GH-90000]")
    class NullValidation {

        @Test
        @DisplayName("null tenantId throws NullPointerException [GH-90000]")
        void nullTenantId() { // GH-90000
            assertThatThrownBy(() -> validBuilder().tenantId(null).build()) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null eventTypeName throws NullPointerException [GH-90000]")
        void nullEventTypeName() { // GH-90000
            assertThatThrownBy(() -> validBuilder().eventTypeName(null).build()) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null contentType throws NullPointerException [GH-90000]")
        void nullContentType() { // GH-90000
            assertThatThrownBy(() -> validBuilder().contentType(null).build()) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null schemaUri throws NullPointerException [GH-90000]")
        void nullSchemaUri() { // GH-90000
            assertThatThrownBy(() -> validBuilder().schemaUri(null).build()) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null payload throws NullPointerException [GH-90000]")
        void nullPayload() { // GH-90000
            assertThatThrownBy(() -> validBuilder().payload((ByteBuffer) null).build()) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("immutability [GH-90000]")
    class Immutability {

        @Test
        @DisplayName("headers are defensively copied (immutable) [GH-90000]")
        void headersImmutable() { // GH-90000
            Map<String, String> mutable = new HashMap<>(); // GH-90000
            mutable.put("key", "value"); // GH-90000
            IngestEvent event = validBuilder().headers(mutable).build(); // GH-90000

            assertThatThrownBy(() -> event.headers().put("new", "val")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("payload is read-only after construction [GH-90000]")
        void payloadReadOnly() { // GH-90000
            IngestEvent event = validBuilder().build(); // GH-90000
            assertThat(event.payload().isReadOnly()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("equality [GH-90000]")
    class Equality {

        @Test
        @DisplayName("equal events are equal [GH-90000]")
        void equalEvents() { // GH-90000
            Instant time = Instant.parse("2024-06-01T12:00:00Z [GH-90000]");
            byte[] data = "same-data".getBytes(); // GH-90000

            IngestEvent e1 = IngestEvent.builder() // GH-90000
                    .tenantId(TENANT) // GH-90000
                    .eventTypeName("test [GH-90000]")
                    .eventTypeVersion("1.0.0 [GH-90000]")
                    .occurrenceTime(time) // GH-90000
                    .contentType(ContentType.JSON) // GH-90000
                    .schemaUri("urn:test [GH-90000]")
                    .payload(data) // GH-90000
                    .build(); // GH-90000

            IngestEvent e2 = IngestEvent.builder() // GH-90000
                    .tenantId(TENANT) // GH-90000
                    .eventTypeName("test [GH-90000]")
                    .eventTypeVersion("1.0.0 [GH-90000]")
                    .occurrenceTime(time) // GH-90000
                    .contentType(ContentType.JSON) // GH-90000
                    .schemaUri("urn:test [GH-90000]")
                    .payload(data) // GH-90000
                    .build(); // GH-90000

            assertThat(e1).isEqualTo(e2); // GH-90000
        }
    }
}
