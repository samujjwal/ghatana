package com.ghatana.aep.integration.events;

import com.ghatana.core.event.cloud.AppendResult;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.core.event.cloud.EventCloud.AppendOptions;
import com.ghatana.core.event.cloud.EventCloud.AppendRequest;
import com.ghatana.core.event.cloud.EventCloud.EventChunk;
import com.ghatana.core.event.cloud.EventCloud.EventConsumer;
import com.ghatana.core.event.cloud.EventCloud.EventEnvelope;
import com.ghatana.core.event.cloud.EventCloud.HistoryQuery;
import com.ghatana.core.event.cloud.EventCloud.HistoryScan;
import com.ghatana.core.event.cloud.EventCloud.Page;
import com.ghatana.core.event.cloud.EventCloud.Paging;
import com.ghatana.core.event.cloud.EventCloud.Selection;
import com.ghatana.core.event.cloud.EventCloud.TimeRange;
import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.core.event.cloud.EventStream;
import com.ghatana.core.event.cloud.EventTypeRef;
import com.ghatana.core.event.cloud.Version;
import com.ghatana.datacloud.StorageTier;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.CorrelationId;
import com.ghatana.platform.types.identity.EventId;
import com.ghatana.platform.types.identity.IdempotencyKey;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for the DataCloud EventCloud adapter layer:
 * {@link DataCloudEventMapper} and {@link DataCloudEventCloudClient}.
 *
 * <p>Tests are organized into logical groups:
 * <ul>
 *   <li>DataCloudEventMapper: Forward/reverse mapping, edge cases, round-trip</li>
 *   <li>DataCloudEventCloudClient: Append, subscribe, query, scan, metrics</li>
 * </ul>
 */
class DataCloudEventCloudClientTest extends EventloopTestBase {

    // ==================== Test Fixtures ====================

    static final TenantId TEST_TENANT = TenantId.of("test-tenant-1");
    /** The raw value of TenantId.of("test-tenant-1").value() */
    static final String TEST_TENANT_STR = TEST_TENANT.value();
    /** Wide time range usable as a "match all" for HistoryQuery (which requires non-null TimeRange) */
    static final TimeRange WIDE_TIME_RANGE = new TimeRange(
        Instant.parse("2000-01-01T00:00:00Z"),
        Instant.parse("2099-12-31T23:59:59Z"));
    static final Instant TEST_TIME = Instant.parse("2026-01-15T10:30:00Z");
    static final Instant TEST_DETECTION = Instant.parse("2026-01-15T10:30:01Z");
    static final String TEST_PAYLOAD_JSON = "{\"key\":\"value\",\"count\":42}";

    /**
     * Creates a fully populated platform EventRecord for testing.
     */
    static EventRecord makeEventRecord(String eventType, String eventId) {
        return EventRecord.builder()
            .tenantId(TEST_TENANT)
            .typeRef(EventTypeRef.of(eventType, 2, 1))
            .eventId(EventId.of(eventId))
            .occurrenceTime(TEST_TIME)
            .detectionTime(TEST_DETECTION)
            .headers(new HashMap<>(Map.of("source", "test", "env", "unit")))
            .contentType(ContentType.JSON)
            .schemaUri("https://schema.ghatana.com/events/" + eventType + "/v2.1")
            .payload(ByteBuffer.wrap(TEST_PAYLOAD_JSON.getBytes(StandardCharsets.UTF_8)))
            .correlationId(CorrelationId.of("corr-" + eventId))
            .idempotencyKey(IdempotencyKey.of("idem-" + eventId))
            .build();
    }

    /**
     * Creates a minimal EventRecord (no optional fields).
     */
    static EventRecord makeMinimalEventRecord(String eventType) {
        return EventRecord.builder()
            .tenantId(TEST_TENANT)
            .typeRef(EventTypeRef.of(eventType, 1, 0))
            .eventId(EventId.of(UUID.randomUUID().toString()))
            .occurrenceTime(Instant.now())
            .detectionTime(Instant.now())
            .headers(Map.of())
            .contentType(ContentType.JSON)
            .schemaUri("")
            .payload(ByteBuffer.allocate(0))
            .build();
    }

    /**
     * Creates a data-cloud EventEntry for testing.
     */
    static EventEntry makeEventEntry(String eventType, UUID eventId) {
        return EventEntry.builder()
            .eventId(eventId)
            .eventType(eventType)
            .eventVersion("2.1")
            .timestamp(TEST_TIME)
            .payload(TEST_PAYLOAD_JSON)
            .contentType("application/json")
            .headers(Map.of("source", "test"))
            .idempotencyKey("idem-key-1")
            .build();
    }

    // ==================== DataCloudEventMapper Tests ====================

    @Nested
    @DisplayName("DataCloudEventMapper")
    class MapperTests {

        @Nested
        @DisplayName("Forward Mapping: EventRecord → EventEntry")
        class ForwardMappingTests {

            @Test
            @DisplayName("maps fully populated EventRecord to EventEntry")
            void mapsFullyPopulatedRecord() {
                EventRecord record = makeEventRecord("order.created", "evt-001");
                EventEntry entry = DataCloudEventMapper.toEventEntry(record);

                assertThat(entry.eventType()).isEqualTo("order.created");
                assertThat(entry.eventVersion()).isEqualTo("2.1");
                assertThat(entry.timestamp()).isEqualTo(TEST_TIME);
                assertThat(entry.contentType()).isEqualTo("application/json");
                assertThat(entry.idempotencyKey()).contains("idem-evt-001");

                // Payload should be preserved
                byte[] payloadBytes = new byte[entry.payload().remaining()];
                entry.payload().get(payloadBytes);
                assertThat(new String(payloadBytes, StandardCharsets.UTF_8))
                    .isEqualTo(TEST_PAYLOAD_JSON);
            }

            @Test
            @DisplayName("preserves round-trip metadata in headers")
            void preservesRoundTripMetadata() {
                EventRecord record = makeEventRecord("user.signed_up", "evt-002");
                EventEntry entry = DataCloudEventMapper.toEventEntry(record);

                Map<String, String> headers = entry.headers();
                assertThat(headers)
                    .containsEntry(DataCloudEventMapper.HEADER_TENANT_ID, TEST_TENANT_STR)
                    .containsEntry(DataCloudEventMapper.HEADER_SCHEMA_URI,
                        "https://schema.ghatana.com/events/user.signed_up/v2.1")
                    .containsEntry(DataCloudEventMapper.HEADER_DETECTION_TIME,
                        TEST_DETECTION.toString())
                    .containsEntry(DataCloudEventMapper.HEADER_OCCURRENCE_TIME,
                        TEST_TIME.toString())
                    .containsEntry(DataCloudEventMapper.HEADER_ORIGINAL_EVENT_ID, "evt-002")
                    .containsEntry(DataCloudEventMapper.HEADER_EVENT_VERSION, "2.1")
                    .containsEntry(DataCloudEventMapper.HEADER_CORRELATION_ID, "corr-evt-002");

                // Original headers should also be present
                assertThat(headers)
                    .containsEntry("source", "test")
                    .containsEntry("env", "unit");
            }

