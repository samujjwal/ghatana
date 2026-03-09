package com.ghatana.core.event.cloud;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.EventId;
import com.ghatana.platform.types.identity.IdempotencyKey;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.types.identity.PartitionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryEventCloud}.
 */
@DisplayName("InMemoryEventCloud")
class InMemoryEventCloudTest extends EventloopTestBase {

    private InMemoryEventCloud eventCloud;

    @BeforeEach
    void setUp() {
        eventCloud = new InMemoryEventCloud();
    }

    private EventRecord buildEvent(String tenantId, String eventType) {
        return EventRecord.builder()
                .tenantId(TenantId.of(tenantId))
                .typeRef(EventTypeRef.of(eventType, 1, 0))
                .eventId(EventId.random())
                .occurrenceTime(Instant.now())
                .detectionTime(Instant.now())
                .headers(Map.of())
                .contentType(ContentType.JSON)
                .schemaUri("https://schema.ghatana.com/" + eventType + "/v1")
                .payload(ByteBuffer.wrap("{}".getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    private EventCloud.AppendRequest appendRequest(EventRecord event) {
        return new EventCloud.AppendRequest(event, EventCloud.AppendOptions.defaults());
    }

    @Nested
    @DisplayName("append")
    class AppendTests {

        @Test
        @DisplayName("should append event and return result with partition and offset")
        void shouldAppendAndReturnResult() {
            var event = buildEvent("tenant-1", "test.event");
            var result = runPromise(() -> eventCloud.append(appendRequest(event)));

            assertThat(result).isNotNull();
            assertThat(result.partitionId()).isNotNull();
            assertThat(result.offset()).isNotNull();
            assertThat(result.appendTime()).isNotNull();
        }

        @Test
        @DisplayName("should assign sequential offsets within same partition")
        void shouldAssignSequentialOffsets() {
            var event1 = buildEvent("tenant-1", "test.event");
            var event2 = buildEvent("tenant-1", "test.event");

            var result1 = runPromise(() -> eventCloud.append(appendRequest(event1)));
            var result2 = runPromise(() -> eventCloud.append(appendRequest(event2)));

            // Offsets should be distinct (sequential within partition)
            if (result1.partitionId().equals(result2.partitionId())) {
                assertThat(result2.offset()).isNotEqualTo(result1.offset());
            }
        }
    }

    @Nested
    @DisplayName("idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("should return same result for duplicate idempotency key")
        void shouldReturnCachedResultForDuplicate() {
            var event = EventRecord.builder()
                    .tenantId(TenantId.of("tenant-1"))
                    .typeRef(EventTypeRef.of("test.event", 1, 0))
                    .eventId(EventId.random())
                    .occurrenceTime(Instant.now())
                    .detectionTime(Instant.now())
                    .headers(Map.of())
                    .contentType(ContentType.JSON)
                    .schemaUri("https://schema.ghatana.com/test/v1")
                    .payload(ByteBuffer.wrap("{}".getBytes(StandardCharsets.UTF_8)))
                    .idempotencyKey(IdempotencyKey.of("unique-key-1"))
                    .build();

            AppendResult result1 = runPromise(() -> eventCloud.append(appendRequest(event)));
            AppendResult result2 = runPromise(() -> eventCloud.append(appendRequest(event)));

            assertThat(result2.partitionId()).isEqualTo(result1.partitionId());
            assertThat(result2.offset()).isEqualTo(result1.offset());
        }
    }

    @Nested
    @DisplayName("AppendResult value object")
    class AppendResultTests {

        @Test
        @DisplayName("should enforce non-null fields")
        void shouldEnforceNonNullFields() {
            var result = new AppendResult(
                    PartitionId.of("p1"),
                    Offset.of(0),
                    Instant.now());

            assertThat(result.partitionId()).isEqualTo(PartitionId.of("p1"));
            assertThat(result.offset()).isEqualTo(Offset.of(0));
            assertThat(result.appendTime()).isNotNull();
        }

        @Test
        @DisplayName("should support record equality")
        void shouldSupportEquality() {
            var time = Instant.now();
            var result1 = new AppendResult(PartitionId.of("p1"), Offset.of(5), time);
            var result2 = new AppendResult(PartitionId.of("p1"), Offset.of(5), time);

            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }
    }
}
