package com.ghatana.datacloud.grpc;

import com.ghatana.contracts.event.v1.AppendRequest;
import com.ghatana.contracts.event.v1.AppendResponse;
import com.ghatana.contracts.event.v1.EventLogServiceGrpc;
import com.ghatana.contracts.event.v1.EventRecordProto;
import com.ghatana.contracts.event.v1.ReadByTypeRequest;
import com.ghatana.contracts.event.v1.ReadByTypeResponse;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import com.ghatana.platform.types.identity.Offset;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * gRPC service implementation that bridges {@code EventLogService} proto operations to the
 * Data-Cloud {@link EventLogStore} SPI.
 *
 * <p>Implements the two RPCs defined in {@code event_log.proto}:
 * <ul>
 *   <li>{@code Append} — appends a single event and returns its storage record.</li>
 *   <li>{@code ReadByType} — paginates events by type from a given offset.</li>
 * </ul>
 *
 * <p>Tenant context is resolved in the following priority order:
 * <ol>
 *   <li>{@code ReadByTypeRequest.tenant_id} / {@code AppendRequest.event.tenant_id} (proto field)</li>
 *   <li>{@code x-tenant-id} gRPC metadata header propagated by {@link TenantGrpcInterceptor}</li>
 *   <li>Fall-back to {@code "default-tenant"} if neither is present</li>
 * </ol>
 *
 * <p>ActiveJ {@code Promise} callbacks ({@code .whenResult} / {@code .whenException}) run on
 * whichever thread resolves the promise. For the in-memory store this is the calling thread;
 * for the PostgreSQL store it is the eventloop thread. Both are acceptable because
 * {@code StreamObserver} implementations are thread-safe.
 *
 * @doc.type class
 * @doc.purpose gRPC EventLogService implementation bridging proto RPCs to EventLogStore SPI
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public final class EventLogGrpcService extends EventLogServiceGrpc.EventLogServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(EventLogGrpcService.class);
    private static final String FALLBACK_TENANT = "default-tenant";

    private final EventLogStore eventLogStore;

    /**
     * Constructs the service backed by the given store.
     *
     * @param eventLogStore the underlying event log store (must not be null)
     */
    public EventLogGrpcService(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore");
    }

    // ==================== RPC: Append ====================

    /**
     * {@inheritDoc}
     *
     * <p>Converts the incoming {@code EventProto} to an {@link EventLogStore.EventEntry},
     * appends it via the store, and responds with the storage record (log id, partition, offset).
     */
    @Override
    public void append(AppendRequest request, StreamObserver<AppendResponse> responseObserver) {
        if (!request.hasEvent()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("AppendRequest.event is required")
                    .asRuntimeException());
            return;
        }

        String tenantId = resolveAppendTenant(request);
        TenantContext tenant = TenantContext.of(tenantId);
        EventLogStore.EventEntry entry;

        try {
            entry = ProtobufMapper.toEventEntry(request.getEvent());
        } catch (Exception ex) {
            log.warn("Failed to map AppendRequest.event to EventEntry: {}", ex.getMessage(), ex);
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Malformed event proto: " + ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException());
            return;
        }

        eventLogStore.append(tenant, entry)
                .whenResult(offset -> {
                    EventRecordProto record = ProtobufMapper.toEventRecord(entry, offset);
                    AppendResponse response = AppendResponse.newBuilder()
                            .setRecord(record)
                            .setDuplicate(false)
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                })
                .whenException(ex -> {
                    log.error("EventLogStore.append() failed for tenant={}, type={}",
                            tenantId, entry.eventType(), ex);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("Append failed: " + ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException());
                });
    }

    // ==================== RPC: ReadByType ====================

    /**
     * {@inheritDoc}
     *
     * <p>Reads up to {@code limit} events of the requested type starting from {@code from_offset}.
     * Results are returned as a single {@code ReadByTypeResponse} page; the {@code next_offset}
     * field is set to {@code from_offset + results.size()} or {@code -1} when the store returns
     * fewer than {@code limit} records (end reached).
     */
    @Override
    public void readByType(ReadByTypeRequest request, StreamObserver<ReadByTypeResponse> responseObserver) {
        String type = request.getType();
        if (type == null || type.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("ReadByTypeRequest.type is required")
                    .asRuntimeException());
            return;
        }

        String tenantId = resolveReadTenant(request);
        TenantContext tenant = TenantContext.of(tenantId);
        long fromOffsetRaw = request.getFromOffset();
        int limit = request.getLimit() > 0 ? request.getLimit() : 100;
        Offset fromOffset = Offset.of(fromOffsetRaw);

        eventLogStore.readByType(tenant, type, fromOffset, limit)
                .whenResult(entries -> {
                    ReadByTypeResponse.Builder builder = ReadByTypeResponse.newBuilder();

                    List<EventLogStore.EventEntry> list = entries;
                    long nextOffset = list.size() < limit ? -1L : fromOffsetRaw + list.size();

                    for (EventLogStore.EventEntry entry : list) {
                        // Build a synthetic offset for per-entry records (sequential in page)
                        Offset entryOffset = Offset.of(fromOffsetRaw + list.indexOf(entry));
                        builder.addRecords(ProtobufMapper.toEventRecord(entry, entryOffset));
                    }

                    builder.setNextOffset(nextOffset);
                    responseObserver.onNext(builder.build());
                    responseObserver.onCompleted();
                })
                .whenException(ex -> {
                    log.error("EventLogStore.readByType() failed for tenant={}, type={}",
                            tenantId, type, ex);
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("ReadByType failed: " + ex.getMessage())
                            .withCause(ex)
                            .asRuntimeException());
                });
    }

    // ==================== Tenant resolution helpers ====================

    private static String resolveAppendTenant(AppendRequest request) {
        // Proto field takes priority
        String fromProto = request.getEvent().getTenantId();
        if (fromProto != null && !fromProto.isBlank()) {
            return fromProto;
        }
        // Fall back to gRPC interceptor-propagated context key
        String fromCtx = TenantGrpcInterceptor.TENANT_ID_CTX_KEY.get();
        return (fromCtx != null && !fromCtx.isBlank()) ? fromCtx : FALLBACK_TENANT;
    }

    private static String resolveReadTenant(ReadByTypeRequest request) {
        String fromProto = request.getTenantId();
        if (fromProto != null && !fromProto.isBlank()) {
            return fromProto;
        }
        String fromCtx = TenantGrpcInterceptor.TENANT_ID_CTX_KEY.get();
        return (fromCtx != null && !fromCtx.isBlank()) ? fromCtx : FALLBACK_TENANT;
    }
}
