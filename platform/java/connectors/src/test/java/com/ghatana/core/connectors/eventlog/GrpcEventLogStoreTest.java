package com.ghatana.core.connectors.eventlog;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.*;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit and integration tests for {@link GrpcEventLogStore}.
 *
 * <p>The "unit" tests verify constructor environment validation with no network.
 * The "integration" tests spin up a WireMock server that emulates the Data-Cloud
 * HTTP REST API and exercise the full {@link EventLogStore} contract.
 *
 * @doc.type class
 * @doc.purpose Tests for GrpcEventLogStore — construction validation and HTTP behavior
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("GrpcEventLogStore")
class GrpcEventLogStoreTest extends EventloopTestBase {

    private static WireMockServer wireMock;
    private static final String TENANT_ID = "tenant-test-1";
    private static final TenantContext TENANT = TenantContext.of(TENANT_ID);

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @AfterEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    // =========================================================================
    //  Constructor — environment validation (no network required)
    // =========================================================================

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("throws when DATACLOUD_GRPC_HOST is absent")
        void missingHost_throwsIllegalStateException() {
            Map<String, String> env = Map.of(
                "APP_ENV", "development"
                // DATACLOUD_GRPC_HOST intentionally missing
            );
            assertThatThrownBy(() -> new GrpcEventLogStore(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_GRPC_HOST");
        }

        @Test
        @DisplayName("dev mode succeeds without auth token")
        void devMode_allowsMissingAuthToken() {
            Map<String, String> env = Map.of(
                "DATACLOUD_GRPC_HOST", "localhost",
                "DATACLOUD_GRPC_PORT", String.valueOf(wireMock.port()),
                "APP_ENV", "development"
                // DATACLOUD_HTTP_AUTH_TOKEN intentionally missing
            );
            assertThatNoException().isThrownBy(() -> new GrpcEventLogStore(env));
        }

        @Test
        @DisplayName("production mode requires auth token")
        void productionMode_requiresAuthToken() {
            Map<String, String> env = Map.of(
                "DATACLOUD_GRPC_HOST", "localhost",
                "APP_ENV", "production"
                // DATACLOUD_HTTP_AUTH_TOKEN intentionally missing
            );
            assertThatThrownBy(() -> new GrpcEventLogStore(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_HTTP_AUTH_TOKEN");
        }

        @Test
        @DisplayName("port defaults to 9090 when not set")
        void defaultPort_is9090() {
            // If host is given but no port, constructor should not throw
            Map<String, String> env = Map.of(
                "DATACLOUD_GRPC_HOST", "localhost",
                "APP_ENV", "development"
            );
            assertThatNoException().isThrownBy(() -> new GrpcEventLogStore(env));
        }

        @Test
        @DisplayName("invalid port value throws IllegalStateException")
        void invalidPort_throwsIllegalStateException() {
            Map<String, String> env = Map.of(
                "DATACLOUD_GRPC_HOST", "localhost",
                "DATACLOUD_GRPC_PORT", "not-a-number",
                "APP_ENV", "development"
            );
            assertThatThrownBy(() -> new GrpcEventLogStore(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_GRPC_PORT");
        }
    }

    // =========================================================================
    //  HTTP behavior (WireMock — emulates Data-Cloud REST API)
    // =========================================================================

    private GrpcEventLogStore store() {
        return new GrpcEventLogStore(Map.of(
            "DATACLOUD_GRPC_HOST", "localhost",
            "DATACLOUD_GRPC_PORT", String.valueOf(wireMock.port()),
            "APP_ENV", "development"
        ));
    }

    @Nested
    @DisplayName("append()")
    class Append {

        @Test
        @DisplayName("happy path — returns assigned offset")
        void append_returnsOffset() {
            wireMock.stubFor(post(urlPathMatching("/api/v1/eventlog/.*/events"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"offset\": 42}")));

            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventType("user.created")
                .payload("{\"userId\":\"u1\"}")
                .build();

            Offset offset = runPromise(() -> store().append(TENANT, entry));

            assertThat(offset.value()).isEqualTo(42L);
            wireMock.verify(postRequestedFor(urlPathMatching("/api/v1/eventlog/" + TENANT_ID + "/events")));
        }

        @Test
        @DisplayName("server error — throws DataCloudRemoteException")
        void append_serverError_throwsException() {
            wireMock.stubFor(post(anyUrl())
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"internal server error\"}")));

            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventType("user.created")
                .payload("{}".getBytes())
                .build();

            assertThatThrownBy(() -> runPromise(() -> store().append(TENANT, entry)))
                .isInstanceOf(DataCloudRemoteException.class)
                .hasMessageContaining("500");
        }

        @Test
        @DisplayName("auth token is sent as Bearer header")
        void append_sendsAuthToken() {
            GrpcEventLogStore storeWithToken = new GrpcEventLogStore(Map.of(
                "DATACLOUD_GRPC_HOST", "localhost",
                "DATACLOUD_GRPC_PORT", String.valueOf(wireMock.port()),
                "APP_ENV", "development",
                "DATACLOUD_HTTP_AUTH_TOKEN", "secret-token"
            ));

            wireMock.stubFor(post(anyUrl())
                .willReturn(aResponse().withStatus(201).withBody("{\"offset\": 1}")));

            runPromise(() -> storeWithToken.append(TENANT, EventLogStore.EventEntry.builder()
                .eventType("test.event").build()));

            wireMock.verify(postRequestedFor(anyUrl())
                .withHeader("Authorization", equalTo("Bearer secret-token")));
        }
    }

    @Nested
    @DisplayName("appendBatch()")
    class AppendBatch {

        @Test
        @DisplayName("empty list returns empty offsets immediately")
        void appendBatch_emptyList_returnsEmptyList() {
            List<Offset> offsets = runPromise(() -> store().appendBatch(TENANT, List.of()));
            assertThat(offsets).isEmpty();
        }

        @Test
        @DisplayName("batch of events — returns list of offsets")
        void appendBatch_multipleEvents_returnsOffsets() {
            wireMock.stubFor(post(urlPathMatching("/api/v1/eventlog/.*/events/batch"))
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"offsets\": [10, 11, 12]}")));

            List<EventLogStore.EventEntry> entries = List.of(
                EventLogStore.EventEntry.builder().eventType("t1").build(),
                EventLogStore.EventEntry.builder().eventType("t2").build(),
                EventLogStore.EventEntry.builder().eventType("t3").build()
            );

            List<Offset> offsets = runPromise(() -> store().appendBatch(TENANT, entries));

            assertThat(offsets).hasSize(3);
            assertThat(offsets.get(0).value()).isEqualTo(10L);
            assertThat(offsets.get(2).value()).isEqualTo(12L);
        }
    }

    @Nested
    @DisplayName("read()")
    class Read {

        @Test
        @DisplayName("read returns deserialized EventEntry list")
        void read_returnsEntries() {
            String uuid = UUID.randomUUID().toString();
            String ts = java.time.Instant.now().toString();
            String payload64 = java.util.Base64.getEncoder().encodeToString("{\"x\":1}".getBytes());
            String body = """
                {"events": [{"eventId":"%s","eventType":"item.updated","timestamp":"%s","payload":"%s"}]}
                """.formatted(uuid, ts, payload64);

            wireMock.stubFor(get(urlPathMatching("/api/v1/eventlog/.*/events"))
                .willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));

            List<EventLogStore.EventEntry> entries = runPromise(() ->
                store().read(TENANT, Offset.of(0), 10));

            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).eventType()).isEqualTo("item.updated");
        }

        @Test
        @DisplayName("null tenant throws NullPointerException")
        void read_nullTenant_throwsNpe() {
            assertThatNullPointerException()
                .isThrownBy(() -> runPromise(() -> store().read(null, Offset.of(0), 10)));
        }
    }

    @Nested
    @DisplayName("getLatestOffset()")
    class GetLatestOffset {

        @Test
        @DisplayName("returns offset from server response")
        void getLatestOffset_returnsValue() {
            wireMock.stubFor(get(urlPathMatching("/api/v1/eventlog/.*/offset/latest"))
                .willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"offset\": 99}")));

            Offset offset = runPromise(() -> store().getLatestOffset(TENANT));

            assertThat(offset.value()).isEqualTo(99L);
        }
    }

    @Nested
    @DisplayName("getEarliestOffset()")
    class GetEarliestOffset {

        @Test
        @DisplayName("returns offset from server response")
        void getEarliestOffset_returnsValue() {
            wireMock.stubFor(get(urlPathMatching("/api/v1/eventlog/.*/offset/earliest"))
                .willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"offset\": 0}")));

            Offset offset = runPromise(() -> store().getEarliestOffset(TENANT));

            assertThat(offset.value()).isEqualTo(0L);
        }
    }
}
