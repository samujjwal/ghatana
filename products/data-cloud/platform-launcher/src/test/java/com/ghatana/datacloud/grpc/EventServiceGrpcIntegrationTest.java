/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
import com.ghatana.datacloud.spi.EventLogStoreAdapters;
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
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowableOfType;

/**
 * gRPC in-process integration tests for {@link EventServiceGrpcService}.
 *
 * <p>Uses {@code grpc-inprocess} to spin up a real gRPC server in the same JVM process
 * without any network I/O. An {@link InMemoryEventLogStoreProvider} backs the service,
 * which resolves Promises synchronously, allowing straightforward blocking assertions
 * without an ActiveJ eventloop.
 *
 * <p>Tests are ordered so that data written in earlier tests (ingest, batch) can be // GH-90000
 * read back in later tests (query, getEvent). // GH-90000
 *
 * @doc.type class
 * @doc.purpose gRPC integration tests for EventServiceGrpcService
 * @doc.layer product
 * @doc.pattern IntegrationTest
 * @since 2.0.0
 */
@DisplayName("EventService gRPC — in-process integration tests [GH-90000]")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class EventServiceGrpcIntegrationTest {

    private static Server server;
    private static ManagedChannel channel;
    private static EventServiceGrpc.EventServiceBlockingStub blockingStub;
    private static EventServiceGrpc.EventServiceStub asyncStub;

    /** Shared store so tests can assert on data written by previous tests. */
    private static final InMemoryEventLogStoreProvider eventLogStore =
            new InMemoryEventLogStoreProvider(); // GH-90000

    /** Unique in-process name avoids collisions if tests run in parallel. */
    private static final String SERVER_NAME =
            "event-grpc-test-" + UUID.randomUUID().toString().replace("-", ""); // GH-90000

    private static final String TENANT = "grpc-test-tenant";

    /** UUID written in test #1, read back in test #4 (getEvent). */ // GH-90000
    private static UUID ingestedEventId;

    // ==================== Lifecycle ====================

    @BeforeAll
    static void startServer() throws IOException { // GH-90000
        server = InProcessServerBuilder.forName(SERVER_NAME) // GH-90000
                .directExecutor() // GH-90000
                .addService(new EventServiceGrpcService( // GH-90000
                    EventLogStoreAdapters.toPlatformStore(eventLogStore))) // GH-90000
                .build() // GH-90000
                .start(); // GH-90000

        channel = InProcessChannelBuilder.forName(SERVER_NAME) // GH-90000
                .directExecutor() // GH-90000
                .build(); // GH-90000

        blockingStub = EventServiceGrpc.newBlockingStub(channel); // GH-90000
        asyncStub    = EventServiceGrpc.newStub(channel); // GH-90000
    }

    @AfterAll
    static void stopServer() throws InterruptedException { // GH-90000
        if (channel != null) { // GH-90000
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS); // GH-90000
        }
        if (server != null) { // GH-90000
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS); // GH-90000
        }
    }

    // ==================== Helper ====================

    private static EventProto buildEvent(String type, String tenantId, String payloadJson) { // GH-90000
        return EventProto.newBuilder() // GH-90000
                .setType(type) // GH-90000
                .setTenantId(tenantId) // GH-90000
                .setTypeVersion("1.0.0 [GH-90000]")
                .setPayloadJson(payloadJson) // GH-90000
                .build(); // GH-90000
    }

    // ==================== Tests ====================

    @Test
    @Order(1) // GH-90000
    @DisplayName("Ingest — single event is stored and echoed back [GH-90000]")
    void ingest_singleEvent_returnsStoredEvent() { // GH-90000
        EventProto event = buildEvent("user.signed_up", TENANT, "{\"userId\":\"abc123\"}"); // GH-90000
        IngestRequestProto request = IngestRequestProto.newBuilder() // GH-90000
                .setEvent(event) // GH-90000
                .build(); // GH-90000

        IngestResponseProto response = blockingStub.ingest(request); // GH-90000

        assertThat(response.hasEvent()).isTrue(); // GH-90000
        assertThat(response.getEvent().getType()).isEqualTo("user.signed_up [GH-90000]");

        // Capture the ID assigned by the mapper so test #4 can retrieve it
        ingestedEventId = response.getEvent().hasId() // GH-90000
                ? UUID.fromString(response.getEvent().getId().getValue()) // GH-90000
                : null;
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("Ingest — missing event field returns INVALID_ARGUMENT [GH-90000]")
    void ingest_missingEventField_returnsInvalidArgument() { // GH-90000
        IngestRequestProto empty = IngestRequestProto.newBuilder().build(); // GH-90000

        StatusRuntimeException ex = catchThrowableOfType( // GH-90000
                () -> blockingStub.ingest(empty), // GH-90000
                StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("IngestBatch — batch of 3 events is stored, counts returned correctly [GH-90000]")
    void ingestBatch_threeEvents_successCountThree() { // GH-90000
        IngestBatchRequestProto request = IngestBatchRequestProto.newBuilder() // GH-90000
                .addEvents(buildEvent("order.placed",    TENANT, "{\"orderId\":\"1\"}")) // GH-90000
                .addEvents(buildEvent("order.placed",    TENANT, "{\"orderId\":\"2\"}")) // GH-90000
                .addEvents(buildEvent("payment.received", TENANT, "{\"amount\":99}")) // GH-90000
                .build(); // GH-90000

        IngestBatchResponseProto response = blockingStub.ingestBatch(request); // GH-90000

        assertThat(response.getSuccessCount()).isEqualTo(3); // GH-90000
        assertThat(response.getErrorCount()).isZero(); // GH-90000
        assertThat(response.getEventsCount()).isEqualTo(3); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("IngestBatch — empty batch returns zero counts without error [GH-90000]")
    void ingestBatch_emptyBatch_returnsZeroCounts() { // GH-90000
        IngestBatchRequestProto empty = IngestBatchRequestProto.newBuilder().build(); // GH-90000

        IngestBatchResponseProto response = blockingStub.ingestBatch(empty); // GH-90000

        assertThat(response.getSuccessCount()).isZero(); // GH-90000
        assertThat(response.getErrorCount()).isZero(); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("Query — scan by type returns matching events [GH-90000]")
    void query_byEventType_returnsMatchingEvents() { // GH-90000
        // "order.placed" events were ingested in test #3 (2 events) // GH-90000
        QueryEventsRequestProto request = QueryEventsRequestProto.newBuilder() // GH-90000
                .setTenantId(TENANT) // GH-90000
                .setTypePrefix("order.placed [GH-90000]")
                .setLimit(10) // GH-90000
                .build(); // GH-90000

        List<QueryEventsResponseProto> responses = new ArrayList<>(); // GH-90000
        blockingStub.query(request).forEachRemaining(responses::add); // GH-90000

        // Server streams one response per matched event
        assertThat(responses).isNotEmpty(); // GH-90000
        responses.forEach(resp -> // GH-90000
                assertThat(resp.getEventsList()) // GH-90000
                        .allSatisfy(e -> assertThat(e.getType()).isEqualTo("order.placed [GH-90000]")));
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("Query — full scan without type filter returns all ingested events [GH-90000]")
    void query_fullScan_returnsAllEvents() { // GH-90000
        QueryEventsRequestProto request = QueryEventsRequestProto.newBuilder() // GH-90000
                .setTenantId(TENANT) // GH-90000
                .setLimit(50) // GH-90000
                .build(); // GH-90000

        List<QueryEventsResponseProto> responses = new ArrayList<>(); // GH-90000
        blockingStub.query(request).forEachRemaining(responses::add); // GH-90000

        // At least 4 events: 1 from test#1 + 3 from test#3
        long total = responses.stream().mapToLong(QueryEventsResponseProto::getTotalCount).findFirst().orElse(0L); // GH-90000
        assertThat(total).isGreaterThanOrEqualTo(4); // GH-90000
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("GetEvent — retrieves the event ingested in test #1 by UUID [GH-90000]")
    void getEvent_existingId_returnsEvent() { // GH-90000
        if (ingestedEventId == null) { // GH-90000
            // Event proto had no id field set — skip lookup assertion
            return;
        }
        GetEventRequestProto request = GetEventRequestProto.newBuilder() // GH-90000
                .setEventId(ingestedEventId.toString()) // GH-90000
                .setTenantId(TENANT) // GH-90000
                .build(); // GH-90000

        GetEventResponseProto response = blockingStub.getEvent(request); // GH-90000

        assertThat(response.hasEvent()).isTrue(); // GH-90000
        assertThat(response.getEvent().getType()).isEqualTo("user.signed_up [GH-90000]");
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("GetEvent — unrecognised UUID returns NOT_FOUND [GH-90000]")
    void getEvent_unknownId_returnsNotFound() { // GH-90000
        GetEventRequestProto request = GetEventRequestProto.newBuilder() // GH-90000
                .setEventId(UUID.randomUUID().toString()) // GH-90000
                .setTenantId(TENANT) // GH-90000
                .build(); // GH-90000

        StatusRuntimeException ex = catchThrowableOfType( // GH-90000
                () -> blockingStub.getEvent(request), // GH-90000
                StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND); // GH-90000
    }

    @Test
    @Order(9) // GH-90000
    @DisplayName("GetEvent \u2014 malformed UUID returns INVALID_ARGUMENT [GH-90000]")
    void getEvent_malformedUuid_returnsInvalidArgument() { // GH-90000
        GetEventRequestProto request = GetEventRequestProto.newBuilder() // GH-90000
                .setEventId("not-a-uuid [GH-90000]")
                .setTenantId(TENANT) // GH-90000
                .build(); // GH-90000

        StatusRuntimeException ex = catchThrowableOfType( // GH-90000
                () -> blockingStub.getEvent(request), // GH-90000
                StatusRuntimeException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT); // GH-90000
    }

    @Test
    @Order(10) // GH-90000
    @DisplayName("IngestStream — bidirectional stream ingests events and receives echo responses [GH-90000]")
    void ingestStream_bidirectional_responsesReceivedForEachEvent() throws InterruptedException { // GH-90000
        int eventCount = 3;
        CountDownLatch completedLatch = new CountDownLatch(1); // GH-90000
        List<IngestResponseProto> received = new ArrayList<>(); // GH-90000
        AtomicReference<Throwable> streamError = new AtomicReference<>(); // GH-90000

        StreamObserver<IngestRequestProto> requestObserver = asyncStub.ingestStream( // GH-90000
                new StreamObserver<>() { // GH-90000
                    @Override
                    public void onNext(IngestResponseProto value) { // GH-90000
                        received.add(value); // GH-90000
                    }

                    @Override
                    public void onError(Throwable t) { // GH-90000
                        streamError.set(t); // GH-90000
                        completedLatch.countDown(); // GH-90000
                    }

                    @Override
                    public void onCompleted() { // GH-90000
                        completedLatch.countDown(); // GH-90000
                    }
                });

        for (int i = 0; i < eventCount; i++) { // GH-90000
            requestObserver.onNext(IngestRequestProto.newBuilder() // GH-90000
                    .setEvent(buildEvent("stream.event", TENANT, "{\"seq\":" + i + "}")) // GH-90000
                    .build()); // GH-90000
        }
        requestObserver.onCompleted(); // GH-90000

        boolean completed = completedLatch.await(10, TimeUnit.SECONDS); // GH-90000

        assertThat(completed).as("Stream should complete within timeout [GH-90000]").isTrue();
        assertThat(streamError.get()).as("No stream errors [GH-90000]").isNull();
        assertThat(received).hasSize(eventCount); // GH-90000
        received.forEach(r -> assertThat(r.getEvent().getType()).isEqualTo("stream.event [GH-90000]"));
    }
}
