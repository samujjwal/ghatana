/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event.spi;

import com.ghatana.contracts.event.v1.EventProto;
import com.ghatana.contracts.event.v1.EventServiceGrpc;
import com.ghatana.contracts.event.v1.IngestRequestProto;
import com.ghatana.contracts.event.v1.QueryEventsRequestProto;
import com.ghatana.contracts.event.v1.QueryEventsResponseProto;
import com.ghatana.contracts.event.v1.UuidProto;
import com.google.protobuf.ByteString;
import io.activej.promise.Promise;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link EventCloudConnector} implementation that publishes and subscribes via gRPC.
 *
 * <p>Selected when {@code EVENT_CLOUD_TRANSPORT=grpc}. The gRPC endpoint is
 * configured via {@code AEP_GRPC_ENDPOINT} (default: {@code localhost:50051}).
 *
 * <p>Uses {@link EventServiceGrpc} generated stubs from {@code platform:contracts}.
 * A {@link ManagedChannel} is created at construction time and shared across
 * all calls. The channel is shut down via {@link #close()}.
 *
 * <p>All blocking channel operations are wrapped with
 * {@code Promise.ofBlocking(executor, …)} to avoid stalling the ActiveJ eventloop.
 *
 * @doc.type class
 * @doc.purpose gRPC-based EventCloudConnector for high-throughput event transport
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class GrpcEventCloudConnector implements EventCloudConnector {

    private static final Logger log = LoggerFactory.getLogger(GrpcEventCloudConnector.class);

    /** Timeout for unary RPC calls (publish). */
    private static final long RPC_TIMEOUT_SEC = 10L;

    private final String endpoint;
    private final Executor blockingExecutor;
    private final ManagedChannel channel;
    private final EventServiceGrpc.EventServiceBlockingStub stub;

    /**
     * @param endpoint        gRPC server endpoint in {@code host:port} format (never blank)
     * @param blockingExecutor executor for blocking channel reads/writes
     */
    public GrpcEventCloudConnector(String endpoint, Executor blockingExecutor) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("gRPC endpoint must not be blank");
        }
        this.endpoint = endpoint;
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor required");

        // Parse host:port
        String[] parts = endpoint.split(":", 2);
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 50051;

        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()  // TLS termination at the infrastructure layer (mTLS via service mesh)
                .build();
        this.stub = EventServiceGrpc.newBlockingStub(channel);
        log.info("[GrpcEventCloudConnector] channel opened to endpoint={}", endpoint);
    }

    @Override
    public Promise<String> publish(String topic, byte[] payload) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(payload, "payload required");

        String eventId = UUID.randomUUID().toString();
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                EventProto event = EventProto.newBuilder()
                        .setId(UuidProto.newBuilder().setValue(eventId).build())
                        .setType(topic)
                        .setPayloadProto(ByteString.copyFrom(payload))
                        .build();

                IngestRequestProto request = IngestRequestProto.newBuilder()
                        .setEvent(event)
                        .build();

                stub.withDeadlineAfter(RPC_TIMEOUT_SEC, TimeUnit.SECONDS).ingest(request);

                log.debug("[gRPC] published topic={} size={}B eventId={}", topic, payload.length, eventId);
                return eventId;
            } catch (StatusRuntimeException e) {
                log.error("[gRPC] publish failed topic={} eventId={} status={}: {}",
                        topic, eventId, e.getStatus(), e.getMessage());
                throw new RuntimeException("gRPC publish failed: " + e.getStatus(), e);
            }
        });
    }

    @Override
    public Promise<ConnectorSubscription> subscribe(
            String topic, String consumerGroup, EventPayloadHandler handler) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(consumerGroup, "consumerGroup required");
        Objects.requireNonNull(handler, "handler required");

        return Promise.ofBlocking(blockingExecutor, () -> {
            AtomicBoolean cancelled = new AtomicBoolean(false);

            // Query server-streaming RPC: drives the subscription loop on a virtual thread
            Thread.ofVirtual()
                    .name("grpc-sub-" + topic + "-" + consumerGroup)
                    .start(() -> {
                        while (!cancelled.get()) {
                            try {
                                QueryEventsRequestProto query = QueryEventsRequestProto.newBuilder()
                                        .setTypePrefix(topic)
                                        .setLimit(500)
                                        .build();

                                Iterator<QueryEventsResponseProto> stream =
                                        stub.withDeadlineAfter(30L, TimeUnit.SECONDS).query(query);

                                while (!cancelled.get() && stream.hasNext()) {
                                    QueryEventsResponseProto response = stream.next();
                                    for (EventProto e : response.getEventsList()) {
                                        if (cancelled.get()) break;
                                        try {
                                            handler.onEvent(
                                                    e.getId().getValue(),
                                                    e.getType(),
                                                    e.getPayloadProto().toByteArray());
                                        } catch (Exception handlerEx) {
                                            log.warn("[gRPC] handler error for eventId={}: {}",
                                                    e.getId().getValue(), handlerEx.getMessage());
                                        }
                                    }
                                }

                                // Pause briefly before re-polling to avoid tight loops on empty result
                                if (!cancelled.get()) {
                                    Thread.sleep(500L);
                                }
                            } catch (StatusRuntimeException e) {
                                if (!cancelled.get()) {
                                    log.warn("[gRPC] subscribe stream error topic={}: {}",
                                            topic, e.getStatus());
                                    try { Thread.sleep(2000L); } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        log.info("[gRPC] subscription loop ended for topic={} group={}",
                                topic, consumerGroup);
                    });

            log.info("[gRPC] subscribe started topic={} group={} endpoint={}",
                    topic, consumerGroup, endpoint);
            return new ConnectorSubscription() {
                @Override public void cancel() { cancelled.set(true); }
                @Override public boolean isCancelled() { return cancelled.get(); }
            };
        });
    }

    /**
     * Gracefully shuts down the managed gRPC channel.
     * Call this on application shutdown to release OS resources.
     */
    public void close() {
        try {
            channel.shutdown().awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
        log.info("[GrpcEventCloudConnector] channel closed for endpoint={}", endpoint);
    }

    /** @return the gRPC endpoint this connector is bound to */
    public String getEndpoint() {
        return endpoint;
    }
}

