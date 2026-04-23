/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved. // GH-90000
 */
package com.ghatana.datacloud.grpc;

import com.ghatana.contracts.event.v1.AppendRequest;
import com.ghatana.contracts.event.v1.AppendResponse;
import com.ghatana.contracts.event.v1.EventLogServiceGrpc;
import com.ghatana.contracts.event.v1.EventProto;
import com.ghatana.contracts.event.v1.EventRecordProto;
import com.ghatana.contracts.event.v1.ReadByTypeRequest;
import com.ghatana.contracts.event.v1.ReadByTypeResponse;
import com.ghatana.datacloud.spi.EventLogStoreAdapters;
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
 *   <li>{@code AppendRequest} wraps an {@link EventProto} via {@code setEvent(EventProto)}. // GH-90000
 *       There is no {@code setEventType} or {@code setPayload} method on AppendRequest.</li>
 *   <li>{@code AppendResponse} contains the persisted {@link EventRecordProto} via
 *       {@code getRecord()}. There is no {@code getSuccess()} or {@code getOffset()} method.</li> // GH-90000
 *   <li>{@code ReadByTypeRequest} uses {@code setType(String)} (the event type).</li> // GH-90000
 *   <li>{@code ReadByTypeResponse} batches all matched records via {@code getRecordsList()}. // GH-90000
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
            new InMemoryEventLogStoreProvider(); // GH-90000

    private static final String SERVER_NAME =
            "grpc-streaming-test-" + UUID.randomUUID().toString().replace("-", ""); // GH-90000

    private static final String TENANT_A = "streaming-tenant-a";
    private static final String TENANT_B = "streaming-tenant-b";

    @BeforeAll
    static void startServer() throws IOException { // GH-90000
        EventLogGrpcService service = new EventLogGrpcService( // GH-90000
            EventLogStoreAdapters.toPlatformStore(eventLogStore)); // GH-90000

        server = InProcessServerBuilder.forName(SERVER_NAME) // GH-90000
                .directExecutor() // GH-90000
                .addService(service) // GH-90000
                .build() // GH-90000
                .start(); // GH-90000

        channel = InProcessChannelBuilder.forName(SERVER_NAME) // GH-90000
                .directExecutor() // GH-90000
                .build(); // GH-90000

        blockingStub = EventLogServiceGrpc.newBlockingStub(channel); // GH-90000
        asyncStub    = EventLogServiceGrpc.newStub(channel); // GH-90000
    }

    @AfterAll
    static void stopServer() throws InterruptedException { // GH-90000
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS); // GH-90000
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS); // GH-90000
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /**
     * Build an AppendRequest wrapping an EventProto.
     * AppendRequest only accepts setEvent(EventProto) — no direct payload setters. // GH-90000
     */
    private static AppendRequest buildAppend(String type, String tenantId, String payloadJson) { // GH-90000
        EventProto event = EventProto.newBuilder() // GH-90000
                .setType(type) // GH-90000
                .setTenantId(tenantId) // GH-90000
                .setTypeVersion("1.0.0")
                .setPayloadJson(payloadJson) // GH-90000
                .build(); // GH-90000
        return AppendRequest.newBuilder().setEvent(event).build(); // GH-90000
    }

    /**
     * Stream records of a given type and collect into a flat list of EventRecordProto.
     * ReadByTypeResponse batches all records in a single message, so we collect all
     * records from each ReadByTypeResponse.getRecordsList(). // GH-90000
     */
    private static List<EventRecordProto> streamRecords( // GH-90000
            String type, String tenantId, int limit) throws InterruptedException {

        ReadByTypeRequest req = ReadByTypeRequest.newBuilder() // GH-90000
                .setType(type) // GH-90000
                .setTenantId(tenantId) // GH-90000
                .setLimit(limit) // GH-90000
                .setAscending(true) // GH-90000
                .build(); // GH-90000

        List<EventRecordProto> collected = Collections.synchronizedList(new ArrayList<>()); // GH-90000
        CountDownLatch latch = new CountDownLatch(1); // GH-90000
        AtomicReference<Throwable> error = new AtomicReference<>(); // GH-90000

        asyncStub.readByType(req, new StreamObserver<>() { // GH-90000
            @Override public void onNext(ReadByTypeResponse r)  { collected.addAll(r.getRecordsList()); } // GH-90000
            @Override public void onError(Throwable t)          { error.set(t); latch.countDown(); } // GH-90000
            @Override public void onCompleted()                 { latch.countDown(); } // GH-90000
        });

        latch.await(10, TimeUnit.SECONDS); // GH-90000
        if (error.get() != null) throw new AssertionError("stream error", error.get()); // GH-90000
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
        void appendedEventsReturnedByStream() throws InterruptedException { // GH-90000
            String type = "STREAM_TEST_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            int count = 5;
            for (int i = 0; i < count; i++) { // GH-90000
                blockingStub.append(buildAppend(type, TENANT_A, "{\"i\":" + i + "}")); // GH-90000
            }

            List<EventRecordProto> records = streamRecords(type, TENANT_A, 50); // GH-90000

            assertThat(records).hasSize(count); // GH-90000
        }

        @Test
        @DisplayName("AppendResponse returns a non-null record for a valid event")
        void appendResponse_recordIsPresent() { // GH-90000
            String type = "APPEND_RESP_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            AppendResponse resp = blockingStub.append(buildAppend(type, TENANT_A, "{\"k\":\"v\"}")); // GH-90000

            assertThat(resp.getRecord()).isNotNull(); // GH-90000
            assertThat(resp.getRecord().getEvent().getType()).isEqualTo(type); // GH-90000
        }

        @Test
        @DisplayName("AppendResponse.duplicate is false for a first-time event")
        void appendResponse_duplicateFalseForNewEvent() { // GH-90000
            String type = "DEDUP_TEST_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            AppendResponse resp = blockingStub.append(buildAppend(type, TENANT_A, "{\"x\":1}")); // GH-90000

            assertThat(resp.getDuplicate()).isFalse(); // GH-90000
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
        void readByType_noMatchingEvents_zeroRecords() throws InterruptedException { // GH-90000
            String unknownType = "NEVER_APPENDED_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000

            List<EventRecordProto> records = streamRecords(unknownType, TENANT_A, 100); // GH-90000

            assertThat(records).isEmpty(); // GH-90000
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 20}) // GH-90000
        @DisplayName("readByType respects the requested limit")
        void readByType_respectsLimit(int limit) throws InterruptedException { // GH-90000
            String type = "LIMIT_" + limit + "_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            // Append more events than the limit
            for (int i = 0; i < 30; i++) { // GH-90000
                blockingStub.append(buildAppend(type, TENANT_A, "{\"n\":" + i + "}")); // GH-90000
            }

            List<EventRecordProto> records = streamRecords(type, TENANT_A, limit); // GH-90000

            assertThat(records.size()).isLessThanOrEqualTo(limit); // GH-90000
        }

        @Test
        @DisplayName("readByType records contain the correct event type field")
        void readByType_recordContainsCorrectType() throws InterruptedException { // GH-90000
            String type = "TYPED_EVT_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            blockingStub.append(buildAppend(type, TENANT_A, "{\"v\":\"typed\"}")); // GH-90000

            List<EventRecordProto> records = streamRecords(type, TENANT_A, 5); // GH-90000

            assertThat(records).isNotEmpty(); // GH-90000
            assertThat(records.get(0).getEvent().getType()).isEqualTo(type); // GH-90000
        }

        @Test
        @DisplayName("Large batch (50 events) streams all events without drops")
        void largeBatch_allEventsStreamed() throws InterruptedException { // GH-90000
            int batchSize = 50;
            String type = "LARGE_BATCH_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            for (int i = 0; i < batchSize; i++) { // GH-90000
                blockingStub.append(buildAppend(type, TENANT_A, "{\"seq\":" + i + "}")); // GH-90000
            }

            List<EventRecordProto> records = streamRecords(type, TENANT_A, batchSize + 10); // GH-90000

            assertThat(records).hasSize(batchSize); // GH-90000
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
        void tenantAStream_omitsTenantBEvents() throws InterruptedException { // GH-90000
            String type = "ISOLATION_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000

            blockingStub.append(buildAppend(type, TENANT_A, "{\"from\":\"A\"}")); // GH-90000
            blockingStub.append(buildAppend(type, TENANT_B, "{\"from\":\"B\"}")); // GH-90000

            // Tenant isolation: reading as TENANT_A returns only TENANT_A's events
            // The store is tenant-partitioned — TENANT_B events are in a separate store bucket
            List<EventRecordProto> tenantARecords = streamRecords(type, TENANT_A, 50); // GH-90000

            // Exactly 1 event was appended for TENANT_A with this type
            assertThat(tenantARecords).hasSize(1); // GH-90000
            // Payload confirms it's the TENANT_A event
            assertThat(tenantARecords.get(0).getEvent().getPayloadJson()).contains("A");
        }

        @Test
        @DisplayName("Concurrent streaming from different tenants maintains isolation")
        void concurrentStreaming_tenantIsolationMaintained() throws InterruptedException { // GH-90000
            String typeA = "CONCURRENT_A_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            String typeB = "CONCURRENT_B_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                blockingStub.append(buildAppend(typeA, TENANT_A, "{\"i\":" + i + "}")); // GH-90000
                blockingStub.append(buildAppend(typeB, TENANT_B, "{\"i\":" + i + "}")); // GH-90000
            }

            List<EventRecordProto> recordsA = Collections.synchronizedList(new ArrayList<>()); // GH-90000
            List<EventRecordProto> recordsB = Collections.synchronizedList(new ArrayList<>()); // GH-90000
            CountDownLatch bothDone = new CountDownLatch(2); // GH-90000

            asyncStub.readByType(ReadByTypeRequest.newBuilder() // GH-90000
                    .setType(typeA).setTenantId(TENANT_A).setLimit(20).setAscending(true).build(), // GH-90000
                    new StreamObserver<>() { // GH-90000
                        @Override public void onNext(ReadByTypeResponse r)  { recordsA.addAll(r.getRecordsList()); } // GH-90000
                        @Override public void onError(Throwable t)          { bothDone.countDown(); } // GH-90000
                        @Override public void onCompleted()                 { bothDone.countDown(); } // GH-90000
                    });

            asyncStub.readByType(ReadByTypeRequest.newBuilder() // GH-90000
                    .setType(typeB).setTenantId(TENANT_B).setLimit(20).setAscending(true).build(), // GH-90000
                    new StreamObserver<>() { // GH-90000
                        @Override public void onNext(ReadByTypeResponse r)  { recordsB.addAll(r.getRecordsList()); } // GH-90000
                        @Override public void onError(Throwable t)          { bothDone.countDown(); } // GH-90000
                        @Override public void onCompleted()                 { bothDone.countDown(); } // GH-90000
                    });

            assertThat(bothDone.await(15, TimeUnit.SECONDS)).isTrue(); // GH-90000
            // Each tenant appended 5 events — isolation means each reads exactly 5
            assertThat(recordsA).hasSize(5); // GH-90000
            assertThat(recordsB).hasSize(5); // GH-90000
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
        void emptyPayload_streamedCorrectly() throws InterruptedException { // GH-90000
            String type = "EMPTY_PAYLOAD_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            blockingStub.append(buildAppend(type, TENANT_A, "{}")); // GH-90000

            List<EventRecordProto> records = streamRecords(type, TENANT_A, 5); // GH-90000

            assertThat(records).hasSize(1); // GH-90000
            assertThat(records.get(0).getEvent().getPayloadJson()).isEqualTo("{}");
        }

        @Test
        @DisplayName("Events with unicode payload are streamed without corruption")
        void unicodePayload_streamedWithoutCorruption() throws InterruptedException { // GH-90000
            String type = "UNICODE_EVT_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            String payload = "{\"text\":\"\uD83D\uDE00 \u4E2D\u6587\"}";
            blockingStub.append(buildAppend(type, TENANT_A, payload)); // GH-90000

            List<EventRecordProto> records = streamRecords(type, TENANT_A, 5); // GH-90000

            assertThat(records).hasSize(1); // GH-90000
            assertThat(records.get(0).getEvent().getPayloadJson()).isEqualTo(payload); // GH-90000
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
        void append50Events_completesWithinTimeout() { // GH-90000
            String type = "THROUGHPUT_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            long start = System.currentTimeMillis(); // GH-90000

            for (int i = 0; i < 50; i++) { // GH-90000
                AppendResponse resp = blockingStub.append(buildAppend(type, TENANT_A, "{\"seq\":" + i + "}")); // GH-90000
                assertThat(resp.getRecord()).isNotNull(); // GH-90000
            }

            assertThat(System.currentTimeMillis() - start).isLessThan(5_000L); // GH-90000
        }

        @Test
        @DisplayName("Concurrent appends from 5 virtual threads all succeed")
        void concurrentAppends_allSucceed() throws InterruptedException { // GH-90000
            String type = "CONCURRENT_APPEND_" + UUID.randomUUID().toString().replace("-", ""); // GH-90000
            int threadCount = 5;
            int eventsPerThread = 10;
            CountDownLatch startGun = new CountDownLatch(1); // GH-90000
            CountDownLatch done = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger failures = new AtomicInteger(0); // GH-90000

            for (int t = 0; t < threadCount; t++) { // GH-90000
                final int tid = t;
                Thread.ofVirtual().start(() -> { // GH-90000
                    try {
                        startGun.await(); // GH-90000
                        for (int e = 0; e < eventsPerThread; e++) { // GH-90000
                            AppendResponse resp = blockingStub.append( // GH-90000
                                buildAppend(type, TENANT_A, "{\"t\":" + tid + ",\"e\":" + e + "}")); // GH-90000
                            if (resp.getRecord() == null) failures.incrementAndGet(); // GH-90000
                        }
                    } catch (Exception ex) { // GH-90000
                        failures.incrementAndGet(); // GH-90000
                    } finally {
                        done.countDown(); // GH-90000
                    }
                });
            }

            startGun.countDown(); // GH-90000
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue(); // GH-90000
            assertThat(failures.get()).isZero(); // GH-90000
        }
    }
}
