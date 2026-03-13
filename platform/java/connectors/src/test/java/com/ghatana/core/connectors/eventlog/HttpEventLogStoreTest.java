package com.ghatana.core.connectors.eventlog;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit and integration tests for {@link HttpEventLogStore}.
 *
 * <p>Constructor tests verify environment variable validation. WireMock-backed
 * tests exercise the full HTTP request/response lifecycle.
 *
 * @doc.type class
 * @doc.purpose Tests for HttpEventLogStore — construction validation and HTTP behavior
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("HttpEventLogStore")
class HttpEventLogStoreTest extends EventloopTestBase {

    private static WireMockServer wireMock;
    private static final String TENANT_ID = "tenant-http-1";
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
    //  Constructor — environment validation (no network)
    // =========================================================================

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("throws when DATACLOUD_HTTP_BASE_URL is absent")
        void missingBaseUrl_throwsIllegalStateException() {
            Map<String, String> env = Map.of("APP_ENV", "development");
            assertThatThrownBy(() -> new HttpEventLogStore(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_HTTP_BASE_URL");
        }

        @Test
        @DisplayName("production mode requires https:// URL")
        void productionMode_requiresHttpsUrl() {
            Map<String, String> env = Map.of(
                "DATACLOUD_HTTP_BASE_URL", "http://data-cloud.example.com",
                "DATACLOUD_HTTP_AUTH_TOKEN", "token",
                "APP_ENV", "production"
            );
            assertThatThrownBy(() -> new HttpEventLogStore(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https://");
        }

        @Test
        @DisplayName("dev mode allows http:// URL without auth token")
        void devMode_allowsHttpUrlWithoutToken() {
            Map<String, String> env = Map.of(
                "DATACLOUD_HTTP_BASE_URL", "http://localhost:" + wireMock.port(),
                "APP_ENV", "development"
            );
            assertThatNoException().isThrownBy(() -> new HttpEventLogStore(env));
        }

        @Test
        @DisplayName("production mode requires auth token")
        void productionMode_requiresAuthToken() {
            Map<String, String> env = Map.of(
                "DATACLOUD_HTTP_BASE_URL", "https://data-cloud.example.com"
                // APP_ENV defaults to production, DATACLOUD_HTTP_AUTH_TOKEN absent
            );
            assertThatThrownBy(() -> new HttpEventLogStore(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_HTTP_AUTH_TOKEN");
        }

        @Test
        @DisplayName("trailing slash in baseUrl is normalised away")
        void trailingSlash_isNormalised() {
            Map<String, String> env = Map.of(
                "DATACLOUD_HTTP_BASE_URL", "http://localhost:" + wireMock.port() + "/",
                "APP_ENV", "development"
            );
            assertThatNoException().isThrownBy(() -> new HttpEventLogStore(env));
        }
    }

    // =========================================================================
    //  HTTP behavior (WireMock)
    // =========================================================================

    private HttpEventLogStore store() {
        return new HttpEventLogStore(Map.of(
            "DATACLOUD_HTTP_BASE_URL", "http://localhost:" + wireMock.port(),
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
                    .withBody("{\"offset\": 7}")));

            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventType("order.placed")
                .payload("{\"orderId\":\"o1\"}")
                .build();

            Offset offset = runPromise(() -> store().append(TENANT, entry));

            assertThat(offset.value()).isEqualTo("7");
        }

        @Test
        @DisplayName("server error — throws DataCloudRemoteException")
        void append_serverError_throwsException() {
            wireMock.stubFor(post(anyUrl())
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"unavailable\"}")));

            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventType("order.placed")
                .build();

            assertThatThrownBy(() -> runPromise(() -> store().append(TENANT, entry)))
                .isInstanceOf(DataCloudRemoteException.class)
                .cause()
                .hasMessageContaining("503");
            clearFatalError();
        }

        @Test
        @DisplayName("null entry throws NullPointerException before remote call")
        void append_nullEntry_throwsNpe() {
            assertThatNullPointerException()
                .isThrownBy(() -> runPromise(() -> store().append(TENANT, null)));
        }
    }

    @Nested
    @DisplayName("appendBatch()")
    class AppendBatch {

        @Test
        @DisplayName("empty batch returns empty offsets without network call")
        void appendBatch_empty_returnsEmptyList() {
            List<Offset> offsets = runPromise(() -> store().appendBatch(TENANT, List.of()));
            assertThat(offsets).isEmpty();
            wireMock.verify(0, postRequestedFor(anyUrl()));
        }

        @Test
        @DisplayName("batch request posts to /events/batch")
        void appendBatch_postsToCorrectPath() {
            wireMock.stubFor(post(urlPathMatching("/api/v1/eventlog/.*/events/batch"))
                .willReturn(aResponse().withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"offsets\": [1, 2]}")));

            List<EventLogStore.EventEntry> entries = List.of(
                EventLogStore.EventEntry.builder().eventType("a").build(),
                EventLogStore.EventEntry.builder().eventType("b").build()
            );

            List<Offset> offsets = runPromise(() -> store().appendBatch(TENANT, entries));

            assertThat(offsets).hasSize(2);
            assertThat(offsets.get(0).value()).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("read()")
    class Read {

        @Test
        @DisplayName("returns empty list when server returns no events")
        void read_noEvents_returnsEmptyList() {
            wireMock.stubFor(get(urlPathMatching("/api/v1/eventlog/.*/events"))
                .willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"events\":[]}")));

            List<EventLogStore.EventEntry> entries = runPromise(() ->
                store().read(TENANT, Offset.of(0), 10));

            assertThat(entries).isEmpty();
        }

        @Test
        @DisplayName("deserializes returned events correctly")
        void read_returnsDeserializedEntries() {
            String eventId = UUID.randomUUID().toString();
            String ts = java.time.Instant.now().toString();
            String payload64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes());
            wireMock.stubFor(get(urlPathMatching("/api/v1/eventlog/.*/events"))
                .willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"events":[{"eventId":"%s","eventType":"msg.sent","timestamp":"%s","payload":"%s"}]}
                        """.formatted(eventId, ts, payload64))));

            List<EventLogStore.EventEntry> entries = runPromise(() ->
                store().read(TENANT, Offset.of(0), 5));

            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).eventId()).isEqualTo(UUID.fromString(eventId));
            assertThat(entries.get(0).eventType()).isEqualTo("msg.sent");
        }
    }

    @Nested
    @DisplayName("getLatestOffset() / getEarliestOffset()")
    class Offsets {

        @Test
        @DisplayName("getLatestOffset returns value from server")
        void getLatestOffset_returnsValue() {
            wireMock.stubFor(get(urlPathMatching("/api/v1/eventlog/.*/offset/latest"))
                .willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"offset\": 200}")));

            Offset offset = runPromise(() -> store().getLatestOffset(TENANT));
            assertThat(offset.value()).isEqualTo("200");
        }

        @Test
        @DisplayName("getEarliestOffset returns value from server")
        void getEarliestOffset_returnsValue() {
            wireMock.stubFor(get(urlPathMatching("/api/v1/eventlog/.*/offset/earliest"))
                .willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"offset\": 0}")));

            Offset offset = runPromise(() -> store().getEarliestOffset(TENANT));
            assertThat(offset.value()).isEqualTo("0");
        }
    }
}
