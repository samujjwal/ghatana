/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.VoiceGatewayHandler;
import com.ghatana.datacloud.launcher.http.voice.NopVoiceSttAdapter;
import com.ghatana.datacloud.launcher.http.voice.NopVoiceTtsAdapter;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stable voice contract tests for the launcher voice gateway.
 *
 * <p>These tests validate the catalog and real execution payload paths directly,
 * without depending on the outer server wrapper.
 *
 * @doc.type class
 * @doc.purpose Stable voice gateway contract tests for Data Cloud launcher
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("VoiceGatewayHandler – Contract Tests (DC-E4) [GH-90000]")
class VoiceGatewayHandlerTest extends EventloopTestBase {

    private DataCloudClient mockClient;
    private VoiceGatewayHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        handler = new VoiceGatewayHandler( // GH-90000
            mockClient,
            null,
            null,
            new ObjectMapper(), // GH-90000
            new HttpHandlerSupport(new ObjectMapper(), "*", "GET,POST", "Content-Type,X-Tenant-Id"), // GH-90000
            Runnable::run,
            NopVoiceSttAdapter.INSTANCE,
            NopVoiceTtsAdapter.INSTANCE
        );
    }

    @Nested
    @DisplayName("Intent catalog [GH-90000]")
    class CatalogTests {

        @Test
        @DisplayName("catalog exposes registered intents for voice UI discovery [GH-90000]")
        void catalogExposesRegisteredIntents() { // GH-90000
            assertThat(VoiceIntentCatalog.ALL).hasSizeGreaterThanOrEqualTo(20); // GH-90000
            assertThat(VoiceIntentCatalog.ALL) // GH-90000
                .extracting(VoiceIntentCatalog.VoiceIntent::name) // GH-90000
                .contains("query_entities", "list_pipelines", "query_events"); // GH-90000
        }

        @Test
        @DisplayName("exact intent name resolves directly from the catalog [GH-90000]")
        void exactIntentNameResolvesDirectly() { // GH-90000
            assertThat(VoiceIntentCatalog.findByName("query_entities [GH-90000]"))
                .isPresent() // GH-90000
                .get() // GH-90000
                .extracting(VoiceIntentCatalog.VoiceIntent::pathTemplate) // GH-90000
                .isEqualTo("/api/v1/entities/:collection [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Intent classification [GH-90000]")
    class ClassificationTests {

        @Test
        @DisplayName("heuristic classification prefers the strongest pipeline match [GH-90000]")
        void heuristicClassificationPrefersStrongestMatch() { // GH-90000
            assertThat(VoiceIntentCatalog.findCandidates("show me the pipeline status for daily etl [GH-90000]"))
                .isNotEmpty() // GH-90000
                .first() // GH-90000
                .extracting(VoiceIntentCatalog.VoiceIntent::name) // GH-90000
                .isEqualTo("get_pipeline_status [GH-90000]");
        }

        @Test
        @DisplayName("unknown utterances do not match any catalog intent [GH-90000]")
        void unknownUtterancesDoNotMatchCatalogIntent() { // GH-90000
            assertThat(VoiceIntentCatalog.findCandidates("xyzzy frobulate the wumpus [GH-90000]")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Execution payload [GH-90000]")
    class ExecutionPayloadTests {

        @Test
        @DisplayName("exact list_pipelines execution returns a resolved action payload [GH-90000]")
        void listPipelinesExecutionReturnsResolvedActionPayload() { // GH-90000
            Map<String, Object> payload = runPromise(() -> handler.executeIntentPayload( // GH-90000
                VoiceIntentCatalog.findByName("list_pipelines [GH-90000]").orElseThrow(),
                Map.of(), // GH-90000
                TestConstants.TENANT_DEFAULT,
                "en"));

            assertThat(payload.get("executed [GH-90000]")).isEqualTo(true);
            assertThat(payload.get("intentName [GH-90000]")).isEqualTo("list_pipelines [GH-90000]");
            assertThat(payload.get("resolvedPath [GH-90000]")).isEqualTo("/api/v1/pipelines [GH-90000]");
        }

        @Test
        @DisplayName("query_entities execution returns real entity results from the data client [GH-90000]")
        @SuppressWarnings("unchecked [GH-90000]")
        void queryEntitiesExecutionReturnsRealEntityResults() { // GH-90000
            when(mockClient.query(eq(TestConstants.TENANT_DEFAULT), eq("orders [GH-90000]"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of( // GH-90000
                    new DataCloudClient.Entity( // GH-90000
                        "order-1",
                        "orders",
                        Map.of("status", "open"), // GH-90000
                        Instant.parse("2026-04-17T00:00:00Z [GH-90000]"),
                        Instant.parse("2026-04-17T00:00:00Z [GH-90000]"),
                        1L
                    )
                )));

            Map<String, Object> payload = runPromise(() -> handler.executeIntentPayload( // GH-90000
                VoiceIntentCatalog.findByName("query_entities [GH-90000]").orElseThrow(),
                Map.of("collection", "orders", "limit", "10"), // GH-90000
                TestConstants.TENANT_DEFAULT,
                "en"));

            Map<String, Object> result = (Map<String, Object>) payload.get("result [GH-90000]");
            assertThat(payload.get("speechSummary [GH-90000]")).isEqualTo("Found 1 entities in orders. [GH-90000]");
            assertThat(result.get("entityCount [GH-90000]")).isEqualTo(1);
        }
    }
}
