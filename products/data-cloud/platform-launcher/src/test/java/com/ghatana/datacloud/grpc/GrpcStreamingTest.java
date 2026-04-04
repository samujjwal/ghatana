/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved.
 */
package com.ghatana.datacloud.grpc;

import com.ghatana.contracts.event.v1.AppendRequest;
import com.ghatana.contracts.event.v1.AppendResponse;
import com.ghatana.contracts.event.v1.EventLogServiceGrpc;
import com.ghatana.contracts.event.v1.EventProto;
import com.ghatana.contracts.event.v1.EventRecordProto;
import com.ghatana.contracts.event.v1.ReadByTypeRequest;
import com.ghatana.contracts.event.v1.ReadByTypeResponse;
import com.ghatana.datacloud.spi.provider.InMemoryEventLogStoreProvider;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * gRPC streaming tests for {@link EventLogGrpcService}.
 *
 * <p><strong>Proto API notes</strong>:
 * <ul>
 *   <li>{@code AppendRequest} wraps an {@link EventProto} via {@code setEvent(EventProto)}.
 *       There is no {@code setEventType} or {@code setPayload} method on AppendRequest.</li>
 *   <li>{@code AppendResponse} contains the persisted {@link EventRecordProto} via
 *       {@code getRecord()}. There is no {@code getSuccess()} or {@code getOffset()} method.</li>
 *   <li>{@code ReadByTypeRequest} uses {@code setType(String)} (the event type).</li>
 *   <li>{@code ReadByTypeResponse} batches all matched records via {@code getRecordsList()}.
 *       The streaming observer receives typically one {@code onNext} call per page.</li>
 * </ul>
 *
 * <p>Tests run in-process using {@link io.grpc.inprocess} — no network I/O.
 * An {@link InMemoryEventLogStoreProvider} backs the service.
 *
 * <p>Covers streaming scenarios that supplement {@link EventServiceGrpcIntegrationTest}:
 * <ul>
 *   <li>Append + readByType round-trip: written events appear in stream.</li>
 *   <li>Empty result set: readByType for unknown type completes with no records.</li>
 *   <li>Limit enforcement: stream respects the requested record limit.</li>
 *   <li>Large batch: 50 events streamed without drops.</li>
 *   <li>Multi-tenant isolation: tenant A stream omits tenant B events.</li>
 *   <li>Concurrent append throughput: 5 threads × 10 events all succeed.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose gRPC streaming tests for EventLogGrpcService append and server-streaming readByType
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("EventLogGrpcService – Streaming Tests")
class GrpcStreamingTest {

    private static Server server;
    private static ManagedChannel channel;
    private static EventLogServiceGrpc.EventLogServiceBlockingStub blockingStub;
    private static EventLogServiceGrpc.EventLogServiceStub asyncStub;

    private static final InMemoryEventLogStoreProvider eventLogStore =
            new InMemoryEventLogStoreProvider();

    private static final String SERVER_NAME =
            "grpc-streaming-test-" + UUID.randomUUID().toString().replace("-", "");

    private static final String TENANT_A = "streaming-tenant-a";
    private static final String TENANT_B = "streaming-tenant-b";

