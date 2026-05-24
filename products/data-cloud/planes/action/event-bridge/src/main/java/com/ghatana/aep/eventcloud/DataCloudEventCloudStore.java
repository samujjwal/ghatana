/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.event.spi.EventCloudCheckpoint;
import com.ghatana.aep.event.spi.EventCloudCheckpointStore;
import com.ghatana.aep.event.spi.EventCloudOffset;
import com.ghatana.aep.event.spi.EventCloudPartialMatchStore;
import com.ghatana.aep.event.spi.EventCloudRecord;
import com.ghatana.aep.event.spi.EventCloudRecordHandler;
import com.ghatana.aep.event.spi.EventCloudStore;
import com.ghatana.aep.event.spi.EventCloudSubscription;
import com.ghatana.aep.event.spi.EventCloudWatermark;
import com.ghatana.aep.model.CanonicalEvent;
import com.ghatana.aep.model.EventInterval;
import com.ghatana.aep.model.PatternPartialMatch;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-Cloud-backed implementation of the AEP {@link EventCloudStore} SPI.
 *
 * <p>This bridge treats Data-Cloud as persistence only. Offset, replay,
 * checkpoint, watermark, and partial-match semantics remain AEP-owned through
 * the {@code EventCloudStore} contract.
 *
 * @doc.type class
 * @doc.purpose Implements AEP EventCloudStore on top of Data-Cloud EventLogStore persistence
 * @doc.layer product
 * @doc.pattern Adapter, Bridge
 */
public final class DataCloudEventCloudStore implements EventCloudStore {

    private static final String HEADER_OFFSET = "_x_dc_offset";
    private static final String HEADER_PARTITION = "aep.partition";
    private static final String DEFAULT_PARTITION = "default";
    private static final int REPLAY_BATCH_LIMIT = 1_000;
    private static final int STATE_READ_LIMIT = 10_000;
    private static final String CHECKPOINT_SAVED = "aep.eventcloud.checkpoint.saved";
    private static final String PARTIAL_MATCH_SAVED = "aep.eventcloud.partial_match.saved";
    private static final String PARTIAL_MATCH_DELETED = "aep.eventcloud.partial_match.deleted";

    private final EventLogStore eventLogStore;
    private final ObjectMapper objectMapper;
    private final EventCloudCheckpointStore checkpointStore;
    private final EventCloudPartialMatchStore partialMatchStore;

    public DataCloudEventCloudStore(EventLogStore eventLogStore) {
        this(eventLogStore, new ObjectMapper());
    }

