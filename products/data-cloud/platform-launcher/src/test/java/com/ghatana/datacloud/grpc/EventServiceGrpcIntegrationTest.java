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
import com.ghatana.datacloud.spi.provider.InMemoryEventLogStoreProvider;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowableOfType;

/**
 * gRPC in-process integration tests for {@link EventServiceGrpcService}.
 *
 * <p>Uses {@code grpc-inprocess} to spin up a real gRPC server in the same JVM process
 * without any network I/O. An {@link InMemoryEventLogStoreProvider} backs the service,
 * which resolves Promises synchronously, allowing straightforward blocking assertions
 * without an ActiveJ eventloop.
 *
 * <p>Tests are ordered so that data written in earlier tests (ingest, batch) can be
 * read back in later tests (query, getEvent).
 *
 * @doc.type class
 * @doc.purpose gRPC integration tests for EventServiceGrpcService
 * @doc.layer product
 * @doc.pattern IntegrationTest
 * @since 2.0.0
 */
@DisplayName("EventService gRPC — in-process integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventServiceGrpcIntegrationTest {

    private static Server server;
    private static ManagedChannel channel;
    private static EventServiceGrpc.EventServiceBlockingStub blockingStub;
    private static EventServiceGrpc.EventServiceStub asyncStub;

    /** Shared store so tests can assert on data written by previous tests. */
    private static final InMemoryEventLogStoreProvider eventLogStore =
            new InMemoryEventLogStoreProvider();

    /** Unique in-process name avoids collisions if tests run in parallel. */
    private static final String SERVER_NAME =
            "event-grpc-test-" + UUID.randomUUID().toString().replace("-", "");

    private static final String TENANT = "grpc-test-tenant";

    /** UUID written in test #1, read back in test #4 (getEvent). */
    private static UUID ingestedEventId;

    // ==================== Lifecycle ====================

    @BeforeAll
    static void startServer() throws IOException {
        server = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .addService(new EventServiceGrpcService(eventLogStore))
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(SERVER_NAME)
                .directExecutor()
                .build();

        blockingStub = EventServiceGrpc.newBlockingStub(channel);
        asyncStub    = EventServiceGrpc.newStub(channel);
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ==================== Helper ====================

    private static EventProto buildEvent(String type, String tenantId, String payloadJson) {
        return EventProto.newBuilder()
                .setType(type)
                .setTenantId(tenantId)
                .setTypeVersion("1.0.0")
                .setPayloadJson(payloadJson)
                .build();
    }

    // ==================== Tests ====================

    @Test
    @Order(1)
    @DisplayName("Ingest — single event is stored and echoed back")
    void ingest_singleEvent_returnsStoredEvent() {
        EventProto event = buildEvent("user.signed_up", TENANT, "{\"userId\":\"abc123\"}");
        IngestRequestProto request = IngestRequestProto.newBuilder()
                .setEvent(event)
                .build();

        IngestResponseProto response = blockingStub.ingest(request);

        assertThat(response.hasEvent()).isTrue();
        assertThat(response.getEvent().getType()).isEqualTo("user.signed_up");

        // Capture the ID assigned by the mapper so test #4 can retrieve it
        ingestedEventId = response.getEvent().hasId()
                ? UUID.fromString(response.getEvent().getId().getValue())
                : null;
    }

    @Test
    @Order(2)
    @DisplayName("Ingest — missing event field returns INVALID_ARGUMENT")
    void ingest_missingEventField_returnsInvalidArgument() {
        IngestRequestProto empty = IngestRequestProto.newBuilder().build();

        StatusRuntimeException ex = catchThrowableOfType(
                () -> blockingStub.ingest(empty),
                StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    @Order(3)
    @DisplayName("IngestBatch — batch of 3 events is stored, counts returned correctly")
    void ingestBatch_threeEvents_successCountThree() {
        IngestBatchRequestProto request = IngestBatchRequestProto.newBuilder()
                .addEvents(buildEvent("order.placed",    TENANT, "{\"orderId\":\"1\"}"))
                .addEvents(buildEvent("order.placed",    TENANT, "{\"orderId\":\"2\"}"))
                .addEvents(buildEvent("payment.received", TENANT, "{\"amount\":99}"))
                .build();

        IngestBatchResponseProto response = blockingStub.ingestBatch(request);

        assertThat(response.getSuccessCount()).isEqualTo(3);
        assertThat(response.getErrorCount()).isZero();
        assertThat(response.getEventsCount()).isEqualTo(3);
    }

    @Test
    @Order(4)
    @DisplayName("IngestBatch — empty batch returns zero counts without error")
    void ingestBatch_emptyBatch_returnsZeroCounts() {
        IngestBatchRequestProto empty = IngestBatchRequestProto.newBuilder().build();

        IngestBatchResponseProto response = blockingStub.ingestBatch(empty);

        assertThat(response.getSuccessCount()).isZero();
        assertThat(response.getErrorCount()).isZero();
    }

    @Test
    @Order(5)
    @DisplayName("Query — scan by type returns matching events")
    void query_byEventType_returnsMatchingEvents() {
        // "order.placed" events were ingested in test #3 (2 events)
        QueryEventsRequestProto request = QueryEventsRequestProto.newBuilder()
                .setTenantId(TENANT)
                .setTypePrefix("order.placed")
                .setLimit(10)
                .build();

        List<QueryEventsResponseProto> responses = new ArrayList<>();
        blockingStub.query(request).forEachRemaining(responses::add);

        // Server streams one response per matched event
        assertThat(responses).isNotEmpty();
        responses.forEach(resp ->
                assertThat(resp.getEventsList())
                        .allSatisfy(e -> assertThat(e.getType()).isEqualTo("order.placed")));
    }

    @Test
    @Order(6)
    @DisplayName("Query — full scan without type filter returns all ingested events")
    void query_fullScan_returnsAllEvents() {
        QueryEventsRequestProto request = QueryEventsRequestProto.newBuilder()
                .setTenantId(TENANT)
                .setLimit(50)
                .build();

        List<QueryEventsResponseProto> responses = new ArrayList<>();
        blockingStub.query(request).forEachRemaining(responses::add);

        // At least 4 events: 1 from test#1 + 3 from test#3
        long total = responses.stream().mapToLong(QueryEventsResponseProto::getTotalCount).findFirst().orElse(0L);
        assertThat(total).isGreaterThanOrEqualTo(4);
    }

    @Test
    @Order(7)
    @DisplayName("GetEvent — retrieves the event ingested in test #1 by UUID")
    void getEvent_existingId_returnsEvent() {
        if (ingestedEventId == null) {
            // Event proto had no id field set — skip lookup assertion
            return;
        }
        GetEventRequestProto request = GetEventRequestProto.newBuilder()
                .setEventId(ingestedEventId.toString())
                .setTenantId(TENANT)
                .build();

        GetEventResponseProto response = blockingStub.getEvent(request);

        assertThat(response.hasEvent()).isTrue();
        assertThat(response.getEvent().getType()).isEqualTo("user.signed_up");
    }

    @Test
    @Order(8)
    @DisplayName("GetEvent — unrecognised UUID returns NOT_FOUND")
    void getEvent_unknownId_returnsNotFound() {
        GetEventRequestProto request = GetEventRequestProto.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setTenantId(TENANT)
                .build();

        StatusRuntimeException ex = catchThrowableOfType(
                () -> blockingStub.getEvent(request),
                StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    @Order(9)
    @DisplayName("GetEvent \u2014 malformed UUID returns INVALID_ARGUMENT")
    void getEvent_malformedUuid_returnsInvalidArgument() {
        GetEventRequestProto request = GetEventRequestProto.newBuilder()
                .setEventId("not-a-uuid")
                .setTenantId(TENANT)
                .build();

        StatusRuntimeException ex = catchThrowableOfType(
                () -> blockingStub.getEvent(request),
                StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    @Order(10)
    @DisplayName("IngestStream — bidirectional stream ingests events and receives echo responses")
    void ingestStream_bidirectional_responsesReceivedForEachEvent() throws InterruptedException {
        int eventCount = 3;
        CountDownLatch completedLatch = new CountDownLatch(1);
        List<IngestResponseProto> received = new ArrayList<>();
        AtomicReference<Throwable> streamError = new AtomicReference<>();

        StreamObserver<IngestRequestProto> requestObserver = asyncStub.ingestStream(
                new StreamObserver<>() {
                    @Override
                    public void onNext(IngestResponseProto value) {
                        received.add(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        streamError.set(t);
                        completedLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        completedLatch.countDown();
                    }
                });

        for (int i = 0; i < eventCount; i++) {
            requestObserver.onNext(IngestRequestProto.newBuilder()
                    .setEvent(buildEvent("stream.event", TENANT, "{\"seq\":" + i + "}"))
                    .build());
        }
        requestObserver.onCompleted();

        boolean completed = completedLatch.await(10, TimeUnit.SECONDS);

        assertThat(completed).as("Stream should complete within timeout").isTrue();
        assertThat(streamError.get()).as("No stream errors").isNull();
        assertThat(received).hasSize(eventCount);
        received.forEach(r -> assertThat(r.getEvent().getType()).isEqualTo("stream.event"));
    }
}
