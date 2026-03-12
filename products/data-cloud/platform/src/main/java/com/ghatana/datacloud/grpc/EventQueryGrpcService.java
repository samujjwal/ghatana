/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.grpc;

import com.ghatana.contracts.event.v1.EventQueryServiceGrpc;
import com.ghatana.contracts.event.v1.ExecuteQueryRequestProto;
import com.ghatana.contracts.event.v1.ExecuteQueryResponseProto;
import com.ghatana.contracts.event.v1.ExplainQueryRequestProto;
import com.ghatana.contracts.event.v1.ExplainQueryResponseProto;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import com.ghatana.platform.types.identity.Offset;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * gRPC service implementation for analytical event queries against the Data-Cloud
 * {@link EventLogStore} SPI.
 *
 * <p>Implements two RPCs defined in {@code event_service.proto}:
 * <ul>
 *   <li>{@code ExecuteQuery} — server-streaming: executes a query and streams row results</li>
 *   <li>{@code ExplainQuery} — unary: returns the logical plan without executing</li>
 * </ul>
 *
 * <p>Query parsing is opportunistic: if the {@code query} field starts with {@code "type:"},
 * the suffix is used as an event-type filter via {@link EventLogStore#readByType}. Otherwise,
 * all events are scanned from offset zero up to {@code limit} (default 100).
 *
 * <p>Results are serialized as {@code google.protobuf.Struct} rows containing each
 * {@link EventLogStore.EventEntry}'s fields (event_id, event_type, event_version,
 * timestamp, content_type, payload).
 *
 * <p>Tenant resolution follows the same priority order as {@link EventLogGrpcService}:
 * envelope → gRPC metadata interceptor → {@code "default-tenant"}.
 *
 * @doc.type class
 * @doc.purpose gRPC EventQueryService implementation for server-streaming analytical queries
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public final class EventQueryGrpcService extends EventQueryServiceGrpc.EventQueryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(EventQueryGrpcService.class);
    private static final String FALLBACK_TENANT = "default-tenant";
    private static final int DEFAULT_QUERY_LIMIT = 100;
    private static final int MAX_QUERY_LIMIT = 10_000;

    private final EventLogStore eventLogStore;

    /**
     * Constructs the service backed by the given event log store.
     *
     * @param eventLogStore the underlying event log store (must not be null)
     */
    public EventQueryGrpcService(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore");
    }

    // ==================== RPC: ExecuteQuery (server-streaming) ====================

    /**
     * {@inheritDoc}
     *
     * <p>Parses the {@code query} field and executes against the warm-tier event log.
     * Each matching {@link EventLogStore.EventEntry} is emitted as a separate
     * {@link ExecuteQueryResponseProto} containing one {@code google.protobuf.Struct} row.
     */
    @Override
    public void executeQuery(
            ExecuteQueryRequestProto request,
            StreamObserver<ExecuteQueryResponseProto> responseObserver) {

        String tenantId = resolveTenant(request.getEnvelope() != null
                ? request.getEnvelope().getTenantId()
                : null);
        TenantContext ctx = TenantContext.of(tenantId);
        int limit = resolveLimit(request.getLimit());
        String query = request.getQuery();

        long startMs = System.currentTimeMillis();

        if (query.startsWith("type:")) {
            String eventType = query.substring("type:".length()).trim();
            executeByType(ctx, eventType, limit, startMs, responseObserver);
        } else {
            executeScan(ctx, limit, startMs, responseObserver);
        }
    }

    private void executeByType(
            TenantContext ctx, String eventType, int limit, long startMs,
            StreamObserver<ExecuteQueryResponseProto> responseObserver) {

        eventLogStore.readByType(ctx, eventType, Offset.zero(), limit)
                .whenResult(entries -> streamResults(entries, startMs, responseObserver))
                .whenException(ex -> {
                    log.error("executeQuery(type={}) failed for tenant={}",
                            eventType, ctx.tenantId(), ex);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("Query failed: " + ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException());
                });
    }

    private void executeScan(
            TenantContext ctx, int limit, long startMs,
            StreamObserver<ExecuteQueryResponseProto> responseObserver) {

        eventLogStore.read(ctx, Offset.zero(), limit)
                .whenResult(entries -> streamResults(entries, startMs, responseObserver))
                .whenException(ex -> {
                    log.error("executeQuery(scan) failed for tenant={}", ctx.tenantId(), ex);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("Query failed: " + ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException());
                });
    }

    private static void streamResults(
            List<EventLogStore.EventEntry> entries,
            long startMs,
            StreamObserver<ExecuteQueryResponseProto> responseObserver) {

        long execTimeMs = System.currentTimeMillis() - startMs;
        for (EventLogStore.EventEntry entry : entries) {
            Struct row = entryToStruct(entry);
            responseObserver.onNext(ExecuteQueryResponseProto.newBuilder()
                    .addResults(row)
                    .setExecutedAt(nowTimestamp())
                    .build());
        }
        responseObserver.onCompleted();
    }

    // ==================== RPC: ExplainQuery ====================

    /**
     * {@inheritDoc}
     *
     * <p>Returns the logical query plan without executing against the store.
     * For type-scoped queries the plan describes a filtered warm-tier scan;
     * for full scans it describes a sequential warm-tier scan.
     */
    @Override
    public void explainQuery(
            ExplainQueryRequestProto request,
            StreamObserver<ExplainQueryResponseProto> responseObserver) {

        String query = request.getQuery();
        String plan;
        if (query.startsWith("type:")) {
            String eventType = query.substring("type:".length()).trim();
            plan = "Warm-tier event log scan › filter(event_type = '" + eventType + "') › limit";
        } else {
            plan = "Warm-tier event log scan › sequential read › limit";
        }

        responseObserver.onNext(ExplainQueryResponseProto.newBuilder()
                .setValid(true)
                .setPlan(plan)
                .setQuery(query)
                .setExplainedAt(nowTimestamp())
                .build());
        responseObserver.onCompleted();
    }

    // ==================== Helpers ====================

    /**
     * Converts an {@link EventLogStore.EventEntry} to a {@code google.protobuf.Struct}
     * for transmission as a query result row.
     */
    private static Struct entryToStruct(EventLogStore.EventEntry entry) {
        Struct.Builder struct = Struct.newBuilder();
        putString(struct, "event_id", entry.eventId() != null ? entry.eventId().toString() : "");
        putString(struct, "event_type", entry.eventType());
        putString(struct, "event_version", entry.eventVersion());
        putString(struct, "timestamp", entry.timestamp() != null
                ? entry.timestamp().toString() : "");
        putString(struct, "content_type", entry.contentType());
        if (entry.payload() != null && entry.payload().remaining() > 0) {
            byte[] bytes = new byte[entry.payload().remaining()];
            entry.payload().slice().get(bytes);
            putString(struct, "payload", new String(bytes, StandardCharsets.UTF_8));
        }
        return struct.build();
    }

    private static void putString(Struct.Builder struct, String key, String value) {
        struct.putFields(key, Value.newBuilder().setStringValue(value != null ? value : "").build());
    }

    private static String resolveTenant(String fromEnvelope) {
        if (fromEnvelope != null && !fromEnvelope.isBlank()) {
            return fromEnvelope;
        }
        String fromCtx = TenantGrpcInterceptor.TENANT_ID_CTX_KEY.get();
        return (fromCtx != null && !fromCtx.isBlank()) ? fromCtx : FALLBACK_TENANT;
    }

    private static int resolveLimit(int requested) {
        if (requested <= 0) return DEFAULT_QUERY_LIMIT;
        return Math.min(requested, MAX_QUERY_LIMIT);
    }

    private static Timestamp nowTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }
}
