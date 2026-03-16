/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api.ai.platform;

import com.ghatana.aiplatform.featurestore.Feature;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link YappcFeatureStoreClient}.
 *
 * @doc.type class
 * @doc.purpose Verifies async façade behaviour and delegation to FeatureStoreService
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YappcFeatureStoreClient")
class YappcFeatureStoreClientTest extends EventloopTestBase {

    private FeatureStoreService delegate;
    private YappcFeatureStoreClient client;

    private static final String TENANT = "yappc-tenant-456";
    private static final String ENTITY_ID = "workspace-abc";

    @BeforeEach
    void setUp() {
        delegate = mock(FeatureStoreService.class);
        client = new YappcFeatureStoreClient(delegate, Executors.newSingleThreadExecutor());
    }

    // =========================================================================
    // Constructor guard-rails
    // =========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects null delegate")
        void rejectsNullDelegate() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new YappcFeatureStoreClient(null, Executors.newCachedThreadPool()))
                    .withMessageContaining("delegate");
        }

        @Test
        @DisplayName("rejects null executor")
        void rejectsNullExecutor() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new YappcFeatureStoreClient(delegate, null))
                    .withMessageContaining("blockingExecutor");
        }
    }

    // =========================================================================
    // ingest(tenantId, entityId, featureName, value)
    // =========================================================================

    @Nested
    @DisplayName("ingest(tenantId, entityId, featureName, value)")
    class IngestConvenienceMethod {

        @Test
        @DisplayName("calls delegate.ingest with a correctly built Feature")
        void callsDelegateWithBuiltFeature() {
            doNothing().when(delegate).ingest(any(), any());

            runPromise(() -> client.ingest(TENANT, ENTITY_ID, "complexity_score", 0.75));

            verify(delegate).ingest(eq(TENANT), argThat(f ->
                    f.getName().equals("complexity_score")
                    && f.getEntityId().equals(ENTITY_ID)
                    && f.getValue() == 0.75
                    && f.getTimestamp() != null));
        }

        @Test
        @DisplayName("rejects null tenantId")
        void rejectsNullTenantId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.ingest(null, ENTITY_ID, "score", 1.0))
                    .withMessageContaining("tenantId");
        }

        @Test
        @DisplayName("rejects null entityId")
        void rejectsNullEntityId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.ingest(TENANT, null, "score", 1.0))
                    .withMessageContaining("entityId");
        }

        @Test
        @DisplayName("rejects null featureName")
        void rejectsNullFeatureName() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.ingest(TENANT, ENTITY_ID, null, 1.0))
                    .withMessageContaining("featureName");
        }
    }

    // =========================================================================
    // ingest(tenantId, feature)
    // =========================================================================

    @Nested
    @DisplayName("ingest(tenantId, feature)")
    class IngestFeatureMethod {

        @Test
        @DisplayName("delegates to FeatureStoreService")
        void delegatesToService() {
            Feature feature = Feature.builder()
                    .name("sprint_velocity_90d")
                    .entityId(ENTITY_ID)
                    .value(42.0)
                    .timestamp(Instant.now())
                    .build();
            doNothing().when(delegate).ingest(any(), any());

            runPromise(() -> client.ingest(TENANT, feature));

            verify(delegate).ingest(TENANT, feature);
        }

        @Test
        @DisplayName("rejects null feature")
        void rejectsNullFeature() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.ingest(TENANT, (Feature) null))
                    .withMessageContaining("feature");
        }
    }

    // =========================================================================
    // ingestAll
    // =========================================================================

    @Nested
    @DisplayName("ingestAll")
    class IngestAll {

        @Test
        @DisplayName("completes immediately for empty list")
        void completesForEmptyList() {
            // Should not call delegate at all
            runPromise(() -> client.ingestAll(TENANT, List.of()));
            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("ingests all features, tolerating partial failures")
        void toleratesPartialFailures() {
            Feature good = buildFeature("ok", 1.0);
            Feature bad = buildFeature("fail", 2.0);

            // First ingest succeeds, second throws
            doNothing().when(delegate).ingest(eq(TENANT), eq(good));
            doThrow(new RuntimeException("Redis unavailable"))
                    .when(delegate).ingest(eq(TENANT), eq(bad));

            // Should NOT throw — failures are logged/counted but swallowed
            runPromise(() -> client.ingestAll(TENANT, List.of(good, bad)));

            verify(delegate, times(2)).ingest(eq(TENANT), any(Feature.class));
        }

        @Test
        @DisplayName("rejects null features list")
        void rejectsNullList() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.ingestAll(TENANT, null))
                    .withMessageContaining("features");
        }
    }

    // =========================================================================
    // getFeatures
    // =========================================================================

    @Nested
    @DisplayName("getFeatures")
    class GetFeatures {

        @Test
        @DisplayName("returns feature map from delegate")
        void returnsFeatureMap() {
            Map<String, Double> featureMap = Map.of(
                    "complexity_score", 0.72,
                    "sprint_velocity_90d", 38.5);
            when(delegate.getFeatures(TENANT, ENTITY_ID,
                    List.of("complexity_score", "sprint_velocity_90d")))
                    .thenReturn(featureMap);

            Map<String, Double> result = runPromise(() -> client.getFeatures(
                    TENANT, ENTITY_ID, List.of("complexity_score", "sprint_velocity_90d")));

            assertThat(result).hasSize(2)
                    .containsEntry("complexity_score", 0.72)
                    .containsEntry("sprint_velocity_90d", 38.5);
        }

        @Test
        @DisplayName("returns empty map for empty feature names list (no delegate call)")
        void returnsEmptyMapForEmptyNames() {
            Map<String, Double> result = runPromise(() ->
                    client.getFeatures(TENANT, ENTITY_ID, List.of()));

            assertThat(result).isEmpty();
            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("rejects null tenantId")
        void rejectsNullTenantId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.getFeatures(null, ENTITY_ID, List.of("f")))
                    .withMessageContaining("tenantId");
        }

        @Test
        @DisplayName("rejects null entityId")
        void rejectsNullEntityId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.getFeatures(TENANT, null, List.of("f")))
                    .withMessageContaining("entityId");
        }

        @Test
        @DisplayName("rejects null featureNames list")
        void rejectsNullList() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.getFeatures(TENANT, ENTITY_ID, null))
                    .withMessageContaining("featureNames");
        }
    }

    // =========================================================================
    // getFeature (single)
    // =========================================================================

    @Nested
    @DisplayName("getFeature (single)")
    class GetSingleFeature {

        @Test
        @DisplayName("returns value when feature exists")
        void returnsValueWhenPresent() {
            when(delegate.getFeatures(TENANT, ENTITY_ID, List.of("complexity_score")))
                    .thenReturn(Map.of("complexity_score", 0.91));

            Double result = runPromise(() ->
                    client.getFeature(TENANT, ENTITY_ID, "complexity_score"));

            assertThat(result).isEqualTo(0.91);
        }

        @Test
        @DisplayName("returns null when feature absent")
        void returnsNullWhenAbsent() {
            when(delegate.getFeatures(TENANT, ENTITY_ID, List.of("missing_feature")))
                    .thenReturn(Map.of());

            Double result = runPromise(() ->
                    client.getFeature(TENANT, ENTITY_ID, "missing_feature"));

            assertThat(result).isNull();
        }
    }

    // =========================================================================
    // cacheSize
    // =========================================================================

    @Nested
    @DisplayName("cacheSize")
    class CacheSizeTests {

        @Test
        @DisplayName("delegates to getCacheSize on the service")
        void delegatesToGetCacheSize() {
            when(delegate.getCacheSize()).thenReturn(42);

            Integer size = runPromise(client::cacheSize);

            assertThat(size).isEqualTo(42);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Feature buildFeature(String name, double value) {
        return Feature.builder()
                .name(name)
                .entityId(ENTITY_ID)
                .value(value)
                .timestamp(Instant.now())
                .build();
    }
}