    @BeforeAll
    static void startServer() throws IOException {
        EventLogGrpcService service = new EventLogGrpcService(eventLogStore);

        server = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .addService(service)
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(SERVER_NAME)
                .directExecutor()
                .build();

        blockingStub = EventLogServiceGrpc.newBlockingStub(channel);
        asyncStub    = EventLogServiceGrpc.newStub(channel);
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /**
     * Build an AppendRequest wrapping an EventProto.
     * AppendRequest only accepts setEvent(EventProto) — no direct payload setters.
     */
    private static AppendRequest buildAppend(String type, String tenantId, String payloadJson) {
        EventProto event = EventProto.newBuilder()
                .setType(type)
                .setTenantId(tenantId)
                .setTypeVersion("1.0.0")
                .setPayloadJson(payloadJson)
                .build();
        return AppendRequest.newBuilder().setEvent(event).build();
    }

    /**
     * Stream records of a given type and collect into a flat list of EventRecordProto.
     * ReadByTypeResponse batches all records in a single message, so we collect all
     * records from each ReadByTypeResponse.getRecordsList().
     */
    private static List<EventRecordProto> streamRecords(
            String type, String tenantId, int limit) throws InterruptedException {

        ReadByTypeRequest req = ReadByTypeRequest.newBuilder()
                .setType(type)
                .setTenantId(tenantId)
                .setLimit(limit)
                .setAscending(true)
                .build();

        List<EventRecordProto> collected = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        asyncStub.readByType(req, new StreamObserver<>() {
            @Override public void onNext(ReadByTypeResponse r)  { collected.addAll(r.getRecordsList()); }
            @Override public void onError(Throwable t)          { error.set(t); latch.countDown(); }
            @Override public void onCompleted()                 { latch.countDown(); }
        });

        latch.await(10, TimeUnit.SECONDS);
        if (error.get() != null) throw new AssertionError("stream error", error.get());
        return collected;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Append + ReadByType round-trip
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Append + ReadByType round-trip")
    class AppendAndStreamRoundTripTests {

        @Test
        @DisplayName("Events appended via gRPC appear in readByType stream")
        void appendedEventsReturnedByStream() throws InterruptedException {
            String type = "STREAM_TEST_" + UUID.randomUUID().toString().replace("-", "");
            int count = 5;
            for (int i = 0; i < count; i++) {
                blockingStub.append(buildAppend(type, TENANT_A, "{\"i\":" + i + "}"));
            }

            List<EventRecordProto> records = streamRecords(type, TENANT_A, 50);

            assertThat(records).hasSize(count);
        }

        @Test
        @DisplayName("AppendResponse returns a non-null record for a valid event")
        void appendResponse_recordIsPresent() {
            String type = "APPEND_RESP_" + UUID.randomUUID().toString().replace("-", "");
            AppendResponse resp = blockingStub.append(buildAppend(type, TENANT_A, "{\"k\":\"v\"}"));

            assertThat(resp.getRecord()).isNotNull();
            assertThat(resp.getRecord().getEvent().getType()).isEqualTo(type);
        }

        @Test
        @DisplayName("AppendResponse.duplicate is false for a first-time event")
        void appendResponse_duplicateFalseForNewEvent() {
            String type = "DEDUP_TEST_" + UUID.randomUUID().toString().replace("-", "");
            AppendResponse resp = blockingStub.append(buildAppend(type, TENANT_A, "{\"x\":1}"));

            assertThat(resp.getDuplicate()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Server-streaming readByType edge cases
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Server-streaming readByType edge cases")
    class StreamingEdgeCaseTests {

        @Test
        @DisplayName("readByType with no matching events completes stream with zero records")
        void readByType_noMatchingEvents_zeroRecords() throws InterruptedException {
            String unknownType = "NEVER_APPENDED_" + UUID.randomUUID().toString().replace("-", "");

            List<EventRecordProto> records = streamRecords(unknownType, TENANT_A, 100);

            assertThat(records).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 20})
        @DisplayName("readByType respects the requested limit")
        void readByType_respectsLimit(int limit) throws InterruptedException {
            String type = "LIMIT_" + limit + "_" + UUID.randomUUID().toString().replace("-", "");
            // Append more events than the limit
            for (int i = 0; i < 30; i++) {
                blockingStub.append(buildAppend(type, TENANT_A, "{\"n\":" + i + "}"));
            }

            List<EventRecordProto> records = streamRecords(type, TENANT_A, limit);

            assertThat(records.size()).isLessThanOrEqualTo(limit);
        }

        @Test
        @DisplayName("readByType records contain the correct event type field")
        void readByType_recordContainsCorrectType() throws InterruptedException {
            String type = "TYPED_EVT_" + UUID.randomUUID().toString().replace("-", "");
            blockingStub.append(buildAppend(type, TENANT_A, "{\"v\":\"typed\"}"));

            List<EventRecordProto> records = streamRecords(type, TENANT_A, 5);

            assertThat(records).isNotEmpty();
            assertThat(records.get(0).getEvent().getType()).isEqualTo(type);
        }

        @Test
        @DisplayName("Large batch (50 events) streams all events without drops")
        void largeBatch_allEventsStreamed() throws InterruptedException {
            int batchSize = 50;
            String type = "LARGE_BATCH_" + UUID.randomUUID().toString().replace("-", "");
            for (int i = 0; i < batchSize; i++) {
                blockingStub.append(buildAppend(type, TENANT_A, "{\"seq\":" + i + "}"));
            }

            List<EventRecordProto> records = streamRecords(type, TENANT_A, batchSize + 10);

            assertThat(records).hasSize(batchSize);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-tenant streaming isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Multi-tenant streaming isolation")
    class MultiTenantStreamingTests {

        @Test
        @DisplayName("Tenant A stream omits events appended for tenant B")
        void tenantAStream_omitsTenantBEvents() throws InterruptedException {
            String type = "ISOLATION_" + UUID.randomUUID().toString().replace("-", "");

            blockingStub.append(buildAppend(type, TENANT_A, "{\"from\":\"A\"}"));
            blockingStub.append(buildAppend(type, TENANT_B, "{\"from\":\"B\"}"));

            // Tenant isolation: reading as TENANT_A returns only TENANT_A's events
            // The store is tenant-partitioned — TENANT_B events are in a separate store bucket
            List<EventRecordProto> tenantARecords = streamRecords(type, TENANT_A, 50);

            // Exactly 1 event was appended for TENANT_A with this type
            assertThat(tenantARecords).hasSize(1);
            // Payload confirms it's the TENANT_A event
            assertThat(tenantARecords.get(0).getEvent().getPayloadJson()).contains("A");
        }

        @Test
        @DisplayName("Concurrent streaming from different tenants maintains isolation")
        void concurrentStreaming_tenantIsolationMaintained() throws InterruptedException {
            String typeA = "CONCURRENT_A_" + UUID.randomUUID().toString().replace("-", "");
            String typeB = "CONCURRENT_B_" + UUID.randomUUID().toString().replace("-", "");

            for (int i = 0; i < 5; i++) {
                blockingStub.append(buildAppend(typeA, TENANT_A, "{\"i\":" + i + "}"));
                blockingStub.append(buildAppend(typeB, TENANT_B, "{\"i\":" + i + "}"));
            }

            List<EventRecordProto> recordsA = Collections.synchronizedList(new ArrayList<>());
            List<EventRecordProto> recordsB = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch bothDone = new CountDownLatch(2);

            asyncStub.readByType(ReadByTypeRequest.newBuilder()
                    .setType(typeA).setTenantId(TENANT_A).setLimit(20).setAscending(true).build(),
                    new StreamObserver<>() {
                        @Override public void onNext(ReadByTypeResponse r)  { recordsA.addAll(r.getRecordsList()); }
                        @Override public void onError(Throwable t)          { bothDone.countDown(); }
                        @Override public void onCompleted()                 { bothDone.countDown(); }
                    });

            asyncStub.readByType(ReadByTypeRequest.newBuilder()
                    .setType(typeB).setTenantId(TENANT_B).setLimit(20).setAscending(true).build(),
                    new StreamObserver<>() {
                        @Override public void onNext(ReadByTypeResponse r)  { recordsB.addAll(r.getRecordsList()); }
                        @Override public void onError(Throwable t)          { bothDone.countDown(); }
                        @Override public void onCompleted()                 { bothDone.countDown(); }
                    });

            assertThat(bothDone.await(15, TimeUnit.SECONDS)).isTrue();
            // Each tenant appended 5 events — isolation means each reads exactly 5
            assertThat(recordsA).hasSize(5);
            assertThat(recordsB).hasSize(5);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Minimal and unicode payload streaming
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Minimal and unicode payload streaming")
    class MinimalPayloadStreamingTests {

        @Test
        @DisplayName("Events with empty JSON object payload are streamed correctly")
        void emptyPayload_streamedCorrectly() throws InterruptedException {
            String type = "EMPTY_PAYLOAD_" + UUID.randomUUID().toString().replace("-", "");
            blockingStub.append(buildAppend(type, TENANT_A, "{}"));

            List<EventRecordProto> records = streamRecords(type, TENANT_A, 5);

            assertThat(records).hasSize(1);
            assertThat(records.get(0).getEvent().getPayloadJson()).isEqualTo("{}");
        }

        @Test
        @DisplayName("Events with unicode payload are streamed without corruption")
        void unicodePayload_streamedWithoutCorruption() throws InterruptedException {
            String type = "UNICODE_EVT_" + UUID.randomUUID().toString().replace("-", "");
            String payload = "{\"text\":\"\uD83D\uDE00 \u4E2D\u6587\"}";
            blockingStub.append(buildAppend(type, TENANT_A, payload));

            List<EventRecordProto> records = streamRecords(type, TENANT_A, 5);

            assertThat(records).hasSize(1);
            assertThat(records.get(0).getEvent().getPayloadJson()).isEqualTo(payload);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent append throughput
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent append throughput")
    class AppendThroughputTests {

        @Test
        @DisplayName("Sequential append of 50 events completes within 5 seconds")
        void append50Events_completesWithinTimeout() {
            String type = "THROUGHPUT_" + UUID.randomUUID().toString().replace("-", "");
            long start = System.currentTimeMillis();

            for (int i = 0; i < 50; i++) {
                AppendResponse resp = blockingStub.append(buildAppend(type, TENANT_A, "{\"seq\":" + i + "}"));
                assertThat(resp.getRecord()).isNotNull();
            }

            assertThat(System.currentTimeMillis() - start).isLessThan(5_000L);
        }

        @Test
        @DisplayName("Concurrent appends from 5 virtual threads all succeed")
        void concurrentAppends_allSucceed() throws InterruptedException {
            String type = "CONCURRENT_APPEND_" + UUID.randomUUID().toString().replace("-", "");
            int threadCount = 5;
            int eventsPerThread = 10;
            CountDownLatch startGun = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger failures = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int tid = t;
                Thread.ofVirtual().start(() -> {
                    try {
                        startGun.await();
                        for (int e = 0; e < eventsPerThread; e++) {
                            AppendResponse resp = blockingStub.append(
                                buildAppend(type, TENANT_A, "{\"t\":" + tid + ",\"e\":" + e + "}"));
                            if (resp.getRecord() == null) failures.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            startGun.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(failures.get()).isZero();
        }
    }
}