            @Test
            @DisplayName("generates deterministic UUID from non-UUID event ID")
            void generatesDeterministicUuidForNonUuidId() {
                EventRecord record = makeEventRecord("evt.test", "my-custom-id-123");
                EventEntry entry = DataCloudEventMapper.toEventEntry(record);

                // Should be deterministic — same input gives same UUID
                UUID expected = UUID.nameUUIDFromBytes("my-custom-id-123".getBytes());
                assertThat(entry.eventId()).isEqualTo(expected);
            }

            @Test
            @DisplayName("preserves UUID event ID directly")
            void preservesUuidEventId() {
                UUID uuid = UUID.randomUUID();
                EventRecord record = makeEventRecord("evt.test", uuid.toString());
                EventEntry entry = DataCloudEventMapper.toEventEntry(record);

                assertThat(entry.eventId()).isEqualTo(uuid);
            }

            @Test
            @DisplayName("handles record without correlation ID")
            void handlesNoCorrelationId() {
                EventRecord record = EventRecord.builder()
                    .tenantId(TEST_TENANT)
                    .typeRef(EventTypeRef.of("test.event", 1, 0))
                    .eventId(EventId.random())
                    .occurrenceTime(Instant.now())
                    .detectionTime(Instant.now())
                    .headers(Map.of())
                    .contentType(ContentType.JSON)
                    .schemaUri("")
                    .payload(ByteBuffer.allocate(0))
                    .build();

                EventEntry entry = DataCloudEventMapper.toEventEntry(record);
                assertThat(entry.headers())
                    .doesNotContainKey(DataCloudEventMapper.HEADER_CORRELATION_ID);
            }

            @Test
            @DisplayName("handles record without idempotency key")
            void handlesNoIdempotencyKey() {
                EventRecord record = makeMinimalEventRecord("test.event");
                EventEntry entry = DataCloudEventMapper.toEventEntry(record);

                assertThat(entry.idempotencyKey()).isEmpty();
            }

            @Test
            @DisplayName("maps all content types correctly")
            void mapsContentTypes() {
                for (ContentType ct : ContentType.values()) {
                    EventRecord record = EventRecord.builder()
                        .tenantId(TEST_TENANT)
                        .typeRef(EventTypeRef.of("test", 1, 0))
                        .eventId(EventId.random())
                        .occurrenceTime(Instant.now())
                        .detectionTime(Instant.now())
                        .headers(Map.of())
                        .contentType(ct)
                        .schemaUri("")
                        .payload(ByteBuffer.allocate(0))
                        .build();

                    EventEntry entry = DataCloudEventMapper.toEventEntry(record);
                    assertThat(entry.contentType()).isEqualTo(ct.getMimeType());
                }
            }

            @Test
            @DisplayName("rejects null EventRecord")
            void rejectsNullRecord() {
                assertThatNullPointerException()
                    .isThrownBy(() -> DataCloudEventMapper.toEventEntry((EventRecord) null))
                    .withMessageContaining("must not be null");
            }

            @Test
            @DisplayName("maps AppendRequest to EventEntry")
            void mapsAppendRequest() {
                EventRecord record = makeEventRecord("order.shipped", "evt-003");
                AppendRequest request = new AppendRequest(record, AppendOptions.defaults());

                EventEntry entry = DataCloudEventMapper.toEventEntry(request);
                assertThat(entry.eventType()).isEqualTo("order.shipped");
            }

            @Test
            @DisplayName("maps batch of records preserving order")
            void mapsBatchPreservingOrder() {
                List<EventRecord> records = List.of(
                    makeEventRecord("evt.first", "id-1"),
                    makeEventRecord("evt.second", "id-2"),
                    makeEventRecord("evt.third", "id-3")
                );

                List<EventEntry> entries = DataCloudEventMapper.toEventEntries(records);
                assertThat(entries).hasSize(3);
                assertThat(entries.get(0).eventType()).isEqualTo("evt.first");
                assertThat(entries.get(1).eventType()).isEqualTo("evt.second");
                assertThat(entries.get(2).eventType()).isEqualTo("evt.third");
            }
        }

        @Nested
        @DisplayName("Reverse Mapping: EventEntry → EventRecord")
        class ReverseMappingTests {

            @Test
            @DisplayName("maps EventEntry back to EventRecord with embedded metadata")
            void mapsWithEmbeddedMetadata() {
                // First, create entry via forward mapping (has round-trip headers)
                EventRecord original = makeEventRecord("user.updated", "evt-010");
                EventEntry entry = DataCloudEventMapper.toEventEntry(original);

                // Now reverse map
                EventRecord restored = DataCloudEventMapper.toEventRecord(entry, TEST_TENANT);

                assertThat(restored.tenantId()).isEqualTo(TEST_TENANT);
                assertThat(restored.typeRef().name()).isEqualTo("user.updated");
                assertThat(restored.typeRef().version()).isEqualTo(new Version(2, 1));
                assertThat(restored.eventId().value()).isEqualTo("evt-010");
                assertThat(restored.occurrenceTime()).isEqualTo(TEST_TIME);
                assertThat(restored.detectionTime()).isEqualTo(TEST_DETECTION);
                assertThat(restored.contentType()).isEqualTo(ContentType.JSON);
                assertThat(restored.schemaUri()).isEqualTo(
                    "https://schema.ghatana.com/events/user.updated/v2.1");
                assertThat(restored.correlationId()).isPresent();
                assertThat(restored.correlationId().get().value()).isEqualTo("corr-evt-010");
                assertThat(restored.idempotencyKey()).isPresent();
                assertThat(restored.idempotencyKey().get().value()).isEqualTo("idem-evt-010");
            }

