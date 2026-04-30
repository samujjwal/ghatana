/**
 * @doc.type class
 * @doc.purpose Property and contract tests for storage-layer value objects and connector interface
 * @doc.layer product
 * @doc.pattern Test
 */
package com.ghatana.datacloud.entity.storage;

import com.ghatana.datacloud.entity.Entity;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * DC-A16: Property and contract tests for the storage layer.
 *
 * <p>Covers:
 * <ul>
 *   <li>Record mutability – verifies that mutable types allow controlled mutation and
 *       immutable types guard their invariants.</li>
 *   <li>Schema evolution – verifies that entity data maps accept new/removed fields
 *       without breaking downstream consumers.</li>
 *   <li>Connector capability negotiation – verifies {@link StorageConnector.ConnectorMetadata}
 *       builder, record accessors, and the convenience default methods on
 *       {@link StorageConnector}.</li>
 *   <li>StorageProfile immutability – verifies defensive copies and unmodifiable returns.</li>
 * </ul>
 */
@DisplayName("Storage Connector Contract Tests (DC-A16)")
class StorageConnectorContractTest {

    // =========================================================================
    //  ConnectorMetadata record
    // =========================================================================

    @Nested
    @DisplayName("ConnectorMetadata")
    class ConnectorMetadataTests {

