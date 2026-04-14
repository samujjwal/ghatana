/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

import com.ghatana.aiplatform.featurestore.Feature;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AepFeatureStoreClient}.
 *
 * <p>Verifies the async façade over {@link FeatureStoreService}:
 * all calls must be dispatched off the ActiveJ event-loop thread and delegate
 * correctly to the underlying synchronous service.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepFeatureStoreClient")
class AepFeatureStoreClientTest extends EventloopTestBase {

    @Mock
    private FeatureStoreService featureStoreService;

    private AepFeatureStoreClient client;

    @BeforeEach
    void setUp() {
        client = new AepFeatureStoreClient(
                featureStoreService,
                Executors.newSingleThreadExecutor());
    }

    // =========================================================================
    // Construction
    // =========================================================================
    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("null delegate throws NullPointerException")
        void nullDelegateThrowsNpe() {
            assertThatThrownBy(() ->
                    new AepFeatureStoreClient(null, Executors.newSingleThreadExecutor()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("delegate");
        }

        @Test
        @DisplayName("null executor throws NullPointerException")
        void nullExecutorThrowsNpe() {
            assertThatThrownBy(() ->
                    new AepFeatureStoreClient(featureStoreService, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("blockingExecutor");
        }
    }

    // =========================================================================
    // Ingestion
    // =========================================================================
    @Nested
    @DisplayName("Ingestion")
    class IngestionTests {

        @Test
        @DisplayName("ingest(tenantId, Feature) delegates to the underlying service")
        void ingestDelegatesToService() {
            Feature feature = Feature.builder()
                    .name("event_count_1h")
                    .entityId("agent-42")
                    .value(17.0)
                    .timestamp(Instant.now())
                    .build();

            runPromise(() -> client.ingest("tenant-1", feature));

            verify(featureStoreService).ingest("tenant-1", feature);
        }

        @Test
        @DisplayName("convenience ingest(tenantId, entityId, name, value) builds Feature and delegates")
        void ingestConvenienceBuildsFeatureAndDelegates() {
            runPromise(() -> client.ingest("t1", "entity-x", "score_7d", 0.85));

            verify(featureStoreService).ingest(eq("t1"), any(Feature.class));
        }

        @Test
        @DisplayName("ingestAll with empty list completes without calling delegate")
        void ingestAllEmptyListNoDelegate() {
            runPromise(() -> client.ingestAll("t1", List.of()));

            verify(featureStoreService, times(0)).ingest(anyString(), any(Feature.class));
        }

        @Test
        @DisplayName("ingestAll ingests each feature independently")
        void ingestAllIngestsEachFeature() {
            Feature f1 = Feature.builder().name("a").entityId("e1").value(1.0).timestamp(Instant.now()).build();
            Feature f2 = Feature.builder().name("b").entityId("e1").value(2.0).timestamp(Instant.now()).build();

            runPromise(() -> client.ingestAll("t1", List.of(f1, f2)));

            verify(featureStoreService).ingest("t1", f1);
            verify(featureStoreService).ingest("t1", f2);
        }

        @Test
        @DisplayName("ingestAll continues after partial failure — no exception propagated")
        void ingestAllPartialFailureContinues() {
            Feature f1 = Feature.builder().name("bad").entityId("e").value(0).timestamp(Instant.now()).build();
            Feature f2 = Feature.builder().name("good").entityId("e").value(1).timestamp(Instant.now()).build();

            doThrow(new RuntimeException("simulated failure")).when(featureStoreService).ingest("t1", f1);

            // Should complete without propagating the partial exception
            runPromise(() -> client.ingestAll("t1", List.of(f1, f2)));

            // f2 still ingested
            verify(featureStoreService).ingest("t1", f2);
        }
    }

    // =========================================================================
    // Retrieval
    // =========================================================================
    @Nested
    @DisplayName("Retrieval")
    class RetrievalTests {

        @Test
        @DisplayName("getFeatures returns empty map for empty feature name list")
        void getFeaturesEmptyNamesReturnsEmptyMap() {
            Map<String, Double> result = runPromise(() ->
                    client.getFeatures("t1", "entity-1", List.of()));

            assertThat(result).isEmpty();
            verify(featureStoreService, times(0)).getFeatures(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("getFeatures delegates to service and returns result")
        void getFeaturesDelegatesAndReturnsResult() {
            when(featureStoreService.getFeatures("t1", "entity-1",
                    List.of("score_7d", "txn_count_24h")))
                    .thenReturn(Map.of("score_7d", 0.92, "txn_count_24h", 7.0));

            Map<String, Double> result = runPromise(() ->
                    client.getFeatures("t1", "entity-1", List.of("score_7d", "txn_count_24h")));

            assertThat(result)
                    .containsEntry("score_7d", 0.92)
                    .containsEntry("txn_count_24h", 7.0);
        }

        @Test
        @DisplayName("getFeatures returns empty map when service finds nothing")
        void getFeaturesNoneFoundReturnsEmptyMap() {
            when(featureStoreService.getFeatures(anyString(), anyString(), anyList()))
                    .thenReturn(Map.of());

            Map<String, Double> result = runPromise(() ->
                    client.getFeatures("t1", "entity-99", List.of("missing_feature")));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Cache management
    // =========================================================================
    @Nested
    @DisplayName("Cache management")
    class CacheTests {

        @Test
        @DisplayName("clearLocalCache delegates to service.clearCache()")
        void clearLocalCacheDelegatesToService() {
            client.clearLocalCache();

            verify(featureStoreService).clearCache();
        }
    }
}