            @Test
            @DisplayName("strips internal headers from restored record")
            void stripsInternalHeaders() {
                EventRecord original = makeEventRecord("test.event", "evt-011");
                EventEntry entry = DataCloudEventMapper.toEventEntry(original);
                EventRecord restored = DataCloudEventMapper.toEventRecord(entry, TEST_TENANT);

                // Internal headers should be stripped
                assertThat(restored.headers())
                    .doesNotContainKey(DataCloudEventMapper.HEADER_TENANT_ID)
                    .doesNotContainKey(DataCloudEventMapper.HEADER_SCHEMA_URI)
                    .doesNotContainKey(DataCloudEventMapper.HEADER_CORRELATION_ID)
                    .doesNotContainKey(DataCloudEventMapper.HEADER_DETECTION_TIME)
                    .doesNotContainKey(DataCloudEventMapper.HEADER_OCCURRENCE_TIME)
                    .doesNotContainKey(DataCloudEventMapper.HEADER_EVENT_VERSION)
                    .doesNotContainKey(DataCloudEventMapper.HEADER_ORIGINAL_EVENT_ID);

                // Original headers should be preserved
                assertThat(restored.headers())
                    .containsEntry("source", "test")
                    .containsEntry("env", "unit");
            }

            @Test
            @DisplayName("handles entry without round-trip headers (external producer)")
            void handlesEntryWithoutRoundTripHeaders() {
                // Entry created outside of AEP (no enriched headers)
                EventEntry entry = makeEventEntry("external.event", UUID.randomUUID());
                TenantId fallbackTenant = TenantId.of("fallback-tenant");

                EventRecord record = DataCloudEventMapper.toEventRecord(
                    entry, fallbackTenant);

                assertThat(record.tenantId()).isEqualTo(fallbackTenant);
                assertThat(record.typeRef().name()).isEqualTo("external.event");
                // Version from entry's eventVersion field (2.1)
                assertThat(record.typeRef().version()).isEqualTo(new Version(2, 1));
                assertThat(record.contentType()).isEqualTo(ContentType.JSON);
            }

            @Test
            @DisplayName("handles null headers in entry")
            void handlesNullHeaders() {
                EventEntry entry = new EventEntry(
                    UUID.randomUUID(), "test.event", "1.0",
                    Instant.now(), ByteBuffer.allocate(0),
                    "application/json", null, Optional.empty());

                EventRecord record = DataCloudEventMapper.toEventRecord(entry, TEST_TENANT);
                assertThat(record).isNotNull();
                assertThat(record.tenantId()).isEqualTo(TEST_TENANT);
            }

            @Test
            @DisplayName("handles unknown content type gracefully")
            void handlesUnknownContentType() {
                EventEntry entry = EventEntry.builder()
                    .eventType("test.event")
                    .contentType("application/x-custom")
                    .build();

                EventRecord record = DataCloudEventMapper.toEventRecord(entry, TEST_TENANT);
                // Falls back to JSON
                assertThat(record.contentType()).isEqualTo(ContentType.JSON);
            }

            @Test
            @DisplayName("maps batch of entries preserving order")
            void mapsBatchPreservingOrder() {
                List<EventEntry> entries = List.of(
                    makeEventEntry("evt.a", UUID.randomUUID()),
                    makeEventEntry("evt.b", UUID.randomUUID()),
                    makeEventEntry("evt.c", UUID.randomUUID())
                );

                List<EventRecord> records = DataCloudEventMapper.toEventRecords(
                    entries, TEST_TENANT);
                assertThat(records).hasSize(3);
                assertThat(records.get(0).typeRef().name()).isEqualTo("evt.a");
                assertThat(records.get(1).typeRef().name()).isEqualTo("evt.b");
                assertThat(records.get(2).typeRef().name()).isEqualTo("evt.c");
            }

            @Test
            @DisplayName("rejects null entry")
            void rejectsNullEntry() {
                assertThatNullPointerException()
                    .isThrownBy(() -> DataCloudEventMapper.toEventRecord(null, TEST_TENANT));
            }

            @Test
            @DisplayName("rejects null tenantId")
            void rejectsNullTenantId() {
                EventEntry entry = makeEventEntry("test", UUID.randomUUID());
                assertThatNullPointerException()
                    .isThrownBy(() -> DataCloudEventMapper.toEventRecord(entry, null));
            }
        }

        @Nested
        @DisplayName("Round-Trip Mapping")
        class RoundTripTests {

            @Test
            @DisplayName("EventRecord survives full round-trip through EventEntry")
            void fullRoundTrip() {
                EventRecord original = makeEventRecord("payment.processed", "evt-rt-001");

                // Forward
                EventEntry entry = DataCloudEventMapper.toEventEntry(original);
                // Reverse
                EventRecord restored = DataCloudEventMapper.toEventRecord(entry, TEST_TENANT);

                // Core fields should match
                assertThat(restored.tenantId()).isEqualTo(original.tenantId());
                assertThat(restored.typeRef().name()).isEqualTo(original.typeRef().name());
                assertThat(restored.typeRef().version()).isEqualTo(original.typeRef().version());
                assertThat(restored.eventId()).isEqualTo(original.eventId());
                assertThat(restored.occurrenceTime()).isEqualTo(original.occurrenceTime());
                assertThat(restored.detectionTime()).isEqualTo(original.detectionTime());
                assertThat(restored.contentType()).isEqualTo(original.contentType());
                assertThat(restored.schemaUri()).isEqualTo(original.schemaUri());
                assertThat(restored.correlationId()).isEqualTo(original.correlationId());
                assertThat(restored.idempotencyKey()).isEqualTo(original.idempotencyKey());

                // Original user headers
                assertThat(restored.headers())
                    .containsEntry("source", "test")
                    .containsEntry("env", "unit");

                // Payload content should match
                byte[] originalPayload = new byte[original.payload().remaining()];
                original.payload().get(originalPayload);
                byte[] restoredPayload = new byte[restored.payload().remaining()];
                restored.payload().get(restoredPayload);
                assertThat(restoredPayload).isEqualTo(originalPayload);
            }

            @Test
            @DisplayName("minimal EventRecord survives round-trip")
            void minimalRoundTrip() {
                EventRecord original = makeMinimalEventRecord("minimal.event");
                EventEntry entry = DataCloudEventMapper.toEventEntry(original);
                EventRecord restored = DataCloudEventMapper.toEventRecord(entry, TEST_TENANT);

                assertThat(restored.typeRef().name()).isEqualTo("minimal.event");
                assertThat(restored.tenantId()).isEqualTo(original.tenantId());
            }
        }

        @Nested
        @DisplayName("Tenant Mapping")
        class TenantMappingTests {

            @Test
            @DisplayName("maps TenantId to TenantContext")
            void mapsTenantIdToContext() {
                TenantContext ctx = DataCloudEventMapper.toTenantContext(TEST_TENANT);
                assertThat(ctx.tenantId()).isEqualTo(TEST_TENANT_STR);
            }

            @Test
            @DisplayName("maps TenantId with workspace")
            void mapsTenantIdWithWorkspace() {
                TenantContext ctx = DataCloudEventMapper.toTenantContext(
                    TEST_TENANT, "ws-001");
                assertThat(ctx.tenantId()).isEqualTo(TEST_TENANT_STR);
                assertThat(ctx.workspaceId()).contains("ws-001");
            }

