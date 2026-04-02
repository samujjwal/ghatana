package com.ghatana.datacloud.cache;

import com.ghatana.platform.cache.DistributedCacheService;
import com.ghatana.datacloud.cache.DataCloudQueryCacheService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Integration tests for Data Cloud query result caching service
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Data Cloud Query Cache Service Integration Tests")
class DataCloudQueryCacheServiceIntegrationTest {

    @Mock
    private DistributedCacheService cacheService;

    private DataCloudQueryCacheService queryCache;

    @BeforeEach
    void setUp() {
        queryCache = new DataCloudQueryCacheService(cacheService);
    }

    @Nested
    @DisplayName("Query Result Caching")
    class QueryResultCachingTests {

        @Test
        void shouldCacheQueryResult() {
            // Given
            String queryId = "query-123";
            String datasetId = "dataset-456";
            QueryResult result = new QueryResult(
                    queryId,
                    datasetId,
                    new String[]{"col1", "col2"},
                    new Object[][]{{"val1", "val2"}},
                    150,
                    System.currentTimeMillis()
            );

            // When
            queryCache.cacheQueryResult(queryId, datasetId, result);

            // Then
            verify(cacheService).put(
                    eq("query:result:" + datasetId + ":" + queryId),
                    eq(result),
                    eq(1800L)
            );
        }

        @Test
        void shouldRetrieveCachedQueryResult() {
            // Given
            String queryId = "query-789";
            String datasetId = "dataset-999";
            QueryResult result = new QueryResult(
                    queryId,
                    datasetId,
                    new String[]{"id", "name"},
                    new Object[][]{{"1", "John"}, {"2", "Jane"}},
                    200,
                    System.currentTimeMillis()
            );
            when(cacheService.get("query:result:" + datasetId + ":" + queryId, QueryResult.class))
                    .thenReturn(Optional.of(result));

            // When
            Optional<QueryResult> cached = queryCache.getCachedQueryResult(queryId, datasetId);

            // Then
            assertThat(cached).isPresent();
            assertThat(cached.get().rows).hasLength(2);
        }

        @Test
        void shouldReturnEmptyWhenResultNotCached() {
            // Given
            String queryId = "missing-query";
            String datasetId = "missing-dataset";
            when(cacheService.get("query:result:" + datasetId + ":" + queryId, QueryResult.class))
                    .thenReturn(Optional.empty());

            // When
            Optional<QueryResult> cached = queryCache.getCachedQueryResult(queryId, datasetId);

            // Then
            assertThat(cached).isEmpty();
        }

        @Test
        void shouldInvalidateAllQueriesForDataset() {
            // Given
            String datasetId = "dataset-to-invalidate";

            // When
            queryCache.invalidateDatasetQueries(datasetId);

            // Then
            verify(cacheService).invalidatePattern("query:result:" + datasetId + ":*");
        }
    }

    @Nested
    @DisplayName("Dataset Metadata Caching")
    class DatasetMetadataCachingTests {

        @Test
        void shouldCacheDatasetMetadata() {
            // Given
            String datasetId = "dataset-123";
            DatasetMetadata metadata = new DatasetMetadata(
                    datasetId,
                    "Customer Data",
                    1000000,
                    new String[]{"id", "name", "email"},
                    new String[]{"int", "string", "string"},
                    System.currentTimeMillis()
            );

            // When
            queryCache.cacheDatasetMetadata(datasetId, metadata);

            // Then
            verify(cacheService).put(
                    eq("dataset:metadata:" + datasetId),
                    eq(metadata),
                    eq(3600L)
            );
        }

        @Test
        void shouldRetrieveCachedDatasetMetadata() {
            // Given
            String datasetId = "dataset-456";
            DatasetMetadata metadata = new DatasetMetadata(
                    datasetId,
                    "Sales Data",
                    500000,
                    new String[]{"transaction_id", "amount"},
                    new String[]{"string", "float"},
                    System.currentTimeMillis()
            );
            when(cacheService.get("dataset:metadata:" + datasetId, DatasetMetadata.class))
                    .thenReturn(Optional.of(metadata));

            // When
            Optional<DatasetMetadata> cached = queryCache.getCachedDatasetMetadata(datasetId);

            // Then
            assertThat(cached).isPresent();
            assertThat(cached.get().recordCount).isEqualTo(500000);
            assertThat(cached.get().columnNames).hasLength(2);
        }

        @Test
        void shouldInvalidateDatasetMetadata() {
            // Given
            String datasetId = "dataset-invalidate";

            // When
            queryCache.invalidateDatasetMetadata(datasetId);

            // Then
            verify(cacheService).invalidate("dataset:metadata:" + datasetId);
        }
    }

    @Nested
    @DisplayName("Query Plan Caching")
    class QueryPlanCachingTests {

        @Test
        void shouldCacheQueryPlan() {
            // Given
            String planId = "plan-123";
            QueryPlan plan = new QueryPlan(
                    planId,
                    "SELECT * FROM customers",
                    "sequential_scan",
                    new String[]{"scan_customers", "output"},
                    5000
            );

            // When
            queryCache.cacheQueryPlan(planId, plan);

            // Then
            verify(cacheService).put(
                    eq("query:plan:" + planId),
                    eq(plan),
                    eq(900L)
            );
        }

