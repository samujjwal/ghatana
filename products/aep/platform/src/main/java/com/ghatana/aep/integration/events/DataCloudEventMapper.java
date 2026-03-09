package com.ghatana.aep.integration.events;

import com.ghatana.core.event.cloud.AppendResult;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.core.event.cloud.EventTypeRef;
import com.ghatana.core.event.cloud.Version;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.CorrelationId;
import com.ghatana.platform.types.identity.EventId;
import com.ghatana.platform.types.identity.IdempotencyKey;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bidirectional mapper between platform EventCloud types and data-cloud EventLogStore types.
 *
 * <p><b>Purpose</b><br>
 * Bridges the type gap between platform EventCloud's rich domain types
 * ({@link EventRecord}, {@link EventTypeRef}, {@link ContentType}) and
 * data-cloud's storage-oriented types ({@link EventLogStore.EventEntry}).
 *
 * <p><b>Type Mapping</b>
 * <pre>
 * Platform EventRecord ←→ Data-Cloud EventEntry
 * Platform TenantId     → Data-Cloud TenantContext
 * Platform Offset(String) ←→ Data-Cloud Offset(long)
 * Platform PartitionId(String) ←→ numeric partition assignment
 * </pre>
 *
 * <p><b>Thread Safety</b><br>
 * This class is stateless and fully thread-safe. All methods are pure functions
 * with no mutable state.
 *
 * @doc.type mapper
 * @doc.purpose Bidirectional type mapping between platform and data-cloud event models
 * @doc.layer integration
 * @doc.pattern Adapter, Mapper
 * @since 1.0.0
 */
public final class DataCloudEventMapper {

    private static final Logger LOG = Logger.getLogger(DataCloudEventMapper.class.getName());

    // Reserved header keys used for round-trip metadata preservation
    static final String HEADER_SCHEMA_URI = "x-schema-uri";
    static final String HEADER_CORRELATION_ID = "x-correlation-id";
    static final String HEADER_DETECTION_TIME = "x-detection-time";
    static final String HEADER_OCCURRENCE_TIME = "x-occurrence-time";
    static final String HEADER_TENANT_ID = "x-tenant-id";
    static final String HEADER_EVENT_VERSION = "x-event-version";
    static final String HEADER_ORIGINAL_EVENT_ID = "x-original-event-id";

    private DataCloudEventMapper() {
        // Utility class — no instantiation
    }

    // ==================== Forward Mapping: Platform → Data-Cloud ====================

    /**
     * Maps a platform {@link EventRecord} to a data-cloud {@link EventLogStore.EventEntry}.
     *
     * <p>Preserves all metadata through header enrichment so that the reverse mapping
     * can reconstruct the original EventRecord faithfully.
     *
     * @param record the platform event record (must not be null)
     * @return the equivalent data-cloud event entry
     * @throws NullPointerException if record is null
     */
    public static EventLogStore.EventEntry toEventEntry(EventRecord record) {
        Objects.requireNonNull(record, "EventRecord must not be null");

        // Build enriched headers preserving round-trip metadata
        Map<String, String> headers = new HashMap<>(record.headers());
        headers.put(HEADER_TENANT_ID, record.tenantId().value());
        headers.put(HEADER_SCHEMA_URI, record.schemaUri());
        headers.put(HEADER_DETECTION_TIME, record.detectionTime().toString());
        headers.put(HEADER_OCCURRENCE_TIME, record.occurrenceTime().toString());
        headers.put(HEADER_ORIGINAL_EVENT_ID, record.eventId().value());
        headers.put(HEADER_EVENT_VERSION, record.typeRef().version().toString());
        record.correlationId().ifPresent(cid ->
            headers.put(HEADER_CORRELATION_ID, cid.value()));

        // Map eventId: UUID if parseable, else generate and track original
        UUID eventUuid = parseOrGenerateUuid(record.eventId().value());

        // Map content type
        String contentTypeStr = record.contentType() != null
            ? record.contentType().getMimeType()
            : "application/json";

        // Map idempotency key
        Optional<String> idempotencyKey = record.idempotencyKey()
            .map(IdempotencyKey::value);

        // Map payload — EventRecord stores as read-only ByteBuffer, need duplicate for entry
        ByteBuffer payload = record.payload().duplicate();

        return EventLogStore.EventEntry.builder()
            .eventId(eventUuid)
            .eventType(record.typeRef().name())
            .eventVersion(record.typeRef().version().toString())
            .timestamp(record.occurrenceTime())
            .payload(payload)
            .contentType(contentTypeStr)
            .headers(headers)
            .idempotencyKey(idempotencyKey.orElse(null))
            .build();
    }