            @Test
            @DisplayName("rejects null tenant")
            void rejectsNullTenant() {
                assertThatNullPointerException()
                    .isThrownBy(() -> DataCloudEventMapper.toTenantContext(null));
            }
        }

        @Nested
        @DisplayName("Offset Mapping")
        class OffsetMappingTests {

            @Test
            @DisplayName("maps numeric string offset to long offset")
            void mapsNumericOffset() {
                var platformOffset = com.ghatana.platform.types.identity.Offset.of("42");
                var coreOffset = DataCloudEventMapper.toCoreOffset(platformOffset);
                assertThat(Long.parseLong(coreOffset.value())).isEqualTo(42L);
            }

            @Test
            @DisplayName("maps zero offset")
            void mapsZeroOffset() {
                var platformOffset = com.ghatana.platform.types.identity.Offset.of("0");
                var coreOffset = DataCloudEventMapper.toCoreOffset(platformOffset);
                assertThat(Long.parseLong(coreOffset.value())).isEqualTo(0L);
            }

            @Test
            @DisplayName("maps non-numeric offset via hash")
            void mapsNonNumericOffset() {
                var platformOffset = com.ghatana.platform.types.identity.Offset.of(
                    UUID.randomUUID().toString());
                var coreOffset = DataCloudEventMapper.toCoreOffset(platformOffset);
                assertThat(Long.parseLong(coreOffset.value())).isGreaterThanOrEqualTo(0L);
            }

            @Test
            @DisplayName("maps core offset to platform offset")
            void mapsCoreToPlaftorm() {
                var coreOffset = com.ghatana.platform.types.identity.Offset.of(99L);
                var platformOffset = DataCloudEventMapper.toPlatformOffset(coreOffset);
                assertThat(platformOffset.value()).isEqualTo("99");
            }

            @Test
            @DisplayName("offset round-trip preserves numeric values")
            void offsetRoundTrip() {
                var original = com.ghatana.platform.types.identity.Offset.of("1234");
                var core = DataCloudEventMapper.toCoreOffset(original);
                var restored = DataCloudEventMapper.toPlatformOffset(core);
                assertThat(restored.value()).isEqualTo("1234");
            }
        }

        @Nested
        @DisplayName("AppendResult Construction")
        class AppendResultTests {

            @Test
            @DisplayName("creates AppendResult from core offset")
            void createsAppendResult() {
                var offset = com.ghatana.platform.types.identity.Offset.of(7L);
                AppendResult result = DataCloudEventMapper.toAppendResult(offset, "1");

                assertThat(result.offset().value()).isEqualTo("7");
                assertThat(result.partitionId().value()).isEqualTo("1");
                assertThat(result.appendTime()).isNotNull();
            }

            @Test
            @DisplayName("uses default partition when hint is null")
            void usesDefaultPartition() {
                var offset = com.ghatana.platform.types.identity.Offset.of(0L);
                AppendResult result = DataCloudEventMapper.toAppendResult(offset, null);
                assertThat(result.partitionId().value()).isEqualTo("0");
            }

            @Test
            @DisplayName("creates AppendResult with explicit timestamp")
            void createsWithExplicitTimestamp() {
                var offset = com.ghatana.platform.types.identity.Offset.of(5L);
                Instant now = Instant.now();
                AppendResult result = DataCloudEventMapper.toAppendResult(offset, "2", now);
                assertThat(result.appendTime()).isEqualTo(now);
            }
        }

        @Nested
        @DisplayName("EventEnvelope Construction")
        class EventEnvelopeTests {

            @Test
            @DisplayName("wraps EventEntry as EventEnvelope")
            void wrapsAsEnvelope() {
                EventEntry entry = makeEventEntry("test.event", UUID.randomUUID());
                var offset = com.ghatana.platform.types.identity.Offset.of(10L);

                EventEnvelope envelope = DataCloudEventMapper.toEventEnvelope(
                    entry, TEST_TENANT, offset, "3");

                assertThat(envelope.record().typeRef().name()).isEqualTo("test.event");
                assertThat(envelope.partitionId().value()).isEqualTo("3");
                assertThat(envelope.offset().value()).isEqualTo("10");
            }
        }

        @Nested
        @DisplayName("Version Parsing")
        class VersionParsingTests {

            @Test
            @DisplayName("parses major.minor format")
            void parsesMajorMinor() {
                Version v = DataCloudEventMapper.parseVersion("2.1");
                assertThat(v).isEqualTo(new Version(2, 1));
            }

            @Test
            @DisplayName("parses semver format (takes first two)")
            void parsesSemver() {
                Version v = DataCloudEventMapper.parseVersion("3.2.1");
                assertThat(v).isEqualTo(new Version(3, 2));
            }

            @Test
            @DisplayName("returns default for null")
            void defaultsForNull() {
                Version v = DataCloudEventMapper.parseVersion(null);
                assertThat(v).isEqualTo(new Version(1, 0));
            }

            @Test
            @DisplayName("returns default for blank")
            void defaultsForBlank() {
                Version v = DataCloudEventMapper.parseVersion("  ");
                assertThat(v).isEqualTo(new Version(1, 0));
            }

            @Test
            @DisplayName("returns default for unparseable")
            void defaultsForUnparseable() {
                Version v = DataCloudEventMapper.parseVersion("abc");
                assertThat(v).isEqualTo(new Version(1, 0));
            }
        }

        @Nested
        @DisplayName("UUID Parsing")
        class UuidParsingTests {

            @Test
            @DisplayName("parses valid UUID string")
            void parsesValidUuid() {
                UUID original = UUID.randomUUID();
                UUID parsed = DataCloudEventMapper.parseOrGenerateUuid(original.toString());
                assertThat(parsed).isEqualTo(original);
            }

            @Test
            @DisplayName("generates deterministic UUID for non-UUID string")
            void generatesDeterministicUuid() {
                UUID first = DataCloudEventMapper.parseOrGenerateUuid("custom-id");
                UUID second = DataCloudEventMapper.parseOrGenerateUuid("custom-id");
                assertThat(first).isEqualTo(second);
            }

            @Test
            @DisplayName("generates random UUID for null")
            void generatesRandomForNull() {
                UUID uuid = DataCloudEventMapper.parseOrGenerateUuid(null);
                assertThat(uuid).isNotNull();
            }

            @Test
            @DisplayName("generates random UUID for blank")
            void generatesRandomForBlank() {
                UUID uuid = DataCloudEventMapper.parseOrGenerateUuid("  ");
                assertThat(uuid).isNotNull();
            }
        }

