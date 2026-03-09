package com.ghatana.datacloud;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * Unified query builder for all record types.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a fluent API for constructing queries against any collection,
 * regardless of record type. Supports:
 * <ul>
 * <li>Field-based filtering with various operators</li>
 * <li>Sorting and pagination</li>
 * <li>Time range queries (optimized for EVENT and TIMESERIES)</li>
 * <li>Stream/partition targeting (EVENT)</li>
 * <li>Aggregation (TIMESERIES)</li>
 * <li>Full-text search</li>
 * <li>Projection (field selection)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * // Simple query
 * RecordQuery query = RecordQuery.forCollection("users")
 *         .where("status", Operator.EQUALS, "active")
 *         .where("age", Operator.GREATER_THAN, 18)
 *         .orderBy("createdAt", SortDirection.DESC)
 *         .limit(100);
 *
 * // Time-range query (EVENT/TIMESERIES)
 * RecordQuery eventQuery = RecordQuery.forCollection("system-events")
 *         .inTimeRange(startTime, endTime)
 *         .inStream("audit-log")
 *         .limit(1000);
 *
 * // Aggregation query (TIMESERIES)
 * RecordQuery metricsQuery = RecordQuery.forCollection("cpu-metrics")
 *         .inTimeRange(lastHour, now)
 *         .groupBy("host")
 *         .aggregate(AggregationType.AVG, "value");
 * }</pre>
 *
 * @see Record
 * @see Collection
 * @doc.type class
 * @doc.purpose Unified query builder
 * @doc.layer core
 * @doc.pattern Builder
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Target collection name.
     */
    private String collectionName;

    /**
     * Tenant ID for multi-tenancy.
     */
    private String tenantId;

    /**
     * Filter conditions.
     */
    @Builder.Default
    private List<FilterCondition> filters = new ArrayList<>();

    /**
     * Logical operator between filters (AND/OR).
     */
    @Builder.Default
    private LogicalOperator filterOperator = LogicalOperator.AND;

    /**
     * Sort specifications.
     */
    @Builder.Default
    private List<SortSpec> sorts = new ArrayList<>();

    /**
     * Fields to return (null = all).
     */
    private List<String> projections;

    /**
     * Maximum records to return.
     */
    @Builder.Default
    private Integer limit = 100;

    /**
     * Records to skip.
     */
    @Builder.Default
    private Integer offset = 0;

    /**
     * Continuation token for keyset pagination.
     */
    private String continuationToken;

    // ==================== Time Range (EVENT/TIMESERIES) ====================
    /**
     * Start of time range (inclusive).
     */
    private Instant startTime;

    /**
     * End of time range (exclusive).
     */
    private Instant endTime;

    /**
     * Time field to filter on (default: createdAt).
     */
    private String timeField;

    // ==================== Stream/Partition (EVENT) ====================
    /**
     * Target stream name.
     */
    private String streamName;

    /**
     * Target partition IDs.
     */
    private List<Integer> partitionIds;

    /**
     * Starting offset.
     */
    private Long startOffset;

    /**
     * Ending offset.
     */
    private Long endOffset;

    // ==================== Aggregation (TIMESERIES) ====================
    /**
     * Group by fields.
     */
    private List<String> groupByFields;

    /**
     * Aggregation specifications.
     */
    private List<AggregationSpec> aggregations;

    /**
     * Time bucket for time-based grouping.
     */
    private TimeBucket timeBucket;

    // ==================== Full-text Search ====================
    /**
     * Full-text search query.
     */
    private String searchText;

    /**
     * Fields to search (null = all text fields).
     */
    private List<String> searchFields;

    // ==================== Supporting Types ====================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterCondition implements Serializable {

        private static final long serialVersionUID = 1L;
        private String field;
        private Operator operator;
        private Object value;
        private List<Object> values; // for IN/NOT_IN operators
    }

    public enum Operator {
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUALS,
        LESS_THAN,
        LESS_THAN_OR_EQUALS,
        IN,
        NOT_IN,
        LIKE,
        NOT_LIKE,
        STARTS_WITH,
        ENDS_WITH,
        CONTAINS,
        IS_NULL,
        IS_NOT_NULL,
        BETWEEN,
        REGEX
    }

    public enum LogicalOperator {
        AND,
        OR
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortSpec implements Serializable {

        private static final long serialVersionUID = 1L;
        private String field;
        @Builder.Default
        private SortDirection direction = SortDirection.ASC;
        @Builder.Default
        private NullHandling nullHandling = NullHandling.NULLS_LAST;
    }

    public enum SortDirection {
        ASC,
        DESC
    }

    public enum NullHandling {
        NULLS_FIRST,
        NULLS_LAST
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AggregationSpec implements Serializable {

        private static final long serialVersionUID = 1L;
        private AggregationType type;
        private String field;
        private String alias;
    }

    public enum AggregationType {
        COUNT,
        SUM,
        AVG,
        MIN,
        MAX,
        FIRST,
        LAST,
        DISTINCT_COUNT,
        PERCENTILE,
        STDDEV,
        VARIANCE
    }

    public enum TimeBucket {
        SECOND,
        MINUTE,
        FIVE_MINUTES,
        FIFTEEN_MINUTES,
        HOUR,
        DAY,
        WEEK,
        MONTH,
        QUARTER,
        YEAR
    }

    // ==================== Fluent API ====================
    public RecordQuery where(String field, Operator operator, Object value) {
        if (filters == null) {
            filters = new ArrayList<>();
        }
        filters.add(FilterCondition.builder()
                .field(field)
                .operator(operator)
                .value(value)
                .build());
        return this;
    }

    public RecordQuery whereIn(String field, List<Object> values) {
        if (filters == null) {
            filters = new ArrayList<>();
        }
        filters.add(FilterCondition.builder()
                .field(field)
                .operator(Operator.IN)
                .values(values)
                .build());
        return this;
    }

    public RecordQuery whereNotIn(String field, List<Object> values) {
        if (filters == null) {
            filters = new ArrayList<>();
        }
        filters.add(FilterCondition.builder()
                .field(field)
                .operator(Operator.NOT_IN)
                .values(values)
                .build());
        return this;
    }

    public RecordQuery orderBy(String field, SortDirection direction) {
        if (sorts == null) {
            sorts = new ArrayList<>();
        }
        sorts.add(SortSpec.builder()
                .field(field)
                .direction(direction)
                .build());
        return this;
    }

    public RecordQuery orderByAsc(String field) {
        return orderBy(field, SortDirection.ASC);
    }

    public RecordQuery orderByDesc(String field) {
        return orderBy(field, SortDirection.DESC);
    }

    public RecordQuery select(String... fields) {
        this.projections = Arrays.asList(fields);
        return this;
    }

    public RecordQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    public RecordQuery offset(int offset) {
        this.offset = offset;
        return this;
    }

    public RecordQuery page(int pageNumber, int pageSize) {
        this.limit = pageSize;
        this.offset = pageNumber * pageSize;
        return this;
    }

    // Time range methods
    public RecordQuery inTimeRange(Instant start, Instant end) {
        this.startTime = start;
        this.endTime = end;
        return this;
    }

    public RecordQuery after(Instant time) {
        this.startTime = time;
        return this;
    }

    public RecordQuery before(Instant time) {
        this.endTime = time;
        return this;
    }

    public RecordQuery onTimeField(String field) {
        this.timeField = field;
        return this;
    }

    // Stream methods (EVENT)
    public RecordQuery inStream(String streamName) {
        this.streamName = streamName;
        return this;
    }

    public RecordQuery inPartitions(Integer... partitions) {
        this.partitionIds = Arrays.asList(partitions);
        return this;
    }

    public RecordQuery fromOffset(Long offset) {
        this.startOffset = offset;
        return this;
    }

    public RecordQuery toOffset(Long offset) {
        this.endOffset = offset;
        return this;
    }

    // Aggregation methods (TIMESERIES)
    public RecordQuery groupBy(String... fields) {
        this.groupByFields = Arrays.asList(fields);
        return this;
    }

    public RecordQuery aggregate(AggregationType type, String field) {
        return aggregate(type, field, type.name().toLowerCase() + "_" + field);
    }

    public RecordQuery aggregate(AggregationType type, String field, String alias) {
        if (aggregations == null) {
            aggregations = new ArrayList<>();
        }
        aggregations.add(AggregationSpec.builder()
                .type(type)
                .field(field)
                .alias(alias)
                .build());
        return this;
    }

    public RecordQuery bucket(TimeBucket bucket) {
        this.timeBucket = bucket;
        return this;
    }

    // Full-text search methods
    public RecordQuery search(String text) {
        this.searchText = text;
        return this;
    }

    public RecordQuery searchIn(String... fields) {
        this.searchFields = Arrays.asList(fields);
        return this;
    }

    // ==================== Validation ====================
    public void validate() {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name is required");
        }
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative");
        }
        if (offset != null && offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if (startOffset != null && endOffset != null && startOffset > endOffset) {
            throw new IllegalArgumentException("Start offset must be less than end offset");
        }
    }
}