        @Test
        void shouldRetrieveCachedQueryPlan() {
            // Given
            String planId = "plan-456";
            QueryPlan plan = new QueryPlan(
                    planId,
                    "SELECT * FROM orders WHERE status='completed'",
                    "indexed_scan",
                    new String[]{"index_scan_orders", "filter", "output"},
                    3000
            );
            when(cacheService.get("query:plan:" + planId, QueryPlan.class))
                    .thenReturn(Optional.of(plan));

            // When
            Optional<QueryPlan> cached = queryCache.getCachedQueryPlan(planId);

            // Then
            assertThat(cached).isPresent();
            assertThat(cached.get().executionStrategy).isEqualTo("indexed_scan");
        }

        @Test
        void shouldInvalidateQueryPlan() {
            // Given
            String planId = "plan-invalidate";

            // When
            queryCache.invalidateQueryPlan(planId);

            // Then
            verify(cacheService).invalidate("query:plan:" + planId);
        }
    }

    @Nested
    @DisplayName("Aggregation Caching")
    class AggregationCachingTests {

        @Test
        void shouldCacheAggregationResult() {
            // Given
            String aggregationId = "agg-123";
            String datasetId = "dataset-789";
            AggregationResult result = new AggregationResult(
                    aggregationId,
                    datasetId,
                    "sum",
                    150000.50,
                    new String[]{"region"},
                    System.currentTimeMillis()
            );

            // When
            queryCache.cacheAggregation(aggregationId, datasetId, result);

            // Then
            verify(cacheService).put(
                    eq("aggregate:" + datasetId + ":" + aggregationId),
                    eq(result),
                    eq(1800L)
            );
        }

        @Test
        void shouldRetrieveCachedAggregation() {
            // Given
            String aggregationId = "agg-456";
            String datasetId = "dataset-111";
            AggregationResult result = new AggregationResult(
                    aggregationId,
                    datasetId,
                    "count",
                    5000L,
                    new String[]{"category"},
                    System.currentTimeMillis()
            );
            when(cacheService.get("aggregate:" + datasetId + ":" + aggregationId, AggregationResult.class))
                    .thenReturn(Optional.of(result));

            // When
            Optional<AggregationResult> cached = queryCache.getCachedAggregation(aggregationId, datasetId);

            // Then
            assertThat(cached).isPresent();
            assertThat(cached.get().aggregationType).isEqualTo("count");
        }

        @Test
        void shouldInvalidateDatasetAggregations() {
            // Given
            String datasetId = "dataset-agg-invalidate";

            // When
            queryCache.invalidateDatasetAggregations(datasetId);

            // Then
            verify(cacheService).invalidatePattern("aggregate:" + datasetId + ":*");
        }
    }

    @Nested
    @DisplayName("Bulk Cache Invalidation")
    class BulkCacheInvalidationTests {

        @Test
        void shouldInvalidateAllCaches() {
            // When
            queryCache.invalidateAllCaches();

            // Then
            verify(cacheService).invalidatePattern("query:*");
            verify(cacheService).invalidatePattern("dataset:*");
            verify(cacheService).invalidatePattern("aggregate:*");
        }

        @Test
        void shouldInvalidateBasedOnDatasetId() {
            // Given
            String datasetId = "dataset-bulk-invalidate";

            // When
            queryCache.invalidateDatasetQueries(datasetId);
            queryCache.invalidateDatasetMetadata(datasetId);
            queryCache.invalidateDatasetAggregations(datasetId);

            // Then
            verify(cacheService).invalidatePattern("query:result:" + datasetId + ":*");
            verify(cacheService).invalidate("dataset:metadata:" + datasetId);
            verify(cacheService).invalidatePattern("aggregate:" + datasetId + ":*");
        }
    }

    @Nested
    @DisplayName("Cache Metrics")
    class CacheMetricsTests {

        @Test
        void shouldProvideDatasetCacheMetrics() {
            // Given
            String datasetId = "dataset-metrics";
            DistributedCacheService.CacheStatistics stats = 
                    new DistributedCacheService.CacheStatistics(50, 2048000);
            when(cacheService.getStatistics("*:" + datasetId + ":*")).thenReturn(stats);

            // When
            DataCloudQueryCacheService.CacheMetrics metrics = queryCache.getDatasetCacheMetrics(datasetId);

            // Then
            assertThat(metrics.cachedItems).isEqualTo(50);
            assertThat(metrics.totalSizeBytes).isEqualTo(2048000);
        }
    }

    @Nested
    @DisplayName("Cache TTL Configuration")
    class CacheTTLConfigurationTests {