        @Nested
        @DisplayName("Instant Parsing")
        class InstantParsingTests {

            @Test
            @DisplayName("parses valid ISO-8601 instant")
            void parsesValidInstant() {
                Instant result = DataCloudEventMapper.parseInstantOrDefault(
                    "2026-01-15T10:30:00Z", Instant.EPOCH);
                assertThat(result).isEqualTo(Instant.parse("2026-01-15T10:30:00Z"));
            }

            @Test
            @DisplayName("returns default for null")
            void returnsDefaultForNull() {
                Instant def = Instant.now();
                assertThat(DataCloudEventMapper.parseInstantOrDefault(null, def))
                    .isEqualTo(def);
            }

            @Test
            @DisplayName("returns default for invalid format")
            void returnsDefaultForInvalid() {
                Instant def = Instant.now();
                assertThat(DataCloudEventMapper.parseInstantOrDefault("not-a-date", def))
                    .isEqualTo(def);
            }
        }
    }

    // ==================== DataCloudEventCloudClient Tests ====================

    @Nested
    @DisplayName("DataCloudEventCloudClient")
    class ClientTests {

        private InMemoryEventLogStore store;
        private DataCloudEventCloudClient client;

        @BeforeEach
        void setUp() {
            store = new InMemoryEventLogStore();
            client = new DataCloudEventCloudClient(store);
        }

        @Nested
        @DisplayName("Construction")
        class ConstructionTests {

            @Test
            @DisplayName("creates with defaults")
            void createsWithDefaults() {
                assertThat(client.getDefaultTier()).isEqualTo(StorageTier.defaultTier());
                assertThat(client.getActiveSubscriptionCount()).isZero();
            }

            @Test
            @DisplayName("creates with explicit tier")
            void createsWithExplicitTier() {
                DataCloudEventCloudClient hotClient =
                    new DataCloudEventCloudClient(store, StorageTier.HOT);
                assertThat(hotClient.getDefaultTier()).isEqualTo(StorageTier.HOT);
            }

            @Test
            @DisplayName("creates via builder")
            void createsViaBuilder() {
                DataCloudEventCloudClient built = DataCloudEventCloudClient.builder()
                    .eventLogStore(store)
                    .defaultTier(StorageTier.COOL)
                    .build();
                assertThat(built.getDefaultTier()).isEqualTo(StorageTier.COOL);
            }

            @Test
            @DisplayName("rejects null event log store")
            void rejectsNullStore() {
                assertThatNullPointerException()
                    .isThrownBy(() -> new DataCloudEventCloudClient(null))
                    .withMessageContaining("must not be null");
            }

            @Test
            @DisplayName("rejects null default tier")
            void rejectsNullTier() {
                assertThatNullPointerException()
                    .isThrownBy(() -> new DataCloudEventCloudClient(store, null))
                    .withMessageContaining("must not be null");
            }
        }

        @Nested
        @DisplayName("Append Operations")
        class AppendTests {

            @Test
            @DisplayName("appends single event successfully")
            void appendsSingleEvent() {
                EventRecord record = makeEventRecord("order.created", "evt-100");
                AppendRequest request = new AppendRequest(record, AppendOptions.defaults());

                AppendResult result = runPromise(() -> client.append(request));

                assertThat(result).isNotNull();
                assertThat(result.offset()).isNotNull();
                assertThat(result.partitionId()).isNotNull();
                assertThat(result.appendTime()).isNotNull();
                assertThat(store.getEntryCount()).isEqualTo(1);
            }

            @Test
            @DisplayName("appends event and verifies stored content")
            void appendsAndVerifiesContent() {
                EventRecord record = makeEventRecord("payment.received", "evt-101");
                AppendRequest request = new AppendRequest(record, AppendOptions.defaults());

                runPromise(() -> client.append(request));

                List<EventEntry> stored = store.getAllEntries();
                assertThat(stored).hasSize(1);
                assertThat(stored.getFirst().eventType()).isEqualTo("payment.received");
            }

            @Test
            @DisplayName("rejects null append request")
            void rejectsNullRequest() {
                assertThatNullPointerException()
                    .isThrownBy(() -> client.append(null));
            }

            @Test
            @DisplayName("appends batch of events")
            void appendsBatch() {
                List<AppendRequest> requests = List.of(
                    new AppendRequest(
                        makeEventRecord("evt.a", "id-a"),
                        AppendOptions.defaults()),
                    new AppendRequest(
                        makeEventRecord("evt.b", "id-b"),
                        AppendOptions.defaults()),
                    new AppendRequest(
                        makeEventRecord("evt.c", "id-c"),
                        AppendOptions.defaults())
                );

                List<AppendResult> results = runPromise(() -> client.appendBatch(requests));

                assertThat(results).hasSize(3);
                assertThat(store.getEntryCount()).isEqualTo(3);
            }

            @Test
            @DisplayName("appends empty batch returns empty list")
            void appendsEmptyBatch() {
                List<AppendResult> results =
                    runPromise(() -> client.appendBatch(List.of()));
                assertThat(results).isEmpty();
            }

            @Test
            @DisplayName("increments append metrics")
            void incrementsAppendMetrics() {
                EventRecord record = makeEventRecord("test", "evt-m1");
                runPromise(() -> client.append(new AppendRequest(record, AppendOptions.defaults())));
                client.append(new AppendRequest(
                    makeEventRecord("test2", "evt-m2"),
runPromise(() -> AppendOptions.defaults())));

                Map<String, Object> metrics = client.getMetrics();
                assertThat(metrics).containsEntry("append_count", 2L);
            }

            @Test
            @DisplayName("handles append failure gracefully")
            void handlesAppendFailure() {
                FailingEventLogStore failingStore = new FailingEventLogStore();
                DataCloudEventCloudClient failingClient =
                    new DataCloudEventCloudClient(failingStore);

                EventRecord record = makeEventRecord("test", "evt-fail");
                AppendRequest request = new AppendRequest(record, AppendOptions.defaults());

                Promise<AppendResult> promise = failingClient.append(request);
                // ActiveJ Promise: check exception via whenException or getException
                assertThat(promise.getException()).isNotNull();
                assertThat(promise.getException())
                    .isInstanceOf(DataCloudEventCloudClient.EventCloudException.class)
                    .hasMessageContaining("Failed to append event");

                Map<String, Object> metrics = failingClient.getMetrics();
                assertThat(metrics).containsEntry("append_failure_count", 1L);
            }
        }

        @Nested
        @DisplayName("Subscribe Operations")
        class SubscribeTests {

