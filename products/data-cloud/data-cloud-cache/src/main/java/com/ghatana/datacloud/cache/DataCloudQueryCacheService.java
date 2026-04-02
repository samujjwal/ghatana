package com.ghatana.datacloud.cache;

import com.ghatana.platform.cache.DistributedCacheService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Query result caching and event streaming cache for Data Cloud
 * @doc.layer product
 * @doc.pattern Service
 */
public class DataCloudQueryCacheService {

    private static final Logger log = LoggerFactory.getLogger(DataCloudQueryCacheService.class);

    // Cache TTLs
    private static final long QUERY_RESULT_TTL = 1800; // 30 minutes
    private static final long DATASET_TTL = 3600; // 1 hour
    private static final long QUERY_PLAN_TTL = 900; // 15 minutes
    private static final long AGGREGATE_TTL = 1800; // 30 minutes

    private final DistributedCacheService cacheService;

    public DataCloudQueryCacheService(DistributedCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Get cached query result
     */
    public Optional<QueryResult> getCachedQueryResult(String queryId, String datasetId) {
        String key = "query:result:" + datasetId + ":" + queryId;
        return cacheService.get(key, QueryResult.class);
    }

    /**
     * Cache query result
     */
    public void cacheQueryResult(String queryId, String datasetId, QueryResult result) {
        String key = "query:result:" + datasetId + ":" + queryId;
        cacheService.put(key, result, QUERY_RESULT_TTL);
        log.info("Cached query result: {} for dataset: {}", queryId, datasetId);
    }

    /**
     * Get cached dataset metadata
     */
    public Optional<DatasetMetadata> getCachedDatasetMetadata(String datasetId) {
        String key = "dataset:metadata:" + datasetId;
        return cacheService.get(key, DatasetMetadata.class);
    }

    /**
     * Cache dataset metadata
     */
    public void cacheDatasetMetadata(String datasetId, DatasetMetadata metadata) {
        String key = "dataset:metadata:" + datasetId;
        cacheService.put(key, metadata, DATASET_TTL);
        log.info("Cached dataset metadata: {}", datasetId);
    }

    /**
     * Get cached query execution plan
     */
    public Optional<QueryPlan> getCachedQueryPlan(String planId) {
        String key = "query:plan:" + planId;
        return cacheService.get(key, QueryPlan.class);
    }

    /**
     * Cache query execution plan
     */
    public void cacheQueryPlan(String planId, QueryPlan plan) {
        String key = "query:plan:" + planId;
        cacheService.put(key, plan, QUERY_PLAN_TTL);
        log.info("Cached query plan: {}", planId);
    }

    /**
     * Get cached aggregation result
     */
    public Optional<AggregationResult> getCachedAggregation(String aggregationId, String datasetId) {
        String key = "aggregate:" + datasetId + ":" + aggregationId;
        return cacheService.get(key, AggregationResult.class);
    }

    /**
     * Cache aggregation result
     */
    public void cacheAggregation(String aggregationId, String datasetId, AggregationResult result) {
        String key = "aggregate:" + datasetId + ":" + aggregationId;
        cacheService.put(key, result, AGGREGATE_TTL);
        log.info("Cached aggregation: {} for dataset: {}", aggregationId, datasetId);
    }

    /**
     * Invalidate all query results for dataset
     */
    public void invalidateDatasetQueries(String datasetId) {
        String pattern = "query:result:" + datasetId + ":*";
        cacheService.invalidatePattern(pattern);
        log.info("Invalidated all query results for dataset: {}", datasetId);
    }

    /**
     * Invalidate dataset metadata cache
     */
    public void invalidateDatasetMetadata(String datasetId) {
        String key = "dataset:metadata:" + datasetId;
        cacheService.invalidate(key);
        log.info("Invalidated dataset metadata cache: {}", datasetId);
    }

    /**
     * Invalidate query plan
     */
    public void invalidateQueryPlan(String planId) {
        String key = "query:plan:" + planId;
        cacheService.invalidate(key);
        log.info("Invalidated query plan: {}", planId);
    }

    /**
     * Invalidate all aggregations for dataset
     */
    public void invalidateDatasetAggregations(String datasetId) {
        String pattern = "aggregate:" + datasetId + ":*";
        cacheService.invalidatePattern(pattern);
        log.info("Invalidated aggregations for dataset: {}", datasetId);
    }

    /**
     * Invalidate all Data Cloud caches
     */
    public void invalidateAllCaches() {
        cacheService.invalidatePattern("query:*");
        cacheService.invalidatePattern("dataset:*");
        cacheService.invalidatePattern("aggregate:*");
        log.info("Invalidated all Data Cloud caches");
    }

    /**
     * Get cache statistics for dataset
     */
    public CacheMetrics getDatasetCacheMetrics(String datasetId) {
        DistributedCacheService.CacheStatistics stats = 
            cacheService.getStatistics("*:" + datasetId + ":*");
        return new CacheMetrics(stats.totalKeys, stats.totalSize);
    }

    /**
     * Cache metrics
     */
    public static class CacheMetrics {
        public final long cachedItems;
        public final long totalSizeBytes;

        public CacheMetrics(long cachedItems, long totalSizeBytes) {
            this.cachedItems = cachedItems;
            this.totalSizeBytes = totalSizeBytes;
        }
    }

    /**
     * Domain model: Query result
     */
    public static class QueryResult {
        public String queryId;
        public String datasetId;
        public String[] columnNames;
        public Object[][] rows;
        public long executionTimeMs;
        public long cachedAt;

        public QueryResult() {}

        public QueryResult(String queryId, String datasetId, String[] columnNames,
                Object[][] rows, long executionTimeMs, long cachedAt) {
            this.queryId = queryId;
            this.datasetId = datasetId;
            this.columnNames = columnNames;
            this.rows = rows;
            this.executionTimeMs = executionTimeMs;
            this.cachedAt = cachedAt;
        }
    }

    /**
     * Domain model: Dataset metadata
     */
    public static class DatasetMetadata {
        public String datasetId;
        public String name;
        public long recordCount;
        public String[] columnNames;
        public String[] columnTypes;
        public long lastUpdatedAt;

        public DatasetMetadata() {}

        public DatasetMetadata(String datasetId, String name, long recordCount,
                String[] columnNames, String[] columnTypes, long lastUpdatedAt) {
            this.datasetId = datasetId;
            this.name = name;
            this.recordCount = recordCount;
            this.columnNames = columnNames;
            this.columnTypes = columnTypes;
            this.lastUpdatedAt = lastUpdatedAt;
        }
    }

    /**
     * Domain model: Query execution plan
     */
    public static class QueryPlan {
        public String planId;
        public String queryText;
        public String executionStrategy;
        public String[] steps;
        public long estimatedTimeMs;

        public QueryPlan() {}

        public QueryPlan(String planId, String queryText, String executionStrategy,
                String[] steps, long estimatedTimeMs) {
            this.planId = planId;
            this.queryText = queryText;
            this.executionStrategy = executionStrategy;
            this.steps = steps;
            this.estimatedTimeMs = estimatedTimeMs;
        }
    }

    /**
     * Domain model: Aggregation result
     */
    public static class AggregationResult {
        public String aggregationId;
        public String datasetId;
        public String aggregationType;
        public Object result;
        public String[] groupByColumns;
        public long cachedAt;

        public AggregationResult() {}

        public AggregationResult(String aggregationId, String datasetId, String aggregationType,
                Object result, String[] groupByColumns, long cachedAt) {
            this.aggregationId = aggregationId;
            this.datasetId = datasetId;
            this.aggregationType = aggregationType;
            this.result = result;
            this.groupByColumns = groupByColumns;
            this.cachedAt = cachedAt;
        }
    }
}