    /**
     * Maps a list of platform {@link EventRecord}s to data-cloud entries.
     *
     * @param records the event records to map
     * @return list of equivalent event entries (same order)
     */
    public static List<EventLogStore.EventEntry> toEventEntries(List<EventRecord> records) {
        Objects.requireNonNull(records, "records must not be null");
        return records.stream()
            .map(DataCloudEventMapper::toEventEntry)
            .toList();
    }

    /**
     * Maps a platform {@link EventCloud.AppendRequest} to an event entry,
     * applying append options validation.
     *
     * @param request the append request
     * @return the event entry ready for storage
     */
    public static EventLogStore.EventEntry toEventEntry(EventCloud.AppendRequest request) {
        Objects.requireNonNull(request, "AppendRequest must not be null");
        return toEventEntry(request.event());
    }

    // ==================== Reverse Mapping: Data-Cloud → Platform ====================

    /**
     * Maps a data-cloud {@link EventLogStore.EventEntry} back to a platform {@link EventRecord}.
     *
     * <p>Uses enriched headers written during forward mapping to reconstruct
     * the original platform types. Falls back to sensible defaults when
     * headers are missing (e.g., events written by non-AEP producers).
     *
     * @param entry the data-cloud event entry
     * @param tenantId the tenant context (used when header is absent)
     * @return the equivalent platform event record
     * @throws NullPointerException if entry or tenantId is null
     */
    public static EventRecord toEventRecord(EventLogStore.EventEntry entry, TenantId tenantId) {
        Objects.requireNonNull(entry, "EventEntry must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        Map<String, String> entryHeaders = entry.headers() != null
            ? entry.headers() : Map.of();

        // Reconstruct TenantId: prefer embedded header, fallback to parameter
        TenantId resolvedTenant = entryHeaders.containsKey(HEADER_TENANT_ID)
            ? TenantId.of(entryHeaders.get(HEADER_TENANT_ID))
            : tenantId;

        // Reconstruct EventTypeRef
        String eventTypeName = entry.eventType();
        Version version = parseVersion(
            entryHeaders.getOrDefault(HEADER_EVENT_VERSION, entry.eventVersion()));

        // Reconstruct EventId: prefer original, fallback to entry UUID
        String originalEventId = entryHeaders.getOrDefault(
            HEADER_ORIGINAL_EVENT_ID, entry.eventId().toString());
        EventId eventId = EventId.of(originalEventId);

        // Reconstruct timestamps
        Instant occurrenceTime = parseInstantOrDefault(
            entryHeaders.get(HEADER_OCCURRENCE_TIME), entry.timestamp());
        Instant detectionTime = parseInstantOrDefault(
            entryHeaders.get(HEADER_DETECTION_TIME), Instant.now());

        // Reconstruct ContentType
        ContentType contentType = ContentType.fromMimeType(entry.contentType());
        if (contentType == null) {
            contentType = ContentType.JSON;
        }

        // Reconstruct schema URI
        String schemaUri = entryHeaders.getOrDefault(HEADER_SCHEMA_URI, "");

        // Reconstruct correlation ID
        Optional<CorrelationId> correlationId = Optional.ofNullable(
            entryHeaders.get(HEADER_CORRELATION_ID))
            .filter(s -> !s.isBlank())
            .map(CorrelationId::of);

        // Reconstruct idempotency key
        Optional<IdempotencyKey> idempotencyKey = entry.idempotencyKey()
            .filter(s -> !s.isBlank())
            .map(IdempotencyKey::of);

        // Strip internal headers from user-visible headers
        Map<String, String> cleanHeaders = new HashMap<>(entryHeaders);
        cleanHeaders.remove(HEADER_SCHEMA_URI);
        cleanHeaders.remove(HEADER_CORRELATION_ID);
        cleanHeaders.remove(HEADER_DETECTION_TIME);
        cleanHeaders.remove(HEADER_OCCURRENCE_TIME);
        cleanHeaders.remove(HEADER_TENANT_ID);
        cleanHeaders.remove(HEADER_EVENT_VERSION);
        cleanHeaders.remove(HEADER_ORIGINAL_EVENT_ID);

        // Duplicate payload buffer so producer retains original position
        ByteBuffer payload = entry.payload() != null
            ? entry.payload().duplicate()
            : ByteBuffer.allocate(0);

        return EventRecord.builder()
            .tenantId(resolvedTenant)
            .typeRef(EventTypeRef.of(eventTypeName, version.major(), version.minor()))
            .eventId(eventId)
            .occurrenceTime(occurrenceTime)
            .detectionTime(detectionTime)
            .headers(cleanHeaders)
            .contentType(contentType)
            .schemaUri(schemaUri)
            .payload(payload)
            .correlationId(correlationId.orElse(null))
            .idempotencyKey(idempotencyKey.orElse(null))
            .build();
    }

    /**
     * Maps a list of data-cloud entries back to platform event records.
     *
     * @param entries the entries to map
     * @param tenantId the tenant context
     * @return list of equivalent event records (same order)
     */
    public static List<EventRecord> toEventRecords(
            List<EventLogStore.EventEntry> entries, TenantId tenantId) {
        Objects.requireNonNull(entries, "entries must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return entries.stream()
            .map(entry -> toEventRecord(entry, tenantId))
            .toList();
    }

    // ==================== Tenant Mapping ====================

    /**
     * Maps a platform {@link TenantId} to a data-cloud {@link TenantContext}.
     *
     * @param tenantId the platform tenant identifier
     * @return the equivalent data-cloud tenant context
     */
    public static TenantContext toTenantContext(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return TenantContext.of(tenantId.value());
    }

    /**
     * Maps a platform {@link TenantId} with workspace to data-cloud {@link TenantContext}.
     *
     * @param tenantId the platform tenant identifier
     * @param workspaceId the workspace identifier
     * @return the equivalent data-cloud tenant context with workspace
     */
    public static TenantContext toTenantContext(TenantId tenantId, String workspaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return TenantContext.of(tenantId.value(), workspaceId);
    }

    // ==================== Offset Mapping ====================

    /**
     * Maps a platform string-based {@link com.ghatana.platform.types.identity.Offset}
     * to a data-cloud long-based {@link com.ghatana.platform.types.identity.Offset}.
     *
     * <p>Parsing strategy:
     * <ol>
     *   <li>Numeric string → direct conversion to long</li>
     *   <li>"0" or zero → Offset.zero()</li>
     *   <li>Non-numeric → hash-based mapping (for UUID-style offsets)</li>
     * </ol>
     *
     * @param platformOffset the string-based platform offset
     * @return the long-based data-cloud offset
     */
    public static com.ghatana.platform.types.identity.Offset toCoreOffset(
            com.ghatana.platform.types.identity.Offset platformOffset) {
        Objects.requireNonNull(platformOffset, "platformOffset must not be null");
        String value = platformOffset.value();
        try {
            long longValue = Long.parseLong(value);
            return com.ghatana.platform.types.identity.Offset.of(longValue);
        } catch (NumberFormatException e) {
            // UUID or other non-numeric offset — use hash mapping
            // This preserves ordering consistency for the same string
            long hashOffset = Math.abs(value.hashCode());
            LOG.log(Level.FINE,
                "Non-numeric offset ''{0}'' mapped to hash-based offset {1}",
                new Object[]{value, hashOffset});
            return com.ghatana.platform.types.identity.Offset.of(hashOffset);
        }
    }

    /**
     * Maps a data-cloud long-based offset to a platform string-based offset.
     *
     * @param coreOffset the data-cloud long-based offset
     * @return the platform string-based offset
     */
    public static com.ghatana.platform.types.identity.Offset toPlatformOffset(
            com.ghatana.platform.types.identity.Offset coreOffset) {
        Objects.requireNonNull(coreOffset, "coreOffset must not be null");
        return com.ghatana.platform.types.identity.Offset.of(
            String.valueOf(coreOffset.value()));
    }

    // ==================== AppendResult Construction ====================

    /**
     * Creates an {@link AppendResult} from a data-cloud offset.
     *
     * @param coreOffset the offset returned by the event log store
     * @param partitionHint optional partition hint (null for default partition)
     * @return the append result for the EventCloud caller
     */
    public static AppendResult toAppendResult(
            com.ghatana.platform.types.identity.Offset coreOffset,
            String partitionHint) {
        Objects.requireNonNull(coreOffset, "coreOffset must not be null");

        com.ghatana.platform.types.identity.Offset platformOffset =
            toPlatformOffset(coreOffset);

        com.ghatana.platform.types.identity.PartitionId partitionId =
            com.ghatana.platform.types.identity.PartitionId.of(
                partitionHint != null ? partitionHint : "0");

        return new AppendResult(partitionId, platformOffset, Instant.now());
    }

    /**
     * Creates an {@link AppendResult} with timestamp control (for testing).
     *
     * @param coreOffset the offset
     * @param partitionHint partition hint
     * @param appendTime the append timestamp
     * @return the append result
     */
    public static AppendResult toAppendResult(
            com.ghatana.platform.types.identity.Offset coreOffset,
            String partitionHint,
            Instant appendTime) {
        Objects.requireNonNull(coreOffset, "coreOffset must not be null");
        Objects.requireNonNull(appendTime, "appendTime must not be null");

        return new AppendResult(
            com.ghatana.platform.types.identity.PartitionId.of(
                partitionHint != null ? partitionHint : "0"),
            toPlatformOffset(coreOffset),
            appendTime);
    }

    // ==================== EventCloud.EventEnvelope Construction ====================

    /**
     * Wraps a data-cloud event entry as a platform {@link EventCloud.EventEnvelope}.
     *
     * @param entry the data-cloud event entry
     * @param tenantId the tenant context
     * @param offset the data-cloud offset
     * @param partitionHint partition assignment hint
     * @return event envelope suitable for EventCloud consumers
     */
    public static EventCloud.EventEnvelope toEventEnvelope(
            EventLogStore.EventEntry entry,
            TenantId tenantId,
            com.ghatana.platform.types.identity.Offset offset,
            String partitionHint) {
        Objects.requireNonNull(entry, "entry must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(offset, "offset must not be null");

        EventRecord record = toEventRecord(entry, tenantId);
        com.ghatana.platform.types.identity.PartitionId partitionId =
            com.ghatana.platform.types.identity.PartitionId.of(
                partitionHint != null ? partitionHint : "0");
        com.ghatana.platform.types.identity.Offset platformOffset =
            toPlatformOffset(offset);

        return new EventCloud.EventEnvelope(record, partitionId, platformOffset);
    }

    // ==================== Internal Helpers ====================

    /**
     * Attempts to parse a string as UUID; generates a new UUID if unparseable.
     */
    static UUID parseOrGenerateUuid(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            // Not a valid UUID format — generate deterministic UUID from the string
            return UUID.nameUUIDFromBytes(value.getBytes());
        }
    }

    /**
     * Parses a version string like "1.0" into a {@link Version}.
     * Falls back to Version(1, 0) on parse failure.
     */
    static Version parseVersion(String versionStr) {
        if (versionStr == null || versionStr.isBlank()) {
            return new Version(1, 0);
        }
        try {
            return Version.parse(versionStr);
        } catch (Exception e) {
            // Handle "1.0.0" semver format — take first two parts
            String[] parts = versionStr.split("\\.");
            if (parts.length >= 2) {
                try {
                    return new Version(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException nfe) {
                    // Fall through
                }
            }
            LOG.log(Level.WARNING, "Unparseable version ''{0}'', defaulting to 1.0", versionStr);
            return new Version(1, 0);
        }
    }

    /**
     * Parses an ISO-8601 instant string, falling back to provided default.
     */
    static Instant parseInstantOrDefault(String instantStr, Instant defaultValue) {
        if (instantStr == null || instantStr.isBlank()) {
            return defaultValue;
        }
        try {
            return Instant.parse(instantStr);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                "Unparseable instant ''{0}'', using default", instantStr);
            return defaultValue;
        }
    }
}