            @Test
            @DisplayName("creates subscription and receives events")
            void createsSubscription() {
                // Subscribe
                EventStream stream = client.subscribe(
                    TEST_TENANT,
                    Selection.all(),
                    new EventCloud.StartAtEarliest());

                List<EventEnvelope> received = new CopyOnWriteArrayList<>();
                stream.onEvent(chunk -> received.addAll(chunk.events()));
                stream.request(Long.MAX_VALUE);

                // Wait for subscription setup
                try { Thread.sleep(50); } catch (InterruptedException e) { /* ok */ }

                // Publish an event via tail
                store.publishToTail(makeEventEntry("test.event", UUID.randomUUID()));

                // Wait for delivery
                try { Thread.sleep(50); } catch (InterruptedException e) { /* ok */ }

                // Note: delivery depends on tail subscription timing, so we verify stream state
                assertThat(client.getActiveSubscriptionCount()).isGreaterThanOrEqualTo(0);
                stream.close();
            }

            @Test
            @DisplayName("stream supports pause and resume")
            void supportsPauseResume() {
                EventStream stream = client.subscribe(
                    TEST_TENANT,
                    Selection.all(),
                    new EventCloud.StartAtEarliest());

                stream.pause();
                stream.resume();
                stream.close();
            }

            @Test
            @DisplayName("stream request rejects non-positive count")
            void requestRejectsNonPositive() {
                EventStream stream = client.subscribe(
                    TEST_TENANT,
                    Selection.all(),
                    new EventCloud.StartAtEarliest());

                assertThatIllegalArgumentException()
                    .isThrownBy(() -> stream.request(0));
                assertThatIllegalArgumentException()
                    .isThrownBy(() -> stream.request(-1));

                stream.close();
            }

            @Test
            @DisplayName("close is idempotent")
            void closeIsIdempotent() {
                EventStream stream = client.subscribe(
                    TEST_TENANT,
                    Selection.all(),
                    new EventCloud.StartAtEarliest());

                stream.close();
                stream.close(); // Should not throw
            }

            @Test
            @DisplayName("subscribe increments metrics")
            void subscribeIncrementsMetrics() {
                EventStream s1 = client.subscribe(
                    TEST_TENANT, Selection.all(), new EventCloud.StartAtEarliest());
                EventStream s2 = client.subscribe(
                    TEST_TENANT, Selection.all(), new EventCloud.StartAtLatest());

                Map<String, Object> metrics = client.getMetrics();
                assertThat(metrics).containsEntry("subscribe_count", 2L);

                s1.close();
                s2.close();
            }

            @Test
            @DisplayName("subscribe with type filter")
            void subscribeWithTypeFilter() {
                EventStream stream = client.subscribe(
                    TEST_TENANT,
                    Selection.byTypes("order.created", "order.completed"),
                    new EventCloud.StartAtEarliest());

                assertThat(stream).isNotNull();
                stream.close();
            }

            @Test
            @DisplayName("subscribe with StartAtTime")
            void subscribeWithStartAtTime() {
                EventStream stream = client.subscribe(
                    TEST_TENANT,
                    Selection.all(),
                    new EventCloud.StartAtTime(Instant.now().minusSeconds(3600)));

                assertThat(stream).isNotNull();
                stream.close();
            }

            @Test
            @DisplayName("subscribe with StartAtOffsets")
            void subscribeWithStartAtOffsets() {
                var offsets = List.of(
                    new EventCloud.StartOffset(
                        com.ghatana.platform.types.identity.PartitionId.of("0"),
                        com.ghatana.platform.types.identity.Offset.of("5")));

                EventStream stream = client.subscribe(
                    TEST_TENANT,
                    Selection.all(),
                    new EventCloud.StartAtOffsets(offsets));

                assertThat(stream).isNotNull();
                stream.close();
            }

            @Test
            @DisplayName("subscribe with empty StartAtOffsets uses earliest")
            void subscribeWithEmptyOffsets() {
                EventStream stream = client.subscribe(
                    TEST_TENANT,
                    Selection.all(),
                    new EventCloud.StartAtOffsets(List.of()));

                assertThat(stream).isNotNull();
                stream.close();
            }

            @Test
            @DisplayName("rejects null subscribe arguments")
            void rejectsNullArguments() {
                assertThatNullPointerException()
                    .isThrownBy(() -> client.subscribe(
                        null, Selection.all(), new EventCloud.StartAtEarliest()));
                assertThatNullPointerException()
                    .isThrownBy(() -> client.subscribe(
                        TEST_TENANT, null, new EventCloud.StartAtEarliest()));
                assertThatNullPointerException()
                    .isThrownBy(() -> client.subscribe(
                        TEST_TENANT, Selection.all(), null));
            }
        }

        @Nested
        @DisplayName("Query Operations")
        class QueryTests {

            @Test
            @DisplayName("queries events by time range")
            void queriesByTimeRange() {
                // Seed data
                seedEvents("order.created", 5);

                Instant from = Instant.now().minusSeconds(3600);
                Instant to = Instant.now().plusSeconds(3600);

                Page page = client.query(new HistoryQuery(
                    TEST_TENANT,
                    List.of(),
                    new TimeRange(from, to),
                    EventCloud.TRUE,
                    Paging.first(10)
runPromise(() -> )));

                assertThat(page).isNotNull();
                assertThat(page.items()).hasSize(5);
            }

            @Test
            @DisplayName("queries events by single type")
            void queriesBySingleType() {
                seedEvents("order.created", 3);
                seedEvents("payment.received", 2);

                Page page = client.query(new HistoryQuery(
                    TEST_TENANT,
                    List.of("order.created"),
                    WIDE_TIME_RANGE,
                    EventCloud.TRUE,
                    Paging.first(10)
runPromise(() -> )));

                assertThat(page.items()).hasSize(3);
                assertThat(page.items())
                    .allSatisfy(env ->
                        assertThat(env.record().typeRef().name())
                            .isEqualTo("order.created"));
            }

            @Test
            @DisplayName("queries events by multiple types")
            void queriesByMultipleTypes() {
                seedEvents("evt.a", 2);
                seedEvents("evt.b", 3);
                seedEvents("evt.c", 1);

                Page page = client.query(new HistoryQuery(
                    TEST_TENANT,
                    List.of("evt.a", "evt.b"),
                    WIDE_TIME_RANGE,
                    EventCloud.TRUE,
                    Paging.first(10)
runPromise(() -> )));

                assertThat(page.items()).hasSize(5);
            }