        @Test
        @DisplayName("builder requires backendType")
        void builderRequiresBackendType() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> StorageConnector.ConnectorMetadata.builder().build())
                    .withMessageContaining("backendType is required");
        }

        @Test
        @DisplayName("builder defaults: STANDARD latency, no transactions, no time-series, no full-text, no schema, 0 maxBatch")
        void builderDefaultValues() {
            StorageConnector.ConnectorMetadata meta = StorageConnector.ConnectorMetadata.builder()
                    .backendType(StorageBackendType.RELATIONAL)
                    .build();

            assertThat(meta.backendType()).isEqualTo(StorageBackendType.RELATIONAL);
            assertThat(meta.latencyClass()).isEqualTo(StorageProfile.LatencyClass.STANDARD);
            assertThat(meta.supportsTransactions()).isFalse();
            assertThat(meta.supportsTimeSeries()).isFalse();
            assertThat(meta.supportsFullText()).isFalse();
            assertThat(meta.supportsSchemaless()).isFalse();
            assertThat(meta.maxBatchSize()).isZero();
        }

        @Test
        @DisplayName("builder sets all fields correctly for a time-series connector")
        void builderSetsAllFieldsForTimeSeries() {
            StorageConnector.ConnectorMetadata meta = StorageConnector.ConnectorMetadata.builder()
                    .backendType(StorageBackendType.TIMESERIES)
                    .latencyClass(StorageProfile.LatencyClass.FAST)
                    .supportsTransactions(false)
                    .supportsTimeSeries(true)
                    .supportsFullText(false)
                    .supportsSchemaless(true)
                    .maxBatchSize(100_000)
                    .build();

            assertThat(meta.backendType()).isEqualTo(StorageBackendType.TIMESERIES);
            assertThat(meta.latencyClass()).isEqualTo(StorageProfile.LatencyClass.FAST);
            assertThat(meta.supportsTransactions()).isFalse();
            assertThat(meta.supportsTimeSeries()).isTrue();
            assertThat(meta.supportsFullText()).isFalse();
            assertThat(meta.supportsSchemaless()).isTrue();
            assertThat(meta.maxBatchSize()).isEqualTo(100_000);
        }

        @Test
        @DisplayName("record equality is value-based (same fields → equal)")
        void recordEqualityIsValueBased() {
            StorageConnector.ConnectorMetadata a = StorageConnector.ConnectorMetadata.builder()
                    .backendType(StorageBackendType.RELATIONAL)
                    .supportsTransactions(true)
                    .maxBatchSize(500)
                    .build();

            StorageConnector.ConnectorMetadata b = StorageConnector.ConnectorMetadata.builder()
                    .backendType(StorageBackendType.RELATIONAL)
                    .supportsTransactions(true)
                    .maxBatchSize(500)
                    .build();

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("record equality distinguishes different backend types")
        void recordEqualityDistinguishesDifferentBackendTypes() {
            StorageConnector.ConnectorMetadata relational = StorageConnector.ConnectorMetadata.builder()
                    .backendType(StorageBackendType.RELATIONAL)
                    .build();

            StorageConnector.ConnectorMetadata kv = StorageConnector.ConnectorMetadata.builder()
                    .backendType(StorageBackendType.KEY_VALUE)
                    .build();

            assertThat(relational).isNotEqualTo(kv);
        }
    }

    // =========================================================================
    //  QueryResult record
    // =========================================================================

    @Nested
    @DisplayName("QueryResult")
    class QueryResultTests {

        @Test
        @DisplayName("hasMore returns true when more pages exist")
        void hasMoreReturnsTrueWhenMorePagesExist() {
            StorageConnector.QueryResult result = new StorageConnector.QueryResult(
                    List.of(), /* entities */
                    100,       /* total */
                    10,        /* limit */
                    0,         /* offset */
                    5          /* executionTimeMs */
            );

            assertThat(result.hasMore()).isTrue();
        }

        @Test
        @DisplayName("hasMore returns false on last page")
        void hasMoreReturnsFalseOnLastPage() {
            StorageConnector.QueryResult result = new StorageConnector.QueryResult(
                    List.of(),
                    100,
                    10,
                    90, /* last page: offset 90 + limit 10 = 100 = total */
                    3
            );

            assertThat(result.hasMore()).isFalse();
        }

        @Test
        @DisplayName("hasMore returns false when total <= limit")
        void hasMoreReturnsFalseWhenTotalFitsInOnePage() {
            StorageConnector.QueryResult result = new StorageConnector.QueryResult(
                    List.of(), 5, 10, 0, 1
            );

            assertThat(result.hasMore()).isFalse();
        }

        @Test
        @DisplayName("record accessors expose all construction parameters")
        void recordAccessorsExposeAllParams() {
            Entity e = Entity.builder().tenantId("t1").collectionName("c1").build();
            StorageConnector.QueryResult result = new StorageConnector.QueryResult(
                    List.of(e), 1L, 10, 0, 7L
            );

            assertThat(result.entities()).containsExactly(e);
            assertThat(result.total()).isEqualTo(1L);
            assertThat(result.limit()).isEqualTo(10);
            assertThat(result.offset()).isZero();
            assertThat(result.executionTimeMs()).isEqualTo(7L);
        }
    }

    // =========================================================================
    //  StorageProfile immutability
    // =========================================================================

    @Nested
    @DisplayName("StorageProfile immutability")
    class StorageProfileImmutabilityTests {

        @Test
        @DisplayName("getSupportedBackends returns unmodifiable set")
        void getSupportedBackendsIsUnmodifiable() {
            StorageProfile profile = StorageProfile.builder()
                    .name("test")
                    .label("Test")
                    .supportedBackends(StorageBackendType.RELATIONAL, StorageBackendType.KEY_VALUE)
                    .latencyClass(StorageProfile.LatencyClass.STANDARD)
                    .costTier(StorageProfile.CostTier.MEDIUM)
                    .consistencyHint(StorageProfile.ConsistencyHint.STRONG)
                    .build();

            Set<StorageBackendType> backends = profile.getSupportedBackends();

            assertThat(backends).containsExactlyInAnyOrder(
                    StorageBackendType.RELATIONAL, StorageBackendType.KEY_VALUE);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> backends.add(StorageBackendType.BLOB));
        }

        @Test
        @DisplayName("supports correctly identifies declared and undeclared backends")
        void supportsCorrectlyIdentifiesBackends() {
            StorageProfile profile = StorageProfile.builder()
                    .name("warm")
                    .label("Warm")
                    .supportedBackends(StorageBackendType.RELATIONAL)
                    .latencyClass(StorageProfile.LatencyClass.STANDARD)
                    .costTier(StorageProfile.CostTier.MEDIUM)
                    .consistencyHint(StorageProfile.ConsistencyHint.STRONG)
                    .build();

            assertThat(profile.supports(StorageBackendType.RELATIONAL)).isTrue();
            assertThat(profile.supports(StorageBackendType.TIMESERIES)).isFalse();
            assertThat(profile.supports(StorageBackendType.KEY_VALUE)).isFalse();
        }

        @Test
        @DisplayName("supports rejects null backend argument")
        void supportsRejectsNull() {
            StorageProfile profile = StorageProfile.builder()
                    .name("strict")
                    .label("Strict")
                    .supportedBackends(StorageBackendType.RELATIONAL)
                    .latencyClass(StorageProfile.LatencyClass.STANDARD)
                    .costTier(StorageProfile.CostTier.MEDIUM)
                    .consistencyHint(StorageProfile.ConsistencyHint.STRONG)
                    .build();

            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> profile.supports(null));
        }

        @Test
        @DisplayName("predefined HOT profile supports IN_MEMORY and KEY_VALUE")
        void predefinedHotProfile() {
            StorageProfile hot = StorageProfile.hotProfile();

            assertThat(hot.supports(StorageBackendType.IN_MEMORY)).isTrue();
            assertThat(hot.supports(StorageBackendType.KEY_VALUE)).isTrue();
            assertThat(hot.getLatencyClass()).isEqualTo(StorageProfile.LatencyClass.IMMEDIATE);
        }

        @Test
        @DisplayName("predefined WARM profile supports RELATIONAL")
        void predefinedWarmProfile() {
            StorageProfile warm = StorageProfile.warmProfile();

            assertThat(warm.supports(StorageBackendType.RELATIONAL)).isTrue();
            assertThat(warm.getLatencyClass()).isEqualTo(StorageProfile.LatencyClass.STANDARD);
        }
    }

    // =========================================================================
    //  Connector capability negotiation via default methods
    // =========================================================================

    @Nested
    @DisplayName("StorageConnector default capability methods")
    class ConnectorCapabilityNegotiationTests {

        /**
         * Minimal stub that satisfies the StorageConnector contract by delegating
         * all abstract methods to unsupported-operation, while returning a real
         * ConnectorMetadata from getMetadata(). Tests target only the default methods.
         */
        private StorageConnector timeSeriesConnector(boolean supportsTimeSeries) {
            StorageConnector.ConnectorMetadata meta = StorageConnector.ConnectorMetadata.builder()
                    .backendType(StorageBackendType.TIMESERIES)
                    .supportsTimeSeries(supportsTimeSeries)
                    .supportsTransactions(false)
                    .maxBatchSize(10_000)
                    .build();

            return new StorageConnector() {
                @Override public ConnectorMetadata getMetadata() { return meta; }
                @Override public Promise<Entity> create(Entity e) { throw new UnsupportedOperationException(); }
                @Override public Promise<Optional<Entity>> read(UUID cId, String tId, UUID eId) { throw new UnsupportedOperationException(); }
                @Override public Promise<Entity> update(Entity e) { throw new UnsupportedOperationException(); }
                @Override public Promise<Void> delete(UUID cId, String tId, UUID eId) { throw new UnsupportedOperationException(); }
                @Override public Promise<QueryResult> query(UUID cId, String tId, QuerySpec spec) { throw new UnsupportedOperationException(); }
                @Override public Promise<List<Entity>> scan(UUID cId, String tId, String filter, int limit, int offset) { throw new UnsupportedOperationException(); }
                @Override public Promise<Long> count(UUID cId, String tId, String filter) { throw new UnsupportedOperationException(); }
                @Override public Promise<List<Entity>> bulkCreate(UUID cId, String tId, List<Entity> entities) { throw new UnsupportedOperationException(); }
                @Override public Promise<List<Entity>> bulkUpdate(UUID cId, String tId, List<Entity> entities) { throw new UnsupportedOperationException(); }
                @Override public Promise<Long> bulkDelete(UUID cId, String tId, List<UUID> ids) { throw new UnsupportedOperationException(); }
                @Override public Promise<Long> truncate(UUID cId, String tId) { throw new UnsupportedOperationException(); }
                @Override public Promise<Void> healthCheck() { throw new UnsupportedOperationException(); }
            };
        }

        @Test
        @DisplayName("getConnectorType delegates to getMetadata().backendType()")
        void getConnectorTypeDelegatesToMetadata() {
            StorageConnector connector = timeSeriesConnector(true);

            assertThat(connector.getConnectorType()).isEqualTo(StorageBackendType.TIMESERIES);
        }

        @Test
        @DisplayName("supportsWindowedQueries is true when supportsTimeSeries=true")
        void supportsWindowedQueriesReflectsMetadata() {
            assertThat(timeSeriesConnector(true).supportsWindowedQueries()).isTrue();
            assertThat(timeSeriesConnector(false).supportsWindowedQueries()).isFalse();
        }

        @Test
        @DisplayName("query(String, String, Map) throws UnsupportedOperationException by default")
        void queryByCollectionNameMapDefaultThrows() {
            StorageConnector connector = timeSeriesConnector(false);

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> connector.query("tenant-1", "orders", Map.of("status", "active")))
                    .withMessageContaining("does not implement query(String, String, Map)");
        }

        @Test
        @DisplayName("deleteByQuery throws UnsupportedOperationException by default")
        void deleteByQueryDefaultThrows() {
            StorageConnector connector = timeSeriesConnector(false);

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> connector.deleteByQuery("tenant-1", "orders", Map.of()))
                    .withMessageContaining("does not implement deleteByQuery");
        }
    }

    // =========================================================================
    //  Schema evolution: entity data map
    // =========================================================================

    @Nested
    @DisplayName("Schema evolution – entity data map")
    class SchemaEvolutionTests {

        @Test
        @DisplayName("entity data map accepts new fields without breaking existing consumers")
        void entityDataMapAcceptsNewFields() {
            // v1 schema: {name, status}
            Map<String, Object> v1Data = new HashMap<>();
            v1Data.put("name", "order-1");
            v1Data.put("status", "pending");

            Entity entityV1 = Entity.builder()
                    .tenantId("tenant-evolve")
                    .collectionName("orders")
                    .data(v1Data)
                    .build();

            assertThat(entityV1.getData()).containsKey("name");
            assertThat(entityV1.getData()).doesNotContainKey("priority");

            // v2 schema: adds {priority} field
            Map<String, Object> v2Data = new HashMap<>(v1Data);
            v2Data.put("priority", "high");

            Entity entityV2 = Entity.builder()
                    .tenantId("tenant-evolve")
                    .collectionName("orders")
                    .data(v2Data)
                    .build();

            // v2 consumer can read both old and new fields
            assertThat(entityV2.getData().get("name")).isEqualTo("order-1");
            assertThat(entityV2.getData().get("status")).isEqualTo("pending");
            assertThat(entityV2.getData().get("priority")).isEqualTo("high");
        }

        @Test
        @DisplayName("v1 consumer reading v2 data ignores unknown fields gracefully")
        void v1ConsumerIgnoresUnknownFields() {
            Map<String, Object> v2Data = new HashMap<>();
            v2Data.put("name", "order-2");
            v2Data.put("status", "active");
            v2Data.put("priority", "high");   // new in v2
            v2Data.put("auditTag", "system"); // new in v2

            Entity entity = Entity.builder()
                    .tenantId("tenant-evolve")
                    .collectionName("orders")
                    .data(v2Data)
                    .build();

            // v1 consumer only reads known fields; unknown ones are present but not accessed
            String name = (String) entity.getData().get("name");
            String status = (String) entity.getData().get("status");

            assertThat(name).isEqualTo("order-2");
            assertThat(status).isEqualTo("active");
            // No ClassCastException, no NPE — backward compatible
        }

        @Test
        @DisplayName("removing a field from data map leaves other fields intact")
        void removingFieldLeavesOthersIntact() {
            Map<String, Object> data = new HashMap<>();
            data.put("name", "order-3");
            data.put("deprecated_field", "old-value");
            data.put("status", "closed");

            // Simulate field removal (v3 removes 'deprecated_field')
            Map<String, Object> v3Data = new HashMap<>(data);
            v3Data.remove("deprecated_field");

            Entity entity = Entity.builder()
                    .tenantId("tenant-evolve")
                    .collectionName("orders")
                    .data(v3Data)
                    .build();

            assertThat(entity.getData()).containsKey("name");
            assertThat(entity.getData()).containsKey("status");
            assertThat(entity.getData()).doesNotContainKey("deprecated_field");
        }

        @Test
        @DisplayName("entity with null data is handled by builder (null data = null map)")
        void entityWithNullData() {
            Entity entity = Entity.builder()
                    .tenantId("tenant-evolve")
                    .collectionName("orders")
                    .data(null)
                    .build();

            // Null data is valid for construction; callers are responsible for null checks
            assertThat(entity.getData()).isNull();
        }
    }

    // =========================================================================
    //  StorageBackendType enum properties
    // =========================================================================

    @Nested
    @DisplayName("StorageBackendType enum")
    class StorageBackendTypeTests {

        @Test
        @DisplayName("every backend type has a non-blank label and identifier")
        void everyBackendTypeHasNonBlankLabelAndIdentifier() {
            for (StorageBackendType type : StorageBackendType.values()) {
                assertThat(type.getLabel())
                        .as("label for %s must not be blank", type)
                        .isNotBlank();
                assertThat(type.getIdentifier())
                        .as("identifier for %s must not be blank", type)
                        .isNotBlank();
            }
        }

        @Test
        @DisplayName("TIMESERIES isWindowedQueryOptimized returns true, RELATIONAL and KEY_VALUE do not")
        void windowedQueryOptimizedFlag() {
            assertThat(StorageBackendType.TIMESERIES.isWindowedQueryOptimized()).isTrue();
            assertThat(StorageBackendType.RELATIONAL.isWindowedQueryOptimized()).isFalse();
            assertThat(StorageBackendType.KEY_VALUE.isWindowedQueryOptimized()).isFalse();
        }
    }
}