    public DataCloudEventCloudStore(EventLogStore eventLogStore, ObjectMapper objectMapper) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper required");
        this.checkpointStore = new EventLogCheckpointStore();
        this.partialMatchStore = new EventLogPartialMatchStore();
    }

    @Override
    public Promise<EventCloudOffset> append(CanonicalEvent event) {
        Objects.requireNonNull(event, "event required");
        String partition = partition(event);
        EventEntry entry = EventEntry.builder()
            .eventType(event.eventType())
            .eventVersion(event.schemaVersion())
            .timestamp(event.eventTime())
            .payload(serialize(event))
            .headers(Map.of(
                HEADER_PARTITION, partition,
                "aep.eventId", event.eventId(),
                "aep.correlationId", event.correlationId()))
            .idempotencyKey(event.idempotencyKey())
            .build();
        return eventLogStore.append(TenantContext.of(event.tenantId()), entry)
            .map(offset -> new EventCloudOffset(event.tenantId(), partition, offsetValue(offset)));
    }

    @Override
    public Promise<Optional<EventCloudRecord>> read(EventCloudOffset offset) {
        Objects.requireNonNull(offset, "offset required");
        return eventLogStore.read(TenantContext.of(offset.tenantId()), Offset.of(offset.offset()), 1)
            .map(entries -> entries.stream()
                .findFirst()
                .map(entry -> toRecord(offset.tenantId(), offset.offset(), entry)));
    }

    @Override
    public Promise<EventCloudWatermark> watermark(String tenantId, String partition) {
        return eventLogStore.getLatestOffset(TenantContext.of(tenantId))
            .map(offset -> new EventCloudWatermark(
                tenantId,
                normalizedPartition(partition),
                Instant.now(),
                new EventCloudOffset(tenantId, normalizedPartition(partition), offsetValue(offset))));
    }

    @Override
    public Promise<EventCloudSubscription> tail(
            String tenantId,
            String partition,
            EventCloudOffset from,
            EventCloudRecordHandler handler) {
        Objects.requireNonNull(handler, "handler required");
        String expectedPartition = normalizedPartition(partition);
        Offset start = Offset.of(from != null ? from.offset() : 0L);
        return eventLogStore.tail(TenantContext.of(tenantId), start, entry -> {
            if (expectedPartition.equals(partition(entry))) {
                handler.onRecord(toRecord(tenantId, offsetValue(entry), entry));
            }
        }).map(subscription -> new EventCloudSubscription() {
            @Override
            public void cancel() {
                subscription.cancel();
            }

            @Override
            public boolean isCancelled() {
                return subscription.isCancelled();
            }
        });
    }

    @Override
    public Promise<Void> replay(
            String tenantId,
            EventCloudOffset from,
            EventCloudOffset to,
            EventCloudRecordHandler handler) {
        Objects.requireNonNull(from, "from required");
        Objects.requireNonNull(to, "to required");
        Objects.requireNonNull(handler, "handler required");
        if (!from.tenantId().equals(tenantId) || !to.tenantId().equals(tenantId)) {
            return Promise.ofException(new IllegalArgumentException("replay offsets must match tenantId"));
        }
        return eventLogStore.read(TenantContext.of(tenantId), Offset.of(from.offset()), REPLAY_BATCH_LIMIT)
            .map(entries -> {
                for (EventEntry entry : entries) {
                    long offset = offsetValue(entry);
                    if (offset >= from.offset() && offset <= to.offset() && from.partition().equals(partition(entry))) {
                        handler.onRecord(toRecord(tenantId, offset, entry));
                    }
                }
                return null;
            });
    }

    @Override
    public EventCloudCheckpointStore checkpoints() {
        return checkpointStore;
    }

    @Override
    public EventCloudPartialMatchStore partialMatches() {
        return partialMatchStore;
    }

    private byte[] serialize(CanonicalEvent event) {
        try {
            return objectMapper.writeValueAsBytes(toEnvelope(event));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to serialize CanonicalEvent", ex);
        }
    }

    private EventCloudRecord toRecord(String tenantId, long fallbackOffset, EventEntry entry) {
        return new EventCloudRecord(
            new EventCloudOffset(tenantId, partition(entry), offsetValue(entry, fallbackOffset)),
            toCanonicalEvent(entry));
    }

    private CanonicalEvent toCanonicalEvent(EventEntry entry) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(
                bytes(entry.payload()),
                new TypeReference<>() {
                });
            Optional<EventInterval> interval = Optional.empty();
            Object intervalValue = envelope.get("interval");
            if (intervalValue instanceof Map<?, ?> intervalMap) {
                interval = Optional.of(new EventInterval(
                    Instant.parse(String.valueOf(intervalMap.get("start"))),
                    Instant.parse(String.valueOf(intervalMap.get("end")))));
            }
            return new CanonicalEvent(
                string(envelope, "eventId"),
                string(envelope, "tenantId"),
                string(envelope, "eventType"),
                string(envelope, "schemaVersion"),
                Instant.parse(string(envelope, "eventTime")),
                optionalInstant(envelope.get("processingTime")),
                optionalInstant(envelope.get("detectionTime")),
                interval,
                stringObjectMap(envelope.get("source")),
                stringList(envelope.get("entityRefs")),
                string(envelope, "correlationId"),
                optionalString(envelope.get("causationId")),
                stringObjectMap(envelope.get("payload")),
                stringObjectMap(envelope.get("confidence")),
                stringObjectMap(envelope.get("provenance")),
                stringList(envelope.get("policyTags")),
                string(envelope, "idempotencyKey"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to deserialize CanonicalEvent", ex);
        }
    }

    private static Map<String, Object> toEnvelope(CanonicalEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId());
        envelope.put("tenantId", event.tenantId());
        envelope.put("eventType", event.eventType());
        envelope.put("schemaVersion", event.schemaVersion());
        envelope.put("eventTime", event.eventTime().toString());
        event.processingTime().ifPresent(value -> envelope.put("processingTime", value.toString()));
        event.detectionTime().ifPresent(value -> envelope.put("detectionTime", value.toString()));
        event.interval().ifPresent(value -> envelope.put("interval", Map.of(
            "start", value.start().toString(),
            "end", value.end().toString())));
        envelope.put("source", event.source());
        envelope.put("entityRefs", event.entityRefs());
        envelope.put("correlationId", event.correlationId());
        event.causationId().ifPresent(value -> envelope.put("causationId", value));
        envelope.put("payload", event.payload());
        envelope.put("confidence", event.confidence());
        envelope.put("provenance", event.provenance());
        envelope.put("policyTags", event.policyTags());
        envelope.put("idempotencyKey", event.idempotencyKey());
        return envelope;
    }

    private static byte[] bytes(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.duplicate();
        byte[] data = new byte[duplicate.remaining()];
        duplicate.get(data);
        return data;
    }

    private static String partition(CanonicalEvent event) {
        Object partition = event.source().get("partition");
        return normalizedPartition(partition != null ? String.valueOf(partition) : DEFAULT_PARTITION);
    }

    private static String partition(EventEntry entry) {
        return normalizedPartition(entry.headers().getOrDefault(HEADER_PARTITION, DEFAULT_PARTITION));
    }

    private static String normalizedPartition(String partition) {
        return partition == null || partition.isBlank() ? DEFAULT_PARTITION : partition;
    }

    private static long offsetValue(Offset offset) {
        return Long.parseLong(offset.value());
    }

    private static long offsetValue(EventEntry entry) {
        return offsetValue(entry, 0L);
    }

    private static long offsetValue(EventEntry entry, long fallback) {
        String value = entry.headers().get(HEADER_OFFSET);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Long.parseLong(value);
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return String.valueOf(value);
    }

    private static Optional<String> optionalString(Object value) {
        return value == null || String.valueOf(value).isBlank()
            ? Optional.empty()
            : Optional.of(String.valueOf(value));
    }

    private static Optional<Instant> optionalInstant(Object value) {
        return optionalString(value).map(Instant::parse);
    }

    private static Map<String, Object> stringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, mapValue) -> {
            if (key instanceof String stringKey) {
                result.put(stringKey, mapValue);
            }
        });
        return result;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> source)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        source.forEach(item -> {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        });
        return result;
    }

    private final class EventLogCheckpointStore implements EventCloudCheckpointStore {
        @Override
        public Promise<Void> save(EventCloudCheckpoint checkpoint) {
            return appendStateEvent(
                checkpoint.tenantId(),
                CHECKPOINT_SAVED,
                checkpointPayload(checkpoint),
                "checkpoint:" + checkpoint.consumerId())
                .map(ignored -> null);
        }

        @Override
        public Promise<Optional<EventCloudCheckpoint>> load(String tenantId, String consumerId) {
            return eventLogStore.readByType(TenantContext.of(tenantId), CHECKPOINT_SAVED, Offset.zero(), STATE_READ_LIMIT)
                .map(entries -> entries.stream()
                    .map(DataCloudEventCloudStore.this::checkpointFromEntry)
                    .filter(checkpoint -> consumerId.equals(checkpoint.consumerId()))
                    .reduce((ignored, latest) -> latest));
        }
    }

    private final class EventLogPartialMatchStore implements EventCloudPartialMatchStore {
        @Override
        public Promise<Void> save(PatternPartialMatch partialMatch) {
            return appendStateEvent(
                partialMatch.tenantId(),
                PARTIAL_MATCH_SAVED,
                partialMatchPayload(partialMatch),
                "partial-match:" + partialMatch.partialMatchId())
                .map(ignored -> null);
        }

        @Override
        public Promise<Optional<PatternPartialMatch>> load(String tenantId, String partialMatchId) {
            return latestPartialMatches(tenantId)
                .map(matches -> Optional.ofNullable(matches.get(partialMatchId)));
        }

        @Override
        public Promise<List<PatternPartialMatch>> loadForPattern(String tenantId, String patternId) {
            return latestPartialMatches(tenantId)
                .map(matches -> matches.values().stream()
                .filter(match -> patternId.equals(match.patternId()))
                .toList());
        }

        @Override
        public Promise<Void> delete(String tenantId, String partialMatchId) {
            return appendStateEvent(
                tenantId,
                PARTIAL_MATCH_DELETED,
                Map.of("partialMatchId", partialMatchId),
                "partial-match-delete:" + partialMatchId)
                .map(ignored -> null);
        }
    }

    private Promise<Offset> appendStateEvent(
            String tenantId,
            String eventType,
            Map<String, Object> payload,
            String idempotencyKey) {
        return eventLogStore.append(TenantContext.of(tenantId), EventEntry.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .payload(writeJson(payload))
            .contentType("application/json")
            .headers(Map.of("aep.stateType", eventType))
            .idempotencyKey(idempotencyKey)
            .build());
    }

    private byte[] writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to serialize EventCloud state", ex);
        }
    }

    private Map<String, Object> readJson(EventEntry entry) {
        try {
            return objectMapper.readValue(bytes(entry.payload()), new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to deserialize EventCloud state", ex);
        }
    }

    private Map<String, Object> checkpointPayload(EventCloudCheckpoint checkpoint) {
        return Map.of(
            "tenantId", checkpoint.tenantId(),
            "consumerId", checkpoint.consumerId(),
            "partition", checkpoint.offset().partition(),
            "offset", checkpoint.offset().offset(),
            "checkpointedAt", checkpoint.checkpointedAt().toString(),
            "metadata", checkpoint.metadata());
    }

    private EventCloudCheckpoint checkpointFromEntry(EventEntry entry) {
        Map<String, Object> payload = readJson(entry);
        String tenantId = string(payload, "tenantId");
        return new EventCloudCheckpoint(
            tenantId,
            string(payload, "consumerId"),
            new EventCloudOffset(
                tenantId,
                string(payload, "partition"),
                longValue(payload.get("offset"))),
            Instant.parse(string(payload, "checkpointedAt")),
            stringObjectMap(payload.get("metadata")));
    }

    private Map<String, Object> partialMatchPayload(PatternPartialMatch partialMatch) {
        return Map.of(
            "partialMatchId", partialMatch.partialMatchId(),
            "patternId", partialMatch.patternId(),
            "tenantId", partialMatch.tenantId(),
            "startedAt", partialMatch.startedAt().toString(),
            "expiresAt", partialMatch.expiresAt().toString(),
            "events", partialMatch.events().stream().map(DataCloudEventCloudStore::toEnvelope).toList(),
            "bindings", partialMatch.bindings(),
            "confidence", partialMatch.confidence());
    }

    private PatternPartialMatch partialMatchFromEntry(EventEntry entry) {
        Map<String, Object> payload = readJson(entry);
        return new PatternPartialMatch(
            string(payload, "partialMatchId"),
            string(payload, "patternId"),
            string(payload, "tenantId"),
            Instant.parse(string(payload, "startedAt")),
            Instant.parse(string(payload, "expiresAt")),
            canonicalEvents(payload.get("events")),
            stringObjectMap(payload.get("bindings")),
            doubleValue(payload.get("confidence")));
    }

    private Promise<Map<String, PatternPartialMatch>> latestPartialMatches(String tenantId) {
        return eventLogStore.readByType(TenantContext.of(tenantId), PARTIAL_MATCH_SAVED, Offset.zero(), STATE_READ_LIMIT)
            .then(saved -> eventLogStore.readByType(TenantContext.of(tenantId), PARTIAL_MATCH_DELETED, Offset.zero(), STATE_READ_LIMIT)
                .map(deleted -> {
                    Map<String, PatternPartialMatch> matches = new LinkedHashMap<>();
                    for (EventEntry entry : saved) {
                        PatternPartialMatch partialMatch = partialMatchFromEntry(entry);
                        matches.put(partialMatch.partialMatchId(), partialMatch);
                    }
                    for (EventEntry entry : deleted) {
                        matches.remove(string(readJson(entry), "partialMatchId"));
                    }
                    return matches;
                }));
    }

    private List<CanonicalEvent> canonicalEvents(Object value) {
        if (!(value instanceof List<?> source)) {
            return List.of();
        }
        return source.stream()
            .filter(Map.class::isInstance)
            .map(item -> canonicalEventFromEnvelope(stringObjectMap(item)))
            .toList();
    }

    private CanonicalEvent canonicalEventFromEnvelope(Map<String, Object> envelope) {
        Optional<EventInterval> interval = Optional.empty();
        Object intervalValue = envelope.get("interval");
        if (intervalValue instanceof Map<?, ?> intervalMap) {
            interval = Optional.of(new EventInterval(
                Instant.parse(String.valueOf(intervalMap.get("start"))),
                Instant.parse(String.valueOf(intervalMap.get("end")))));
        }
        return new CanonicalEvent(
            string(envelope, "eventId"),
            string(envelope, "tenantId"),
            string(envelope, "eventType"),
            string(envelope, "schemaVersion"),
            Instant.parse(string(envelope, "eventTime")),
            optionalInstant(envelope.get("processingTime")),
            optionalInstant(envelope.get("detectionTime")),
            interval,
            stringObjectMap(envelope.get("source")),
            stringList(envelope.get("entityRefs")),
            string(envelope, "correlationId"),
            optionalString(envelope.get("causationId")),
            stringObjectMap(envelope.get("payload")),
            stringObjectMap(envelope.get("confidence")),
            stringObjectMap(envelope.get("provenance")),
            stringList(envelope.get("policyTags")),
            string(envelope, "idempotencyKey"));
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