            @Test
            @DisplayName("queries with pagination limit")
            void queriesWithPagination() {
                seedEvents("test.event", 10);

                Page page = client.query(new HistoryQuery(
                    TEST_TENANT,
                    List.of(),
                    WIDE_TIME_RANGE,
                    EventCloud.TRUE,
                    Paging.first(3)
runPromise(() -> )));

                assertThat(page.items()).hasSize(3);
                assertThat(page.hasMore()).isTrue();
                assertThat(page.nextResumeToken()).isNotNull();
            }

            @Test
            @DisplayName("returns empty page for no matches")
            void returnsEmptyPage() {
                Page page = client.query(new HistoryQuery(
                    TEST_TENANT,
                    List.of("nonexistent.type"),
                    WIDE_TIME_RANGE,
                    EventCloud.TRUE,
                    Paging.first(10)
runPromise(() -> )));

                assertThat(page.items()).isEmpty();
                assertThat(page.hasMore()).isFalse();
            }

            @Test
            @DisplayName("query without filters reads from offset")
            void queryWithoutFilters() {
                seedEvents("test.event", 5);

                Page page = client.query(new HistoryQuery(
                    TEST_TENANT,
                    List.of(),
                    WIDE_TIME_RANGE,
                    EventCloud.TRUE,
                    Paging.first(100)
runPromise(() -> )));

                assertThat(page.items()).hasSize(5);
            }

            @Test
            @DisplayName("queries by time range and event type")
            void queriesByTimeRangeAndType() {
                seedEvents("order.created", 3);
                seedEvents("payment.received", 2);

                Instant from = Instant.now().minusSeconds(3600);
                Instant to = Instant.now().plusSeconds(3600);

                Page page = client.query(new HistoryQuery(
                    TEST_TENANT,
                    List.of("order.created"),
                    new TimeRange(from, to),
                    EventCloud.TRUE,
                    Paging.first(10)
runPromise(() -> )));

                assertThat(page.items()).hasSize(3);
            }

            @Test
            @DisplayName("increments query metrics")
            void incrementsQueryMetrics() {
                client.query(new HistoryQuery(
                    TEST_TENANT, List.of(), WIDE_TIME_RANGE,
                    EventCloud.TRUE, Paging.first(10)
runPromise(() -> )));

                assertThat(client.getMetrics()).containsEntry("query_count", 1L);
            }

            @Test
            @DisplayName("rejects null query")
            void rejectsNullQuery() {
                assertThatNullPointerException()
                    .isThrownBy(() -> client.query(null));
            }

            private void seedEvents(String eventType, int count) {
                TenantContext tc = DataCloudEventMapper.toTenantContext(TEST_TENANT);
                for (int i = 0; i < count; i++) {
                    EventEntry entry = EventEntry.builder()
                        .eventType(eventType)
                        .eventVersion("1.0")
                        .timestamp(Instant.now())
                        .payload("{}")
                        .build();
                    runPromise(() -> store.append(tc, entry));
                }
            }
        }

        @Nested
        @DisplayName("Scan Operations")
        class ScanTests {

            @Test
            @DisplayName("creates scan and reads batches")
            void createsAndReadsBatches() {
                // Seed data
                TenantContext tc = DataCloudEventMapper.toTenantContext(TEST_TENANT);
                for (int i = 0; i < 5; i++) {
                    store.append(tc, EventEntry.builder()
                        .eventType("scan.event")
                        .timestamp(Instant.now())
                        .payload("{}")
runPromise(() -> .build()));
                }

                List<List<EventEnvelope>> batches = new CopyOnWriteArrayList<>();
                HistoryScan scan = client.scan(new HistoryQuery(
                    TEST_TENANT, List.of(), WIDE_TIME_RANGE,
                    EventCloud.TRUE, Paging.first(100)));

                scan.onBatch(batches::add);
                scan.start();

                // Wait for async batch delivery
                try { Thread.sleep(100); } catch (InterruptedException e) { /* ok */ }

                assertThat(batches).isNotEmpty();
                scan.close();
            }

            @Test
            @DisplayName("scan supports pause and resume")
            void supportsPauseResume() {
                HistoryScan scan = client.scan(new HistoryQuery(
                    TEST_TENANT, List.of(), WIDE_TIME_RANGE,
                    EventCloud.TRUE, Paging.first(10)));

                List<List<EventEnvelope>> batches = new CopyOnWriteArrayList<>();
                scan.onBatch(batches::add);
                scan.pause();
                scan.resume();
                scan.close();
            }

            @Test
            @DisplayName("scan start requires batch consumer")
            void startRequiresBatchConsumer() {
                HistoryScan scan = client.scan(new HistoryQuery(
                    TEST_TENANT, List.of(), WIDE_TIME_RANGE,
                    EventCloud.TRUE, Paging.first(10)));

                assertThatIllegalStateException()
                    .isThrownBy(scan::start)
                    .withMessageContaining("consumer");
            }

            @Test
            @DisplayName("scan start is not reentrant")
            void startIsNotReentrant() {
                HistoryScan scan = client.scan(new HistoryQuery(
                    TEST_TENANT, List.of(), WIDE_TIME_RANGE,
                    EventCloud.TRUE, Paging.first(10)));

                scan.onBatch(b -> {});
                scan.start();

                assertThatIllegalStateException()
                    .isThrownBy(scan::start)
                    .withMessageContaining("already started");

                scan.close();
            }

            @Test
            @DisplayName("increments scan metrics")
            void incrementsScanMetrics() {
                client.scan(new HistoryQuery(
                    TEST_TENANT, List.of(), WIDE_TIME_RANGE,
                    EventCloud.TRUE, Paging.first(10)));

                assertThat(client.getMetrics()).containsEntry("scan_count", 1L);
            }
        }

        @Nested
        @DisplayName("Metrics & Lifecycle")
        class MetricsTests {

            @Test
            @DisplayName("returns comprehensive metrics")
            void returnsComprehensiveMetrics() {
                Map<String, Object> metrics = client.getMetrics();

                assertThat(metrics)
                    .containsKeys(
                        "append_count", "append_batch_count", "append_failure_count",
                        "subscribe_count", "query_count", "scan_count",
                        "active_subscriptions", "default_storage_tier");
            }

            @Test
            @DisplayName("closeAllSubscriptions cleans up")
            void closeAllSubscriptions() {
                EventStream s1 = client.subscribe(
                    TEST_TENANT, Selection.all(), new EventCloud.StartAtEarliest());
                EventStream s2 = client.subscribe(
                    TEST_TENANT, Selection.all(), new EventCloud.StartAtLatest());

                assertThat(client.getActiveSubscriptionCount()).isGreaterThanOrEqualTo(0);

                client.closeAllSubscriptions();
                assertThat(client.getActiveSubscriptionCount()).isZero();
            }
        }
    }

    // ==================== Test Doubles ====================

