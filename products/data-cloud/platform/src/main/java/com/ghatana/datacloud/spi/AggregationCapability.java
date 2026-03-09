package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.RecordQuery;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Extension interface for plugins supporting aggregation operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Optional capability for storage plugins that support aggregation queries.
 * Primarily used for TIMESERIES record types but can be used by any type.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * if (plugin instanceof AggregationCapability aggregation) {
 *     AggregationResult result = aggregation.aggregate(
 *         "tenantId",
 *         "cpu-metrics",
 *         AggregationQuery.builder()
 *             .timeRange(lastHour, now)
 *             .groupBy("host")
 *             .metric("value", AggregationType.AVG, "avg_cpu")
 *             .metric("value", AggregationType.MAX, "max_cpu")
 *             .bucket(TimeBucket.MINUTE)
 *             .build()
 *     ).getResult();
 *
 *     for (AggregationBucket bucket : result.buckets()) {
 *         System.out.println(bucket.timestamp() + ": " + bucket.values());
 *     }
 * }
 * }</pre>
 *
 * @see StoragePlugin
 * @see RecordQuery
 * @doc.type interface
 * @doc.purpose Aggregation capability
 * @doc.layer core
 * @doc.pattern Capability Interface
 */
public interface AggregationCapability {

    /**
     * Executes an aggregation query.
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @param query Aggregation query
     * @return Promise with aggregation result
     */
    Promise<AggregationResult> aggregate(
            String tenantId,
            String collectionName,
            AggregationQuery query
    );

    /**
     * Gets distinct values for a field.
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @param field Field name
     * @param limit Maximum values to return
     * @return Promise with distinct values
     */
    Promise<List<Object>> distinctValues(
            String tenantId,
            String collectionName,
            String field,
            int limit
    );

    /**
     * Aggregation query specification.
     */
    record AggregationQuery(
            java.time.Instant startTime,
            java.time.Instant endTime,
            String timeField,
            List<String> groupByFields,
            List<MetricSpec> metrics,
            RecordQuery.TimeBucket bucket,
            List<RecordQuery.FilterCondition> filters,
            Integer limit
            ) {

        public static AggregationQueryBuilder builder() {
            return new AggregationQueryBuilder();
        }

        public static class AggregationQueryBuilder {

            private java.time.Instant startTime;
            private java.time.Instant endTime;
            private String timeField = "timestamp";
            private List<String> groupByFields = List.of();
            private List<MetricSpec> metrics = new java.util.ArrayList<>();
            private RecordQuery.TimeBucket bucket;
            private List<RecordQuery.FilterCondition> filters = List.of();
            private Integer limit;

            public AggregationQueryBuilder timeRange(java.time.Instant start, java.time.Instant end) {
                this.startTime = start;
                this.endTime = end;
                return this;
            }

            public AggregationQueryBuilder timeField(String field) {
                this.timeField = field;
                return this;
            }

            public AggregationQueryBuilder groupBy(String... fields) {
                this.groupByFields = List.of(fields);
                return this;
            }

            public AggregationQueryBuilder metric(String field, RecordQuery.AggregationType type, String alias) {
                this.metrics.add(new MetricSpec(field, type, alias));
                return this;
            }

            public AggregationQueryBuilder metric(String field, RecordQuery.AggregationType type) {
                return metric(field, type, type.name().toLowerCase() + "_" + field);
            }

            public AggregationQueryBuilder bucket(RecordQuery.TimeBucket bucket) {
                this.bucket = bucket;
                return this;
            }

            public AggregationQueryBuilder filters(List<RecordQuery.FilterCondition> filters) {
                this.filters = filters;
                return this;
            }

            public AggregationQueryBuilder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public AggregationQuery build() {
                return new AggregationQuery(startTime, endTime, timeField, groupByFields,
                        metrics, bucket, filters, limit);
            }
        }
    }

    /**
     * Specification for a metric aggregation.
     */
    record MetricSpec(
            String field,
            RecordQuery.AggregationType type,
            String alias
            ) {

    }

    /**
     * Result of an aggregation query.
     */
    record AggregationResult(
            List<AggregationBucket> buckets,
            Map<String, Object> summary,
            long totalRecords,
            long processedRecords
            ) {

        public static AggregationResult empty() {
            return new AggregationResult(List.of(), Map.of(), 0, 0);
        }

        public static AggregationResult of(List<AggregationBucket> buckets) {
            return new AggregationResult(buckets, Map.of(),
                    buckets.stream().mapToLong(AggregationBucket::count).sum(),
                    buckets.stream().mapToLong(AggregationBucket::count).sum());
        }
    }

    /**
     * A single bucket in aggregation results.
     */
    record AggregationBucket(
            java.time.Instant timestamp,
            Map<String, Object> groupKeys,
            Map<String, Number> values,
            long count
            ) {

        public Number getValue(String alias) {
            return values.get(alias);
        }

        public Object getGroupKey(String field) {
            return groupKeys.get(field);
        }
    }
}
