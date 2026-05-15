/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Data Cloud-backed implementation of {@link AgentTraceLedger}.
 *
 * <p>Trace entries are stored as typed Data Cloud event-log rows so AEP and Kernel
 * lifecycle evidence share the governed append-only persistence plane in platform
 * mode. The hash chain is still validated before writes and during reads.
 *
 * @doc.type class
 * @doc.purpose Data Cloud EventLogStore-backed agent trace ledger
 * @doc.layer agent-runtime
 * @doc.pattern Repository, Adapter
 */
public final class DataCloudAgentTraceLedger implements AgentTraceLedger {

    public static final String EVENT_TYPE_PREFIX = "aep.agent.trace.";
    public static final String TRACE_LEDGER_HEADER = "x-ghatana-ledger";
    public static final String TRACE_LEDGER_HEADER_VALUE = "agent-trace";
    private static final int MAX_TRACE_REPLAY_EVENTS = 10_000;

    private final EventLogStore eventLogStore;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, String> lastHashesByTenant = new ConcurrentHashMap<>();

    public DataCloudAgentTraceLedger(@NotNull EventLogStore eventLogStore) {
        this(eventLogStore, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    DataCloudAgentTraceLedger(@NotNull EventLogStore eventLogStore, @NotNull ObjectMapper objectMapper) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    @NotNull
    public Promise<Void> append(@NotNull TraceEvent event) {
        Objects.requireNonNull(event, "event");
        return recoverLastHash(event.tenantId()).then(expectedPreviousHash -> {
            if (!event.previousHash().equals(expectedPreviousHash)) {
                return Promise.ofException(new IllegalStateException(
                        "Hash chain broken for tenant=%s: expected previousHash='%s' but got '%s'"
                                .formatted(event.tenantId(), expectedPreviousHash, event.previousHash())));
            }
            String computedHash = HashChainedTraceAppender.computeHash(event);
            if (!computedHash.equals(event.eventHash())) {
                return Promise.ofException(new IllegalStateException(
                        "Event hash mismatch for eventId=%s: computed='%s' but declared='%s'"
                                .formatted(event.eventId(), computedHash, event.eventHash())));
            }
            return eventLogStore.append(TenantContext.of(event.tenantId()), toEventEntry(event))
                    .map(offset -> {
                        lastHashesByTenant.put(event.tenantId(), event.eventHash());
                        return null;
                    });
        });
    }

    @Override
    @NotNull
    public Promise<List<TraceEvent>> getByTrace(@NotNull String traceId, @NotNull String tenantId) {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(tenantId, "tenantId");
        return readTraceEvents(tenantId)
                .map(events -> events.stream()
                        .filter(event -> event.traceId().equals(traceId))
                        .sorted(Comparator.comparingLong(TraceEvent::sequenceNumber))
                        .toList());
    }

    @Override
    @NotNull
    public Promise<List<TraceEvent>> getByAgent(
            @NotNull String agentId,
            @NotNull String tenantId,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        return readTraceEvents(tenantId)
                .map(events -> events.stream()
                        .filter(event -> event.agentId().equals(agentId))
                        .filter(event -> from == null || !event.timestamp().isBefore(from))
                        .filter(event -> to == null || event.timestamp().isBefore(to))
                        .sorted(Comparator.comparing(TraceEvent::timestamp)
                                .thenComparingLong(TraceEvent::sequenceNumber))
                        .limit(Math.max(0, limit))
                        .toList());
    }

    @Override
    @NotNull
    public Promise<List<TraceEvent>> getByType(
            @NotNull TraceEventType eventType,
            @NotNull String tenantId,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(tenantId, "tenantId");
        return eventLogStore.readByType(TenantContext.of(tenantId), eventTypeName(eventType), Offset.zero(), MAX_TRACE_REPLAY_EVENTS)
                .map(entries -> entries.stream()
                        .filter(this::isTraceLedgerEntry)
                        .map(this::fromEventEntry)
                        .filter(event -> from == null || !event.timestamp().isBefore(from))
                        .filter(event -> to == null || event.timestamp().isBefore(to))
                        .sorted(Comparator.comparing(TraceEvent::timestamp)
                                .thenComparingLong(TraceEvent::sequenceNumber))
                        .limit(Math.max(0, limit))
                        .toList());
    }

    @Override
    public boolean verifyChain(@NotNull List<TraceEvent> events) {
        return new HashChainedTraceAppender().verifyChain(events);
    }

    /**
     * Returns the latest hash known in this process for the tenant partition.
     *
     * <p>The governed dispatcher uses this to construct the next event without
     * depending on a concrete in-memory ledger implementation.
     */
    @NotNull
    public String getLastHash(@NotNull String tenantId) {
        return lastHashesByTenant.getOrDefault(Objects.requireNonNull(tenantId, "tenantId"), "");
    }

    private Promise<String> recoverLastHash(String tenantId) {
        String cached = lastHashesByTenant.get(tenantId);
        if (cached != null) {
            return Promise.of(cached);
        }
        return readTraceEvents(tenantId).map(events -> {
            String recovered = events.isEmpty() ? "" : events.get(events.size() - 1).eventHash();
            lastHashesByTenant.put(tenantId, recovered);
            return recovered;
        });
    }

    private Promise<List<TraceEvent>> readTraceEvents(String tenantId) {
        return eventLogStore.read(TenantContext.of(tenantId), Offset.zero(), MAX_TRACE_REPLAY_EVENTS)
                .map(entries -> entries.stream()
                        .filter(this::isTraceLedgerEntry)
                        .map(this::fromEventEntry)
                        .sorted(Comparator.comparing(TraceEvent::timestamp)
                                .thenComparingLong(TraceEvent::sequenceNumber))
                        .toList());
    }

    private boolean isTraceLedgerEntry(EventLogStore.EventEntry entry) {
        return TRACE_LEDGER_HEADER_VALUE.equals(entry.headers().get(TRACE_LEDGER_HEADER));
    }

    private EventLogStore.EventEntry toEventEntry(TraceEvent event) {
        try {
            return EventLogStore.EventEntry.builder()
                    .eventId(UUID.fromString(event.eventId()))
                    .eventType(eventTypeName(event.eventType()))
                    .eventVersion("1.0.0")
                    .timestamp(event.timestamp())
                    .contentType("application/json")
                    .headers(Map.of(
                            TRACE_LEDGER_HEADER, TRACE_LEDGER_HEADER_VALUE,
                            "traceId", event.traceId(),
                            "agentId", event.agentId()))
                    .payload(objectMapper.writeValueAsString(event))
                    .idempotencyKey(event.eventId())
                    .build();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trace eventId must be a UUID: " + event.eventId(), e);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize agent trace event " + event.eventId(), e);
        }
    }

    private TraceEvent fromEventEntry(EventLogStore.EventEntry entry) {
        try {
            ByteBuffer payload = entry.payload().asReadOnlyBuffer();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            return objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), TraceEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize agent trace ledger entry " + entry.eventId(), e);
        }
    }

    private static String eventTypeName(TraceEventType eventType) {
        return EVENT_TYPE_PREFIX + eventType.name().toLowerCase(java.util.Locale.ROOT);
    }
}
