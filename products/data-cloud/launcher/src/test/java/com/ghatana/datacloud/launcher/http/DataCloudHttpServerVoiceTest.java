/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("DataCloudHttpServer – Voice Gateway Endpoints (DC-E4)")
class DataCloudHttpServerVoiceTest extends EventloopTestBase {

    private DataCloudClient mockClient;
    private VoiceGatewayHandler handler;

    @BeforeEach
    void setUp() {
        mockClient = mock(DataCloudClient.class);
        handler = new VoiceGatewayHandler(
            mockClient,
            null,
            null,
            new ObjectMapper(),
            new HttpHandlerSupport(new ObjectMapper(), "*", "GET,POST", "Content-Type,X-Tenant-Id"),
            Runnable::run,
            NopVoiceSttAdapter.INSTANCE,
            NopVoiceTtsAdapter.INSTANCE
        );
    }

    @Nested
    @DisplayName("Intent catalog")
    class CatalogTests {

        @Test
        @DisplayName("catalog exposes registered intents for voice UI discovery")
        void catalogExposesRegisteredIntents() {
            assertThat(VoiceIntentCatalog.ALL).hasSizeGreaterThanOrEqualTo(20);
            assertThat(VoiceIntentCatalog.ALL)
                .extracting(VoiceIntentCatalog.VoiceIntent::name)
                .contains("query_entities", "list_pipelines", "query_events");
        }

        @Test
        @DisplayName("exact intent name resolves directly from the catalog")
        void exactIntentNameResolvesDirectly() {
            assertThat(VoiceIntentCatalog.findByName("query_entities"))
                .isPresent()
                .get()
                .extracting(VoiceIntentCatalog.VoiceIntent::pathTemplate)
                .isEqualTo("/api/v1/entities/:collection");
        }
    }

    @Nested
    @DisplayName("Intent classification")
    class ClassificationTests {

        @Test
        @DisplayName("heuristic classification prefers the strongest pipeline match")
        void heuristicClassificationPrefersStrongestMatch() {
            assertThat(VoiceIntentCatalog.findCandidates("show me the pipeline status for daily etl"))
                .isNotEmpty()
                .first()
                .extracting(VoiceIntentCatalog.VoiceIntent::name)
                .isEqualTo("get_pipeline_status");
        }

        @Test
        @DisplayName("unknown utterances do not match any catalog intent")
        void unknownUtterancesDoNotMatchCatalogIntent() {
            assertThat(VoiceIntentCatalog.findCandidates("xyzzy frobulate the wumpus")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Execution payload")
    class ExecutionPayloadTests {

        @Test
        @DisplayName("exact list_pipelines execution returns a resolved action payload")
        void listPipelinesExecutionReturnsResolvedActionPayload() {
            Map<String, Object> payload = runPromise(() -> handler.executeIntentPayload(
                VoiceIntentCatalog.findByName("list_pipelines").orElseThrow(),
                Map.of(),
                TestConstants.TENANT_DEFAULT,
                "en"));

            assertThat(payload.get("executed")).isEqualTo(true);
            assertThat(payload.get("intentName")).isEqualTo("list_pipelines");
            assertThat(payload.get("resolvedPath")).isEqualTo("/api/v1/pipelines");
        }

        @Test
        @DisplayName("query_entities execution returns real entity results from the data client")
        @SuppressWarnings("unchecked")
        void queryEntitiesExecutionReturnsRealEntityResults() {
            when(mockClient.query(eq(TestConstants.TENANT_DEFAULT), eq("orders"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of(
                    new DataCloudClient.Entity(
                        "order-1",
                        "orders",
                        Map.of("status", "open"),
                        Instant.parse("2026-04-17T00:00:00Z"),
                        Instant.parse("2026-04-17T00:00:00Z"),
                        1L
                    )
                )));

            Map<String, Object> payload = runPromise(() -> handler.executeIntentPayload(
                VoiceIntentCatalog.findByName("query_entities").orElseThrow(),
                Map.of("collection", "orders", "limit", "10"),
                TestConstants.TENANT_DEFAULT,
                "en"));

            Map<String, Object> result = (Map<String, Object>) payload.get("result");
            assertThat(payload.get("speechSummary")).isEqualTo("Found 1 entities in orders.");
            assertThat(result.get("entityCount")).isEqualTo(1);
        }
    }
}
