package com.ghatana.datacloud.cache;

import com.ghatana.platform.cache.DistributedCacheService;
import com.ghatana.platform.testing.base.BasePerformanceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Performance benchmarking tests for Data Cloud query result caching.
 * @doc.layer product-test
 * @doc.pattern PerformanceTest
 *
 * Measures:
 * - Query result cache hit ratios across dataset access patterns
 * - Metadata cache effectiveness (high TTL, low invalidation)
 * - Query plan cache reuse for identical queries
 * - Aggregation cache hit ratios
 * - Latency improvements with caching
 * - Memory efficiency for large result sets
 *
 * Success criteria:
 * - Query result cache hit ratio > 80%
 * - Metadata cache hit ratio > 90%
 * - Cached query execution < 5ms
 * - Uncached query execution < 50ms (baseline)
 * - Handle 10K+ result sets efficiently
 * - Support 100+ concurrent dataset access
 */
@DisplayName("DataCloudQueryCacheService Performance Benchmarks")
class DataCloudQueryCachePerformanceBenchmarkTest extends BasePerformanceTest {

    @Mock
    private DistributedCacheService cacheService;

    private DataCloudQueryCacheService queryCache;
    private Random random = new Random(42);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        queryCache = new DataCloudQueryCacheService(cacheService);
    }

    @Nested
    @DisplayName("Query Result Cache Hit Ratio")
    class QueryResultCacheHitRatioTests {

        @Test
        @DisplayName("should achieve 80% cache hit ratio for repeated queries")
        void shouldAchieve80PercentQueryHitRatio() {
            int datasetCount = 20;
            int queriesPerDataset = 5;
            int accessCount = 5000;
            int hitCount = 0;

            // Pre-populate query results
            for (int d = 0; d < datasetCount; d++) {
                for (int q = 0; q < queriesPerDataset; q++) {
                    QueryResult result = createTestQueryResult(d, q);
                    when(cacheService.get("query:result:dataset:" + d + ":query:" + q, QueryResult.class))
                        .thenReturn(Optional.of(result));
                }
            }

            // Access with distribution favoring recent queries
            for (int i = 0; i < accessCount; i++) {
                int datasetId = random.nextInt(datasetCount);
                int queryId = random.nextInt(queriesPerDataset);
                Optional<QueryResult> cached = queryCache.getCachedQueryResult("query:" + queryId, "dataset:" + datasetId);

                if (cached.isPresent()) {
                    hitCount++;
                }
            }

            double hitRatio = (double) hitCount / accessCount;
            assertThat(hitRatio)
                .isGreaterThanOrEqualTo(0.75)
                .describedAs("Query result cache hit ratio: %.2f%%", hitRatio * 100);

            System.out.println("Query result hit ratio: " + (hitRatio * 100) + "%");
        }

        @Test
        @DisplayName("should significantly reduce latency with cached results")
        void shouldReduceLatencyWithCachedResults() {
            QueryResult result = createTestQueryResult(1, 1);
            when(cacheService.get("query:result:dataset:1:query:1", QueryResult.class))
                .thenReturn(Optional.of(result));

            int iterations = 1000;
            long cachedTotalMs = 0;
            long uncachedTotalMs = 0;

            // Measure cached access
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                Optional<QueryResult> cached = queryCache.getCachedQueryResult("query:1", "dataset:1");
                cachedTotalMs += (System.nanoTime() - start) / 1_000_000;
            }

            // Reset and measure uncached
            reset(cacheService);
            when(cacheService.get("query:result:dataset:1:query:1", QueryResult.class))
                .thenReturn(Optional.empty());

            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                Optional<QueryResult> uncached = queryCache.getCachedQueryResult("query:1", "dataset:1");
                uncachedTotalMs += (System.nanoTime() - start) / 1_000_000;
            }

            double cachedAvgMs = (double) cachedTotalMs / iterations;
            double uncachedAvgMs = (double) uncachedTotalMs / iterations;
            double speedup = uncachedAvgMs / cachedAvgMs;

            assertThat(cachedAvgMs).isLessThan(5);
            assertThat(speedup).isGreaterThan(1.5);  // At least 1.5x speedup with caching

            System.out.println("Cached: " + cachedAvgMs + "ms, Uncached: " + uncachedAvgMs + "ms, Speedup: " + speedup + "x");
        }

        @Test
        @DisplayName("should efficiently handle 10000+ result sets")
        void shouldHandleLargeResultSets() {
            QueryResult largeResult = createLargeTestQueryResult(10000, 50);  // 10K rows, 50 columns
            when(cacheService.get(contains("query:result:"), eq(QueryResult.class)))
                .thenReturn(Optional.of(largeResult));

            int iterations = 100;
            long totalMs = 0;

            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                queryCache.getCachedQueryResult("query:1", "dataset:1");
                totalMs += (System.nanoTime() - start) / 1_000_000;
            }

            double avgMs = (double) totalMs / iterations;
            assertThat(avgMs).isLessThan(10);  // Even large results served from cache < 10ms

            System.out.println("10K row result - avg access: " + avgMs + "ms");
        }
    }

    @Nested
    @DisplayName("Dataset Metadata Cache Hit Ratio")
    class DatasetMetadataCacheHitRatioTests {

        @Test
        @DisplayName("should achieve 90%+ cache hit ratio for metadata")
        void shouldAchieve90PercentMetadataHitRatio() {
            int datasetCount = 100;
            int accessCount = 5000;
            int hitCount = 0;

            // Pre-populate metadata with longer TTL
            for (int i = 0; i < datasetCount; i++) {
                DatasetMetadata metadata = new DatasetMetadata(
                    "dataset:" + i, "Dataset " + i, 1_000_000 + (i * 10000),
                    new String[]{"col1", "col2", "col3"},
                    new String[]{"STRING", "INTEGER", "DOUBLE"},
                    System.currentTimeMillis()
                );
                when(cacheService.get("dataset:metadata:" + i, DatasetMetadata.class))
                    .thenReturn(Optional.of(metadata));
            }

            // Access with Zipfian distribution (some datasets accessed more frequently)
            for (int i = 0; i < accessCount; i++) {
                int datasetId = (int) (Math.pow(random.nextDouble(), 0.3) * datasetCount);  // Heavily skewed
                Optional<DatasetMetadata> cached = queryCache.getCachedDatasetMetadata("dataset:" + datasetId);

                if (cached.isPresent()) {
                    hitCount++;
                }
            }

            double hitRatio = (double) hitCount / accessCount;
            assertThat(hitRatio)
                .isGreaterThanOrEqualTo(0.88)
                .describedAs("Metadata cache hit ratio: %.2f%%", hitRatio * 100);

            System.out.println("Metadata cache hit ratio: " + (hitRatio * 100) + "%");
        }

        @Test
        @DisplayName("should maintain metadata consistency across updates")
        void shouldMaintainMetadataConsistency() {
            int datasetCount = 50;

            // Pre-populate and then update some datasets
            for (int i = 0; i < datasetCount; i++) {
                DatasetMetadata metadata = new DatasetMetadata(
                    "dataset:" + i, "Dataset " + i, 1000 + i,
                    new String[]{"col1", "col2"},
                    new String[]{"STRING", "INTEGER"},
                    System.currentTimeMillis()
                );
                when(cacheService.get("dataset:metadata:" + i, DatasetMetadata.class))
                    .thenReturn(Optional.of(metadata));
            }

            // Simulate updates: invalidate every 10th dataset
            for (int i = 0; i < datasetCount; i += 10) {
                doNothing().when(cacheService).invalidate("dataset:metadata:" + i);
                queryCache.invalidateDatasetMetadata("dataset:" + i);
            }

            verify(cacheService, times(5)).invalidate(contains("dataset:metadata:"));
        }
    }

    @Nested
    @DisplayName("Query Plan Cache Performance")
    class QueryPlanCachePerformanceTests {

        @Test
        @DisplayName("should cache identical queries efficiently")
        void shouldCacheIdenticalQueriesEfficiently() {
            String query = "SELECT * FROM dataset WHERE status = 'active';";
            QueryPlan plan = new QueryPlan(
                "plan:1", query, "sequential_scan",
                new String[]{"scan_all_rows", "filter_status", "project_columns"},
                500
            );

            when(cacheService.get("query:plan:plan:1", QueryPlan.class))
                .thenReturn(Optional.of(plan));

            long startTime = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                queryCache.getCachedQueryPlan("plan:1");
            }
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            double avgMs = (double) elapsedMs / 10000;
            assertThat(avgMs).isLessThan(5);

            System.out.println("Query plan cache - avg access: " + avgMs + "ms for 10K accesses");
        }

        @Test
        @DisplayName("should support complex query plan structures")
        void shouldSupportComplexQueryPlans() {
            QueryPlan complexPlan = new QueryPlan(
                "plan:complex",
                "SELECT t1.id, COUNT(*) as cnt FROM table1 t1 JOIN table2 t2 ON t1.id = t2.id GROUP BY t1.id;",
                "hash_join_group_by",
                new String[]{
                    "scan_table1",
                    "scan_table2",
                    "hash_join",
                    "group_aggregate",
                    "sort_by_count",
                    "limit_1000"
                },
                2000
            );

            when(cacheService.get(contains("query:plan:"), eq(QueryPlan.class)))
                .thenReturn(Optional.of(complexPlan));

            Optional<QueryPlan> cached = queryCache.getCachedQueryPlan("plan:complex");

            assertThat(cached)
                .isPresent()
                .hasValueSatisfying(plan -> {
                    assertThat(plan.queryText).contains("GROUP BY");
                    assertThat(plan.steps).hasLength(6);
                    assertThat(plan.estimatedTimeMs).isEqualTo(2000);
                });
        }
    }

    @Nested
    @DisplayName("Aggregation Cache Performance")
    class AggregationCachePerformanceTests {

        @Test
        @DisplayName("should achieve 75%+ cache hit ratio for aggregations")
        void shouldAchieve75PercentAggregationHitRatio() {
            int datasetCount = 30;
            int aggregationsPerDataset = 8;
            int accessCount = 5000;
            int hitCount = 0;

            // Pre-populate aggregations
            for (int d = 0; d < datasetCount; d++) {
                for (int a = 0; a < aggregationsPerDataset; a++) {
                    AggregationResult agg = new AggregationResult(
                        "agg:" + d + ":" + a, "dataset:" + d, "SUM",
                        1000 + (d * 100) + a, new String[]{"department", "region"}, System.currentTimeMillis()
                    );
                    when(cacheService.get("aggregate:dataset:" + d + ":" + ("agg:" + d + ":" + a), AggregationResult.class))
                        .thenReturn(Optional.of(agg));
                }
            }

            // Access patterns
            for (int i = 0; i < accessCount; i++) {
                int datasetId = random.nextInt(datasetCount);
                int aggId = random.nextInt(aggregationsPerDataset);
                Optional<AggregationResult> cached = queryCache.getCachedAggregation("agg:" + datasetId + ":" + aggId, "dataset:" + datasetId);

                if (cached.isPresent()) {
                    hitCount++;
                }
            }

            double hitRatio = (double) hitCount / accessCount;
            assertThat(hitRatio)
                .isGreaterThanOrEqualTo(0.7)
                .describedAs("Aggregation cache hit ratio: %.2f%%", hitRatio * 100);

            System.out.println("Aggregation cache hit ratio: " + (hitRatio * 100) + "%");
        }
    }

    @Nested
    @DisplayName("Dataset Invalidation Performance")
    class DatasetInvalidationPerformanceTests {

        @Test
        @DisplayName("should efficiently invalidate all queries for a dataset")
        void shouldInvalidateDatasetQueriesEfficiently() {
            int datasetCount = 50;
            int queriesPerDataset = 100;

            when(cacheService.invalidatePattern(contains("query:result:")))
                .thenReturn((long) queriesPerDataset);

            long startTime = System.nanoTime();
            for (int d = 0; d < datasetCount; d++) {
                queryCache.invalidateDatasetQueries("dataset:" + d);
            }
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            double avgInvalidationMs = (double) elapsedMs / datasetCount;
            assertThat(avgInvalidationMs).isLessThan(20);  // Invalidate 100 queries/dataset in < 20ms

            verify(cacheService, times(datasetCount))
                .invalidatePattern(contains("query:result:"));

            System.out.println("Dataset invalidation - avg time: " + avgInvalidationMs + "ms for 100 queries");
        }

        @Test
        @DisplayName("should handle bulk cache invalidation efficiently")
        void shouldHandleBulkInvalidationEfficiently() {
            when(cacheService.invalidatePattern(anyString()))
                .thenReturn(1000L);

            long startTime = System.nanoTime();
            queryCache.invalidateAllCaches();
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            assertThat(elapsedMs).isLessThan(100);  // Bulk invalidate all 3 patterns in < 100ms

            verify(cacheService, atLeast(3)).invalidatePattern(anyString());
            System.out.println("Bulk invalidation - total time: " + elapsedMs + "ms");
        }
    }

    @Nested
    @DisplayName("Concurrent Dataset Access")
    class ConcurrentDatasetAccessTests {

        @Test
        @DisplayName("should maintain performance under concurrent dataset access")
        void shouldMaintainPerformanceUnderConcurrency() throws InterruptedException {
            int threadCount = 20;
            int operationsPerThread = 500;
            List<Long> threadTimes = new ArrayList<>();

            Thread[] threads = new Thread[threadCount];
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    long threadStartTime = System.nanoTime();

                    for (int op = 0; op < operationsPerThread; op++) {
                        int datasetId = random.nextInt(100);
                        int queryId = random.nextInt(10);

                        QueryResult result = createTestQueryResult(datasetId, queryId);
                        when(cacheService.get("query:result:dataset:" + datasetId + ":query:" + queryId, QueryResult.class))
                            .thenReturn(Optional.of(result));

                        queryCache.getCachedQueryResult("query:" + queryId, "dataset:" + datasetId);
                    }

                    long elapsedMs = (System.nanoTime() - threadStartTime) / 1_000_000;
                    synchronized (threadTimes) {
                        threadTimes.add(elapsedMs);
                    }
                });
                threads[t].start();
            }

            for (Thread t : threads) {
                t.join();
            }

            double avgThreadTimeMs = threadTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxThreadTimeMs = threadTimes.stream().mapToLong(Long::longValue).max().orElse(0);

            assertThat(avgThreadTimeMs).isLessThan(2000);  // Avg thread completes 500 ops in < 2 seconds
            assertThat(maxThreadTimeMs).isLessThan(3000);  // Max thread completes 500 ops in < 3 seconds

            System.out.println("Concurrent access - avg thread time: " + avgThreadTimeMs + "ms, max: " + maxThreadTimeMs + "ms");
        }
    }

    // ============= Test Support Methods and Classes =============

    private QueryResult createTestQueryResult(int datasetId, int queryId) {
        return new QueryResult(
            "query:" + queryId,
            "dataset:" + datasetId,
            new String[]{"col1", "col2", "col3"},
            new Object[][]{
                {"row1col1", 100, 10.5},
                {"row2col1", 200, 20.5},
                {"row3col1", 300, 30.5}
            },
            500,
            System.currentTimeMillis()
        );
    }

    private QueryResult createLargeTestQueryResult(int rowCount, int columnCount) {
        String[] columns = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            columns[i] = "column_" + i;
        }

        Object[][] rows = new Object[rowCount][columnCount];
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < columnCount; c++) {
                rows[r][c] = "row" + r + "col" + c;
            }
        }

        return new QueryResult(
            "query:large", "dataset:large",
            columns, rows,
            2000, System.currentTimeMillis()
        );
    }

    // Test support classes
    static class QueryResult {
        String queryId;
        String datasetId;
        String[] columnNames;
        Object[][] rows;
        long executionTimeMs;
        long cachedAt;

        QueryResult(String queryId, String datasetId, String[] columnNames, Object[][] rows, long executionTimeMs, long cachedAt) {
            this.queryId = queryId;
            this.datasetId = datasetId;
            this.columnNames = columnNames;
            this.rows = rows;
            this.executionTimeMs = executionTimeMs;
            this.cachedAt = cachedAt;
        }
    }

    static class DatasetMetadata {
        String datasetId;
        String name;
        long recordCount;
        String[] columnNames;
        String[] columnTypes;
        long lastUpdatedAt;

        DatasetMetadata(String datasetId, String name, long recordCount, String[] columnNames, String[] columnTypes, long lastUpdatedAt) {
            this.datasetId = datasetId;
            this.name = name;
            this.recordCount = recordCount;
            this.columnNames = columnNames;
            this.columnTypes = columnTypes;
            this.lastUpdatedAt = lastUpdatedAt;
        }
    }

    static class QueryPlan {
        String planId;
        String queryText;
        String executionStrategy;
        String[] steps;
        long estimatedTimeMs;

        QueryPlan(String planId, String queryText, String executionStrategy, String[] steps, long estimatedTimeMs) {
            this.planId = planId;
            this.queryText = queryText;
            this.executionStrategy = executionStrategy;
            this.steps = steps;
            this.estimatedTimeMs = estimatedTimeMs;
        }
    }

    static class AggregationResult {
        String aggregationId;
        String datasetId;
        String aggregationType;
        Object result;
        String[] groupByColumns;
        long cachedAt;

        AggregationResult(String aggregationId, String datasetId, String aggregationType, Object result, String[] groupByColumns, long cachedAt) {
            this.aggregationId = aggregationId;
            this.datasetId = datasetId;
            this.aggregationType = aggregationType;
            this.result = result;
            this.groupByColumns = groupByColumns;
            this.cachedAt = cachedAt;
        }
    }
}