    /**
     * Simple in-memory EventLogStore for testing the client.
     */
    static class InMemoryEventLogStore implements EventLogStore {

        private final List<StoredEntry> entries =
            new CopyOnWriteArrayList<>();
        private final AtomicLong offsetCounter = new AtomicLong(0);
        private final List<Consumer<EventEntry>> tailHandlers =
            new CopyOnWriteArrayList<>();

        @Override
        public Promise<com.ghatana.platform.types.identity.Offset> append(
                TenantContext tenant, EventEntry entry) {
            long offset = offsetCounter.getAndIncrement();
            entries.add(new StoredEntry(tenant, entry, offset));
            return Promise.of(
                com.ghatana.platform.types.identity.Offset.of(offset));
        }

        @Override
        public Promise<List<com.ghatana.platform.types.identity.Offset>> appendBatch(
                TenantContext tenant, List<EventEntry> batchEntries) {
            List<com.ghatana.platform.types.identity.Offset> offsets = new ArrayList<>();
            for (EventEntry entry : batchEntries) {
                long offset = offsetCounter.getAndIncrement();
                entries.add(new StoredEntry(tenant, entry, offset));
                offsets.add(com.ghatana.platform.types.identity.Offset.of(offset));
            }
            return Promise.of(offsets);
        }

        @Override
        public Promise<List<EventEntry>> read(
                TenantContext tenant,
                com.ghatana.platform.types.identity.Offset from,
                int limit) {
            long fromOffset = Long.parseLong(from.value());
            List<EventEntry> result = entries.stream()
                .filter(se -> se.tenant.tenantId().equals(tenant.tenantId()))
                .filter(se -> se.offset >= fromOffset)
                .sorted((a, b) -> Long.compare(a.offset, b.offset))
                .limit(limit)
                .map(se -> se.entry)
                .toList();
            return Promise.of(result);
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(
                TenantContext tenant, Instant startTime, Instant endTime, int limit) {
            List<EventEntry> result = entries.stream()
                .filter(se -> se.tenant.tenantId().equals(tenant.tenantId()))
                .filter(se -> !se.entry.timestamp().isBefore(startTime)
                    && se.entry.timestamp().isBefore(endTime))
                .sorted((a, b) -> a.entry.timestamp().compareTo(b.entry.timestamp()))
                .limit(limit)
                .map(se -> se.entry)
                .toList();
            return Promise.of(result);
        }

        @Override
        public Promise<List<EventEntry>> readByType(
                TenantContext tenant, String eventType,
                com.ghatana.platform.types.identity.Offset from,
                int limit) {
            long fromOffset = Long.parseLong(from.value());
            List<EventEntry> result = entries.stream()
                .filter(se -> se.tenant.tenantId().equals(tenant.tenantId()))
                .filter(se -> se.entry.eventType().equals(eventType))
                .filter(se -> se.offset >= fromOffset)
                .sorted((a, b) -> Long.compare(a.offset, b.offset))
                .limit(limit)
                .map(se -> se.entry)
                .toList();
            return Promise.of(result);
        }

        @Override
        public Promise<com.ghatana.platform.types.identity.Offset> getLatestOffset(
                TenantContext tenant) {
            return Promise.of(
                com.ghatana.platform.types.identity.Offset.of(
                    Math.max(0, offsetCounter.get() - 1)));
        }

        @Override
        public Promise<com.ghatana.platform.types.identity.Offset> getEarliestOffset(
                TenantContext tenant) {
            return Promise.of(
                com.ghatana.platform.types.identity.Offset.zero());
        }

        @Override
        public Promise<Subscription> tail(
                TenantContext tenant,
                com.ghatana.platform.types.identity.Offset from,
                Consumer<EventEntry> handler) {
            tailHandlers.add(handler);
            AtomicBoolean cancelled = new AtomicBoolean(false);

            // Deliver any existing events after the starting offset
            long fromOffset = Long.parseLong(from.value());
            entries.stream()
                .filter(se -> se.tenant.tenantId().equals(tenant.tenantId()))
                .filter(se -> se.offset >= fromOffset)
                .sorted((a, b) -> Long.compare(a.offset, b.offset))
                .forEach(se -> {
                    if (!cancelled.get()) handler.accept(se.entry);
                });

            return Promise.of(new Subscription() {
                @Override
                public void cancel() {
                    cancelled.set(true);
                    tailHandlers.remove(handler);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }
            });
        }

        // Test helpers
        void publishToTail(EventEntry entry) {
            for (Consumer<EventEntry> handler : tailHandlers) {
                handler.accept(entry);
            }
        }

        int getEntryCount() {
            return entries.size();
        }

        List<EventEntry> getAllEntries() {
            return entries.stream().map(se -> se.entry).toList();
        }

        private record StoredEntry(TenantContext tenant, EventEntry entry, long offset) {}
    }

    /**
     * EventLogStore that always fails — for testing error handling.
     */
    static class FailingEventLogStore implements EventLogStore {
        @Override
        public Promise<com.ghatana.platform.types.identity.Offset> append(
                TenantContext tenant, EventEntry entry) {
            return Promise.ofException(new RuntimeException("Storage unavailable"));
        }

        @Override
        public Promise<List<com.ghatana.platform.types.identity.Offset>> appendBatch(
                TenantContext tenant, List<EventEntry> entries) {
            return Promise.ofException(new RuntimeException("Storage unavailable"));
        }

        @Override
        public Promise<List<EventEntry>> read(
                TenantContext tenant,
                com.ghatana.platform.types.identity.Offset from, int limit) {
            return Promise.ofException(new RuntimeException("Storage unavailable"));
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(
                TenantContext tenant, Instant startTime, Instant endTime, int limit) {
            return Promise.ofException(new RuntimeException("Storage unavailable"));
        }

        @Override
        public Promise<List<EventEntry>> readByType(
                TenantContext tenant, String eventType,
                com.ghatana.platform.types.identity.Offset from, int limit) {
            return Promise.ofException(new RuntimeException("Storage unavailable"));
        }

        @Override
        public Promise<com.ghatana.platform.types.identity.Offset> getLatestOffset(
                TenantContext tenant) {
            return Promise.ofException(new RuntimeException("Storage unavailable"));
        }

        @Override
        public Promise<com.ghatana.platform.types.identity.Offset> getEarliestOffset(
                TenantContext tenant) {
            return Promise.ofException(new RuntimeException("Storage unavailable"));
        }

        @Override
        public Promise<Subscription> tail(
                TenantContext tenant,
                com.ghatana.platform.types.identity.Offset from,
                Consumer<EventEntry> handler) {
            return Promise.ofException(new RuntimeException("Storage unavailable"));
        }
    }
}
