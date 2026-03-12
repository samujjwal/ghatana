/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.grpc;

import com.ghatana.contracts.event.v1.EventProto;
import com.ghatana.contracts.event.v1.EventServiceGrpc;
import com.ghatana.contracts.event.v1.GetEventRequestProto;
import com.ghatana.contracts.event.v1.GetEventResponseProto;
import com.ghatana.contracts.event.v1.IngestBatchRequestProto;
import com.ghatana.contracts.event.v1.IngestBatchResponseProto;
import com.ghatana.contracts.event.v1.IngestRequestProto;
import com.ghatana.contracts.event.v1.IngestResponseProto;
import com.ghatana.contracts.event.v1.QueryEventsRequestProto;
import com.ghatana.contracts.event.v1.QueryEventsResponseProto;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import com.ghatana.platform.types.identity.Offset;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * gRPC service implementation that exposes the unified {@code EventService} proto interface
 * against the Data-Cloud {@link EventLogStore} SPI.
 *
 * <p>Implements all five RPCs defined in {@code event_service.proto}:
 * <ul>
 *   <li>{@code Ingest}        — unary: appends a single event</li>
 *   <li>{@code IngestBatch}   — unary: appends multiple events atomically</li>
 *   <li>{@code IngestStream}  — bidirectional streaming: appends events as they arrive</li>
 *   <li>{@code Query}         — server-streaming: streams events matching a filter</li>
 *   <li>{@code GetEvent}      — unary: retrieves a specific event by ID</li>
 * </ul>
 *
 * <p>Tenant resolution follows the same priority order as {@link EventLogGrpcService}:
 * proto field → gRPC metadata interceptor → {@code "default-tenant"}.
 *
 * <p>For {@code GetEvent}, a scan of the warm-tier event log is performed searching for
 * the requested event ID. This is O(n) and should be replaced by an indexed lookup
 * once the {@link EventLogStore} SPI exposes {@code getById()}.
 *
 * @doc.type class
 * @doc.purpose gRPC EventService implementation covering unified ingest and query RPCs
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public final class EventServiceGrpcService extends EventServiceGrpc.EventServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(EventServiceGrpcService.class);
    private static final String FALLBACK_TENANT = "default-tenant";
    private static final int DEFAULT_QUERY_LIMIT = 100;
    private static final int GET_EVENT_SCAN_LIMIT = 10_000;

    private final EventLogStore eventLogStore;

    /**
     * Constructs the service backed by the given event log store.
     *
     * @param eventLogStore the underlying event log store (must not be null)
     */
    public EventServiceGrpcService(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore");
    }

    // ==================== RPC: Ingest ====================

    /**
     * {@inheritDoc}
     *
     * <p>Converts the incoming {@code EventProto} to an {@link EventEntry}, appends it to the
     * store, and responds with the stored event proto.
     */
    @Override
    public void ingest(
            IngestRequestProto request,
            StreamObserver<IngestResponseProto> responseObserver) {

        if (!request.hasEvent()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("IngestRequest.event is required")
                    .asRuntimeException());
            return;
        }

        EventEntry entry;
        try {
            entry = ProtobufMapper.toEventEntry(request.getEvent());
        } catch (Exception ex) {
            log.warn("Malformed IngestRequest.event: {}", ex.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Malformed event proto: " + ex.getMessage())
                    .asRuntimeException());
            return;
        }

        String tenantId = resolveTenantFromEvent(request.getEvent());
        TenantContext ctx = TenantContext.of(tenantId);

        eventLogStore.append(ctx, entry)
                .whenResult(offset -> {
                    EventProto stored = ProtobufMapper.toEventProto(entry);
                    responseObserver.onNext(IngestResponseProto.newBuilder()
                            .setEvent(stored)
                            .setDuplicate(false)
                            .build());
                    responseObserver.onCompleted();
                })
                .whenException(ex -> {
                    log.error("ingest failed for tenant={}, type={}", tenantId, entry.eventType(), ex);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("Ingest failed: " + ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException());
                });
    }

    // ==================== RPC: IngestBatch ====================

    /**
     * {@inheritDoc}
     *
     * <p>Converts all events in the batch to {@link EventEntry} records, appends them
     * via {@link EventLogStore#appendBatch}, and returns a summary response.
     */
    @Override
    public void ingestBatch(
            IngestBatchRequestProto request,
            StreamObserver<IngestBatchResponseProto> responseObserver) {

        if (request.getEventsCount() == 0) {
            responseObserver.onNext(IngestBatchResponseProto.newBuilder()
                    .setSuccessCount(0)
                    .setErrorCount(0)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        List<EventEntry> entries = new ArrayList<>(request.getEventsCount());
        for (EventProto proto : request.getEventsList()) {
            try {
                entries.add(ProtobufMapper.toEventEntry(proto));
            } catch (Exception ex) {
                log.warn("Skipping malformed event in batch: {}", ex.getMessage());
            }
        }

        String tenantId = resolveTenantFromFirstEvent(request);
        TenantContext ctx = TenantContext.of(tenantId);

        eventLogStore.appendBatch(ctx, entries)
                .whenResult(offsets -> {
                    IngestBatchResponseProto.Builder resp = IngestBatchResponseProto.newBuilder();
                    for (int i = 0; i < entries.size(); i++) {
                        resp.addEvents(ProtobufMapper.toEventProto(entries.get(i)));
                    }
                    resp.setSuccessCount(offsets.size());
                    resp.setErrorCount(entries.size() - offsets.size());
                    responseObserver.onNext(resp.build());
                    responseObserver.onCompleted();
                })
                .whenException(ex -> {
                    log.error("ingestBatch failed for tenant={}", tenantId, ex);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("IngestBatch failed: " + ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException());
                });
    }

    // ==================== RPC: IngestStream (bidirectional) ====================

    /**
     * {@inheritDoc}
     *
     * <p>Returns a {@link StreamObserver} that appends each incoming event to the store and
     * immediately streams back an {@link IngestResponseProto}. Errors on individual events
     * are logged and skipped; the stream stays open. {@code onCompleted()} completes the RPC.
     */
    @Override
    public StreamObserver<IngestRequestProto> ingestStream(
            StreamObserver<IngestResponseProto> responseObserver) {

        String tenantId = resolveTenantFromContext();

        return new StreamObserver<>() {

            @Override
            public void onNext(IngestRequestProto request) {
                if (!request.hasEvent()) {
                    log.warn("ingestStream: received request without event field, skipping");
                    return;
                }

                EventEntry entry;
                try {
                    entry = ProtobufMapper.toEventEntry(request.getEvent());
                } catch (Exception ex) {
                    log.warn("ingestStream: malformed event, skipping: {}", ex.getMessage());
                    return;
                }

                String effectiveTenant = request.getEvent().getTenantId().isBlank()
                        ? tenantId
                        : request.getEvent().getTenantId();
                TenantContext ctx = TenantContext.of(effectiveTenant);

                eventLogStore.append(ctx, entry)
                        .whenResult(offset -> {
                            EventProto stored = ProtobufMapper.toEventProto(entry);
                            responseObserver.onNext(IngestResponseProto.newBuilder()
                                    .setEvent(stored)
                                    .setDuplicate(false)
                                    .build());
                        })
                        .whenException(ex -> log.error(
                                "ingestStream: append failed for type={}", entry.eventType(), ex));
            }

            @Override
            public void onError(Throwable t) {
                log.warn("ingestStream: client error - {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    // ==================== RPC: Query (server-streaming) ====================

    /**
     * {@inheritDoc}
     *
     * <p>If {@code type_prefix} is specified in the request, delegates to
     * {@link EventLogStore#readByType}; otherwise performs a full scan from offset zero.
     * Each matching event is streamed as a separate {@link QueryEventsResponseProto}.
     */
    @Override
    public void query(
            QueryEventsRequestProto request,
            StreamObserver<QueryEventsResponseProto> responseObserver) {

        String tenantId = resolveTenantFromQuery(request);
        TenantContext ctx = TenantContext.of(tenantId);
        int limit = request.getLimit() > 0 ? request.getLimit() : DEFAULT_QUERY_LIMIT;

        long startMs = System.currentTimeMillis();

        if (!request.getTypePrefix().isBlank()) {
            eventLogStore.readByType(ctx, request.getTypePrefix(), Offset.zero(), limit)
                    .whenResult(entries -> streamQueryResults(entries, startMs, responseObserver))
                    .whenException(ex -> handleQueryError("query(type)", tenantId, ex, responseObserver));
        } else {
            eventLogStore.read(ctx, Offset.zero(), limit)
                    .whenResult(entries -> streamQueryResults(entries, startMs, responseObserver))
                    .whenException(ex -> handleQueryError("query(scan)", tenantId, ex, responseObserver));
        }
    }

    private static void streamQueryResults(
            List<EventEntry> entries, long startMs,
            StreamObserver<QueryEventsResponseProto> responseObserver) {

        long execMs = System.currentTimeMillis() - startMs;
        for (EventEntry entry : entries) {
            responseObserver.onNext(QueryEventsResponseProto.newBuilder()
                    .addEvents(ProtobufMapper.toEventProto(entry))
                    .setTotalCount(entries.size())
                    .setExecutionTimeMs(execMs)
                    .build());
        }
        responseObserver.onCompleted();
    }

    private static void handleQueryError(
            String operation, String tenantId, Throwable ex,
            StreamObserver<QueryEventsResponseProto> responseObserver) {
        log.error("{} failed for tenant={}", operation, tenantId, ex);
        responseObserver.onError(Status.INTERNAL
                .withDescription("Query failed: " + ex.getMessage())
                .withCause(ex)
                .asRuntimeException());
    }

    // ==================== RPC: GetEvent ====================

    /**
     * {@inheritDoc}
     *
     * <p>Scans the warm-tier event log searching for an event with the requested
     * {@code event_id}. This is O(n) and should be replaced by an indexed SPI method
     * once {@link EventLogStore} exposes {@code getById()} or {@code findByEventId()}.
     */
    @Override
    public void getEvent(
            GetEventRequestProto request,
            StreamObserver<GetEventResponseProto> responseObserver) {

        String eventId = request.getEventId();
        if (eventId == null || eventId.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("GetEvent.event_id is required")
                    .asRuntimeException());
            return;
        }

        String tenantId = request.getTenantId().isBlank()
                ? resolveTenantFromContext()
                : request.getTenantId();
        TenantContext ctx = TenantContext.of(tenantId);

        UUID targetId;
        try {
            targetId = UUID.fromString(eventId);
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("event_id is not a valid UUID: " + eventId)
                    .asRuntimeException());
            return;
        }

        eventLogStore.read(ctx, Offset.zero(), GET_EVENT_SCAN_LIMIT)
                .whenResult(entries -> {
                    EventEntry found = null;
                    for (EventEntry e : entries) {
                        if (targetId.equals(e.eventId())) {
                            found = e;
                            break;
                        }
                    }
                    if (found == null) {
                        responseObserver.onError(Status.NOT_FOUND
                                .withDescription("Event not found: " + eventId)
                                .asRuntimeException());
                    } else {
                        responseObserver.onNext(GetEventResponseProto.newBuilder()
                                .setEvent(ProtobufMapper.toEventProto(found))
                                .build());
                        responseObserver.onCompleted();
                    }
                })
                .whenException(ex -> {
                    log.error("getEvent({}) failed for tenant={}", eventId, tenantId, ex);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("GetEvent failed: " + ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException());
                });
    }

    // ==================== Tenant resolution helpers ====================

    private static String resolveTenantFromEvent(EventProto event) {
        String fromProto = event.getTenantId();
        if (fromProto != null && !fromProto.isBlank()) return fromProto;
        return resolveTenantFromContext();
    }

    private static String resolveTenantFromFirstEvent(IngestBatchRequestProto request) {
        if (request.getEventsCount() > 0) {
            String fromProto = request.getEvents(0).getTenantId();
            if (fromProto != null && !fromProto.isBlank()) return fromProto;
        }
        return resolveTenantFromContext();
    }

    private static String resolveTenantFromQuery(QueryEventsRequestProto request) {
        String fromProto = request.getTenantId();
        if (fromProto != null && !fromProto.isBlank()) return fromProto;
        return resolveTenantFromContext();
    }

    private static String resolveTenantFromContext() {
        String fromCtx = TenantGrpcInterceptor.TENANT_ID_CTX_KEY.get();
        return (fromCtx != null && !fromCtx.isBlank()) ? fromCtx : FALLBACK_TENANT;
    }
}
