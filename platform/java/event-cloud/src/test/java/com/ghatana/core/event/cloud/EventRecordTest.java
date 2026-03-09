package com.ghatana.core.event.cloud;

import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.CorrelationId;
import com.ghatana.platform.types.identity.EventId;
import com.ghatana.platform.types.identity.IdempotencyKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link EventRecord} value object and its builder.
 */
@DisplayName("EventRecord")
class EventRecordTest {

    private static final TenantId TENANT = TenantId.of("test-tenant");
    private static final EventTypeRef TYPE_REF = EventTypeRef.of("order.created", 1, 0);
    private static final ContentType CONTENT_TYPE = ContentType.JSON;
    private static final String SCHEMA_URI = "https://schema.ghatana.com/order.created/v1";

    private EventRecord.Builder minimalBuilder() {
        return EventRecord.builder()
                .tenantId(TENANT)
                .typeRef(TYPE_REF)
                .eventId(EventId.random())
                .occurrenceTime(Instant.now())
                .detectionTime(Instant.now())
                .contentType(CONTENT_TYPE)
                .schemaUri(SCHEMA_URI)
                .payload(ByteBuffer.wrap("{\"orderId\":\"123\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        void shouldBuildMinimalEventRecord() {
            EventRecord record = minimalBuilder().build();
            assertThat(record.tenantId()).isEqualTo(TENANT);
            assertThat(record.typeRef()).isEqualTo(TYPE_REF);
            assertThat(record.eventId()).isNotNull();
            assertThat(record.contentType()).isEqualTo(CONTENT_TYPE);
            assertThat(record.schemaUri()).isEqualTo(SCHEMA_URI);
            assertThat(record.correlationId()).isEmpty();
            assertThat(record.idempotencyKey()).isEmpty();
        }

        @Test
        void shouldBuildWithOptionalFields() {
            EventRecord record = minimalBuilder()
                    .correlationId(CorrelationId.random())
                    .idempotencyKey(IdempotencyKey.of("key-1"))
                    .headers(Map.of("source", "mobile-app"))
                    .build();

            assertThat(record.correlationId()).isPresent();
            assertThat(record.idempotencyKey()).isPresent();
            assertThat(record.headers()).containsEntry("source", "mobile-app");
        }

        @Test
        void shouldDefaultToEmptyHeaders() {
            EventRecord record = minimalBuilder().build();
            assertThat(record.headers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void shouldRejectNullTenantId() {
            assertThatThrownBy(() -> minimalBuilder().tenantId(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tenantId");
        }

        @Test
        void shouldRejectNullTypeRef() {
            assertThatThrownBy(() -> minimalBuilder().typeRef(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("typeRef");
        }

        @Test
        void shouldRejectNullEventId() {
            assertThatThrownBy(() -> minimalBuilder().eventId(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("eventId");
        }

        @Test
        void shouldRejectNullOccurrenceTime() {
            assertThatThrownBy(() -> minimalBuilder().occurrenceTime(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("occurrenceTime");
        }

        @Test
        void shouldRejectNullPayload() {
            assertThatThrownBy(() -> minimalBuilder().payload(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("payload");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        void shouldReturnReadOnlyPayload() {
            EventRecord record = minimalBuilder().build();
            ByteBuffer payload = record.payload();
            assertThat(payload.isReadOnly()).isTrue();
        }

        @Test
        void shouldReturnImmutableHeaders() {
            EventRecord record = minimalBuilder()
                    .headers(Map.of("key", "value"))
                    .build();
            assertThatThrownBy(() -> record.headers().put("newKey", "newValue"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        void shouldBeEqualForSameData() {
            Instant now = Instant.now();
            EventId eventId = EventId.random();
            ByteBuffer payload = ByteBuffer.wrap("test".getBytes(StandardCharsets.UTF_8));

            EventRecord a = EventRecord.builder()
                    .tenantId(TENANT).typeRef(TYPE_REF).eventId(eventId)
                    .occurrenceTime(now).detectionTime(now)
                    .contentType(CONTENT_TYPE).schemaUri(SCHEMA_URI)
                    .payload(payload.duplicate())
                    .build();

            EventRecord b = EventRecord.builder()
                    .tenantId(TENANT).typeRef(TYPE_REF).eventId(eventId)
                    .occurrenceTime(now).detectionTime(now)
                    .contentType(CONTENT_TYPE).schemaUri(SCHEMA_URI)
                    .payload(payload.duplicate())
                    .build();

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }
}