        @Test
        void shouldUseDifferentTTLsForDifferentDataTypes() {
            // Given
            QueryResult queryResult = new QueryResult("q1", "d1", new String[0], new Object[0][0], 100, System.currentTimeMillis());
            DatasetMetadata metadata = new DatasetMetadata("d1", "name", 100, new String[0], new String[0], System.currentTimeMillis());
            QueryPlan plan = new QueryPlan("p1", "query", "strategy", new String[0], 100);
            AggregationResult agg = new AggregationResult("a1", "d1", "sum", 0, new String[0], System.currentTimeMillis());

            // When
            queryCache.cacheQueryResult("q1", "d1", queryResult);
            queryCache.cacheDatasetMetadata("d1", metadata);
            queryCache.cacheQueryPlan("p1", plan);
            queryCache.cacheAggregation("a1", "d1", agg);

            // Then - verify different TTLs used
            verify(cacheService).put(anyString(), eq(queryResult), eq(1800L));      // 30 minutes
            verify(cacheService).put(anyString(), eq(metadata), eq(3600L));         // 1 hour
            verify(cacheService).put(anyString(), eq(plan), eq(900L));              // 15 minutes
            verify(cacheService).put(anyString(), eq(agg), eq(1800L));              // 30 minutes
        }
    }

    @Nested
    @DisplayName("Large Dataset Handling")
    class LargeDatasetHandlingTests {

        @Test
        void shouldHandleLargeQueryResults() {
            // Given
            String queryId = "large-query";
            String datasetId = "large-dataset";
            Object[][] largeRows = new Object[10000][10];
            for (int i = 0; i < 10000; i++) {
                for (int j = 0; j < 10; j++) {
                    largeRows[i][j] = "value-" + i + "-" + j;
                }
            }
            QueryResult result = new QueryResult(
                    queryId,
                    datasetId,
                    new String[]{"col1", "col2", "col3", "col4", "col5", "col6", "col7", "col8", "col9", "col10"},
                    largeRows,
                    5000,
                    System.currentTimeMillis()
            );

            // When
            queryCache.cacheQueryResult(queryId, datasetId, result);

            // Then
            verify(cacheService).put(anyString(), eq(result), anyLong());
        }

        @Test
        void shouldHandleLargeDatasets() {
            // Given
            String datasetId = "huge-dataset";
            DatasetMetadata metadata = new DatasetMetadata(
                    datasetId,
                    "Huge Dataset",
                    100000000,
                    new String[]{"id", "data", "timestamp"},
                    new String[]{"bigint", "text", "timestamp"},
                    System.currentTimeMillis()
            );

            // When
            queryCache.cacheDatasetMetadata(datasetId, metadata);

            // Then
            verify(cacheService).put(anyString(), eq(metadata), anyLong());
        }
    }

    @Nested
    @DisplayName("Multiple Concurrent Queries")
    class MultipleConcurrentQueriesTests {

        @Test
        void shouldHandleMultipleQueriesOnSameDataset() {
            // Given
            String datasetId = "shared-dataset";
            String[] queryIds = {"q1", "q2", "q3"};
            QueryResult[] results = new QueryResult[3];
            for (int i = 0; i < 3; i++) {
                results[i] = new QueryResult(
                        queryIds[i],
                        datasetId,
                        new String[]{"col1"},
                        new Object[][]{{"val" + i}},
                        100,
                        System.currentTimeMillis()
                );
            }

            // When
            for (int i = 0; i < 3; i++) {
                queryCache.cacheQueryResult(queryIds[i], datasetId, results[i]);
            }

            // Then - all should be cached separately
            for (int i = 0; i < 3; i++) {
                verify(cacheService).put(
                        eq("query:result:" + datasetId + ":" + queryIds[i]),
                        eq(results[i]),
                        anyLong()
                );
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        void shouldHandleEmptyQueryResults() {
            // Given
            String queryId = "empty-query";
            String datasetId = "empty-dataset";
            QueryResult result = new QueryResult(
                    queryId,
                    datasetId,
                    new String[]{"col1"},
                    new Object[0][0],
                    10,
                    System.currentTimeMillis()
            );

            // When
            queryCache.cacheQueryResult(queryId, datasetId, result);

            // Then
            verify(cacheService).put(anyString(), eq(result), anyLong());
        }

        @Test
        void shouldHandleNullAggregationResult() {
            // Given
            String aggregationId = "null-agg";
            String datasetId = "null-dataset";
            AggregationResult result = new AggregationResult(
                    aggregationId,
                    datasetId,
                    "count",
                    null,
                    new String[0],
                    System.currentTimeMillis()
            );

            // When
            queryCache.cacheAggregation(aggregationId, datasetId, result);

            // Then
            verify(cacheService).put(anyString(), eq(result), anyLong());
        }

        @Test
        void shouldHandleSpecialCharactersInIdentifiers() {
            // Given
            String queryId = "query-with-special-chars:@#$%";
            String datasetId = "dataset-with-special:!@#$";
            QueryResult result = new QueryResult(
                    queryId,
                    datasetId,
                    new String[]{"col.1"},
                    new Object[][]{{"val"}},
                    100,
                    System.currentTimeMillis()
            );

            // When
            queryCache.cacheQueryResult(queryId, datasetId, result);

            // Then
            verify(cacheService).put(anyString(), eq(result), anyLong());
        }
    }
}
